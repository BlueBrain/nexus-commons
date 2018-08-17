package ch.epfl.bluebrain.nexus.commons.sparql.client

import java.io.{ByteArrayInputStream, StringWriter}

import akka.http.scaladsl.model._
import cats.syntax.flatMap._
import cats.{ApplicativeError, MonadError}
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlClient._
import ch.epfl.bluebrain.nexus.rdf
import ch.epfl.bluebrain.nexus.rdf.syntax.jena._
import io.circe.Json
import org.apache.jena.graph.Graph
import org.apache.jena.query.ResultSet
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.{Lang, RDFDataMgr}

/**
  * Base sparql client implementing basic SPARQL query execution logic
  */
abstract class SparqlClient[F[_]]()(implicit F: MonadError[F, Throwable],
                                    rsJson: HttpClient[F, Json],
                                    rsSet: HttpClient[F, ResultSet]) {

  /**
    * Drops the graph identified by the argument URI from the store.
    *
    * @param uri the graph to drop
    */
  def drop(uri: Uri): F[Unit] = {
    val query = s"DROP GRAPH <$uri>"
    executeUpdate(uri, query)
  }

  /**
    * Removes all triples from the graph identified by the argument URI and stores the triples in the data argument in
    * the same graph.
    *
    * @param uri  the target graph
    * @param data the new graph content
    */
  def replace(uri: Uri, data: Json): F[Unit] =
    graphOf(data).flatMap(replace(uri, _))

  /**
    * Removes all triples from the graph identified by the argument URI and stores the triples in the data argument in
    * the same graph.
    *
    * @param uri  the target graph
    * @param data the new graph content
    */
  def replace(uri: Uri, data: rdf.Graph): F[Unit] = {
    replace(uri, data.asJenaModel.getGraph)
  }

  private def replace(uri: Uri, graph: Graph): F[Unit] = {
    val query =
      s"""DROP GRAPH <$uri>;
           |
           |INSERT DATA {
           |  GRAPH <$uri> {
           |    ${toNTriples(graph)}
           |  }
           |}""".stripMargin
    executeUpdate(uri, query)
  }

  /**
    * Patches the graph by selecting a collection of triples to remove or retain and inserting the triples in the data
    * argument.
    *
    * @see [[ch.epfl.bluebrain.nexus.commons.sparql.client.PatchStrategy]]
    * @param uri    the target graph
    * @param data     the additional graph content
    * @param strategy the patch strategy
    */
  def patch(uri: Uri, data: rdf.Graph, strategy: PatchStrategy): F[Unit] = {
    patch(uri, data.asJenaModel.getGraph, strategy)
  }

  /**
    * Patches the graph by selecting a collection of triples to remove or retain and inserting the triples in the data
    * argument.
    *
    * @see [[ch.epfl.bluebrain.nexus.commons.sparql.client.PatchStrategy]]
    * @param uri    the target graph
    * @param data     the additional graph content
    * @param strategy the patch strategy
    */
  def patch(uri: Uri, data: Json, strategy: PatchStrategy): F[Unit] =
    graphOf(data).flatMap(patch(uri, _, strategy))

  private def patch(uri: Uri, data: Graph, strategy: PatchStrategy): F[Unit] = {
    val filterExpr = strategy match {
      case RemovePredicates(predicates)    => predicates.map(p => s"?p = <$p>").mkString(" || ")
      case RemoveButPredicates(predicates) => predicates.map(p => s"?p != <$p>").mkString(" && ")
    }
    val query =
      s"""DELETE {
           |  GRAPH <$uri> {
           |    ?s ?p ?o .
           |  }
           |}
           |WHERE {
           |  GRAPH <$uri> {
           |    ?s ?p ?o .
           |    FILTER ( $filterExpr )
           |  }
           |};
           |
           |INSERT DATA {
           |  GRAPH <$uri> {
           |    ${toNTriples(data)}
           |  }
           |}""".stripMargin
    executeUpdate(uri, query)
  }

  /**
    * @param q the query to execute against the sparql endpoint
    * @return the unmarshalled result set of the provided query executed against the sparql endpoint
    */
  def queryRs(q: String): F[ResultSet] =
    query(q)(rsSet)

  /**
    * @param q the query to execute against the sparql endpoint
    * @return the raw result of the provided query executed against the sparql endpoint
    */
  def queryRaw(q: String): F[Json] =
    query(q)(rsJson)

  /**
    *
    * @param query the query to execute against the sparql endpoint
    * @param rs the implicitly available httpclient used to unmarshall the response
    * @tparam A the generic type of the unmarshalled response
    * @return the unmarshalled result ''A'' of the provided query executed against the sparql endpoint
    */
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
}
