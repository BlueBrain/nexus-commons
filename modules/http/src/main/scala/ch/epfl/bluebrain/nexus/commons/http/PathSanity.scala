package ch.epfl.bluebrain.nexus.commons.http

import akka.http.scaladsl.model.Uri.Path
import ch.epfl.bluebrain.nexus.commons.http.PathSanity.Position._

import scala.annotation.tailrec

object PathSanity {

  /**
    * Strip slashes from the provided ''path'' if found on the requested ''position''.
    *
    * @param path     the path from where to strip the necessary slashes
    * @param position the position where to look for slashes
    */
  def stripSlash(path: Path, position: Position): Path = {
    @tailrec
    def strip(p: Path): Path = p match {
      case Path.Empty       => Path.Empty
      case Path.Slash(rest) => strip(rest)
      case other            => other
    }
    position match {
      case Start => strip(path)
      case End   => strip(path.reverse).reverse
      case Both  => stripSlash(stripSlash(path, Start), End)
    }
  }

  /**
    * Interface syntax to expose new functionality into [[Path]] type
    *
    * @param path the targeted path
    */
  implicit class PathSanitySyntax(path: Path) {

    /**
      * Method exposed on [[Path]] instances
      *
      * @param position defines from which position we want to strip the slash
      * @return a path with slash stripped on the provided position
      */
    def stripSlash(position: Position): Path = PathSanity.stripSlash(path, position)
  }

  /**
    * Base enumeration type for position classes.
    */
  sealed trait Position extends Product with Serializable

  object Position {

    /**
      * Describes a selection on the starting position
      */
    final case object Start extends Position

    /**
      * Describes a selection on the ending position
      */
    final case object End extends Position

    /**
      * Describes a selection on starting and ending position
      */
    final case object Both extends Position

  }
}
