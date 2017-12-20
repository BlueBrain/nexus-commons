package ch.epfl.bluebrain.nexus.commons.http

import akka.http.scaladsl.model.Uri

/**
  * Wrapper class holding a context URI.
  *
  * @param context the underlying context URI
  */
final case class ContextUri(context: Uri) {
  override def toString: String = context.toString
}
