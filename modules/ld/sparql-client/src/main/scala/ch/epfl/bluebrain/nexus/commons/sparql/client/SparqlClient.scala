package ch.epfl.bluebrain.nexus.commons.sparql.client

import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Accept
import cats.MonadError
import cats.syntax.flatMap._
import cats.syntax.functor._
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlCirceSupport._
import io.circe.Json
import journal.Logger
import org.apache.jena.query.ResultSet
import org.apache.jena.sparql.modify.request.{Target, UpdateClear}
import org.apache.jena.update.UpdateFactory

import scala.concurrent.ExecutionContext

/**
  * Sparql client implementation that uses a RESTful API endpoint for interacting with a Sparql deployment.
  *
  * @param sparqlBase the base uri of the sparql endpoint
  * @tparam F         the monadic effect type
  */
class SparqlClient[F[_]](sparqlBase: Uri)(implicit
  cl: UntypedHttpClient[F],
  rs: HttpClient[F, ResultSet],
  ec: ExecutionContext,
  F: MonadError[F, Throwable]) {

  private val log = Logger[this.type]

  /**
    * Creates a new named graph within the specified index with the provided data.
    *
    * @param index the index to use
    * @param ctx   the graph address
    * @param data  the graph data to insert
    */
  def createGraph(index: String, ctx: Uri, data: Json): F[Unit] = {
    val uri = endpointFor(index).withQuery(Query("context-uri" -> ctx.toString()))
    execute(Post(uri, data), Set(StatusCodes.Created, StatusCodes.OK), "create graph")
  }

  /**
    * Clears all data within a named graph.  Does not remove the graph itself.
    *
    * @param index the index to use
    * @param ctx   the graph address
    */
  def clearGraph(index: String, ctx: Uri): F[Unit] = {
    val clear = new UpdateClear(Target.create(ctx.toString()))
    val query = UpdateFactory.create().add(clear).toString
    val uri = endpointFor(index).withQuery(Query("update" -> query, "using-named-graph-uri" -> ctx.toString()))
    execute(Post(uri), Set(StatusCodes.OK), "clear graph")
  }

  /**
    * Replaces all data within a named graph with the provided data.  The operation is NOT atomic.
    *
    * @param index the index to use
    * @param ctx   the graph address
    * @param data  the new data to insert
    * @return
    */
  def replaceGraph(index: String, ctx: Uri, data: Json): F[Unit] =
    for {
      _ <- clearGraph(index, ctx)
      _ <- createGraph(index, ctx, data)
    } yield ()

  /**
    * Removes all triples selected by the argument ''query'' in the ''index''.
    *
    * __Important__: the query must be a ''CONSTRUCT'' or ''DESCRIBE'' query.
    *
    * @param index the index to use
    * @param query the query used in selecting the triples to be removed
    */
  def delete(index: String, query: String): F[Unit] = {
    val uri = endpointFor(index).withQuery(Query("query" -> query))
    execute(Delete(uri), Set(StatusCodes.OK), "delete with query")
  }

  /**
    * Updates the data of a named graph, removing the triples selected with the argument ''query'' and inserting the
    * provided data.  The operation is NOT atomic.
    *
    * __Important__: the query must be a ''CONSTRUCT'' or ''DESCRIBE'' query.
    *
    * @param index the index to use
    * @param ctx   the graph address
    * @param query the query used in selecting the triples to be removed
    * @param data  the new data to insert
    */
  def patchGraph(index: String, ctx: Uri, query: String, data: Json): F[Unit] =
    for {
      _ <- delete(index, query)
      _ <- createGraph(index, ctx, data)
    } yield ()

  /**
    * Queries the index, producing a possibly empty result set.
    *
    * @param index the index to use
    * @param query the query to execute
    * @return the query result set
    */
  def query(index: String, query: String): F[ResultSet] = {
    val accept = Accept(MediaRange.One(RdfMediaTypes.`application/sparql-results+json`, 1F))
    val formData = FormData("query" -> query)
    val request = Post(endpointFor(index), formData).withHeaders(accept)
    rs(request)
  }

  /**
    * Checks if a index exists as a namespace
    *
    * @param index the name of the index
    * @return ''false'' when the index does not exist, ''true'' when it does exist
    *         and it signals an error otherwise.
    */
  def exists(index: String): F[Boolean] = {
    val req = Get(s"$sparqlBase/namespace/$index")
    cl(req).flatMap { resp =>
      resp.status match {
        case StatusCodes.OK       =>
          cl.discardBytes(resp.entity).map(_ => true)
        case StatusCodes.NotFound =>
          cl.discardBytes(resp.entity).map(_ => false)
        case other                =>
          cl.toString(resp.entity).flatMap { body =>
            log.error(s"Unexpected Sparql response for intent 'namespace exists':\nRequest: '${req.method} ${req.uri}'\nStatus: '$other'\nResponse: '$body'")
            F.raiseError(UnexpectedSparqlResponse(resp.status, body))
          }
      }
    }
  }

  /**
    * Creates a new index with the specified name and collection of properties.
    *
    * @param name       the name of the index
    * @param properties the sparql index properties
    */
  def createIndex(name: String, properties: Map[String, String]): F[Unit] = {
    val updated = properties + ("com.bigdata.rdf.sail.namespace" -> name)
    val payload = updated.map { case (key, value) => s"$key=$value" }.mkString("\n")
    val req = Post(s"$sparqlBase/namespace", HttpEntity(payload))
    execute(req, Set(StatusCodes.Created), "create index")
  }

  private def endpointFor(index: String): Uri =
    s"$sparqlBase/namespace/$index/sparql"

  private def execute(req: HttpRequest, expectedCodes: Set[StatusCode], intent: => String): F[Unit] =
    cl(req).flatMap { resp =>
      if (expectedCodes.contains(resp.status))
        cl.discardBytes(resp.entity).map(_ => ())
      else
        cl.toString(resp.entity).flatMap { body =>
          log.error(s"Unexpected Sparql response for intent '$intent':\nRequest: '${req.method} ${req.uri}'\nStatus: '${resp.status}'\nResponse: '$body'")
          F.raiseError(UnexpectedSparqlResponse(resp.status, body))
        }
    }
}

object SparqlClient {

  /**
    * Constructs a new ''SparqlClient[F]'' that uses the argument ''sparqlBase'' as the base uri for the sparql
    * endpoint.
    *
    * @param sparqlBase the base uri of the sparql endpoint
    * @tparam F         the monadic effect type
    */
  final def apply[F[_]](sparqlBase: Uri)(implicit
    cl: UntypedHttpClient[F],
    rs: HttpClient[F, ResultSet],
    ec: ExecutionContext,
    F: MonadError[F, Throwable]): SparqlClient[F] =
    new SparqlClient[F](sparqlBase)
}