package ch.epfl.bluebrain.nexus.commons.rdf

import _root_.akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.rdf.encoder.NodeEncoder
import ch.epfl.bluebrain.nexus.rdf.encoder.NodeEncoder.stringEncoder
import ch.epfl.bluebrain.nexus.rdf.encoder.NodeEncoderError.IllegalConversion

import scala.util.Try

package object instances {
  implicit val uriNodeEncoder: NodeEncoder[Uri] = node =>
    stringEncoder(node).flatMap { uri =>
      Try(Uri(uri)).toEither.left.map(err => IllegalConversion(s"Invalid URI '${err.getMessage}'"))
  }
}
