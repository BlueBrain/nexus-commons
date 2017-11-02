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
    * @return the ''credentials'' used by the caller to authenticate
    */
  def credentials: Option[OAuth2BearerToken]
}
object Caller {

  /**
    * An anonymous caller.
    */
  final case object AnonymousCaller extends Caller {
    override val identities  = Set(Anonymous())
    override val credentials = None
  }

  /**
    * An authenticated caller.
    *
    * @param credentials the identities this ''caller'' belongs to
    * @param identities the ''credentials'' used by the caller to authenticate
    */
  final case class AuthenticatedCaller(credentials: Option[OAuth2BearerToken], identities: Set[Identity]) extends Caller

  object AuthenticatedCaller {

    /**
      * Construct a [[AuthenticatedCaller]] from provided ''credentials'' and ''user''.
      *
      * @param credentials the identities this ''caller'' belongs to
      * @param user       the user information about this caller
      */
    final def apply(credentials: OAuth2BearerToken, user: User): AuthenticatedCaller =
      new AuthenticatedCaller(Some(credentials), user.identities)
  }

  /**
    * Implicit conversion from implicitly available ''available'' to optional [[OAuth2BearerToken]]
    *
    * @param caller the implicitly available caller
    * @return an optional [[OAuth2BearerToken]]
    */
  final implicit def callerToToken(implicit caller: Caller): Option[OAuth2BearerToken] =
    caller.credentials
}
