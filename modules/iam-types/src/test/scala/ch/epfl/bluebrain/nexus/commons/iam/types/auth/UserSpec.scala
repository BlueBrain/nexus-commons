package ch.epfl.bluebrain.nexus.commons.iam.types.auth

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.commons.iam.types.identity.Identity
import ch.epfl.bluebrain.nexus.commons.iam.types.identity.Identity.GroupRef
import ch.epfl.bluebrain.nexus.commons.iam.types.auth.User._
import io.circe.DecodingFailure
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest._

import scala.util._

class UserSpec extends WordSpecLike with Matchers with Inspectors {

  val identity = GroupRef(Uri("https://bbpteam.epfl.ch/auth/realms/BBP"), "/bbp-ou-nexus")

  private val values = List[(User, String)](
    AuthenticatedUser(Set(identity)) -> """{"identities":[{"origin":"https://bbpteam.epfl.ch/auth/realms/BBP","group":"/bbp-ou-nexus","type":"GroupRef"}],"type":"AuthenticatedUser"}""",
    AnonymousUser                    -> """{"type":"AnonymousUser"}"""
  )

  "A User" should {
    "be decoded from Json properly" in {
      forAll(values) {
        case (model, json) => decode[User](json) shouldEqual Right(model)
      }
    }
    "be encoded to Json properly" in {
      forAll(values) {
        case (model, json) => model.asJson.noSpaces shouldEqual json
      }

    }
    "not be decoded when origin URI is bogus" in {
      decode[Identity]("""{"type": "AnonymousUser3"}""") match {
        case Right(_) => fail()
        case Left(e)  => e shouldBe a[DecodingFailure]
      }
    }
  }
}
