package ch.epfl.bluebrain.nexus.commons.iam.identity

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import ch.epfl.bluebrain.nexus.commons.iam.auth.User
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.Anonymous

/**
  * Base enumeration type for caller classes.
  */
sealed trait Caller extends Product with Serializable {

  /**
    * @return the identities this ''caller'' belongs to
    */
  def identities: Set[Identity]

  /**
    * @return the ''credential'' used by the caller to authenticate
    */
  def credential: Option[OAuth2BearerToken]
}
object Caller {

  /**
    * An anonymous caller.
    */
  final case object AnonymousCaller extends Caller {
    override val identities = Set(Anonymous)
    override val credential = None
  }

  /**
    * An authenticated caller.
    *
    * @param credential the identities this ''caller'' belongs to
    * @param identities the ''credential'' used by the caller to authenticate
    */
  final case class AuthenticatedCaller(credential: Option[OAuth2BearerToken], identities: Set[Identity])
      extends Caller {
    def this(cred: OAuth2BearerToken, user: User) {
      this(Some(cred), user.identities)
    }
  }
  object AuthenticatedCaller {

    /**
      * Construct a [[AuthenticatedCaller]] from provided ''credential'' and ''user''.
      *
      * @param credential the identities this ''caller'' belongs to
      * @param user       the user information about this caller
      */
    final def apply(credential: OAuth2BearerToken, user: User): AuthenticatedCaller =
      new AuthenticatedCaller(credential, user)
  }
}
