package ch.epfl.bluebrain.nexus.commons.iam.types.acls

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.commons.iam.types.identity.Identity._
import ch.epfl.bluebrain.nexus.commons.iam.types.acls.Permission._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.parser._
import io.circe.syntax._
import org.scalatest.{Matchers, WordSpecLike}

class AccessControlListSpec extends WordSpecLike with Matchers {

  "A AccessControlList" should {
    val Publish                        = Permission("publish")
    val permissions                    = Permissions(Own, Read, Write, Publish)
    implicit val config: Configuration = Configuration.default.withDiscriminator("type")
    val model = AccessControlList(
      Set(AccessControl(GroupRef("https://bbpteam.epfl.ch/auth/realms/BBP", "/bbp-ou-nexus"), permissions)))
    val json =
      """{"acl":[{"identity":{"origin":"https://bbpteam.epfl.ch/auth/realms/BBP","group":"/bbp-ou-nexus","type":"GroupRef"},"permissions":["own","read","write","publish"]}]}"""

    "be decoded from Json properly" in {
      decode[AccessControlList](json) shouldEqual Right(model)
    }
    "be encoded to Json properly" in {
      model.asJson.noSpaces shouldEqual json
    }

    "convert to map" in {
      val identity = GroupRef(Uri("https://bbpteam.epfl.ch/auth/realms/BBP"), "/bbp-ou-nexus")
      model.toMap shouldEqual Map(identity -> Permissions(Own, Read, Write, Publish))
    }
    "check if it has void permissions" in {
      model.hasVoidPermissions shouldEqual false
      AccessControlList().hasVoidPermissions shouldEqual true
    }

  }

}
