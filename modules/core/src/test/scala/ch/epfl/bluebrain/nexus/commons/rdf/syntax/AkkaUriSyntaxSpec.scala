package ch.epfl.bluebrain.nexus.commons.rdf.syntax

import _root_.akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.rdf.Iri
import ch.epfl.bluebrain.nexus.rdf.Iri.Path
import org.scalatest.{EitherValues, Matchers, WordSpecLike}

class AkkaUriSyntaxSpec extends WordSpecLike with Matchers with EitherValues {

  "An Iri" should {

    "be converted to a Uri" in {
      val expected = Uri("http://user:password@host/test")
      Iri("http://user:password@host/test").right.value.toAkkaUri shouldEqual expected
    }
  }

  "An Uri.Path" should {
    "be converted to Iri.Path" in {
      Uri.Path("/a/b/c").toIriPath shouldEqual Path("/a/b/c").right.value
      Uri.Path("/a/b/c/d/").toIriPath shouldEqual Path("/a/b/c/d/").right.value
      Uri.Path("/").toIriPath shouldEqual Path("/").right.value
      Uri.Path("").toIriPath shouldEqual Path("").right.value
    }
  }

  "An Iri.Path" should {
    "be converted to Uri.Path" in {
      Path("/a/b/c").right.value.toUriPath shouldEqual Uri.Path("/a/b/c")
      Path("/a/b/c/d/").right.value.toUriPath shouldEqual Uri.Path("/a/b/c/d/")
      Path("/").right.value.toUriPath shouldEqual Uri.Path("/")
      Path("").right.value.toUriPath shouldEqual Uri.Path("")
    }
  }

  "A Uri" should {

    "be converted to an Iri" in {
      val expected = Iri("http://host/path?param1=value1#fragment").right.value
      Uri("http://host/path?param1=value1#fragment").toIri shouldEqual expected
    }
  }
}
