package ch.epfl.bluebrain.nexus.commons.rdf.akka

import _root_.akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.rdf.Iri
import AkkaUriSyntax._

trait AkkaUriSyntax {

  final implicit def akkaSyntaxUri(uri: Uri): UriOps =
    new UriOps(uri)

}

object AkkaUriSyntax extends AkkaUriSyntax {

  /**
    * Syntactic sugar for constructing an [[Iri]] from the [[Uri]]
    */
  final class UriOps(private val uri: Uri) extends AnyVal {
    def toIri: Iri = Iri.unsafe(uri.toString())
  }
}
