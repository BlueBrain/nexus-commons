package ch.epfl.bluebrain.nexus.sourcing

import java.util.UUID
import ch.epfl.bluebrain.nexus.sourcing.FixturesAggregate.Event._
import ch.epfl.bluebrain.nexus.sourcing.FixturesAggregate.Command._
import ch.epfl.bluebrain.nexus.sourcing.FixturesAggregate.State._

object FixturesAggregate {

  val own     = Permissions(Set("own"))
  val read    = Permissions(Set("read"))
  val ownRead = Permissions(Set("own", "read"))

  val initial: State = Initial

  def genId: String = UUID.randomUUID().toString.toLowerCase

  final case class Permissions(values: Set[String]) {
    def --(permissions: Permissions): Permissions = Permissions(values -- permissions.values)
    def ++(permissions: Permissions): Permissions = Permissions(values ++ permissions.values)
  }

  sealed trait State extends Product with Serializable
  object State {
    final case object Initial                          extends State
    final case class Current(permissions: Permissions) extends State
  }

  sealed trait Event extends Product with Serializable
  object Event {
    final case class PermissionsWritten(permissions: Permissions)    extends Event
    final case class PermissionsAppended(permissions: Permissions)   extends Event
    final case class PermissionsSubtracted(permissions: Permissions) extends Event
    final case object PermissionsDeleted                             extends Event
  }

  sealed trait Command extends Product with Serializable
  object Command {
    final case class WritePermissions(permissions: Permissions)    extends Command
    final case class AppendPermissions(permissions: Permissions)   extends Command
    final case class SubtractPermissions(permissions: Permissions) extends Command
    final case object DeletePermissions                            extends Command
  }

  final case class Rejection(reason: String)

  def next(state: State, event: Event): State = (state, event) match {
    case (_, PermissionsWritten(perms))                => Current(perms)
    case (_, PermissionsDeleted)                       => Initial
    case (Initial, PermissionsAppended(perms))         => Current(perms)
    case (Current(curr), PermissionsAppended(perms))   => Current(curr ++ perms)
    case (Initial, PermissionsSubtracted(_))           => Initial
    case (Current(curr), PermissionsSubtracted(perms)) => Current(curr -- perms)
  }

  def eval(state: State, cmd: Command): Either[Rejection, Event] = (state, cmd) match {
    case (_, WritePermissions(perms)) => Right(PermissionsWritten(perms))

    case (Initial, DeletePermissions) => Left(Rejection("Unable to delete permissions, no permissions exist"))
    case (_, DeletePermissions)       => Right(PermissionsDeleted)

    case (Initial, SubtractPermissions(_)) => Left(Rejection("Unable to subtract permissions, no permissions defined"))
    case (Current(curr), SubtractPermissions(perms)) =>
      val intersect = curr.values.intersect(perms.values)
      if (intersect.nonEmpty) Right(PermissionsSubtracted(Permissions(intersect)))
      else Left(Rejection("Unable to subtract permissions, no available permissions to subtract from"))

    case (Initial, AppendPermissions(perms)) =>
      Right(PermissionsAppended(perms))
    case (Current(curr), AppendPermissions(perms)) =>
      val delta = perms.values -- curr.values
      if (delta.nonEmpty) Right(PermissionsAppended(Permissions(delta)))
      else Left(Rejection("Unable to append permissions, no new permissions specified"))
  }
}
