package ch.epfl.bluebrain.nexus.commons.iam.auth

import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import io.circe.{Decoder, Encoder}

/**
  * Base enumeration type for the internal user representation.
  */
sealed trait User extends Product with Serializable {
  def identities: Set[Identity]
}

/**
  * Type holding the entire set of ''identities'' that an authenticated user belongs to.
  */
final case class AuthenticatedUser(identities: Set[Identity]) extends User

/**
  * Singleton representing any unauthenticated user.
  */
case object AnonymousUser extends User {
  override val identities = Set(Identity.Anonymous())
}

object User {

  import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity

  implicit def userDecoder(implicit D: Decoder[Identity], C: Configuration): Decoder[User] = deriveDecoder[User]

  implicit def userEncoder(implicit E: Encoder[Identity], C: Configuration): Encoder[User] = deriveEncoder[User]

}
