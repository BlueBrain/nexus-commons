package ch.epfl.bluebrain.nexus.commons.iam.identity

import cats.Show
import io.circe._
import io.circe.generic.extras._
import io.circe.generic.extras.semiauto._

/**
  * Base enumeration type for identity classes.
  */
sealed trait Identity extends Product with Serializable

/**
  * Represents identities that were authenticated from a third party origin.
  */
trait Authenticated

object Identity {

  /**
    * The ''user'' identity class.
    *
    * @param realm the authentication's realm name
    * @param sub   the JWT ''sub'' field
    */
  final case class UserRef(realm: String, sub: String) extends Identity with Authenticated

  /**
    * The ''group'' identity class.
    *
    * @param realm the authentication's realm name
    * @param group the group name
    */
  final case class GroupRef(realm: String, group: String) extends Identity with Authenticated

  /**
    * The ''authenticated'' identity class that represents anyone authenticated from ''origin''.
    *
    * @param realm the authentication's realm name
    */
  final case class AuthenticatedRef(realm: Option[String]) extends Identity with Authenticated

  /**
    * The ''anonymous'' identity singleton that covers unknown and unauthenticated users.
    */
  final case object Anonymous extends Identity

  implicit val identityShow: Show[Identity] = Show.fromToString[Identity]

  implicit val config: Configuration              = Configuration.default.withDiscriminator("type")
  implicit val printer                            = Printer.noSpaces.copy(dropNullKeys = true)
  implicit val identityEncoder: Encoder[Identity] = deriveEncoder[Identity]
  implicit val identityDecoder: Decoder[Identity] = deriveDecoder[Identity]

}
