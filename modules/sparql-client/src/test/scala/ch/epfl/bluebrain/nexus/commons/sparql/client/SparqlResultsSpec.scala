package ch.epfl.bluebrain.nexus.commons.sparql.client

import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlResults.{Binding, Bindings, Head}
import ch.epfl.bluebrain.nexus.commons.test.{CirceEq, Resources}
import io.circe.syntax._
import org.scalatest.{EitherValues, Matchers, WordSpecLike}

class SparqlResultsSpec extends WordSpecLike with Matchers with Resources with EitherValues with CirceEq {
  "A Sparql Json result" should {
    val json = jsonContentOf("/results/query-result.json")

    val blurb = Binding("literal",
                        "<p xmlns=\"http://www.w3.org/1999/xhtml\">My name is <b>alice</b></p>",
                        None,
                        Some("http://www.w3.org/1999/02/22-rdf-syntax-ns#XMLLiteral"))

    val map1 = Map(
      "x"      -> Binding("bnode", "r1"),
      "hpage"  -> Binding("uri", "http://work.example.org/alice/"),
      "name"   -> Binding("literal", "Alice"),
      "mbox"   -> Binding("literal", ""),
      "blurb"  -> blurb,
      "friend" -> Binding("bnode", "r2")
    )

    val map2 = Map(
      "x"      -> Binding("bnode", "r2"),
      "hpage"  -> Binding("uri", "http://work.example.org/bob/"),
      "name"   -> Binding("literal", "Bob", Some("en")),
      "mbox"   -> Binding("uri", "mailto:bob@work.example.org"),
      "friend" -> Binding("bnode", "r1")
    )

    val head = Head(List("x", "hpage", "name", "mbox", "age", "blurb", "friend"),
                    Some(List("http://www.w3.org/TR/rdf-sparql-XMLres/example.rq")))

    val qr = SparqlResults(head, Bindings(map1, map2))

    "be encoded" in {
      qr.asJson should equalIgnoreArrayOrder(json)
    }

    "be decoded" in {
      json.as[SparqlResults].right.value shouldEqual qr
    }

    "add head" in {
      head ++ Head(List("v", "hpage", "name")) shouldEqual Head(
        List("x", "hpage", "name", "mbox", "age", "blurb", "friend", "v"),
        Some(List("http://www.w3.org/TR/rdf-sparql-XMLres/example.rq")))
    }

    "add binding" in {
      (Bindings(map1) ++ Bindings(map2)) shouldEqual qr.results
    }
  }

}
