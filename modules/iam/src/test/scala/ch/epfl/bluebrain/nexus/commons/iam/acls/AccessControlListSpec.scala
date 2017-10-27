package ch.epfl.bluebrain.nexus.commons.iam.acls

import ch.epfl.bluebrain.nexus.commons.iam.acls.Permission._
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity._
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
    val model                          = AccessControlList(Set(AccessControl(GroupRef("BBP", "/bbp-ou-nexus"), permissions)))
    val json =
      """{"acl":[{"identity":{"realm":"BBP","group":"/bbp-ou-nexus","type":"GroupRef"},"permissions":["own","read","write","publish"]}]}"""

    "be decoded from Json properly" in {
      decode[AccessControlList](json) shouldEqual Right(model)
    }
    "be encoded to Json properly" in {
      model.asJson.noSpaces shouldEqual json
    }

    "convert to map" in {
      val identity = GroupRef("BBP", "/bbp-ou-nexus")
      model.toMap shouldEqual Map(identity -> Permissions(Own, Read, Write, Publish))
    }
    "check if it has void permissions" in {
      model.hasVoidPermissions shouldEqual false
      AccessControlList().hasVoidPermissions shouldEqual true
    }

    "collapse into Permissions" in {
      val permission = Permission("something")
      val acls = AccessControlList(
        Set(AccessControl(GroupRef("BBP", "/bbp-ou-nexus"), permissions),
            AccessControl(GroupRef("BBP", "/something"), Permissions(permission, Own))))
      acls.permissions shouldEqual Permissions(Own, Read, Write, Publish, permission)
    }

  }

}
