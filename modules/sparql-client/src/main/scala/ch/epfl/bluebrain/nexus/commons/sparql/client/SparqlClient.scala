package ch.epfl.bluebrain.nexus.commons.sparql.client

import java.io.{ByteArrayInputStream, StringWriter}

import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Accept, HttpCredentials}
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{ApplicativeError, MonadError}
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.{HttpResponseSyntax, UntypedHttpClient}
import ch.epfl.bluebrain.nexus.commons.http.{HttpClient, RdfMediaTypes}
import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlClient._
import io.circe.Json
import journal.Logger
import org.apache.jena.graph.Graph
import org.apache.jena.query.ResultSet
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.{Lang, RDFDataMgr}
import org.apache.jena.update.UpdateFactory

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

/**
  * A minimalistic sparql client that operates on a predefined endpoint with optional HTTP basic authentication.
  *
  * @param endpoint    the sparql endpoint
  * @param credentials the credentials to use when communicating with the sparql endpoint
  */
class SparqlClient[F[_]](endpoint: Uri, credentials: Option[HttpCredentials])(implicit F: MonadError[F, Throwable],
                                                                              cl: UntypedHttpClient[F],
                                                                              rs: HttpClient[F, ResultSet],
                                                                              ec: ExecutionContext) {

  private val log = Logger[this.type]

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
    * Executes the argument ''query'' against the underlying sparql endpoint.
    *
    * @param query the query to execute
    * @return the query result set
    */
  def query(query: String): F[ResultSet] = {
    val accept   = Accept(MediaRange.One(RdfMediaTypes.`application/sparql-results+json`, 1F))
    val formData = FormData("query" -> query)
    val req      = Post(endpoint, formData).withHeaders(accept)
    rs(addCredentials(req)).handleErrorWith {
      case NonFatal(th) =>
        log.error(s"""Unexpected Sparql response for sparql query:
             |Request: '${req.method} ${req.uri}'
             |Query: '$query'
           """.stripMargin)
        F.raiseError(th)
    }
  }

  private def executeUpdate(graph: Uri, query: String): F[Unit] = {
    F.catchNonFatal(UpdateFactory.create(query)).flatMap { _ =>
      val formData = FormData("update" -> query)
      val req      = Post(endpoint.withQuery(Query("using-named-graph-uri" -> graph.toString())), formData)
      log.debug(s"Executing sparql update: '$query'")
      cl(addCredentials(req)).discardOnCodesOr(Set(StatusCodes.OK)) { resp =>
        SparqlFailure.fromResponse(resp).flatMap { f =>
          log.error(s"""Unexpected Sparql response for sparql update:
               |Request: '${req.method} ${req.uri}'
               |Query: '$query'
               |Status: '${resp.status}'
               |Response: '${f.body}'
             """.stripMargin)
          F.raiseError(f)
        }
      }
    }
  }

  protected def addCredentials(req: HttpRequest): HttpRequest = credentials match {
    case None        => req
    case Some(value) => req.addCredentials(value)
  }
}

object SparqlClient {

  def apply[F[_]](endpoint: Uri, credentials: Option[HttpCredentials])(implicit F: MonadError[F, Throwable],
                                                                       cl: UntypedHttpClient[F],
                                                                       rs: HttpClient[F, ResultSet],
                                                                       ec: ExecutionContext): SparqlClient[F] =
    new SparqlClient[F](endpoint, credentials)

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
