package ch.epfl.bluebrain.nexus.commons.iam.types.acls

import org.scalatest.{Matchers, WordSpecLike}

class PermissionSpec extends WordSpecLike with Matchers {
  "A Permission" should {

    "should match the regex" in {
      intercept[IllegalArgumentException] {
        Permission("One")
      }
    }
  }
}
