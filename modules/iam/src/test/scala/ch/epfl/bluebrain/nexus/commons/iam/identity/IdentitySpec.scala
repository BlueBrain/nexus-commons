package ch.epfl.bluebrain.nexus.commons.iam.identity

import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity._
import io.circe.DecodingFailure
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest._

import scala.util._

class IdentitySpec extends WordSpecLike with Matchers with Inspectors {

  private val valuesJson = List(
    ("""{"type":"Anonymous"}""", Anonymous),
    ("""{"realm":"realm","type":"AuthenticatedRef"}""", AuthenticatedRef(Some("realm"))),
    ("""{"type":"AuthenticatedRef"}""", AuthenticatedRef(None)),
    ("""{"realm":"realm3","sub":"alice","type":"UserRef"}""", UserRef("realm3", "alice")),
    ("""{"realm":"realm2","group":"some-group","type":"GroupRef"}""", GroupRef("realm2", "some-group"))
  )

  "An Identity" should {
    "be decoded from Json properly" in {
      forAll(valuesJson) {
        case (json, id) => decode[Identity](json) shouldEqual Right(id)
      }
    }

    "be encoded to Json properly" in {
      forAll(valuesJson) {
        case (json, id) => printer.pretty(id.asJson) shouldEqual json
      }
    }

    "not be decoded when origin URI is bogus" in {
      decode[Identity]("""{"realm":"föó://bar","sub":"bob"}""") match {
        case Right(_) => fail()
        case Left(e)  => e shouldBe a[DecodingFailure]
      }
    }
  }
}
