package ch.epfl.bluebrain.nexus.commons.sparql.client

import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel
import java.util.Properties
import java.util.regex.Pattern.quote

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import cats.instances.future._
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient._
import ch.epfl.bluebrain.nexus.commons.sparql.client.BlazegraphClientFixture._
import ch.epfl.bluebrain.nexus.commons.sparql.client.BlazegraphClientSpec._
import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlCirceSupport._
import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlFailure.SparqlServerError
import ch.epfl.bluebrain.nexus.commons.test.Resources._
import com.bigdata.rdf.sail.webapp.NanoSparqlServer
import io.circe.Json
import io.circe.parser._
import org.apache.jena.query.ResultSet
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, EitherValues, Matchers, WordSpecLike}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

//noinspection TypeAnnotation
class BlazegraphClientSpec
    extends TestKit(ActorSystem("BlazegraphClientSpec"))
    with WordSpecLike
    with Matchers
    with ScalaFutures
    with EitherValues
    with BeforeAndAfterAll {

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

  private implicit val uc = akkaHttpClient
  private implicit val rs = implicitly[HttpClient[Future, ResultSet]]

  "A BlazegraphClient" should {
    val client = BlazegraphClient[Future](s"http://$localhost:$port/blazegraph", _: String, None)

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

    "return the JSON response from query" in new BlazegraphClientFixture {
      val cl = client(namespace)
      cl.createNamespace(properties()).futureValue
      cl.replace(graph, load(id, label, value)).futureValue
      val expected = jsonContentOf("/sparql-json.json",
                                   Map(quote("{id}") -> id, quote("{label}") -> label, quote("{value}") -> value))
      cl.queryRaw(s"SELECT * WHERE { GRAPH <$graph> { ?s ?p ?o } }").futureValue shouldEqual expected
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
      cl.patch(graph, json, strategy).futureValue
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
      cl.patch(graph, json, strategy).futureValue
      val results = cl.triples(graph)
      results should have size 5
      results.map(_._3).toSet should contain allOf (label + "-updated", value, "name", "title")
    }
  }

  implicit class BlazegraphClientOps(cl: BlazegraphClient[Future])(implicit ec: ExecutionContext) {
    private def triplesFor(query: String) =
      cl.queryRs(query).map { rs =>
        rs.asScala.toList.map { qs =>
          val obj = {
            val node = qs.get("?o")
            if (node.isLiteral) node.asLiteral().getLexicalForm
            else node.asResource().toString
          }
          (qs.get("?s").toString, qs.get("?p").toString, obj)
        }
      }

    def triples(graph: Uri): List[(String, String, String)] =
      triplesFor(s"SELECT * WHERE { GRAPH <$graph> { ?s ?p ?o } }").futureValue

    def triples(): List[(String, String, String)] =
      triplesFor("SELECT * { ?s ?p ?o }").futureValue
  }
}

object BlazegraphClientSpec {
  private def freePort(): Int = {
    val serverSocket = ServerSocketChannel.open().socket()
    serverSocket.bind(new InetSocketAddress(localhost, 0))
    val port = serverSocket.getLocalPort
    serverSocket.close()
    port
  }

  private def load(id: String, label: String, value: String): Json =
    jsonContentOf("/ld.json", Map(quote("{{ID}}") -> id, quote("{{LABEL}}") -> label, quote("{{VALUE}}") -> value))

  private def properties(file: String = "/index.properties"): Map[String, String] = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream(file))
    props.asScala.toMap
  }
}
