package ch.epfl.bluebrain.nexus.common.types

import cats.kernel.{Eq, Order}
import io.circe.{Decoder, Encoder, Json}
import org.scalatest.{Inspectors, Matchers, WordSpecLike}

class VersionSpec extends WordSpecLike with Matchers with Inspectors {

  "A Version" should {
    "be parsed correctly from a string" in {
      val mapping = Map(
        "v0.0.0"    -> Version(0, 0, 0),
        "v1.0.0"    -> Version(1, 0, 0),
        "v0.100.0"  -> Version(0, 100, 0),
        "v1234.0.0" -> Version(1234, 0, 0),
        "v1.1.1"    -> Version(1, 1, 1)
      )
      forAll(mapping.toList) {
        case (left, right) =>
          Version(left) shouldEqual Some(right)
      }
    }
    "fail to be parsed from illegal strings" in {
      val listing = List(
        "1.0.0",
        "1",
        "a1.0.0",
        "v-1.1.1",
        "v0.0.-1",
        "v1.0.0.0"
      )
      forAll(listing) { str =>
        Version(str) shouldEqual None
      }
    }
    "fail construction for negative major value" in {
      intercept[IllegalArgumentException] {
        Version(-1, 0, 0)
      }
    }
    "fail construction for negative minor value" in {
      intercept[IllegalArgumentException] {
        Version(0, -1, 0)
      }
    }
    "fail construction for negative patch value" in {
      intercept[IllegalArgumentException] {
        Version(0, 0, -1)
      }
    }
    "be transformed to a PartialVersion" when {
      "dropping the patch" in {
        Version(1, 2, 3).dropPatch shouldEqual PartialVersion(1, 2 :: Nil)
      }
      "dropping the minor" in {
        Version(1, 2, 3).dropMinor shouldEqual PartialVersion(1, Nil)
      }
    }
    "be compatible with a PartialVersion" when {
      "having the same major value" in {
        Version(1, 2, 3).isCompatible(PartialVersion(1, Nil)) shouldEqual true
      }
      "having the same major and minor values" in {
        Version(1, 2, 3).isCompatible(PartialVersion(1, 2 :: Nil)) shouldEqual true
      }
      "having the same major, minor and patch values" in {
        Version(1, 2, 3).isCompatible(PartialVersion(1, 2 :: 3 :: Nil)) shouldEqual true
      }
    }
    "not be compatible with a PartialVersion" when {
      "major value differs" in {
        Version(1, 2, 3).isCompatible(PartialVersion(2, Nil)) shouldEqual false
      }
      "minor value differs" in {
        Version(1, 2, 3).isCompatible(PartialVersion(1, 3 :: Nil)) shouldEqual false
      }
      "patch value differs" in {
        Version(1, 2, 3).isCompatible(PartialVersion(1, 2 :: 4 :: Nil)) shouldEqual false
      }
    }
    "bump the patch correctly" in {
      Version(1, 2, 3).bumpPatch shouldEqual Version(1, 2, 4)
    }
    "bump the minor correctly" in {
      Version(1, 2, 3).bumpMinor shouldEqual Version(1, 3, 0)
    }
    "bump the major correctly" in {
      Version(1, 2, 3).bumpMajor shouldEqual Version(2, 0, 0)
    }
    "be converted to the correct PartialVersion" in {
      Version(1, 2, 3).asPartial shouldEqual PartialVersion(1, 2, 3)
    }
  }

  "A Show[Version]" should {
    "convert correctly a version to String" in {
      import cats.syntax.show._
      val mapping = Map(
        Version(0, 0, 0)    -> "v0.0.0",
        Version(1, 0, 0)    -> "v1.0.0",
        Version(0, 100, 0)  -> "v0.100.0",
        Version(1234, 0, 0) -> "v1234.0.0",
        Version(1, 1, 1)    -> "v1.1.1"
      )
      forAll(mapping.toList) {
        case (ver, str) =>
          ver.show shouldEqual str
      }
    }
  }

  "An Eq[Version]" should {
    "return true for universal equality" in {
      val v1 = Version(1, 2, 3)
      val v2 = Version(1, 2, 3)
      Eq[Version].eqv(v1, v2) shouldEqual true
    }
  }

  "An Order[Version]" should {
    "compare versions correctly" in {
      val unsorted = List(
        Version(1, 1, 0),
        Version(0, 0, 0),
        Version(1, 0, 0),
        Version(2, 0, 0),
        Version(0, 1, 0),
        Version(1, 1, 0),
        Version(2, 1, 0),
        Version(0, 0, 1),
        Version(1, 0, 1),
        Version(2, 0, 1)
      )

      val sorted = List(
        Version(0, 0, 0),
        Version(0, 0, 1),
        Version(0, 1, 0),
        Version(1, 0, 0),
        Version(1, 0, 1),
        Version(1, 1, 0),
        Version(1, 1, 0),
        Version(2, 0, 0),
        Version(2, 0, 1),
        Version(2, 1, 0)
      )
      val ordering = Order[Version].toOrdering
      unsorted.sorted(ordering) shouldEqual sorted
    }
  }

  "An Encoder[Version]" should {
    "provide a string encoded representation" in {
      Encoder[Version].apply(Version(1, 2, 3)) shouldEqual Json.fromString("v1.2.3")
    }
  }

  "A Decoder[Version]" should {
    "decode a properly formatted version" in {
      Decoder[Version].decodeJson(Json.fromString("v1.2.3")) shouldEqual Right(Version(1, 2, 3))
    }
    "fail to decode incorrectly formatted versions" in {
      val listing = List(
        "1.0.0",
        "1",
        "a1.0.0",
        "v-1.1.1",
        "v0.0.-1",
        "v1.0.0.0"
      )
      forAll(listing) { str =>
        Decoder[Version].decodeJson(Json.fromString(str)) match {
          case Left(df) => df.message shouldEqual "Illegal version format"
          case _        => fail("Expected decoding failure")
        }
      }
    }
  }

  "A PartialVersion" should {
    "be parsed correctly from a string" in {
      val mapping = Map(
        "v0.0.2"    -> PartialVersion(0, 0, 2),
        "v23"       -> PartialVersion(23),
        "v1.0"      -> PartialVersion(1, 0),
        "v1234.0.0" -> PartialVersion(1234, 0, 0)
      )
      forAll(mapping.toList) {
        case (left, right) =>
          PartialVersion(left) shouldEqual Some(right)
      }
    }
    "fail construction for negative major value" in {
      intercept[IllegalArgumentException] {
        PartialVersion(-1, 0, 0)
      }
    }
    "fail construction for negative minor value" in {
      intercept[IllegalArgumentException] {
        PartialVersion(0, -1, 0)
      }
    }
    "fail construction for negative patch value" in {
      intercept[IllegalArgumentException] {
        PartialVersion(0, 0, -1)
      }
    }
    "fail construction for more than 3 segments" in {
      intercept[IllegalArgumentException] {
        PartialVersion(0, List(1, 2, 3))
      }
    }
  }

  "A Show[PartialVersion]" should {
    "convert correctly a partial version to a String" when {
      "defined solely by a major value" in {
        import cats.syntax.show._
        PartialVersion(1).show shouldEqual "v1"
      }
      "defined by major and minor values" in {
        import cats.syntax.show._
        PartialVersion(1, 2).show shouldEqual "v1.2"
      }
      "defined by major, minor and patch values" in {
        import cats.syntax.show._
        PartialVersion(1, 2, 3).show shouldEqual "v1.2.3"
      }
    }
  }

  "An Eq[PartialVersion]" should {
    "return true for universal equality" in {
      val v1 = PartialVersion(1, 2)
      val v2 = PartialVersion(1, 2)
      Eq[PartialVersion].eqv(v1, v2) shouldEqual true
    }
    "return false for different precisions" in {
      val v1 = PartialVersion(1, 2)
      val v2 = PartialVersion(1, 2, 0)
      Eq[PartialVersion].eqv(v1, v2) shouldEqual false
    }
  }

  "An Encoder[PartialVersion]" should {
    "provide a string encoded representation" in {
      val mapping = Map(
        PartialVersion(1, 2, 3) -> "v1.2.3",
        PartialVersion(1, 2)    -> "v1.2",
        PartialVersion(1)       -> "v1"
      )
      forAll(mapping.toList) {
        case (pv, str) =>
          Encoder[PartialVersion].apply(pv) shouldEqual Json.fromString(str)
      }
    }
  }

  "A Decoder[PartialVersion]" should {
    "decode a properly formatted version" in {
      val mapping = Map(
        "v1.2.3" -> PartialVersion(1, 2, 3),
        "v1.2"   -> PartialVersion(1, 2),
        "v1"     -> PartialVersion(1)
      )
      forAll(mapping.toList) {
        case (str, pv) =>
          Decoder[PartialVersion].decodeJson(Json.fromString(str)) shouldEqual Right(pv)
      }
    }
    "fail to decode incorrectly formatted versions" in {
      val listing = List(
        "1.0.0",
        "1",
        "a1.0.0",
        "v-1.1.1",
        "v0.-1",
        "v1.0.0.0"
      )
      forAll(listing) { str =>
        Decoder[PartialVersion].decodeJson(Json.fromString(str)) match {
          case Left(df) => df.message shouldEqual "Illegal partial version format"
          case _        => fail("Expected decoding failure")
        }
      }
    }
  }

}
