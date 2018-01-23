package ch.epfl.bluebrain.nexus.commons.sparql.client

import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel
import java.util.Properties
import java.util.regex.Pattern

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import cats.instances.future._
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient._
import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlCirceSupport._
import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlClientFixture._
import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlClientSpec._
import com.bigdata.rdf.sail.webapp.NanoSparqlServer
import io.circe.Json
import io.circe.parser._
import org.apache.jena.query.ResultSet
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.Source

//noinspection TypeAnnotation
class SparqlClientSpec
    extends TestKit(ActorSystem("SparqlClientSpec"))
    with WordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  private val port = freePort()

  private val server = {
    System.setProperty("jetty.home", getClass.getResource("/war").toExternalForm)
    NanoSparqlServer.newInstance(port, null, null)
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(6 seconds, 300 milliseconds)

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

  private implicit val cl = akkaHttpClient
  private implicit val rs = implicitly[HttpClient[Future, ResultSet]]

  private val baseUri: Uri = s"http://$localhost:$port/blazegraph"

  private def triples(index: String, ctx: Uri, client: SparqlClient[Future]): Future[List[(String, String, String)]] =
    query(index, s"SELECT * WHERE { GRAPH <$ctx> { ?s ?p ?o } }", client)

  private def allTriples(index: String, client: SparqlClient[Future]): Future[List[(String, String, String)]] =
    query(index, s"SELECT * { ?s ?p ?o }", client)

  private def query(index: String,
                    query: String,
                    client: SparqlClient[Future]): Future[List[(String, String, String)]] =
    client.query(index, query).map { rs =>
      rs.asScala.toList.map { qs =>
        val obj = {
          val node = qs.get("?o")
          if (node.isLiteral) node.asLiteral().getLexicalForm
          else node.asResource().toString
        }
        (qs.get("?s").toString, qs.get("?p").toString, obj)
      }
    }

  "A SparqlClient" should {
    val client = SparqlClient[Future](baseUri)
    val index  = genString(length = 8)

    "verify if index exists" in new SparqlClientFixture {
      client.exists(index).futureValue shouldEqual false
    }

    "create an index" in new SparqlClientFixture {
      client.createIndex(index, properties).futureValue
      client.exists(index).futureValue shouldEqual true
    }

    "create a named graph" in new SparqlClientFixture {
      client.createGraph(index, ctx, load(id, label, value)).futureValue
      triples(index, ctx, client).futureValue should have size 2
      allTriples(index, client).futureValue should have size 2
    }

    "clear a named graph" in new SparqlClientFixture {
      client.createGraph(index, ctx, load(id, label, value)).futureValue
      client.clearGraph(index, ctx).futureValue
      triples(index, ctx, client).futureValue shouldBe empty
    }

    "replace a named graph" in new SparqlClientFixture {
      client.createGraph(index, ctx, load(id, label, value)).futureValue
      client.replaceGraph(index, ctx, load(id, label, value + "-updated")).futureValue
      val results = triples(index, ctx, client).futureValue
      results.map(_._3).toSet shouldEqual Set(label, value + "-updated")
    }

    "delete selected triples in a named graph" in new SparqlClientFixture {
      val query =
        s"""
           |prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
           |
           |CONSTRUCT {
           |  <http://localhost/$id> rdfs:label ?o .
           |} WHERE {
           |  <http://localhost/$id> rdfs:label ?o .
           |}
         """.stripMargin
      client.createGraph(index, ctx, load(id, label, value)).futureValue
      client.delete(index, query).futureValue
      val results = triples(index, ctx, client).futureValue
      results should have size 1
      results.map(_._3).toSet shouldEqual Set(value)
    }

    "patch a named graph" in new SparqlClientFixture {
      val query =
        s"""
           |prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
           |
           |CONSTRUCT {
           |  <http://localhost/$id> rdfs:label ?o .
           |} WHERE {
           |  <http://localhost/$id> rdfs:label ?o .
           |}
         """.stripMargin
      val json = parse(s"""
           |{
           |  "@context": {
           |    "label": "http://www.w3.org/2000/01/rdf-schema#label"
           |  },
           |  "@id": "http://localhost/$id",
           |  "label": "$label-updated"
           |}
         """.stripMargin).toTry.get
      client.createGraph(index, ctx, load(id, label, value)).futureValue
      client.patchGraph(index, ctx, query, json).futureValue
      val results = triples(index, ctx, client).futureValue
      results should have size 2
      results.map(_._3).toSet shouldEqual Set(label + "-updated", value)
    }
  }
}

object SparqlClientSpec {
  private def freePort(): Int = {
    val serverSocket = ServerSocketChannel.open().socket()
    serverSocket.bind(new InetSocketAddress(localhost, 0))
    val port = serverSocket.getLocalPort
    serverSocket.close()
    port
  }

  private def load(id: String, label: String, value: String): Json =
    parse(
      Source
        .fromInputStream(getClass.getResourceAsStream("/ld.json"))
        .mkString
        .replaceAll(Pattern.quote("{{ID}}"), id)
        .replaceAll(Pattern.quote("{{LABEL}}"), label)
        .replaceAll(Pattern.quote("{{VALUE}}"), value)
    ).toTry.get

  private val properties: Map[String, String] = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/index.properties"))
    props.asScala.toMap
  }
}
