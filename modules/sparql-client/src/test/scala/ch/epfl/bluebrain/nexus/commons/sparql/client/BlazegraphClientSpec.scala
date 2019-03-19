package ch.epfl.bluebrain.nexus.commons.sparql.client

import java.util.regex.Pattern.quote

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import cats.instances.future._
import ch.epfl.bluebrain.nexus.commons.http.HttpClient._
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._
import ch.epfl.bluebrain.nexus.commons.sparql.client.BlazegraphClientFixture._
import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlFailure.{SparqlClientError, SparqlServerError}
import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlResults._
import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlWriteQuery._
import ch.epfl.bluebrain.nexus.commons.test.{CirceEq, Randomness}
import ch.epfl.bluebrain.nexus.commons.test.Resources._
import ch.epfl.bluebrain.nexus.rdf.Graph
import ch.epfl.bluebrain.nexus.rdf.Node.blank
import ch.epfl.bluebrain.nexus.rdf.syntax._
import com.bigdata.rdf.sail.webapp.NanoSparqlServer
import io.circe.parser._
import io.circe.syntax._
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, EitherValues, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

//noinspection TypeAnnotation
class BlazegraphClientSpec
    extends TestKit(ActorSystem("BlazegraphClientSpec"))
    with WordSpecLike
    with Matchers
    with ScalaFutures
    with EitherValues
    with BeforeAndAfterAll
    with Randomness
    with CirceEq
    with Eventually {

  private val port = freePort()

  private val server = {
    System.setProperty("jetty.home", getClass.getResource("/war").toExternalForm)
    NanoSparqlServer.newInstance(port, null, null)
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10 seconds, 500 milliseconds)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    server.start()
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    server.stop()
    super.afterAll()
  }

  private implicit val ec = system.dispatcher
  private implicit val mt = ActorMaterializer()

  private implicit val uc = untyped[Future]
  private implicit val jc = withUnmarshaller[Future, SparqlResults]

  "A BlazegraphClient" should {
    def client(ns: String) = BlazegraphClient[Future](s"http://$localhost:$port/blazegraph", ns, None)

    "verify if a namespace exists" in new BlazegraphClientFixture {
      client(namespace).namespaceExists.futureValue shouldEqual false
    }

    "create an index" in new BlazegraphClientFixture {
      val cl = client(namespace)
      cl.createNamespace(properties()).futureValue shouldEqual true
      cl.namespaceExists.futureValue shouldEqual true
      cl.createNamespace(properties()).futureValue shouldEqual false
    }

    "create index with wrong payload" in new BlazegraphClientFixture {
      val cl = client(namespace)
      whenReady(cl.createNamespace(properties("/wrong.properties")).failed)(_ shouldBe a[SparqlServerError])
    }

    "delete an index" in new BlazegraphClientFixture {
      val cl = client(namespace)
      cl.deleteNamespace.futureValue shouldEqual false
      cl.createNamespace(properties()).futureValue shouldEqual true
      cl.deleteNamespace.futureValue shouldEqual true
    }

    "create a new named graph" in new BlazegraphClientFixture {
      val cl = client(namespace)
      cl.createNamespace(properties()).futureValue
      cl.replace(graph, load(id, label, value)).futureValue
      cl.copy(namespace = namespace).triples(graph) should have size 2
      cl.triples() should have size 2
    }

    "drop a named graph" in new BlazegraphClientFixture {
      val cl = client(namespace)
      cl.createNamespace(properties()).futureValue
      cl.replace(graph, load(id, label, value)).futureValue
      cl.drop(graph).futureValue
      cl.triples() shouldBe empty
    }

    "replace a named graph" in new BlazegraphClientFixture {
      val cl = client(namespace)
      cl.createNamespace(properties()).futureValue
      cl.replace(graph, load(id, label, value)).futureValue
      cl.replace(graph, load(id, label, value + "-updated")).futureValue
      cl.triples(graph).map(_._3) should contain theSameElementsAs Set(label, value + "-updated")
      cl.triples().map(_._3) should contain theSameElementsAs Set(label, value + "-updated")
    }

    "run bulk operation" in new BlazegraphClientFixture {
      val id2: String    = genString()
      val label2: String = genString()
      val value2: String = genString()
      val graph2: Uri    = s"http://$localhost:8080/graphs/${genString()}"

      val cl = client(namespace)
      cl.createNamespace(properties()).futureValue
      cl.bulk(replace(graph, load(id, label, value)), replace(graph2, load(id2, label2, value2))).futureValue
      cl.triples(graph).map(_._3) should contain theSameElementsAs Set(label, value)
      cl.triples(graph2).map(_._3) should contain theSameElementsAs Set(label2, value2)
      cl.triples().map(_._3) should contain theSameElementsAs Set(label, value) ++ Set(label2, value2)
    }

    "return the JSON response from query" in new BlazegraphClientFixture {
      val cl = client(namespace)
      cl.createNamespace(properties()).futureValue
      cl.replace(graph, load(id, label, value)).futureValue
      val expected = jsonContentOf("/sparql-json.json",
                                   Map(quote("{id}") -> id, quote("{label}") -> label, quote("{value}") -> value))
      eventually {
        cl.queryRaw(s"SELECT * WHERE { GRAPH <$graph> { ?s ?p ?o } }").futureValue.asJson should equalIgnoreArrayOrder(
          expected)
      }
    }

    "fail the query" in new BlazegraphClientFixture {
      val cl = client(namespace)
      cl.createNamespace(properties()).futureValue
      cl.replace(graph, load(id, label, value)).futureValue
      whenReady(cl.queryRaw(s"SELECT somethingwrong").failed)(_ shouldBe a[SparqlClientError])
    }

    "patch a named graph removing matching predicates" in new BlazegraphClientFixture {
      val cl = client(namespace)
      cl.createNamespace(properties()).futureValue
      val json = parse(
        s"""
           |{
           |  "@context": {
           |    "label": "http://www.w3.org/2000/01/rdf-schema#label",
           |    "schema": "http://schema.org/",
           |    "nested": "http://localhost/nested/"
           |  },
           |  "@id": "http://localhost/$id",
           |  "label": "$label-updated",
           |  "nested": {
           |     "schema:name": "name",
           |     "schema:title": "title"
           |  }
           |}""".stripMargin
      ).right.value
      cl.replace(graph, load(id, label, value)).futureValue
      val strategy = PatchStrategy.removePredicates(
        Set(
          "http://schema.org/value",
          "http://www.w3.org/2000/01/rdf-schema#label"
        ))
      cl.patch(graph, json.asGraph(blank).right.value, strategy).futureValue
      cl.triples() should have size 4
      val results = cl.triples(graph)
      results should have size 4
      results.map(_._2).toSet should contain theSameElementsAs Set(
        "http://www.w3.org/2000/01/rdf-schema#label",
        "http://schema.org/name",
        "http://localhost/nested/",
        "http://schema.org/title"
      )
      results.map(_._3).toSet should contain allOf ("name", "title", s"$label-updated")
    }

    "patch a named graph retaining matching predicates" in new BlazegraphClientFixture {
      val cl = client(namespace)
      cl.createNamespace(properties()).futureValue
      val json = parse(
        s"""
           |{
           |  "@context": {
           |    "label": "http://www.w3.org/2000/01/rdf-schema#label",
           |    "schema": "http://schema.org/",
           |    "nested": "http://localhost/nested/"
           |  },
           |  "@id": "http://localhost/$id",
           |  "label": "$label-updated",
           |  "nested": {
           |     "schema:name": "name",
           |     "schema:title": "title"
           |  }
           |}
           """.stripMargin
      ).right.value
      cl.replace(graph, load(id, label, value)).futureValue
      val strategy = PatchStrategy.removeButPredicates(Set("http://schema.org/value"))
      cl.patch(graph, json.asGraph(blank).right.value, strategy).futureValue
      val results = cl.triples(graph)
      results should have size 5
      results.map(_._3).toSet should contain allOf (label + "-updated", value, "name", "title")
    }
  }

  implicit class BlazegraphClientOps(cl: BlazegraphClient[Future])(implicit ec: ExecutionContext) {
    private def triplesFor(query: String) =
      cl.queryRaw(query).map {
        case SparqlResults(_, Bindings(mapList)) =>
          mapList.map { triples =>
            (triples("s").value, triples("p").value, triples("o").value)
          }
      }

    def triples(graph: Uri): List[(String, String, String)] =
      triplesFor(s"SELECT * WHERE { GRAPH <$graph> { ?s ?p ?o } }").futureValue

    def triples(): List[(String, String, String)] =
      triplesFor("SELECT * { ?s ?p ?o }").futureValue
  }

  private def load(id: String, label: String, value: String): Graph =
    jsonContentOf("/ld.json", Map(quote("{{ID}}") -> id, quote("{{LABEL}}") -> label, quote("{{VALUE}}") -> value))
      .asGraph(blank)
      .right
      .value
}
