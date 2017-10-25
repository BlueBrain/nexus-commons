package ch.epfl.bluebrain.nexus.commons.iam

import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.HttpCredentials
import cats.syntax.show._
import cats.{MonadError, Show}
import ch.epfl.bluebrain.nexus.commons.http.{HttpClient, UnexpectedUnsuccessfulHttpResponse}
import ch.epfl.bluebrain.nexus.commons.iam.acls.AccessControlList
import ch.epfl.bluebrain.nexus.commons.iam.auth.User
import ch.epfl.bluebrain.nexus.commons.iam.identity.Caller
import ch.epfl.bluebrain.nexus.commons.iam.identity.Caller._
import ch.epfl.bluebrain.nexus.commons.types.CommonRejections.UnauthorizedAccess
import journal.Logger

import scala.concurrent.{ExecutionContext, Future}

trait IamClient[F[_]] {

  /**
    * Retrieve the ''caller'' form the optional [[HttpCredentials]]
    *
    * @param optCredentials the optionally provided [[HttpCredentials]]
    */
  def getCaller(optCredentials: Option[HttpCredentials]): F[Caller]

  /**
    * Retrieve the current ''acls'' for some particular ''resource''
    *
    * @param resource the resource against which to check the acls
    * @param caller   the implicitly available [[Caller]]
    */
  def getAcls(resource: String)(implicit caller: Caller): F[AccessControlList]

  /**
    * Retrieve the current ''acls'' for some particular ''resource'' with some ''prefix''
    *
    * @param prefix   the prefix the prepend to the resource
    * @param resource the resource against which to retrieve the acls
    * @param caller   the implicitly available [[Caller]]
    */
  def getAcls[Id](prefix: String, resource: Id)(implicit caller: Caller, S: Show[Id]): F[AccessControlList]

}

object IamClient {

  implicit def akkaHttpIamClient(implicit ec: ExecutionContext,
                                 aclClient: HttpClient[Future, AccessControlList],
                                 userClient: HttpClient[Future, User],
                                 iamUri: IamUri,
                                 F: MonadError[Future, Throwable]) = new IamClient[Future] {
    private val log = Logger[this.type]

    override def getCaller(optCredentials: Option[HttpCredentials]) =
      optCredentials
        .map { cred =>
          userClient(getRequest("oauth2/user", optCredentials))
            .map[Caller](AuthenticatedCaller(cred, _))
            .recoverWith {
              case ur: UnexpectedUnsuccessfulHttpResponse =>
                log.warn(
                  s"Received an unexpected response status code '${ur.response.status}' from IAM when attempting to retrieve the user information")
                F.raiseError(UnauthorizedAccess)
              case err =>
                log.error(s"IAM returned an exception when attempting to retrieve user information", err)
                F.raiseError(err)
            }
        }
        .getOrElse(F.pure(AnonymousCaller))

    override def getAcls(resource: String)(implicit caller: Caller) =
      aclClient(getRequest(s"acls/$resource", caller.credentials))
        .recoverWith {
          case ur: UnexpectedUnsuccessfulHttpResponse =>
            log.warn(
              s"Received an unexpected response status code '${ur.response.status}' from IAM when attempting to retrieve permission for resource '$resource' and caller identities '${caller.identities}'")
            F.raiseError(ur)
          case err =>
            log.error(
              s"IAM returned an exception when attempting to retrieve permission for resource '$resource' and caller identities '${caller.identities}'",
              err)
            F.raiseError(err)
        }

    override def getAcls[Id](prefix: String, resource: Id)(implicit caller: Caller, S: Show[Id]) =
      getAcls(s"$prefix/${resource.show}")

    private def getRequest(path: String, credentials: Option[HttpCredentials]) = {
      val request = Get(Uri(s"${iamUri.value}/$path"))
      credentials
        .map(request.addCredentials(_))
        .getOrElse(request)
    }
  }
}
