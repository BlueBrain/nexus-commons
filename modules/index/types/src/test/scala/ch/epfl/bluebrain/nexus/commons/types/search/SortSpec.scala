package ch.epfl.bluebrain.nexus.commons.types.search

import ch.epfl.bluebrain.nexus.commons.types.search.Sort.OrderType.{Asc, Desc}
import org.scalatest.{Matchers, WordSpecLike}

class SortSpec extends WordSpecLike with Matchers {

  "A Sort" should {
    val prefix1 = "http://localhost/vocab/standards/"
    val prefix2 = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
    "reject when empty" in {
      Sort("") shouldEqual None
    }

    "reject when not absolute URI " in {
      Sort("something") shouldEqual None
    }

    "created correctly " in {
      Sort(s"${prefix1}createdAtTime").get shouldEqual Sort(Asc, s"${prefix1}createdAtTime")
      Sort(s"+${prefix1}createdAtTime").get shouldEqual Sort(Asc, s"${prefix1}createdAtTime")
      Sort(s"-${prefix2}type").get shouldEqual Sort(Desc, s"${prefix2}type")
    }
  }
}
