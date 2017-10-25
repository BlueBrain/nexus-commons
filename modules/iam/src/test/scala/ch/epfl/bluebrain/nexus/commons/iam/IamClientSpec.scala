package ch.epfl.bluebrain.nexus.commons.iam

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import cats.instances.future._
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.iam.acls.AccessControlList
import ch.epfl.bluebrain.nexus.commons.iam.auth.User
import ch.epfl.bluebrain.nexus.commons.iam.identity.Caller.{AnonymousCaller, AuthenticatedCaller}
import ch.epfl.bluebrain.nexus.commons.types.CommonRejections.UnauthorizedAccess
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpecLike}

import scala.concurrent.duration._

class IamClientSpec
    extends TestKit(ActorSystem("IamClientSpec"))
    with WordSpecLike
    with Matchers
    with ScalaFutures
    with MockedIAMClient {

  private implicit val config                = Configuration.default.withDiscriminator("type")
  private implicit val ec                    = system.dispatcher
  private implicit val mt: ActorMaterializer = ActorMaterializer()
  private implicit val iamUri                = IamUri("http://localhost:8080")
  private implicit val aclsCl                = HttpClient.withAkkaUnmarshaller[AccessControlList]
  private implicit val userCl                = HttpClient.withAkkaUnmarshaller[User]
  private val iamClient                      = IamClient.akkaHttpIamClient

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(5 seconds, 200 milliseconds)

  "An IamClient" should {

    "return unathorized whenever the token is wrong" in {
      val response = iamClient.getCaller(Some(OAuth2BearerToken("invalidToken")))
      ScalaFutures.whenReady(response.failed, Timeout(patienceConfig.timeout)) { e =>
        e shouldBe a[UnauthorizedAccess.type]
      }
    }

    "return anonymous caller whenever there is no token provided" in {
      iamClient.getCaller(None).futureValue shouldEqual AnonymousCaller
    }

    "return an authenticated caller whenever the token provided is correct" in {
      val credentials = OAuth2BearerToken(ValidToken)
      iamClient.getCaller(Some(credentials)).futureValue shouldEqual AuthenticatedCaller(credentials, mockedUser)
    }

    "return expected acls whenever the caller is authenticated" in {
      implicit val caller = iamClient.getCaller(Some(OAuth2BearerToken(ValidToken))).futureValue
      iamClient.getAcls("/prefix/some/resource/one").futureValue shouldEqual mockedAcls
    }
    "return expected acls whenever the caller is anonymous" in {
      implicit val anonCaller = iamClient.getCaller(None).futureValue
      iamClient.getAcls("/prefix/some/resource/two").futureValue shouldEqual mockedAnonAcls
    }
  }
}
