package ch.epfl.bluebrain.nexus.commons.iam

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpMessage.DiscardedEntity
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.TestKit
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.iam.IamClientSpec._
import ch.epfl.bluebrain.nexus.commons.iam.acls.Permission.{Own, Read, Write}
import ch.epfl.bluebrain.nexus.commons.iam.acls.{AccessControl, AccessControlList, Path, Permissions}
import ch.epfl.bluebrain.nexus.commons.iam.auth.{AuthenticatedUser, User}
import ch.epfl.bluebrain.nexus.commons.iam.identity.Caller.{AnonymousCaller, AuthenticatedCaller}
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.{AuthenticatedRef, GroupRef, UserRef}
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.UnauthorizedAccess
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import _root_.io.circe.Encoder
import _root_.io.circe.generic.extras.Configuration
import _root_.io.circe.generic.extras.auto._
import _root_.io.circe.syntax._
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfter, Matchers, WordSpecLike}
import ch.epfl.bluebrain.nexus.commons.iam.identity.Caller._
import scala.concurrent.Future
import scala.concurrent.duration._

class IamClientSpec
    extends TestKit(ActorSystem("IamClientSpec"))
    with WordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfter {

  private implicit val config                = Configuration.default.withDiscriminator("type")
  private implicit val ec                    = system.dispatcher
  private implicit val mt: ActorMaterializer = ActorMaterializer()
  private implicit val iamUri                = IamUri("http://localhost:8080")
  private val credentials                    = OAuth2BearerToken(ValidToken)
  private val authUser: User = AuthenticatedUser(
    Set(GroupRef("BBP", "group1"), GroupRef("BBP", "group2"), UserRef("realm", "f:someUUID:username")))

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(5 seconds, 200 milliseconds)

  "An IamClient" should {

    "return unathorized whenever the token is wrong" in {
      implicit val cl         = fixedClient[None.type]()
      implicit val httpClient = HttpClient.withAkkaUnmarshaller[User]
      val response            = IamClient().getCaller(Some(OAuth2BearerToken("invalidToken")))
      ScalaFutures.whenReady(response.failed, Timeout(patienceConfig.timeout)) { e =>
        e shouldBe a[UnauthorizedAccess.type]
      }
    }

    "return anonymous caller whenever there is no token provided" in {
      implicit val cl         = fixedClient[None.type]()
      implicit val httpClient = HttpClient.withAkkaUnmarshaller[User]
      IamClient().getCaller(None).futureValue shouldEqual AnonymousCaller()
    }

    "return an authenticated caller whenever the token provided is correct" in {

      implicit val cl         = fixedClient(authA = Some(authUser))
      implicit val httpClient = HttpClient.withAkkaUnmarshaller[User]

      IamClient().getCaller(Some(credentials)).futureValue shouldEqual AuthenticatedCaller(credentials, authUser)
    }

    "return expected acls whenever the caller is authenticated" in {
      val aclAuth             = AccessControlList(Set(AccessControl(GroupRef("BBP", "group1"), Permissions(Own, Read, Write))))
      implicit val cl         = fixedClient(authA = Some(aclAuth))
      implicit val httpClient = HttpClient.withAkkaUnmarshaller[AccessControlList]

      implicit val caller = AuthenticatedCaller(credentials, authUser)
      IamClient().getAcls(Path("/prefix/some/resource/one")).futureValue shouldEqual aclAuth
    }
    "return expected acls whenever the caller is anonymous" in {
      val aclAnon             = AccessControlList(Set(AccessControl(AuthenticatedRef(None), Permissions(Read))))
      implicit val cl         = fixedClient(anonA = Some(aclAnon))
      implicit val httpClient = HttpClient.withAkkaUnmarshaller[AccessControlList]
      implicit val anonCaller = AnonymousCaller()

      IamClient().getAcls(Path("///prefix/some/resource/two")).futureValue shouldEqual aclAnon
    }
  }
}

object IamClientSpec {

  val ValidToken = "validToken"

  def fixedClient[A](anonA: Option[A] = None, authA: Option[A] = None)(implicit mt: Materializer,
                                                                       E: Encoder[A]): UntypedHttpClient[Future] =
    new UntypedHttpClient[Future] {
      override def apply(req: HttpRequest): Future[HttpResponse] =
        req
          .header[Authorization]
          .collect {
            case Authorization(OAuth2BearerToken(ValidToken)) =>
              responseOrEmpty(authA)
            case Authorization(OAuth2BearerToken(_)) =>
              Future.successful(
                HttpResponse(
                  entity = HttpEntity(
                    ContentTypes.`application/json`,
                    """{"code" : "UnauthorizedCaller", "description" : "The caller is not permitted to perform this request"}"""),
                  status = StatusCodes.Unauthorized
                ))
          }
          .getOrElse(responseOrEmpty(anonA))

      override def discardBytes(entity: HttpEntity): Future[DiscardedEntity] = Future.successful(entity.discardBytes())

      override def toString(entity: HttpEntity): Future[String] = Future.successful("")

      private def responseOrEmpty(entity: Option[A])(implicit E: Encoder[A]) = {
        Future.successful(
          entity
            .map(e => HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, e.asJson.noSpaces)))
            .getOrElse(HttpResponse()))
      }
    }
}
