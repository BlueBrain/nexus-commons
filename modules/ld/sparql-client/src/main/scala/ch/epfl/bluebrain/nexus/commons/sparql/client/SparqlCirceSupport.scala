package ch.epfl.bluebrain.nexus.commons.sparql.client

import java.io.ByteArrayInputStream

import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import ch.epfl.bluebrain.nexus.commons.http.{JsonLdCirceSupport, RdfMediaTypes}
import org.apache.jena.query.{ResultSet, ResultSetFactory}

/**
  * Sparql specific akka http circe support.
  *
  * It uses [[ch.epfl.bluebrain.nexus.commons.http.RdfMediaTypes.`application/ld+json`]] as the default content
  * type for encoding json trees into http request payloads.
  */
trait SparqlCirceSupport extends JsonLdCirceSupport {

  final implicit def sparqlResultsUnmarshaller: FromEntityUnmarshaller[ResultSet] =
    Unmarshaller.byteArrayUnmarshaller
      .forContentTypes(RdfMediaTypes.`application/sparql-results+json`)
      .map {
        case Array.emptyByteArray => throw Unmarshaller.NoContentException
        case data                 => ResultSetFactory.fromJSON(new ByteArrayInputStream(data))
      }
}

object SparqlCirceSupport extends SparqlCirceSupport
