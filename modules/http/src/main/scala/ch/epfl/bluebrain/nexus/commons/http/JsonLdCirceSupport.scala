package ch.epfl.bluebrain.nexus.commons.http

import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model.{ContentTypeRange, HttpEntity}
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.{Key, OrderedKeys}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.syntax._
import io.circe.{Encoder, Json, JsonObject, Printer}

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

    implicit val _: Ordering[Key] = new Ordering[Key] {
      private val middlePos = keys.withPosition("")

      private def position(key: Key): Int =
        keys.withPosition.get(s"*${key.raw}") orElse keys.withPosition.get(key.prefixed) getOrElse (middlePos)

      override def compare(x: Key, y: Key): Int = {
        val posX = position(x)
        val posY = position(y)
        if (posX == middlePos && posY == middlePos) x.raw compareTo y.raw
        else posX compareTo posY
      }
    }

    def canonicalJson(json: Json, prefixPath: Option[String] = None): Json =
      json.arrayOrObject[Json](
        json,
        arr => Json.fromValues(arr.map(j => canonicalJson(j, prefixPath))),
        obj =>
          JsonObject
            .fromIterable(
              obj.toVector
                .sortBy {
                  case (k, _) => Key(prefixPath.map(p => s"${p}.$k").getOrElse(k), k)
                }
                .map {
                  case (k, v) => k -> canonicalJson(v, Some(prefixPath.map(p => k).getOrElse(k)))
                })
            .asJson
      )

    canonicalJson(json)
  }

  /**
    * `A` => HTTP entity
    *
    * @tparam A type to encode
    * @return marshaller for any `A` value
    */
  implicit final def marshallerHttp[A: Encoder](implicit printer: Printer = Printer.noSpaces.copy(dropNullKeys = true),
                                                keys: OrderedKeys = OrderedKeys()): ToEntityMarshaller[A] =
    jsonLdMarshaller.compose(implicitly[Encoder[A]].apply)

  /**
    * `Json` => HTTP entity
    *
    * @return marshaller for JSON-LD value
    */
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

  private[http] case class Key(prefixed: String, raw: String)

  object OrderedKeys {

    /**
      * Construct an empty [[OrderedKeys]]
      */
    final def apply(): OrderedKeys = new OrderedKeys(List(""))
  }

}
