package ch.epfl.bluebrain.nexus.commons.iam.auth

import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import io.circe.{Decoder, Encoder}

/**
  * Detailed user information.
  *
  * @param sub               the subject (typically corresponds to the user id)
  * @param name              the name of the user
  * @param preferredUsername the preferred user name (used for login purposes)
  * @param givenName         the given name
  * @param familyName        the family name
  * @param email             the email
  * @param groups            the collection of groups that this user belongs to
  */
final case class UserInfo(sub: String,
                          name: String,
                          preferredUsername: String,
                          givenName: String,
                          familyName: String,
                          email: String,
                          groups: Set[String]) {

  /**
    * @param realm the authentication provider realm
    * @return the set of all [[Identity]] references that this user belongs to
    */
  def identities(realm: String): Set[Identity] =
    Set(Anonymous, AuthenticatedRef(Some(realm)), UserRef(realm, sub)) ++ groups.map(g => GroupRef(realm, g))

  /**
    * Converts this object to a [[User]] instance.
    * @param realm the authentication provider realm
    */
  def toUser(realm: String): User = AuthenticatedUser(identities(realm))
}

object UserInfo {

  implicit val config: Configuration = Configuration.default.withSnakeCaseKeys

  implicit val userInfoDecoder: Decoder[UserInfo] = deriveDecoder[UserInfo]

  implicit val userInfoEncoder: Encoder[UserInfo] = deriveEncoder[UserInfo]
}
