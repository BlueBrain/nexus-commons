package ch.epfl.bluebrain.nexus.commons.iam.identity

import akka.http.scaladsl.model.headers.HttpCredentials
import ch.epfl.bluebrain.nexus.commons.iam.auth.User
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.Anonymous

sealed trait Caller extends Product with Serializable {

  /**
    * @return the identities this ''caller'' belongs to
    */
  def identities: Set[Identity]

  /**
    * @return the ''credentials'' used by the caller to authenticate
    */
  def credentials: Option[HttpCredentials]
}
object Caller {
  final case object AnonymousCaller extends Caller {
    override val identities  = Set(Anonymous)
    override val credentials = None
  }
  final case class AuthenticatedCaller(cred: HttpCredentials, user: User) extends Caller {
    override val identities: Set[Identity] = user.identities
    override val credentials               = Some(cred)
  }
}
