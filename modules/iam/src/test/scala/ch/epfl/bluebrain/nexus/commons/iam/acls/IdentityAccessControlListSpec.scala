package ch.epfl.bluebrain.nexus.commons.iam.acls

import ch.epfl.bluebrain.nexus.commons.iam.acls.Permission._
import ch.epfl.bluebrain.nexus.commons.iam.io.serialization.SimpleIdentitySerialization._
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.{Matchers, WordSpecLike}
import ch.epfl.bluebrain.nexus.commons.iam.acls.Path._
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.{GroupRef, UserRef}
import ch.epfl.bluebrain.nexus.commons.test.Resources

import scala.collection.immutable.ListMap
class IdentityAccessControlListSpec extends WordSpecLike with Matchers with Resources {

  "A IdentityAccessControlList" should {
    val Publish     = Permission("publish")
    val permissions = Permissions(Own, Read, Write, Publish)
    val group       = GroupRef("BBP", "some-group")
    val user        = UserRef("BBP", "user1")
    val path        = /
    val path1       = "a" / "path"
    val path2       = "another" / "path" / "longer"

    val model = IdentityAccessControlList(
      group -> List(PathAccessControl(path, Permissions(Publish)),
                    PathAccessControl(path1, Permissions(Write, Read)),
                    PathAccessControl(path2, Permissions(Read))),
      user -> List(PathAccessControl(path1, Permissions(Own, Write, Publish)),
                   PathAccessControl(path2, Permissions(Own)))
    )

    val json = jsonContentOf("/acls/identities-acls.json")

    "be encoded to Json properly" in {
      model.asJson shouldEqual json
    }

    "convert to aggregated map" in {
      model.aggregatedMap shouldEqual ListMap(path  -> Permissions(Publish),
                                              path1 -> permissions,
                                              path2 -> Permissions(Own, Read))
    }

  }

}
