package ch.epfl.bluebrain.nexus.commons.sparql.client

import java.io.{ByteArrayInputStream, StringWriter}

import akka.http.scaladsl.model._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{ApplicativeError, MonadError}
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlClient._
import io.circe.Json
import org.apache.jena.graph.Graph
import org.apache.jena.query.ResultSet
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.{Lang, RDFDataMgr}

/**
  * Base sparql client implementing basic SPARQL query execution logic
  */
abstract class SparqlClient[F[_]]()(implicit F: MonadError[F, Throwable]) {

  /**
    * Drops the graph identified by the argument URI from the store.
    *
    * @param graph the graph to drop
    */
  def drop(graph: Uri): F[Unit] = {
    val query = s"DROP GRAPH <$graph>"
    executeUpdate(graph, query)
  }

  /**
    * Removes all triples from the graph identified by the argument URI and stores the triples in the data argument in
    * the same graph.
    *
    * @param graph the target graph
    * @param data  the new graph content
    */
  def replace(graph: Uri, data: Json): F[Unit] = {
    toNTriples(data).flatMap { triples =>
      val query =
        s"""DROP GRAPH <$graph>;
           |
           |INSERT DATA {
           |  GRAPH <$graph> {
           |    $triples
           |  }
           |}""".stripMargin
      executeUpdate(graph, query)
    }
  }

  /**
    * Patches the graph by selecting a collection of triples to remove or retain and inserting the triples in the data
    * argument.
    *
    * @see [[ch.epfl.bluebrain.nexus.commons.sparql.client.PatchStrategy]]
    * @param graph    the target graph
    * @param data     the additional graph content
    * @param strategy the patch strategy
    */
  def patch(graph: Uri, data: Json, strategy: PatchStrategy): F[Unit] = {
    toNTriples(data).flatMap { triples =>
      val filterExpr = strategy match {
        case RemovePredicates(predicates)    => predicates.map(p => s"?p = <$p>").mkString(" || ")
        case RemoveButPredicates(predicates) => predicates.map(p => s"?p != <$p>").mkString(" && ")
      }
      val query =
        s"""DELETE {
           |  GRAPH <$graph> {
           |    ?s ?p ?o .
           |  }
           |}
           |WHERE {
           |  GRAPH <$graph> {
           |    ?s ?p ?o .
           |    FILTER ( $filterExpr )
           |  }
           |};
           |
           |INSERT DATA {
           |  GRAPH <$graph> {
           |    $triples
           |  }
           |}""".stripMargin
      executeUpdate(graph, query)
    }
  }

  /**
    * @param q the query to execute
    * @return the unmarshalled result set of the provided query executed against the sparql endpoint
    */
  def queryRs(q: String)(implicit rs: HttpClient[F, ResultSet]): F[ResultSet] =
    query(q)(rs)

  /**
    * Executes the argument ''query'' against the underlying sparql endpoint.
    *
    * @param q the query to execute
    * @return the raw result of the provided query executed against the sparql endpoint
    */
  def queryRaw(q: String)(implicit rs: HttpClient[F, Json]): F[Json] =
    query(q)(rs)

  def query[A](query: String)(implicit rs: HttpClient[F, A]): F[A]

  protected def executeUpdate(graph: Uri, query: String): F[Unit]

}

object SparqlClient {

  private[client] def graphOf[F[_]](json: Json)(implicit F: ApplicativeError[F, Throwable]): F[Graph] =
    F.catchNonFatal {
      val model = ModelFactory.createDefaultModel()
      RDFDataMgr.read(model, new ByteArrayInputStream(json.noSpaces.getBytes), Lang.JSONLD)
      model.getGraph
    }

  private[client] def toNTriples(graph: Graph): String = {
    val writer = new StringWriter()
    RDFDataMgr.write(writer, graph, Lang.NTRIPLES)
    writer.toString
  }

  private[client] def toNTriples[F[_]](json: Json)(implicit F: ApplicativeError[F, Throwable]): F[String] =
    graphOf(json).map(toNTriples)
}
