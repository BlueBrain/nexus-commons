package ch.epfl.bluebrain.nexus.commons.service.directives

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Directives.rawPathPrefix
import akka.http.scaladsl.server.{Directive0, PathMatcher}
import ch.epfl.bluebrain.nexus.commons.http.PathSanity.Position.End
import ch.epfl.bluebrain.nexus.commons.http.PathSanity._

/**
  * Collection of custom directives for matching against prefix paths.
  */
trait PrefixDirectives {

  /**
    * Creates a path matcher from the argument ''uri'' by stripping the slashes at the end of its path.  The matcher
    * is applied directly to the prefix of the unmatched path.
    *
    * @param uri the uri to use as a prefix
    */
  final def uriPrefix(uri: Uri): Directive0 =
    rawPathPrefix(PathMatcher(uri.path.stripSlash(End), ()))
}

/**
  * Collection of custom directives for matching against prefix paths.
  */
object PrefixDirectives extends PrefixDirectives
