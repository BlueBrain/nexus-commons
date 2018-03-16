package ch.epfl.bluebrain.nexus.commons.sparql.client

import java.util.regex.Pattern

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.model.Uri
import akka.testkit.{DefaultTimeout, TestKit}
import cats.instances.future._
import ch.epfl.bluebrain.nexus.commons.sparql.client.BlazegraphClientFixture.{genString, localhost}
import ch.epfl.bluebrain.nexus.commons.sparql.client.InMemorySparqlClientSpec._
import io.circe.Json
import io.circe.parser.parse
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, EitherValues, Matchers, WordSpecLike}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

class InMemorySparqlClientSpec
    extends TestKit(ActorSystem("InMemorySparqlClientSpec"))
    with DefaultTimeout
    with WordSpecLike
    with Matchers
    with ScalaFutures
    with EitherValues
    with BeforeAndAfterAll
    with IntegrationPatience {

  private implicit val ec = system.dispatcher
  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  def graph() = {
    val rand: String = genString(length = 8)
    Uri(s"http://$localhost:8080/graphs/$rand")
  }
  val id: String    = genString()
  val label: String = genString()
  val v: String     = genString()

  val inMemoryActor = system.actorOf(Props[InMemorySparqlActor]())
  val cl            = InMemorySparqlClient(inMemoryActor)

  "InMemorySparqlClient" should {

    "create a new named graph" in {
      val g = graph()
      cl.replace(g, load(id, label, v)).futureValue
      cl.triples(g) should have size 2
      cl.triples() should have size 2
    }

    "drop a named graph" in {
      val g = graph()
      cl.replace(g, load(id, label, v)).futureValue
      cl.triples(g) should have size 2
      cl.drop(g).futureValue
      cl.triples(g) shouldBe empty
    }

    "replace a named graph" in {
      val g = graph()
      cl.replace(g, load(id, label, v)).futureValue
      cl.triples(g).map(_._3) should contain theSameElementsAs Set(label, v)
      cl.replace(g, load(id, label, v + "-updated")).futureValue
      cl.triples(g).map(_._3) should contain theSameElementsAs Set(label, v + "-updated")
    }

    "patch a named graph removing matching predicates" in {
      val g = graph()
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
      cl.replace(g, load(id, label, v)).futureValue
      val strategy = PatchStrategy.removePredicates(
        Set(
          "http://schema.org/value",
          "http://www.w3.org/2000/01/rdf-schema#label"
        ))
      cl.patch(g, json, strategy).futureValue
      cl.triples(g) should have size 4
      val results = cl.triples(g)
      results should have size 4
      results.map(_._2).toSet should contain theSameElementsAs Set(
        "http://www.w3.org/2000/01/rdf-schema#label",
        "http://schema.org/name",
        "http://localhost/nested/",
        "http://schema.org/title"
      )
      results.map(_._3).toSet should contain allOf ("name", "title", s"$label-updated")
    }

    "patch a named graph retaining matching predicates" in {
      val g = graph()
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
      cl.replace(g, load(id, label, v)).futureValue
      val strategy = PatchStrategy.removeButPredicates(Set("http://schema.org/value"))
      cl.patch(g, json, strategy).futureValue
      val results = cl.triples(g)
      results should have size 5
      results.map(_._3).toSet should contain allOf (label + "-updated", v, "name", "title")
    }
  }

  implicit class InMemorySparqlClientOps(cl: InMemorySparqlClient)(implicit ec: ExecutionContext) {
    private def triplesFor(query: String): Future[List[(String, String, String)]] =
      cl.query(query).map { rs =>
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
      triplesFor("SELECT * WHERE {GRAPH ?g { ?s ?p ?o }}").futureValue
  }
}

object InMemorySparqlClientSpec {

  private def load(id: String, label: String, value: String): Json =
    parse(
      Source
        .fromInputStream(getClass.getResourceAsStream("/ld.json"))
        .mkString
        .replaceAll(Pattern.quote("{{ID}}"), id)
        .replaceAll(Pattern.quote("{{LABEL}}"), label)
        .replaceAll(Pattern.quote("{{VALUE}}"), value)
    ).toTry.get
}
