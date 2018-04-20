package ch.epfl.bluebrain.nexus.commons.types.identity

import ch.epfl.bluebrain.nexus.commons.types.identity.Identity._
import ch.epfl.bluebrain.nexus.commons.types.identity.IdentityId.IdentityIdPrefix
import io.circe.Printer
import io.circe.generic.extras.Configuration
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest._

import scala.util._

class UserSpec extends WordSpecLike with Matchers with Inspectors {

  private val valuesJson = List(
    ("""{"type":"AnonymousUser"}""", AnonymousUser()),
    ("""{"identities":[{"id":"realms/realm/authenticated","type":"AuthenticatedRef"}],"type":"AuthenticatedUser"}""",
     AuthenticatedUser(Set(AuthenticatedRef(Some("realm"))))),
    ("""{"identities":[{"id":"realms/realm3/users/alice","type":"UserRef"},{"id":"realms/realm2/groups/some-group","type":"GroupRef"}],"type":"AuthenticatedUser"}""",
     AuthenticatedUser(Set(UserRef("realm3", "alice"), GroupRef("realm2", "some-group"))))
  )
  private val printer                        = Printer.noSpaces.copy(dropNullValues = true)
  private implicit val config: Configuration = Configuration.default.withDiscriminator("type")

  "A User" should {

    "be decoded from Json properly" in {
      forAll(valuesJson) {
        case (json, id) => decode[User](json) shouldEqual Right(id)
      }
    }

    "be encoded to Json properly" in {
      forAll(valuesJson) {
        case (json, id) =>
          println(printer.pretty(id.asJson))
          printer.pretty(id.asJson) shouldEqual json
      }
    }

    "have the correct identities when it is an AnonymousUser" in {
      AnonymousUser().identities shouldEqual Set(Anonymous())
      val prefix = IdentityIdPrefix("http://localhost/prefix")
      AnonymousUser()(prefix).identities shouldEqual Set(Anonymous()(prefix))

    }
  }
}
