package ch.epfl.bluebrain.nexus.commons.circe

import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.OrderedKeys
import io.circe.syntax._
import io.circe.{Json, JsonObject}

object JsonKeys {

  /**
    * Order json keys.
    *
    * @param json the json
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

  /**
    * Removes the provided keys from the json.
    *
    * @param json the json
    * @param keys list of ''keys'' to be removed from the top level of the ''json''
    * @return the original json without the provided ''keys'' on the top level of the structure
    */
  def removeKeys(json: Json, keys: String*): Json = {
    def inner(obj: JsonObject): Json =
      keys.foldLeft(obj)((accObj, key) => accObj.remove(key)).asJson

    json.arrayOrObject[Json](json, arr => arr.map(j => removeKeys(j, keys: _*)).asJson, obj => inner(obj))
  }
}
