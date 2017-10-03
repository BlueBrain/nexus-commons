package ch.epfl.bluebrain.nexus.service.commons.persistence

import akka.persistence.journal.{Tagged, WriteEventAdapter}

object Fixture {

  sealed trait Event
  final case object Executed        extends Event
  final case object OtherExecuted   extends Event
  final case object AnotherExecuted extends Event

  sealed trait Cmd
  final case object Execute        extends Cmd
  final case object ExecuteOther   extends Cmd
  final case object ExecuteAnother extends Cmd

  sealed trait State
  final case object Perpetual extends State

  sealed trait Rejection
  final case object Reject extends Rejection

  class TaggingAdapter extends WriteEventAdapter {
    override def manifest(event: Any): String = ""
    override def toJournal(event: Any): Any = event match {
      case Executed        => Tagged(event, Set("executed"))
      case OtherExecuted   => Tagged(event, Set("other"))
      case AnotherExecuted => Tagged(event, Set("another"))
    }
  }

  val initial: State                          = Perpetual
  def next(state: State, event: Event): State = Perpetual
  def eval(state: State, cmd: Cmd): Either[Rejection, Event] = cmd match {
    case Execute        => Right(Executed)
    case ExecuteOther   => Right(OtherExecuted)
    case ExecuteAnother => Right(AnotherExecuted)
  }
}
