package ch.epfl.bluebrain.nexus.commons.rdf

import _root_.akka.http.scaladsl.model._
import ch.epfl.bluebrain.nexus.rdf.encoder.NodeEncoder
import ch.epfl.bluebrain.nexus.rdf.encoder.NodeEncoder.stringEncoder
import ch.epfl.bluebrain.nexus.rdf.encoder.NodeEncoderError.IllegalConversion

import scala.util.Try

package object instances {
  implicit val uriNodeEncoder: NodeEncoder[Uri] = node =>
    stringEncoder(node).flatMap { uri =>
      Try(Uri(uri)).toEither.left.map(err => IllegalConversion(s"Invalid URI '${err.getMessage}'"))
    }

  implicit val uriPathNodeEncoder: NodeEncoder[Uri.Path] = node =>
    stringEncoder(node).flatMap { path =>
      Try(Uri.Path(path)).toEither.left.map(err => IllegalConversion(s"Invalid Path '${err.getMessage}'"))

    }

  implicit val mediaTypeNodeEncoder: NodeEncoder[ContentType] = node =>
    NodeEncoder.stringEncoder(node).flatMap {
      ContentType.parse(_).left.map(_ => IllegalConversion("Expected a media type, but found otherwise"))
    }
}
