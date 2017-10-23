package ch.epfl.bluebrain.nexus.commons.iam.types.identity

import ch.epfl.bluebrain.nexus.commons.iam.types.auth.User
import ch.epfl.bluebrain.nexus.commons.iam.types.identity.Identity.Anonymous

sealed trait Caller extends Product with Serializable {

  /**
    * @return Set of [[Identity]]s of the caller
    */
  def identities: Set[Identity]

  /**
    * @return optional token from the caller
    */
  def accessToken: Option[String]
}

object Caller {
  final case object AnonymousCaller extends Caller {
    override def identities: Set[Identity] = Set(Anonymous)

    override def accessToken: Option[String] = None
  }
  final case class AuthenticatedCaller(user: User, token: String) extends Caller {
    override def identities: Set[Identity]   = user.identities
    override def accessToken: Option[String] = Some(token)

  }
}
