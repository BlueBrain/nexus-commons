package ch.epfl.bluebrain.nexus.commons.rdf.akka

import _root_.akka.http.scaladsl.model.Uri
import cats.syntax.show._
import ch.epfl.bluebrain.nexus.rdf.Iri
import IriToAkkaSyntax._

trait IriToAkkaSyntax {

  final implicit def akkaSyntaxIri(iri: Iri): IriOps =
    new IriOps(iri)
}

object IriToAkkaSyntax extends IriToAkkaSyntax {

  /**
    * Syntactic sugar for constructing a [[Uri]] from the [[Iri]]
    */
  final class IriOps(private val iri: Iri) extends AnyVal {
    def toAkkaUri: Uri = Uri(iri.show)

  }
}
