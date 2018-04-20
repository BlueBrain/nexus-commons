package ch.epfl.bluebrain.nexus.commons.types.identity

import cats.syntax.show._
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity._
import ch.epfl.bluebrain.nexus.commons.types.identity.IdentityId.IdentityIdPrefix
import io.circe.generic.extras.Configuration
import io.circe.parser.decode
import io.circe.syntax._
import io.circe.{DecodingFailure, Printer}
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
  private val printer                        = Printer.noSpaces.copy(dropNullValues = true)
  private implicit val config: Configuration = Configuration.default.withDiscriminator("type")

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

    "construct with prefix" in {

      implicit val prefix = IdentityIdPrefix("http://localhost/prefix/")

      val user = UserRef("realm3", "alice")(IdentityIdPrefix("http://localhost/prefix"))
      user.id.show shouldEqual "http://localhost/prefix/realms/realm3/users/alice"

      val group = GroupRef("realm2", "some")
      group.id.show shouldEqual "http://localhost/prefix/realms/realm2/groups/some"

      val authenticated = AuthenticatedRef(None)
      authenticated.id.show shouldEqual "http://localhost/prefix/authenticated"

      val authenticated2 = AuthenticatedRef(Some("realm"))
      authenticated2.id.show shouldEqual "http://localhost/prefix/realms/realm/authenticated"
    }

    "resolve the realm and sub/group properly when present" in {
      AuthenticatedRef(Some("realm")).realm shouldEqual Some("realm")
      AuthenticatedRef(None).realm shouldEqual None
      UserRef("realm3", "alice").realm shouldEqual "realm3"
      UserRef("realm3", "alice").sub shouldEqual "alice"
      GroupRef("realm2", "some-group").realm shouldEqual "realm2"
      GroupRef("realm2", "some-group").group shouldEqual "some-group"

      implicit val prefix = IdentityIdPrefix("http://localhost/prefix/")

      val user = UserRef("realm3", "alice")
      user.realm shouldEqual "realm3"
      user.sub shouldEqual "alice"

      val group = GroupRef("realm2", "some")
      group.realm shouldEqual "realm2"
      group.group shouldEqual "some"

      val authenticated = AuthenticatedRef(None)
      authenticated.realm shouldEqual None

      val authenticated2 = AuthenticatedRef(Some("realm"))
      authenticated2.realm shouldEqual Some("realm")
    }
  }
}
