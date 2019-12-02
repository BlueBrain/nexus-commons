package ch.epfl.bluebrain.nexus.commons.rdf.syntax

import _root_.akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.commons.test.EitherValues
import ch.epfl.bluebrain.nexus.rdf.Iri
import ch.epfl.bluebrain.nexus.rdf.Iri.Path
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class AkkaUriSyntaxSpec extends AnyWordSpecLike with Matchers with EitherValues {

  "An Iri" should {

    "be converted to a Uri" in {
      val expected = Uri("http://user:password@host/%C3%86Screenshot%202019-06-14%20at%2010.13.31.png")
      val iri: Iri = Iri.absolute("http://user:password@host").rightValue + "ÆScreenshot 2019-06-14 at 10.13.31.png"
      iri.toAkkaUri shouldEqual expected
    }
  }

  "An Uri.Path" should {
    "be converted to Iri.Path" in {
      Uri.Path("/a/b/c/ Æ").toIriPath shouldEqual Path("/a/b/c/%20Æ").rightValue
      Uri.Path("/a/b/c/d/").toIriPath shouldEqual Path("/a/b/c/d/").rightValue
      Uri.Path("/").toIriPath shouldEqual Path("/").rightValue
      Uri.Path("").toIriPath shouldEqual Path("").rightValue
    }
  }

  "An Iri.Path" should {
    "be converted to Uri.Path" in {
      Path("/a/b/Æ").rightValue.toUriPath shouldEqual Uri.Path("/a/b/%C3%86")
      Path("/a/b/c/d/").rightValue.toUriPath shouldEqual Uri.Path("/a/b/c/d/")
      Path("/").rightValue.toUriPath shouldEqual Uri.Path("/")
      Path("").rightValue.toUriPath shouldEqual Uri.Path("")
    }
  }

  "A Uri" should {

    "be converted to an Iri" in {
      val expected = Iri("http://host/path?param1=value1#fragment").rightValue
      Uri("http://host/path?param1=value1#fragment").toIri shouldEqual expected
    }
  }
}
