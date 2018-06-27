package ch.epfl.bluebrain.nexus.commons.http

import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.OrderedKeys
import io.circe.{Json, JsonObject}
import io.circe.syntax._

object JsonOps {

  private val `@context` = "@context"

  /**
    * Interface syntax to expose new functionality into Json type
    *
    * @param json json payload
    */
  implicit class JsonOpsSyntax(json: Json) {

    /**
      * Order json keys.
      *
      * @param keys the implicitly available definition of the ordering
      */
    def sortKeys(implicit keys: OrderedKeys): Json = {

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
      * Method exposed on Json instances.
      *
      * @param keys list of ''keys'' to be removed from the top level of the ''json''
      * @return the original json without the provided ''keys'' on the top level of the structure
      */
    def removeKeys(keys: String*): Json = {
      def removeKeys(obj: JsonObject): Json =
        keys.foldLeft(obj)((accObj, key) => accObj.remove(key)).asJson

      json.arrayOrObject[Json](json, arr => arr.map(j => j.removeKeys(keys: _*)).asJson, obj => removeKeys(obj))
    }

    /**
      * Adds or merges a context URI to an existing JSON object.
      *
      * @param context the standard context URI
      * @return a new JSON object
      */
    def addContext(context: ContextUri): Json = {
      val contextUriString = Json.fromString(context.toString)

      json.asObject match {
        case Some(jo) =>
          val updated = jo(`@context`) match {
            case None => jo.add(`@context`, contextUriString)
            case Some(value) =>
              (value.asObject, value.asArray, value.asString) match {
                case (Some(vo), _, _) if !vo.values.exists(_ == contextUriString) =>
                  jo.add(`@context`, Json.arr(value, contextUriString))
                case (_, Some(va), _) if !va.contains(contextUriString) =>
                  jo.add(`@context`, Json.fromValues(va :+ contextUriString))
                case (_, _, Some(vs)) if vs != context.toString =>
                  jo.add(`@context`, Json.arr(value, contextUriString))
                case _ => jo
              }
          }
          Json.fromJsonObject(updated)
        case None => json
      }
    }

    /**
      * @return a new Json with the values of the top ''@context'' key
      */
    def contextValue: Json = json.hcursor.get[Json]("@context").getOrElse(Json.obj())

    /**
      * @param that the other context from where to merge this context with
      * @return a new Json with the values of the top ''@context'' key (this) and the provided ''that'' top ''@context'' key
      *         If two keys inside both contexts collide, the one in the ''other'' context will override the one in this context
      */
    def mergeContext(that: Json): Json =
      Json.obj("@context" -> (contextValue deepMerge that.contextValue))

    /**
      * @param that the context to append to this json
      * @return a new Json with the original context plus the context on the provided (''that'') json
      */
    def appendContextOf(that: Json): Json = json deepMerge mergeContext(that)
  }
}
