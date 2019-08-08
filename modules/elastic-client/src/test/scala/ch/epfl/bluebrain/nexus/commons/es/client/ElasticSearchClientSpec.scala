package ch.epfl.bluebrain.nexus.commons.es.client

import java.util.regex.Pattern

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import cats.instances.future._
import ch.epfl.bluebrain.nexus.commons.es.client.ElasticSearchClient.BulkOp
import ch.epfl.bluebrain.nexus.commons.es.client.ElasticSearchFailure.{ElasticSearchClientError, ElasticUnexpectedError}
import ch.epfl.bluebrain.nexus.commons.es.server.embed.ElasticServer
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient._
import ch.epfl.bluebrain.nexus.commons.search.Pagination
import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.commons.search.QueryResult._
import ch.epfl.bluebrain.nexus.commons.search.QueryResults._
import ch.epfl.bluebrain.nexus.commons.search.{QueryResults, Sort, SortList}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.parser.parse
import io.circe.{Decoder, Json}
import org.scalatest._
import org.scalatest.concurrent.{Eventually, ScalaFutures}

import scala.concurrent.duration._
import scala.concurrent.Future

class ElasticSearchClientSpec
    extends ElasticServer
    with ScalaFutures
    with Matchers
    with Resources
    with Inspectors
    with CancelAfterFailure
    with Assertions
    with OptionValues
    with Eventually {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(15 seconds, 300 milliseconds)

  private def genIndexString(): String =
    genString(length = 10, pool = Vector.range('a', 'f') ++ """ "*\<>|,/?""")

  private implicit val uc: UntypedHttpClient[Future] = untyped[Future]

  "An ElasticSearchClient" when {
    val cl: ElasticSearchClient[Future] = ElasticSearchClient[Future](esUri)
    val indexPayload                    = jsonContentOf("/index_payload.json")
    val mappingPayload                  = jsonContentOf("/mapping_payload.json")
    def genJson(k: String, k2: String): Json =
      Json.obj(k -> Json.fromString(genString()), k2 -> Json.fromString(genString()))
    def getValue(key: String, json: Json): String = json.hcursor.get[String](key).getOrElse("")

    val matchAll = Json.obj("query" -> Json.obj("match_all" -> Json.obj()))

    "sanitilizing index names" should {
      "succeed" in {
        forAll(""" "*\<>|,/?""") { ch =>
          cl.sanitize(s"a${ch}a") shouldEqual "a_a"
        }
        cl.sanitize(s"a*a", allowWildCard = true) shouldEqual "a*a"
      }
    }

    "performing index operations" should {

      "return false when index does not exist" in {
        cl.existsIndex("some").futureValue shouldEqual false
      }

      "create mappings if index does not exist" in {
        val index = genIndexString()
        cl.createIndex(index, indexPayload).futureValue shouldEqual true
        cl.createIndex(index, indexPayload).futureValue shouldEqual false
      }

      "create mappings and index settings" in {
        val index = genIndexString()
        cl.createIndex(index, indexPayload).futureValue shouldEqual true
        cl.existsIndex(index).futureValue shouldEqual true
      }

      "create mappings" in {
        val index = genIndexString()
        cl.createIndex(index).futureValue shouldEqual true
        cl.updateMapping(index, mappingPayload).futureValue shouldEqual true
        cl.updateMapping(genIndexString(), mappingPayload).futureValue shouldEqual false
        whenReady(cl.updateMapping(index, indexPayload).failed)(_ shouldBe a[ElasticSearchClientError])
      }

      "delete index" in {
        val index = genIndexString()
        cl.createIndex(index, indexPayload).futureValue shouldEqual true
        cl.deleteIndex(index).futureValue shouldEqual true
        cl.deleteIndex(index).futureValue shouldEqual false
      }
    }

    "performing document operations" should {
      val p: Pagination                                             = Pagination(0, 3)
      implicit val D: Decoder[QueryResults[Json]]                   = ElasticSearchDecoder[Json]
      implicit val rsSearch: HttpClient[Future, QueryResults[Json]] = withUnmarshaller[Future, QueryResults[Json]]
      implicit val rsGet: HttpClient[Future, Json]                  = withUnmarshaller[Future, Json]

      val index          = genIndexString()
      val indexSanitized = cl.sanitize(index)
      val list           = List.fill(10)(genString() -> genJson("key", "key2"))

      "add documents" in {
        cl.createIndex(index, indexPayload).futureValue shouldEqual true
        val ops = list.map { case (id, json) => BulkOp.Create(indexSanitized, id, json) }
        cl.bulk(ops).futureValue shouldEqual (())
      }

      "fetch documents" in {
        forAll(list) {
          case (id, json) =>
            eventually {
              cl.get[Json](index, id).futureValue.value shouldEqual json
            }
        }
      }

      "return none when fetch documents that do not exist" in {
        cl.get[Json](index, genString()).futureValue shouldEqual None
      }

      "search for some specific keys and values" in {
        val (_, json) = list.head
        val query = jsonContentOf(
          "/query.json",
          Map(
            Pattern.quote("{{value1}}") -> getValue("key", json),
            Pattern.quote("{{value2}}") -> getValue("key2", json)
          )
        )
        val qrs = ScoredQueryResults(1L, 1f, List(ScoredQueryResult(1f, json)))
        cl.search[Json](query, Set(indexSanitized))(p).futureValue shouldEqual qrs
      }

      "search for some specific keys and values with wildcard index" in {
        val (_, json) = list.head
        val query = jsonContentOf(
          "/query.json",
          Map(
            Pattern.quote("{{value1}}") -> getValue("key", json),
            Pattern.quote("{{value2}}") -> getValue("key2", json)
          )
        )
        val qrs = ScoredQueryResults(1L, 1f, List(ScoredQueryResult(1f, json)))
        cl.search[Json](query, Set(indexSanitized.take(5) + "*"))(p).futureValue shouldEqual qrs
      }

      "search on an index which does not exist" in {
        val qrs = ScoredQueryResults(0L, 0f, List.empty)

        cl.search[Json](matchAll, Set("non_exists"))(p).futureValue shouldEqual qrs
      }

      "search which returns only specified fields" in {
        val (_, json) = list.head
        val query = jsonContentOf(
          "/query.json",
          Map(
            Pattern.quote("{{value1}}") -> getValue("key", json),
            Pattern.quote("{{value2}}") -> getValue("key2", json)
          )
        )
        val expectedJson = Json.obj("key" -> Json.fromString(getValue("key", json)))
        val qrs          = ScoredQueryResults(1L, 1f, List(ScoredQueryResult(1f, expectedJson)))
        cl.search[Json](query)(p, fields = Set("key")).futureValue shouldEqual qrs
      }

      "search all elements sorted in order ascending" in {
        val elems = list.map(_._2).sortWith(getValue("key", _) < getValue("key", _))
        val token = Json.arr(Json.fromString(getValue("key", elems(2))))

        val qrs =
          UnscoredQueryResults(elems.length.toLong, elems.take(3).map(UnscoredQueryResult.apply), Some(token.noSpaces))
        cl.search[Json](matchAll, Set(indexSanitized))(p, sort = SortList(List(Sort("key"))))
          .futureValue shouldEqual qrs
      }
      "search all elements sorted in order descending" in {
        val elems = list.map(_._2).sortWith(getValue("key", _) > getValue("key", _))
        val token = Json.arr(Json.fromString(getValue("key", elems(2))))

        val qrs =
          UnscoredQueryResults(elems.length.toLong, elems.take(3).map(UnscoredQueryResult.apply), Some(token.noSpaces))
        cl.search[Json](matchAll, Set(indexSanitized))(p, sort = SortList(List(Sort("-key"))))
          .futureValue shouldEqual qrs
      }

      "search elements with sort_after" in {
        val elems = list.map(_._2).sortWith(getValue("key", _) < getValue("key", _))
        val token = Json.arr(Json.fromString(getValue("key", elems(2))))

        val qrs1 =
          UnscoredQueryResults(elems.length.toLong, elems.take(3).map(UnscoredQueryResult.apply), Some(token.noSpaces))

        val results1: QueryResults[Json] =
          cl.search[Json](matchAll, Set(indexSanitized))(p, sort = SortList(List(Sort("key")))).futureValue

        results1 shouldEqual qrs1

        val sort                  = parse(results1.token.value).toOption.value
        val searchAfterPagination = Pagination(sort, 3)
        val token2                = Json.arr(Json.fromString(getValue("key", elems(5))))

        val qrs2 = UnscoredQueryResults(
          elems.length.toLong,
          elems.slice(3, 6).map(UnscoredQueryResult.apply),
          Some(token2.noSpaces)
        )

        val results2 = cl
          .search[Json](matchAll, Set(indexSanitized))(searchAfterPagination, sort = SortList(List(Sort("key"))))
          .futureValue

        results2 shouldEqual qrs2

      }

      "search elements with from" in {
        val elems = list.map(_._2).sortWith(getValue("key", _) < getValue("key", _))
        val token = Json.arr(Json.fromString(getValue("key", elems(2))))

        val qrs1 =
          UnscoredQueryResults(elems.length.toLong, elems.take(3).map(UnscoredQueryResult.apply), Some(token.noSpaces))

        val results1 = cl.search[Json](matchAll, Set(indexSanitized))(p, sort = SortList(List(Sort("key")))).futureValue

        results1 shouldEqual qrs1

        val searchAfterPagination = Pagination(3, 3)
        val token2                = Json.arr(Json.fromString(getValue("key", elems(5))))

        val qrs2 = UnscoredQueryResults(
          elems.length.toLong,
          elems.slice(3, 6).map(UnscoredQueryResult.apply),
          Some(token2.noSpaces)
        )

        val results2 = cl
          .search[Json](matchAll, Set(indexSanitized))(searchAfterPagination, sort = SortList(List(Sort("key"))))
          .futureValue

        results2 shouldEqual qrs2
      }

      "search which returns 0 values" in {
        val query = jsonContentOf(
          "/simple_query.json",
          Map(Pattern.quote("{{k}}") -> "key", Pattern.quote("{{v}}") -> genString())
        )
        val qrs = UnscoredQueryResults(0L, List.empty)
        cl.search[Json](query, Set(indexSanitized))(p).futureValue shouldEqual qrs

      }

      "search raw all elements sorted in order ascending" in {

        val sortedMatchAll = Json.obj(
          "query" -> Json.obj("match_all" -> Json.obj()),
          "sort" -> Json.arr(
            Json.obj(
              "key" -> Json.obj(
                "order" -> Json.fromString("asc")
              )
            )
          )
        )
        val elems =
          list.sortWith((e1, e2) => getValue("key", e1._2) < getValue("key", e2._2))

        val json = cl.searchRaw(sortedMatchAll, Set(indexSanitized)).futureValue
        println(json.asObject.value("took").value)
        val expectedResponse = jsonContentOf("/elastic_search_response.json").mapObject { obj =>
          obj
            .add("took", json.asObject.value("took").value)
            .add(
              "hits",
              Json.obj(
                "total"     -> Json.obj("value" -> Json.fromInt(10), "relation" -> Json.fromString("eq")),
                "max_score" -> Json.Null,
                "hits" -> Json.arr(
                  elems.map { e =>
                    Json.obj(
                      "_index"  -> Json.fromString(cl.sanitize(index)),
                      "_type"   -> Json.fromString("_doc"),
                      "_id"     -> Json.fromString(e._1),
                      "_score"  -> Json.Null,
                      "_source" -> e._2,
                      "sort"    -> Json.arr(Json.fromString(getValue("key", e._2)))
                    )
                  }: _*
                )
              )
            )
        }
        json shouldEqual expectedResponse
      }

      "search raw all elements sorted in order descending" in {
        val sortedMatchAll = Json.obj(
          "query" -> Json.obj("match_all" -> Json.obj()),
          "sort" -> Json.arr(
            Json.obj(
              "key" -> Json.obj(
                "order" -> Json.fromString("desc")
              )
            )
          )
        )
        val elems =
          list.sortWith((e1, e2) => getValue("key", e1._2) > getValue("key", e2._2))

        val json = cl.searchRaw(sortedMatchAll, Set(indexSanitized)).futureValue
        val expectedResponse = jsonContentOf("/elastic_search_response.json").mapObject { obj =>
          obj
            .add("took", json.asObject.value("took").value)
            .add(
              "hits",
              Json.obj(
                "total"     -> Json.obj("value" -> Json.fromInt(10), "relation" -> Json.fromString("eq")),
                "max_score" -> Json.Null,
                "hits" -> Json.arr(
                  elems.map { e =>
                    Json.obj(
                      "_index"  -> Json.fromString(cl.sanitize(index)),
                      "_type"   -> Json.fromString("_doc"),
                      "_id"     -> Json.fromString(e._1),
                      "_score"  -> Json.Null,
                      "_source" -> e._2,
                      "sort"    -> Json.arr(Json.fromString(getValue("key", e._2)))
                    )
                  }: _*
                )
              )
            )
        }
        json shouldEqual expectedResponse

      }

      "return ElasticClientError when query is wrong" in {

        val query = Json.obj("query" -> Json.obj("other" -> Json.obj()))
        val result: ElasticSearchClientError =
          cl.searchRaw(query, Set(indexSanitized))
            .failed
            .futureValue
            .asInstanceOf[ElasticSearchClientError]
        result.status shouldEqual StatusCodes.BadRequest
        parse(result.body).toOption.value shouldEqual jsonContentOf("/elastic_client_error.json")

      }

      val listModified = list.map {
        case (id, json) => id -> (json deepMerge Json.obj("key" -> Json.fromString(genString())))
      }

      "update key field on documents" in {
        forAll(listModified) {
          case (id, json) =>
            val updateScript = jsonContentOf("/update.json", Map(Pattern.quote("{{value}}") -> getValue("key", json)))
            cl.update(index, id, updateScript).futureValue shouldEqual (())
            cl.get[Json](index, id).futureValue.value shouldEqual json
            val jsonWithKey = Json.obj("key" -> Json.fromString(getValue("key", json)))
            cl.get[Json](index, id, include = Set("key")).futureValue.value shouldEqual jsonWithKey
            cl.get[Json](index, id, exclude = Set("key2")).futureValue.value shouldEqual jsonWithKey
            cl.get[Json](index, id, include = Set("key"), exclude = Set("key2"))
              .futureValue
              .value shouldEqual jsonWithKey
        }
      }

      val mapModified = list.map {
        case (id, json) => id -> (json deepMerge Json.obj("key" -> Json.fromString(genString())))
      }.toMap

      "update key field on documents from query" in {
        forAll(listModified) {
          case (id, json) =>
            val query =
              jsonContentOf(
                "/simple_query.json",
                Map(Pattern.quote("{{k}}") -> "key", Pattern.quote("{{v}}") -> getValue("key", json))
              )
            val updateScript =
              jsonContentOf("/update.json", Map(Pattern.quote("{{value}}") -> getValue("key", mapModified(id))))

            cl.updateDocuments(Set(indexSanitized), query, updateScript).futureValue shouldEqual (())
            cl.get[Json](index, id).futureValue.value shouldEqual mapModified(id)
        }
      }

      "delete documents" in {
        forAll(mapModified.toList.take(3)) {
          case (id, _) =>
            cl.delete(index, id).futureValue shouldEqual (())
            cl.get[Json](index, id).futureValue shouldEqual None
        }
      }

      "delete documents from query" in {
        forAll(mapModified.toList.drop(3)) {
          case (id, json) =>
            val query =
              jsonContentOf(
                "/simple_query.json",
                Map(Pattern.quote("{{k}}") -> "key", Pattern.quote("{{v}}") -> getValue("key", json))
              )
            cl.deleteDocuments(Set(indexSanitized), query).futureValue shouldEqual (())
            cl.get[Json](index, id).futureValue shouldEqual None
        }
      }

      "add several bulk operations" in {
        val toUpdate   = genString() -> genJson("key", "key1")
        val toOverride = genString() -> genJson("key", "key2")
        val toDelete   = genString() -> genJson("key", "key3")
        val list       = List(toUpdate, toOverride, toDelete)
        val ops        = list.map { case (id, json) => BulkOp.Create(indexSanitized, id, json) }
        cl.bulk(ops).futureValue shouldEqual (())

        val updated = Json.obj("key1" -> Json.fromString("updated"))
        cl.bulk(
            List(
              BulkOp.Delete(indexSanitized, toDelete._1),
              BulkOp.Index(indexSanitized, toOverride._1, updated),
              BulkOp.Update(indexSanitized, toUpdate._1, Json.obj("doc" -> updated))
            )
          )
          .futureValue shouldEqual (())
        eventually {
          cl.get[Json](index, toUpdate._1).futureValue.value shouldEqual toUpdate._2.deepMerge(updated)
        }
        eventually {
          cl.get[Json](index, toOverride._1).futureValue.value shouldEqual updated
        }
        eventually {
          cl.get[Json](index, toDelete._1).futureValue shouldEqual None
        }
      }

      "do nothing when BulkOp list is empty" in {
        cl.bulk(List.empty).futureValue shouldEqual (())
      }

      "add and fetch ids with non UTF-8 characters" in {
        val list = List.fill(5)(genIndexString() -> genJson("key", "key1"))
        val ops  = list.map { case (id, json) => BulkOp.Create(indexSanitized, id, json) }
        cl.bulk(ops).futureValue shouldEqual (())

        forAll(list) {
          case (id, json) =>
            eventually {
              cl.get[Json](index, id).futureValue.value shouldEqual json
            }
        }

        forAll(list) {
          case (id, _) =>
            cl.update(index, id, Json.obj("doc" -> Json.obj("id" -> Json.fromString(id)))).futureValue
        }

        forAll(list) {
          case (id, json) =>
            eventually {
              cl.get[Json](index, id).futureValue.value shouldEqual json.deepMerge(
                Json.obj("id" -> Json.fromString(id))
              )
            }
        }

        forAll(list) {
          case (id, _) => cl.delete(index, id).futureValue
        }
      }

      "fail bulking" in {
        val toUpdate = genString() -> genJson("key", "key1")
        val toDelete = genString() -> genJson("key", "key3")
        val list     = List(toUpdate, toDelete)
        val ops      = list.map { case (id, json) => BulkOp.Create(indexSanitized, id, json) }
        cl.bulk(ops).futureValue shouldEqual (())

        val updated = Json.obj("key1" -> Json.fromString("updated"))
        cl.bulk(
            List(
              BulkOp.Delete(indexSanitized, toDelete._1),
              BulkOp.Update(genString(), toUpdate._1, Json.obj("doc" -> updated))
            )
          )
          .failed
          .futureValue shouldBe a[ElasticUnexpectedError]
      }
    }
  }

  implicit class HttpResponseSyntax(value: Future[HttpResponse]) {

    def mapJson(body: (Json, HttpResponse) => Assertion)(implicit um: FromEntityUnmarshaller[Json]): Assertion =
      whenReady(value)(res => um(res.entity).map(json => body(json, res)).futureValue)

  }
}
