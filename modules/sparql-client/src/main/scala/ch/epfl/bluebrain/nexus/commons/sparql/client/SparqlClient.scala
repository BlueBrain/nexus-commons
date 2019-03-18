package ch.epfl.bluebrain.nexus.commons.sparql.client

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.rdf.Graph

/**
  * Base sparql client implementing basic SPARQL query execution logic
  */
abstract class SparqlClient[F[_]]()(implicit rsJson: HttpClient[F, SparqlResults]) {

  /**
    * @param q the query to execute against the sparql endpoint
    * @return the raw result of the provided query executed against the sparql endpoint
    */
  def queryRaw(q: String): F[SparqlResults] =
    query(q)(rsJson)

  /**
    *
    * @param query the query to execute against the sparql endpoint
    * @param rs the implicitly available httpclient used to unmarshall the response
    * @tparam A the generic type of the unmarshalled response
    * @return the unmarshalled result ''A'' of the provided query executed against the sparql endpoint
    */
  def query[A](query: String)(implicit rs: HttpClient[F, A]): F[A]

  /**
    * Executes the argument update queries against the underlying sparql endpoint.
    *
    * @param queries the write queries
    * @return successful Future[Unit] if update succeeded, failure otherwise
    */
  def bulk(queries: SparqlWriteQuery*): F[Unit]

  /**
    * Executes the query that removes all triples from the graph identified by the argument URI and stores the triples in the data argument in
    * the same graph.
    *
    * @param graph the target graph
    * @param data  the new graph content
    */
  def replace(graph: Uri, data: Graph): F[Unit] =
    bulk(SparqlWriteQuery.replace(graph, data))

  /**
    * Executes the query that patches the graph by selecting a collection of triples to remove or retain and inserting the triples in the data
    * argument.
    *
    * @see [[ch.epfl.bluebrain.nexus.commons.sparql.client.PatchStrategy]]
    * @param graph    the target graph
    * @param data     the additional graph content
    * @param strategy the patch strategy
    */
  def patch(graph: Uri, data: Graph, strategy: PatchStrategy): F[Unit] =
    bulk(SparqlWriteQuery.patch(graph, data, strategy))

  /**
    * Executes the replace query that drops the graph identified by the argument ''uri'' from the store.
    *
    * @param graph the graph to drop
    */
  def drop(graph: Uri): F[Unit] =
    bulk(SparqlWriteQuery.drop(graph))

}
