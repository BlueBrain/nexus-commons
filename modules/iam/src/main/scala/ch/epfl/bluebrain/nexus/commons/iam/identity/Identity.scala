package ch.epfl.bluebrain.nexus.commons.iam.identity

import cats.Show
import io.circe._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.{deriveDecoder, deriveEncoder}

import scala.util.matching.Regex

/**
  * Base enumeration type for identity classes.
  */
sealed trait Identity extends Product with Serializable {

  /**
    * @return the unique identity identifier
    */
  def id: IdentityId
}

/**
  * Represents identities that were authenticated from a third party origin.
  */
trait Authenticated

object Identity {

  val authenticatedKey     = "authenticated"
  val anonymousKey         = "anonymous"
  private val allowedInput = "([^/]*)"

  /**
    * The ''user'' identity class.
    *
    * @param id    the unique identity identifier
    */
  final case class UserRef(id: IdentityId) extends Identity with Authenticated {
    private val regex: Regex = s"^.*realms/$allowedInput/users/$allowedInput".r
    require(regex.findFirstIn(id.id).isDefined)

    /**
      * the authentication's realm name
      */
    val realm: String = id.id match {
      case regex(r, _) => r
    }

    /**
      * the JWT ''sub'' field
      */
    val sub: String = id.id match {
      case regex(_, g) => g
    }
  }

  object UserRef {

    /**
      * Constructs a ''UserRef'' with the default ''IdentityId''
      *
      * @param realm the authentication's realm name
      * @param sub   the JWT ''sub'' field
      */
    final def apply(realm: String, sub: String): UserRef =
      UserRef(IdentityId(s"realms/$realm/users/$sub"))
  }

  /**
    * The ''group'' identity class.
    *
    * @param id    the unique identity identifier
    */
  final case class GroupRef(id: IdentityId) extends Identity with Authenticated {
    private val regex: Regex = s"^.*realms/$allowedInput/groups/$allowedInput".r
    require(regex.findFirstIn(id.id).isDefined)

    /**
      * the authentication's realm name
      */
    val realm: String = id.id match {
      case regex(r, _) => r
    }

    /**
      * the group name
      */
    val group: String = id.id match {
      case regex(_, g) => g
    }

  }

  object GroupRef {

    /**
      * Constructs a ''GroupRef'' with the default ''IdentityId''
      *
      * @param realm the authentication's realm name
      * @param group the group name
      */
    final def apply(realm: String, group: String): GroupRef =
      GroupRef(IdentityId(s"realms/$realm/groups/$group"))
  }

  /**
    * The ''authenticated'' identity class that represents anyone authenticated from ''origin''.
    *
    * @param id    the unique identity identifier
    */
  final case class AuthenticatedRef(id: IdentityId) extends Identity with Authenticated {
    private val regex: Regex = s"^.*realms/$allowedInput/$authenticatedKey".r
    require(id.id.trim.endsWith(authenticatedKey))

    /**
      * the authentication's realm name
      */
    val realm: Option[String] = id.id match {
      case regex(r) => Some(r)
      case _        => None
    }

  }

  object AuthenticatedRef {

    /**
      * Constructs a ''AuthenticatedRef'' with the default ''IdentityId''
      *
      * @param realm the authentication's realm name
      */
    final def apply(realm: Option[String]): AuthenticatedRef =
      realm match {
        case Some(r) => AuthenticatedRef(IdentityId(s"realms/$r/$authenticatedKey"))
        case None    => AuthenticatedRef(IdentityId(s"$authenticatedKey"))
      }

  }

  /**
    * The ''anonymous'' identity singleton that covers unknown and unauthenticated users.
    *
    * @param id    the unique identity identifier
    */
  final case class Anonymous(id: IdentityId) extends Identity {
    require(id.id.trim.endsWith(anonymousKey))
  }

  object Anonymous {

    /**
      * Constructs a ''Anonymous'' with the default ''IdentityId''
      */
    final def apply(): Anonymous = Anonymous(IdentityId(s"$anonymousKey"))
  }

  implicit val identityShow: Show[Identity]       = Show.fromToString[Identity]
  private implicit val config: Configuration      = Configuration.default.withDiscriminator("type")
  implicit val identityEncoder: Encoder[Identity] = deriveEncoder[Identity]
  implicit val identityDecoder: Decoder[Identity] = deriveDecoder[Identity]
}
