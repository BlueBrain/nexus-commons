package ch.epfl.bluebrain.nexus.commons.iam

import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.headers.{HttpCredentials, OAuth2BearerToken}
import akka.http.scaladsl.model.{HttpResponse, StatusCodes, Uri}
import ch.epfl.bluebrain.nexus.commons.http.{HttpClient, UnexpectedUnsuccessfulHttpResponse}
import ch.epfl.bluebrain.nexus.commons.iam.acls.AccessControlList
import ch.epfl.bluebrain.nexus.commons.iam.auth.User
import ch.epfl.bluebrain.nexus.commons.iam.identity.Caller
import ch.epfl.bluebrain.nexus.commons.iam.identity.Caller._
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.UnauthorizedAccess
import journal.Logger

import scala.concurrent.{ExecutionContext, Future}

/**
  * Iam client contract.
  *
  * @tparam F the monadic effect type
  */
trait IamClient[F[_]] {

  /**
    * Retrieve the ''caller'' form the optional [[HttpCredentials]]
    *
    * @param optCredentials the optionally provided [[HttpCredentials]]
    */
  def getCaller(optCredentials: Option[OAuth2BearerToken]): F[Caller]

  /**
    * Retrieve the current ''acls'' for some particular ''resource''
    *
    * @param resource the resource against which to check the acls
    * @param caller   the implicitly available [[Caller]]
    */
  def getAcls(resource: Path)(implicit caller: Caller): F[AccessControlList]

}

object IamClient {
  private val log = Logger[this.type]

  final def apply()(implicit ec: ExecutionContext,
                    aclClient: HttpClient[Future, AccessControlList],
                    userClient: HttpClient[Future, User],
                    iamUri: IamUri): IamClient[Future] = new IamClient[Future] {

    override def getCaller(optCredentials: Option[OAuth2BearerToken]) =
      optCredentials
        .map { cred =>
          userClient(getRequest("oauth2/user", optCredentials))
            .map[Caller](AuthenticatedCaller(cred, _))
            .recoverWith {
              case UnexpectedUnsuccessfulHttpResponse(HttpResponse(StatusCodes.Unauthorized, _, _, _)) =>
                Future.failed(UnauthorizedAccess)
              case ur: UnexpectedUnsuccessfulHttpResponse =>
                log.warn(
                  s"Received an unexpected response status code '${ur.response.status}' from IAM when attempting to retrieve the user information")
                Future.failed(ur)
              case err =>
                log.error(s"IAM returned an exception when attempting to retrieve user information", err)
                Future.failed(err)
            }
        }
        .getOrElse(Future.successful(AnonymousCaller))

    override def getAcls(resource: Path)(implicit caller: Caller) =
      aclClient(getRequest(s"acls/$resource", caller.credential))
        .recoverWith {
          case UnexpectedUnsuccessfulHttpResponse(HttpResponse(StatusCodes.Unauthorized, _, _, _)) =>
            Future.failed(UnauthorizedAccess)
          case ur: UnexpectedUnsuccessfulHttpResponse =>
            log.warn(
              s"Received an unexpected response status code '${ur.response.status}' from IAM when attempting to retrieve permission for resource '$resource' and caller identities '${caller.identities}'")
            Future.failed(ur)
          case err =>
            log.error(
              s"IAM returned an exception when attempting to retrieve permission for resource '$resource' and caller identities '${caller.identities}'",
              err)
            Future.failed(err)
        }

    private def getRequest(path: String, credentials: Option[HttpCredentials]) = {
      val request = Get(Uri(s"${iamUri.value}/$path"))
      credentials
        .map(request.addCredentials(_))
        .getOrElse(request)
    }
  }
}
