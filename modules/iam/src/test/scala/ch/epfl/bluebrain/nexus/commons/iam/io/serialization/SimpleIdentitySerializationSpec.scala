package ch.epfl.bluebrain.nexus.commons.iam.io.serialization

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.{Anonymous, AuthenticatedRef, GroupRef, UserRef}
import ch.epfl.bluebrain.nexus.commons.iam.io.serialization.SimpleIdentitySerialization._
import ch.epfl.bluebrain.nexus.commons.test.Resources
import io.circe.syntax._
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{Matchers, WordSpecLike}

class SimpleIdentitySerializationSpec extends WordSpecLike with Matchers with TableDrivenPropertyChecks with Resources {
  val apiUri: Uri = Uri("http://localhost/prefix")

  private val realm     = "realm"
  private val user      = UserRef(realm, "alice")
  private val group     = GroupRef(realm, "some-group")
  private val auth      = AuthenticatedRef(Some("realm"))
  private val anonymous = Anonymous()

  val results = Table(
    ("identity", "json"),
    (user, jsonContentOf("/serialization/identity/user.json")),
    (group, jsonContentOf("/serialization/identity/group.json")),
    (auth, jsonContentOf("/serialization/identity/auth.json")),
    (anonymous, jsonContentOf("/serialization/identity/anon.json"))
  )

  "SimpleIdentitySerialization" should {
    "encoder events to JSON" in {
      forAll(results) { (event, json) =>
        event.asJson shouldBe json
      }
    }
    "decode events from JSON" in {
      forAll(results) { (event, json) =>
        json.as[Identity] shouldEqual Right(event)
      }
    }
  }
}
