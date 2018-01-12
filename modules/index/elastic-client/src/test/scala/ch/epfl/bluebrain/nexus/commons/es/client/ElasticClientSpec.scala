package ch.epfl.bluebrain.nexus.commons.es.client

import java.util.regex.Pattern

import akka.http.scaladsl.model.Uri
import cats.instances.future._
import ch.epfl.bluebrain.nexus.commons.es.client.ElasticFailure.ElasticClientError
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

  "A ElasticClient" should {
    implicit val ec: ExecutionContext          = system.dispatcher
    implicit val ul: UntypedHttpClient[Future] = akkaHttpClient
    val queryCl: ElasticQueryClient[Future]    = ElasticQueryClient[Future](esUri)
    val cl: ElasticClient[Future]              = ElasticClient[Future](esUri, queryCl)
    val prefix                                 = "http://localhost/voc/"

    def indexPayload = jsonContentOf("/index_payload.json")
    def genJson(k: String, k2: String): Json =
      Json.obj(k -> Json.fromString(genString()), k2 -> Json.fromString(genString()))
    def createIndex(index: String): Unit          = Await.result(cl.createIndex(index, indexPayload), 15 seconds)
    def getValue(key: String, json: Json): String = json.hcursor.get[String](key).getOrElse("")

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
      implicit val rsGet: HttpClient[Future, Json]                  = withAkkaUnmarshaller[Json]
      val p: Pagination                                             = Pagination(0L, 3)
      implicit val D: Decoder[QueryResults[Json]]                   = ElasticDecoder[Json]
      implicit val rsSearch: HttpClient[Future, QueryResults[Json]] = withAkkaUnmarshaller[QueryResults[Json]]

      val index = genString(length = 6)
      val type1 = genString(length = 6)
      val type2 = genString(length = 6)
      val list = List.tabulate(10) { n =>
        val t = if (n >= 5) type1 else type2
        (t, genString(), genJson(s"${prefix}key", s"${prefix}key2"))
      }

      "add documents" in {
        createIndex(index)
        forAll(list) {
          case (t, id, json) =>
            cl.createDocument(index, t, id, json).futureValue shouldEqual (())
        }
      }

      "fetch documents" in {
        forAll(list) {
          case (t, id, json) =>
            cl.get[Json](index, t, id).futureValue shouldEqual json
        }
      }

      val parents = List.tabulate(2) { _ =>
        ("aa", genString(), genJson(s"${prefix}key", s"${prefix}key2"))
      }
      val children1 = List.tabulate(5) { _ =>
        ("bb", genString(), genJson(s"${prefix}key", s"${prefix}key2"))
      }
      val children2 = List.tabulate(5) { _ =>
        ("bb", genString(), genJson(s"${prefix}key", s"${prefix}key2"))
      }
      val parentsChildren = Map(parents.head._2 -> children1, parents(1)._2 -> children2)

      "add documents in parent/child relationship" in {
        forAll(parents) {
          case (t, id, json) =>
            cl.createDocument(index, t, id, json).futureValue shouldEqual (())
        }

        def createChildren(parent: String, children: List[(String, String, Json)]) =
          forAll(children) {
            case (t, id, json) =>
              cl.createDocument(index, t, id, json, parent = Some(parent)).futureValue shouldEqual (())
          }
        createChildren(parents.head._2, children1)
        createChildren(parents(1)._2, children2)
      }

      "get documents in parent/child relationship" in {
        forAll(parents) {
          case (t, id, json) =>
            cl.get[Json](index, t, id).futureValue shouldEqual json
        }

        def fetchChildren(parent: String, children: List[(String, String, Json)]) =
          forAll(children) {
            case (t, id, json) =>
              cl.get[Json](index, t, id, parent = Some(parent)).futureValue shouldEqual json
              whenReady(cl.get[Json](index, t, id).failed) { e =>
                e shouldBe a[ElasticClientError]
              }
          }
        fetchChildren(parents.head._2, children1)
        fetchChildren(parents(1)._2, children2)
      }

      "search for some specific keys and values" in {
        val (_, _, json) = list.head
        val query = jsonContentOf(
          "/query.json",
          Map(
            Pattern.quote("{{key1}}")   -> "key",
            Pattern.quote("{{key2}}")   -> "key2",
            Pattern.quote("{{value1}}") -> getValue(s"${prefix}key", json),
            Pattern.quote("{{value2}}") -> getValue(s"${prefix}key2", json)
          )
        )
        val qrs = ScoredQueryResults(1L, 1F, List(ScoredQueryResult(1F, json)))
        cl.search[Json](query, Set(index))(p).futureValue shouldEqual qrs
      }

      "search which returns only specified fields" in {
        val (_, _, json) = list.head
        val query = jsonContentOf(
          "/query.json",
          Map(
            Pattern.quote("{{key1}}")   -> "key",
            Pattern.quote("{{key2}}")   -> "key2",
            Pattern.quote("{{value1}}") -> getValue(s"${prefix}key", json),
            Pattern.quote("{{value2}}") -> getValue(s"${prefix}key2", json)
          )
        )
        val expectedJson = Json.obj(s"${prefix}key" -> Json.fromString(getValue(s"${prefix}key", json)))
        val qrs          = ScoredQueryResults(1L, 1F, List(ScoredQueryResult(1F, expectedJson)))
        cl.search[Json](query)(p, fields = Set(Uri(s"${prefix}key"))).futureValue shouldEqual qrs
      }

      "search all elements in a specific type" in {
        val type2Elems =
          list.take(5).map(_._3).sortWith(getValue(s"${prefix}key", _) < getValue(s"${prefix}key", _))

        val query = Json.obj("query" -> Json.obj())
        val qrs   = UnscoredQueryResults(5L, type2Elems.take(3).map(UnscoredQueryResult(_)))
        cl.search[Json](query, Set(index), Set(type2))(p, sort = SortList(List(Sort(s"${prefix}key").get)))
          .futureValue shouldEqual qrs
      }

      "search which returns 0 values" in {
        val query = jsonContentOf("/simple_query.json",
                                  Map(Pattern.quote("{{k}}") -> "key", Pattern.quote("{{v}}") -> genString()))
        val qrs = UnscoredQueryResults(0L, List.empty)
        cl.search[Json](query, Set(index))(p).futureValue shouldEqual qrs

      }

      "search all children" in {
        val query                = Json.obj("query" -> Json.obj())
        val (parentId, children) = parentsChildren.head
        val qrs = ScoredQueryResults(children.length.toLong, 1F, children.take(p.size).map {
          case (_, _, json) => ScoredQueryResult(1F, json)
        })
        cl.searchChildren[Json](query, Set(index), "bb", parentId)(p).futureValue shouldEqual qrs

        val p2 = Pagination(p.size.toLong, 2)
        val qrsPage2 = ScoredQueryResults(children.length.toLong, 1F, children.slice(p.size, p.size + p2.size).map {
          case (_, _, json) => ScoredQueryResult(1F, json)
        })
        cl.searchChildren[Json](query, Set(index), "bb", parentId)(p2).futureValue shouldEqual qrsPage2
      }

      "search all children sorted ascending" in {
        val query                = Json.obj("query" -> Json.obj())
        val (parentId, children) = parentsChildren.head
        val sortedChildren = children.sortWith {
          case ((_, _, source), (_, _, source2)) =>
            getValue(s"${prefix}key", source) < getValue(s"${prefix}key", source2)
        }
        val qrs = UnscoredQueryResults(children.length.toLong, sortedChildren.take(p.size).map {
          case (_, _, json) => UnscoredQueryResult(json)
        })
        cl.searchChildren[Json](query, Set(index), "bb", parentId)(p, sort = SortList(List(Sort(s"${prefix}key").get)))
          .futureValue shouldEqual qrs
      }

      "search all children sorted descending" in {
        val query                = Json.obj("query" -> Json.obj())
        val (parentId, children) = parentsChildren.head
        val sortedChildren = children.sortWith {
          case ((_, _, source), (_, _, source2)) =>
            getValue(s"${prefix}key", source) > getValue(s"${prefix}key", source2)
        }
        val qrs = UnscoredQueryResults(children.length.toLong, sortedChildren.take(p.size).map {
          case (_, _, json) => UnscoredQueryResult(json)
        })
        cl.searchChildren[Json](query, Set(index), "bb", parentId)(p, sort = SortList(List(Sort(s"-${prefix}key").get)))
          .futureValue shouldEqual qrs
      }
    }
  }
}
