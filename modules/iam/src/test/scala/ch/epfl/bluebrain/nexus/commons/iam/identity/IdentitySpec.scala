package ch.epfl.bluebrain.nexus.commons.iam.identity

import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity._
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.serialization._
import io.circe.DecodingFailure
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest._

import scala.util._

class IdentitySpec extends WordSpecLike with Matchers with Inspectors {

  private val valuesJson = List(
    ("""{"id":"anonymous","type":"Anonymous"}""", Anonymous()),
    ("""{"id":"realms/realm/authenticated","realm":"realm","type":"AuthenticatedRef"}""",
     AuthenticatedRef(Some("realm"))),
    ("""{"id":"authenticated","type":"AuthenticatedRef"}""", AuthenticatedRef(None)),
    ("""{"id":"realms/realm3/users/alice","realm":"realm3","sub":"alice","type":"UserRef"}""",
     UserRef("realm3", "alice")),
    ("""{"id":"realms/realm2/groups/some-group","realm":"realm2","group":"some-group","type":"GroupRef"}""",
     GroupRef("realm2", "some-group"))
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
