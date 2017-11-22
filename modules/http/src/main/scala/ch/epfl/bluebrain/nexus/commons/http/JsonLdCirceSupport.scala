package ch.epfl.bluebrain.nexus.commons.http

import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model.{ContentTypeRange, HttpEntity}
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.OrderedKeys
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.syntax._
import io.circe.{Json, JsonObject, Printer}

import scala.collection.immutable.Seq

/**
  * Json-LD specific akka http circe support.
  *
  * It uses [[ch.epfl.bluebrain.nexus.commons.http.RdfMediaTypes.`application/ld+json`]] as the default content
  * type for encoding json trees into http request payloads.
  */
trait JsonLdCirceSupport extends FailFastCirceSupport {

  override def unmarshallerContentTypes: Seq[ContentTypeRange] =
    List(`application/json`, RdfMediaTypes.`application/ld+json`)

  /**
    * Order json keys.
    *
    * @param json the ''json'' we want to order
    * @param keys the implicitly available definition of the ordering
    */
  def sortKeys(json: Json)(implicit keys: OrderedKeys): Json = {

    implicit val _: Ordering[String] = new Ordering[String] {
      private val middlePos = keys.withPosition("")

      private def position(key: String): Int = keys.withPosition.getOrElse(key, middlePos)

      override def compare(x: String, y: String): Int = {
        val posX = position(x)
        val posY = position(y)
        if (posX == middlePos && posY == middlePos) x compareTo y
        else posX compareTo posY
      }
    }

    def canonicalJson(json: Json): Json =
      json.arrayOrObject[Json](json, arr => Json.fromValues(arr.map(canonicalJson)), obj => sorted(obj).asJson)

    def sorted(jObj: JsonObject): JsonObject =
      JsonObject.fromIterable(jObj.toVector.sortBy(_._1).map { case (k, v) => k -> canonicalJson(v) })

    canonicalJson(json)
  }

  final implicit def jsonLdMarshaller(implicit printer: Printer = Printer.noSpaces.copy(dropNullKeys = true),
                                      keys: OrderedKeys = OrderedKeys()): ToEntityMarshaller[Json] =
    Marshaller.withFixedContentType(RdfMediaTypes.`application/ld+json`) { json =>
      HttpEntity(RdfMediaTypes.`application/ld+json`, printer.pretty(sortKeys(json)))
    }
}

object JsonLdCirceSupport extends JsonLdCirceSupport {

  /**
    * Data type which holds the ordering for the JSON-LD keys.
    *
    * @param keys list of strings which defines the ordering for the JSON-LD keys
    */
  final case class OrderedKeys(keys: List[String]) {
    lazy val withPosition = keys.zipWithIndex.toMap
  }
  object OrderedKeys {

    /**
      * Construct an empty [[OrderedKeys]]
      */
    final def apply(): OrderedKeys = new OrderedKeys(List(""))
  }
}
