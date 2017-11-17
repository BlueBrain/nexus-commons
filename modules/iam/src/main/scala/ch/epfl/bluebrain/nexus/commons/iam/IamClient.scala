package ch.epfl.bluebrain.nexus.commons.iam

import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import ch.epfl.bluebrain.nexus.commons.http.{HttpClient, UnexpectedUnsuccessfulHttpResponse}
import ch.epfl.bluebrain.nexus.commons.iam.acls.Path._
import ch.epfl.bluebrain.nexus.commons.iam.acls.{AccessControlList, Path}
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
    * Retrieve the ''caller'' form the optional [[OAuth2BearerToken]]
    *
    * @param optCredentials the optionally provided [[OAuth2BearerToken]]
    * @param filterGroups   if true, will only return groups currently in use in IAM
    *
    */
  def getCaller(optCredentials: Option[OAuth2BearerToken], filterGroups: Boolean = false): F[Caller]

  /**
    * Retrieve the current ''acls'' for some particular ''resource''
    *
    * @param resource the resource against which to check the acls
    * @param credentials    a possibly available token
    */
  def getAcls(resource: Path)(implicit credentials: Option[OAuth2BearerToken]): F[AccessControlList]
}

object IamClient {
  private val log               = Logger[this.type]
  private val Acls              = Path("acls")
  private val User              = Path("oauth2/user")
  private val filterGroupsParam = "filterGroups"

  final def apply()(implicit ec: ExecutionContext,
                    aclClient: HttpClient[Future, AccessControlList],
                    userClient: HttpClient[Future, User],
                    iamUri: IamUri): IamClient[Future] = new IamClient[Future] {

    override def getCaller(optCredentials: Option[OAuth2BearerToken], filterGroups: Boolean = false) =
      optCredentials
        .map { cred =>
          userClient(requestFrom(optCredentials, User, Query(filterGroupsParam -> filterGroups.toString)))
            .map[Caller](AuthenticatedCaller(cred, _))
            .recoverWith[Caller] { case e => recover(e, User) }
        }
        .getOrElse(Future.successful(AnonymousCaller()))

    override def getAcls(resource: Path)(implicit credentials: Option[OAuth2BearerToken]) = {
      aclClient(requestFrom(credentials, Acls ++ resource))
        .recoverWith[AccessControlList] { case e => recover(e, resource) }
    }
    def recover(th: Throwable, resource: Path) = th match {
      case UnexpectedUnsuccessfulHttpResponse(HttpResponse(StatusCodes.Unauthorized, _, _, _)) =>
        Future.failed(UnauthorizedAccess)
      case ur: UnexpectedUnsuccessfulHttpResponse =>
        log.warn(
          s"Received an unexpected response status code '${ur.response.status}' from IAM when attempting to perform and operation on a resource '$resource'")
        Future.failed(ur)
      case err =>
        log.error(
          s"Received an unexpected exception from IAM when attempting to perform and operation on a resource '$resource'",
          err)
        Future.failed(err)
    }

    private def requestFrom(credentials: Option[OAuth2BearerToken], path: Path, query: Query = Query.Empty) = {
      val uriPath: Path = iamUri.value.path
      val request       = Get(iamUri.value.copy(path = uriPath ++ path).withQuery(query))
      credentials.map(request.addCredentials).getOrElse(request)
    }
  }
}
