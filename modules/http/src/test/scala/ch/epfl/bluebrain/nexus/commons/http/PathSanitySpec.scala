package ch.epfl.bluebrain.nexus.commons.http

import akka.http.scaladsl.model.Uri.Path
import ch.epfl.bluebrain.nexus.commons.http.PathSanity.Position._
import ch.epfl.bluebrain.nexus.commons.http.PathSanity._
import org.scalatest.{Inspectors, Matchers, WordSpecLike}

class PathSanitySpec extends WordSpecLike with Matchers with Inspectors {

  "A PathSanity" should {

    "remove the ending slash(es)" in {
      forAll(
        Map(
          ""         -> "",
          "/"        -> "",
          "///"      -> "",
          "/dev"     -> "/dev",
          "/dev/"    -> "/dev",
          "/dev///"  -> "/dev",
          "/dev/sn/" -> "/dev/sn"
        ).toList) {
        case (input, output) =>
          val path = Path(input)
          path.stripSlash(End) shouldEqual Path(output)
      }
    }

    "remove the starting slash(es)" in {
      forAll(
        Map(
          ""              -> "",
          "/"             -> "",
          "///"           -> "",
          "//dev"         -> "dev",
          "///dev//"      -> "dev//",
          "/dev///"       -> "dev///",
          "/////dev/sn/a" -> "dev/sn/a"
        ).toList) {
        case (input, output) =>
          val path = Path(input)
          path.stripSlash(Start) shouldEqual Path(output)
      }
    }

    "remove the starting and ending shash(es)" in {
      forAll(
        Map(
          ""                 -> "",
          "/"                -> "",
          "///"              -> "",
          "//dev"            -> "dev",
          "///dev//"         -> "dev",
          "/dev///"          -> "dev",
          "/////dev/sn/a"    -> "dev/sn/a",
          "/////dev/sn/a/n/" -> "dev/sn/a/n"
        ).toList) {
        case (input, output) =>
          val path = Path(input)
          path.stripSlash(Both) shouldEqual Path(output)
      }
    }
  }
}
