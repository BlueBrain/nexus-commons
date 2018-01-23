package ch.epfl.bluebrain.nexus.commons.types

import org.scalatest.{Matchers, WordSpecLike}

class ConcurrentSetSpec extends WordSpecLike with Matchers {

  "A ConcurrentSetBuilder" should {

    "build a mutable.Set" in {
      val set = ConcurrentSetBuilder("a", "b")
      set("a") shouldBe true
      set("c") shouldBe false
    }
  }
}
