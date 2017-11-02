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
    ("""{"id":"realms/realm/authenticated","type":"AuthenticatedRef"}""", AuthenticatedRef(Some("realm"))),
    ("""{"id":"authenticated","type":"AuthenticatedRef"}""", AuthenticatedRef(None)),
    ("""{"id":"realms/realm3/users/alice","type":"UserRef"}""", UserRef("realm3", "alice")),
    ("""{"id":"realms/realm2/groups/some-group","type":"GroupRef"}""", GroupRef("realm2", "some-group"))
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

    "resolve the realm and sub/group properly when present" in {
      AuthenticatedRef(Some("realm")).realm shouldEqual Some("realm")
      AuthenticatedRef(None).realm shouldEqual None
      UserRef("realm3", "alice").realm shouldEqual "realm3"
      UserRef("realm3", "alice").sub shouldEqual "alice"
      GroupRef("realm2", "some-group").realm shouldEqual "realm2"
      GroupRef("realm2", "some-group").group shouldEqual "some-group"

      val user = UserRef(IdentityId("http://localhost/prefix/realms/realm3/users/alice"))
      user.realm shouldEqual "realm3"
      user.sub shouldEqual "alice"

      val group = GroupRef(IdentityId("http://localhost/prefix/realms/realm2/groups/some"))
      group.realm shouldEqual "realm2"
      group.group shouldEqual "some"

      val authenticated = AuthenticatedRef(IdentityId("http://localhost/prefix/authenticated"))
      authenticated.realm shouldEqual None

      val authenticated2 = AuthenticatedRef(IdentityId("http://localhost/prefix/realms/realm/authenticated"))
      authenticated2.realm shouldEqual Some("realm")
    }
  }
}
