package ch.epfl.bluebrain.nexus.commons.iam.identity

import cats.Show
import io.circe._
import io.circe.generic.extras.Configuration

/**
  * Base enumeration type for identity classes.
  */
sealed trait Identity extends Product with Serializable {

  /**
    * @return the optionally available unique identity identifier
    */
  def id: Option[IdentityId]
}

/**
  * Represents identities that were authenticated from a third party origin.
  */
trait Authenticated

object Identity {

  val authenticatedKey = "authenticated"
  val anonymousKey     = "anonymous"

  /**
    * The ''user'' identity class.
    *
    * @param id    the optionally available unique identity identifier
    * @param realm the authentication's realm name
    * @param sub   the JWT ''sub'' field
    */
  final case class UserRef(id: Option[IdentityId], realm: String, sub: String) extends Identity with Authenticated

  object UserRef {

    /**
      * Constructs a ''UserRef'' with the default ''IdentityId''
      *
      * @param realm the authentication's realm name
      * @param sub   the JWT ''sub'' field
      */
    final def apply(realm: String, sub: String): UserRef =
      UserRef(Some(IdentityId(s"realms/$realm/users/$sub")), realm, sub)
  }

  /**
    * The ''group'' identity class.
    *
    * @param id    the optionally available unique identity identifier
    * @param realm the authentication's realm name
    * @param group the group name
    */
  final case class GroupRef(id: Option[IdentityId], realm: String, group: String) extends Identity with Authenticated

  object GroupRef {

    /**
      * Constructs a ''GroupRef'' with the default ''IdentityId''
      *
      * @param realm the authentication's realm name
      * @param group the group name
      */
    final def apply(realm: String, group: String): GroupRef =
      GroupRef(Some(IdentityId(s"realms/$realm/groups/$group")), realm, group)
  }

  /**
    * The ''authenticated'' identity class that represents anyone authenticated from ''origin''.
    *
    * @param id    the optionally available unique identity identifier
    * @param realm the authentication's realm name
    */
  final case class AuthenticatedRef(id: Option[IdentityId], realm: Option[String]) extends Identity with Authenticated

  object AuthenticatedRef {

    /**
      * Constructs a ''AuthenticatedRef'' with the default ''IdentityId''
      *
      * @param realm the authentication's realm name
      */
    final def apply(realm: Option[String]): AuthenticatedRef =
      realm match {
        case Some(r) => AuthenticatedRef(Some(IdentityId(s"realms/$r/$authenticatedKey")), realm)
        case None    => AuthenticatedRef(Some(IdentityId(s"$authenticatedKey")), realm)
      }

  }

  /**
    * The ''anonymous'' identity singleton that covers unknown and unauthenticated users.
    *
    * @param id    the optionally available unique identity identifier
    */
  final case class Anonymous(id: Option[IdentityId]) extends Identity

  object Anonymous {

    /**
      * Constructs a ''Anonymous'' with the default ''IdentityId''
      */
    final def apply(): Anonymous = Anonymous(Some(IdentityId(s"$anonymousKey")))
  }

  implicit val identityShow: Show[Identity] = Show.fromToString[Identity]

  object serialization {
    import io.circe.generic.extras.semiauto._
    import ch.epfl.bluebrain.nexus.commons.iam.identity.IdentityId._
    implicit val config: Configuration              = Configuration.default.withDiscriminator("type")
    implicit val printer                            = Printer.noSpaces.copy(dropNullKeys = true)
    implicit val identityEncoder: Encoder[Identity] = deriveEncoder[Identity]
    implicit val identityDecoder: Decoder[Identity] = deriveDecoder[Identity]
  }

}
