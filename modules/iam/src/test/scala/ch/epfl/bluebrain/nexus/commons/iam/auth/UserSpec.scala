package ch.epfl.bluebrain.nexus.commons.iam.auth

import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity._
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.GroupRef
import io.circe.DecodingFailure
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest._

import scala.util._

class UserSpec extends WordSpecLike with Matchers with Inspectors {

  val identity = GroupRef("BBP", "/bbp-ou-nexus")

  private val values = List[(User, String)](
    AuthenticatedUser(Set(identity)) -> """{"identities":[{"realm":"BBP","group":"/bbp-ou-nexus","type":"GroupRef"}],"type":"AuthenticatedUser"}""",
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
