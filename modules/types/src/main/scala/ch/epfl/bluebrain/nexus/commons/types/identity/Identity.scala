package ch.epfl.bluebrain.nexus.commons.types.identity

import cats.Show
import ch.epfl.bluebrain.nexus.commons.types.identity.IdentityId.IdentityIdPrefix
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
  final case class UserRef private (id: IdentityId) extends Identity with Authenticated {
    private val regex: Regex = s"^.*realms/$allowedInput/users/$allowedInput".r

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
    final def apply(realm: String, sub: String)(implicit prefix: IdentityIdPrefix = IdentityIdPrefix.Empty): UserRef =
      UserRef(IdentityId(prefix.appendAsPath(s"realms/$realm/users/$sub")))
  }

  /**
    * The ''group'' identity class.
    *
    * @param id    the unique identity identifier
    */
  final case class GroupRef private (id: IdentityId) extends Identity with Authenticated {
    private val regex: Regex = s"^.*realms/$allowedInput/groups/$allowedInput".r

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
    final def apply(realm: String, group: String)(
        implicit prefix: IdentityIdPrefix = IdentityIdPrefix.Empty): GroupRef =
      GroupRef(IdentityId(prefix.appendAsPath(s"realms/$realm/groups/$group")))
  }

  /**
    * The ''authenticated'' identity class that represents anyone authenticated from ''origin''.
    *
    * @param id    the unique identity identifier
    */
  final case class AuthenticatedRef private (id: IdentityId) extends Identity with Authenticated {
    private val regex: Regex = s"^.*realms/$allowedInput/$authenticatedKey".r

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
    def apply(realm: Option[String])(implicit prefix: IdentityIdPrefix = IdentityIdPrefix.Empty): AuthenticatedRef =
      realm match {
        case Some(r) => AuthenticatedRef(IdentityId(prefix.appendAsPath(s"realms/$r/$authenticatedKey")))
        case None    => AuthenticatedRef(IdentityId(prefix.appendAsPath(s"$authenticatedKey")))
      }

  }

  /**
    * The ''anonymous'' identity singleton that covers unknown and unauthenticated users.
    *
    * @param id    the unique identity identifier
    */
  final case class Anonymous private (id: IdentityId) extends Identity

  object Anonymous {

    /**
      * Constructs a ''Anonymous'' with the default ''IdentityId''
      */
    final def apply()(implicit prefix: IdentityIdPrefix = IdentityIdPrefix.Empty): Anonymous =
      Anonymous(IdentityId(prefix.appendAsPath(anonymousKey)))
  }

  implicit val identityShow: Show[Identity]                                  = Show.fromToString[Identity]
  implicit def identityEncoder(implicit c: Configuration): Encoder[Identity] = deriveEncoder[Identity]
  implicit def identityDecoder(implicit c: Configuration): Decoder[Identity] = deriveDecoder[Identity]
}
