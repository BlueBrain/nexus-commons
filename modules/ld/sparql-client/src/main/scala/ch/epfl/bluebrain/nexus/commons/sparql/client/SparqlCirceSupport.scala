package ch.epfl.bluebrain.nexus.commons.sparql.client

import java.io.ByteArrayInputStream

import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.{ContentTypeRange, HttpEntity}
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Json, Printer}
import org.apache.jena.query.{ResultSet, ResultSetFactory}

import scala.collection.immutable.Seq

/**
  * Sparql specific akka http circe support.
  *
  * It uses [[ch.epfl.bluebrain.nexus.commons.sparql.client.RdfMediaTypes.`application/ld+json`]] as the default content
  * type for encoding json trees into http request payloads.
  */
trait SparqlCirceSupport extends FailFastCirceSupport {

  override def unmarshallerContentTypes: Seq[ContentTypeRange] =
    List(`application/json`, RdfMediaTypes.`application/ld+json`)

  final implicit def jsonLdMarshaller(implicit printer: Printer = Printer.noSpaces): ToEntityMarshaller[Json] =
    Marshaller.withFixedContentType(RdfMediaTypes.`application/ld+json`) { json =>
      HttpEntity(RdfMediaTypes.`application/ld+json`, printer.pretty(json))
    }

  final implicit def sparqlResultsUnmarshaller: FromEntityUnmarshaller[ResultSet] =
    Unmarshaller.byteArrayUnmarshaller
      .forContentTypes(RdfMediaTypes.`application/sparql-results+json`)
      .map {
        case Array.emptyByteArray => throw Unmarshaller.NoContentException
        case data                 => ResultSetFactory.fromJSON(new ByteArrayInputStream(data))
      }
}

object SparqlCirceSupport extends SparqlCirceSupport
