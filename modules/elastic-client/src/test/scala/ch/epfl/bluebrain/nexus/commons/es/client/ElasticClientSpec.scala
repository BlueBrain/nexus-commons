package ch.epfl.bluebrain.nexus.commons.es.client

import java.util.regex.Pattern

import cats.instances.future._
import ch.epfl.bluebrain.nexus.commons.es.client.ElasticFailure.ElasticClientError
import ch.epfl.bluebrain.nexus.commons.es.server.embed.ElasticServer
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.{UntypedHttpClient, akkaHttpClient, withAkkaUnmarshaller}
import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.commons.types.search.QueryResult._
import ch.epfl.bluebrain.nexus.commons.types.search.QueryResults._
import ch.epfl.bluebrain.nexus.commons.types.search.{Pagination, QueryResults, Sort, SortList}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.{Decoder, Json}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Inspectors, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class ElasticClientSpec extends ElasticServer with ScalaFutures with Matchers with Resources with Inspectors {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(3 seconds, 100 millis)

  "An ElasticClient" should {
    implicit val ec: ExecutionContext          = system.dispatcher
    implicit val ul: UntypedHttpClient[Future] = akkaHttpClient
    val queryCl: ElasticQueryClient[Future]    = ElasticQueryClient[Future](esUri)
    val cl: ElasticClient[Future]              = ElasticClient[Future](esUri, queryCl)
    val t                                      = "doc"
    def indexPayload                           = jsonContentOf("/index_payload.json")
    def genJson(k: String, k2: String): Json =
      Json.obj(k -> Json.fromString(genString()), k2 -> Json.fromString(genString()))
    def createIndex(index: String): Unit          = Await.result(cl.createIndex(index, indexPayload), 15 seconds)
    def getValue(key: String, json: Json): String = json.hcursor.get[String](key).getOrElse("")

    val matchAll = Json.obj("query" -> Json.obj("match_all" -> Json.obj()))

    "perform index operations" when {
      "fail when index does not exist" in {
        whenReady(cl.existsIndex("some").failed) { e =>
          e shouldBe a[ElasticClientError]
        }
      }

      "create mappings and index settings" in {
        val index = genString(length = 6)
        cl.createIndex(index, indexPayload).futureValue shouldEqual (())
        cl.existsIndex(index).futureValue shouldEqual (())
      }
    }

    "perform document operations" when {
      val p: Pagination                                             = Pagination(0L, 3)
      implicit val D: Decoder[QueryResults[Json]]                   = ElasticDecoder[Json]
      implicit val rsSearch: HttpClient[Future, QueryResults[Json]] = withAkkaUnmarshaller[QueryResults[Json]]
      implicit val rsGet: HttpClient[Future, Json]                  = withAkkaUnmarshaller[Json]

      val index = genString(length = 6)
      val list  = List.fill(10)(genString() -> genJson("key", "key2"))

      "add documents" in {
        createIndex(index)
        forAll(list) {
          case (id, json) =>
            cl.create(index, t, id, json).futureValue shouldEqual (())

        }
      }

      "fetch documents" in {
        forAll(list) {
          case (id, json) =>
            cl.get[Json](index, t, id).futureValue shouldEqual json
        }
      }

      "fail when fetch documents that do not exist" in {
        whenReady(cl.get[Json](index, t, genString()).failed) { e =>
          e shouldBe a[ElasticClientError]
        }
      }

      "search for some specific keys and values" in {
        val (_, json) = list.head
        val query = jsonContentOf("/query.json",
                                  Map(Pattern.quote("{{value1}}") -> getValue("key", json),
                                      Pattern.quote("{{value2}}") -> getValue("key2", json)))
        val qrs = ScoredQueryResults(1L, 1F, List(ScoredQueryResult(1F, json)))
        cl.search[Json](query, Set(index))(p).futureValue shouldEqual qrs
      }

      "search which returns only specified fields" in {
        val (_, json) = list.head
        val query = jsonContentOf("/query.json",
                                  Map(Pattern.quote("{{value1}}") -> getValue("key", json),
                                      Pattern.quote("{{value2}}") -> getValue("key2", json)))
        val expectedJson = Json.obj("key" -> Json.fromString(getValue("key", json)))
        val qrs          = ScoredQueryResults(1L, 1F, List(ScoredQueryResult(1F, expectedJson)))
        cl.search[Json](query)(p, fields = Set("key")).futureValue shouldEqual qrs
      }

      "search all elements sorted in order ascending" in {
        val elems =
          list.map(_._2).sortWith(getValue("key", _) < getValue("key", _))

        val qrs = UnscoredQueryResults(elems.length.toLong, elems.take(3).map(UnscoredQueryResult(_)))
        cl.search[Json](matchAll, Set(index))(p, sort = SortList(List(Sort("key")))).futureValue shouldEqual qrs
      }
      "search all elements sorted in order descending" in {
        val elems =
          list.map(_._2).sortWith(getValue("key", _) > getValue("key", _))

        val qrs = UnscoredQueryResults(elems.length.toLong, elems.take(3).map(UnscoredQueryResult(_)))
        cl.search[Json](matchAll, Set(index))(p, sort = SortList(List(Sort("-key")))).futureValue shouldEqual qrs
      }

      "search which returns 0 values" in {
        val query = jsonContentOf("/simple_query.json",
                                  Map(Pattern.quote("{{k}}") -> "key", Pattern.quote("{{v}}") -> genString()))
        val qrs = UnscoredQueryResults(0L, List.empty)
        cl.search[Json](query, Set(index))(p).futureValue shouldEqual qrs

      }

      val listModified = list.map {
        case (id, json) => id -> (json deepMerge Json.obj("key" -> Json.fromString(genString())))
      }

      "update key field on documents documents" in {
        forAll(listModified) {
          case (id, json) =>
            val updateScript = jsonContentOf("/update.json", Map(Pattern.quote("{{value}}") -> getValue("key", json)))
            cl.update(index, t, id, updateScript).futureValue shouldEqual (())
            cl.get[Json](index, t, id).futureValue shouldEqual json
        }
      }

      val mapModified = list.map {
        case (id, json) => id -> (json deepMerge Json.obj("key" -> Json.fromString(genString())))
      }.toMap

      "update key field on documents documents from query" in {
        forAll(listModified) {
          case (id, json) =>
            val query =
              jsonContentOf("/simple_query.json",
                            Map(Pattern.quote("{{k}}") -> "key", Pattern.quote("{{v}}") -> getValue("key", json)))
            val updateScript =
              jsonContentOf("/update.json", Map(Pattern.quote("{{value}}") -> getValue("key", mapModified(id))))

            cl.updateDocuments(Set(index), query, updateScript).futureValue shouldEqual (())
            cl.get[Json](index, t, id).futureValue shouldEqual mapModified(id)
        }
      }

      "delete documents" in {
        forAll(mapModified.toList.take(3)) {
          case (id, _) =>
            cl.delete(index, t, id).futureValue shouldEqual (())
            whenReady(cl.get[Json](index, t, id).failed) { e =>
              e shouldBe a[ElasticClientError]
            }
        }
      }

      "delete documents from query" in {
        forAll(mapModified.toList.drop(3)) {
          case (id, json) =>
            val query =
              jsonContentOf("/simple_query.json",
                            Map(Pattern.quote("{{k}}") -> "key", Pattern.quote("{{v}}") -> getValue("key", json)))
            cl.deleteDocuments(Set(index), query).futureValue shouldEqual (())
            whenReady(cl.get[Json](index, t, id).failed) { e =>
              e shouldBe a[ElasticClientError]
            }
        }
      }
    }
  }
}
