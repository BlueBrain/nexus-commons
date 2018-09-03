package ch.epfl.bluebrain.nexus.commons.sparql.client

import akka.http.scaladsl.client.RequestBuilding.Post
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Accept, HttpCredentials}
import cats.MonadError
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.http.{HttpClient, RdfMediaTypes}
import io.circe.Json
import journal.Logger
import org.apache.jena.query.ResultSet
import org.apache.jena.update.UpdateFactory

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

/**
  * A minimalistic sparql client that operates on a predefined endpoint with optional HTTP basic authentication.
  *
  * @param endpoint    the sparql endpoint
  * @param credentials the credentials to use when communicating with the sparql endpoint
  */
class HttpSparqlClient[F[_]](endpoint: Uri, credentials: Option[HttpCredentials])(implicit F: MonadError[F, Throwable],
                                                                                  cl: UntypedHttpClient[F],
                                                                                  rsJson: HttpClient[F, Json],
                                                                                  rsSet: HttpClient[F, ResultSet],
                                                                                  ec: ExecutionContext)
    extends SparqlClient[F] {

  private val log = Logger[this.type]

  def query[A](query: String)(implicit rs: HttpClient[F, A]): F[A] = {
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

  /**
    * Executes the argument update query against the underlying sparql endpoint
    *
    * @param graph Graph targeted in the update
    * @param query the update query
    * @return successful Future[Unit] if update succeeded, failure otherwise
    */
  override def executeUpdate(graph: Uri, query: String): F[Unit] = {
    F.catchNonFatal(UpdateFactory.create(query)).flatMap { _ =>
      val formData = FormData("update" -> query)
      val req      = Post(endpoint.withQuery(Query("using-named-graph-uri" -> graph.toString())), formData)
      log.debug(s"Executing sparql update: '$query'")
      cl(addCredentials(req)).flatMap { resp =>
        resp.status match {
          case StatusCodes.OK => cl.discardBytes(resp.entity).map(_ => ())
          case _              => error(req, resp, "sparql update")
        }
      }
    }
  }

  private[client] def error[A](req: HttpRequest, resp: HttpResponse, op: String): F[A] =
    cl.toString(resp.entity).flatMap { body =>
      log.error(s"""Unexpected Blazegraph response for '$op':
                   |Request: '${req.method} ${req.uri}'
                   |Status: '${resp.status}'
                   |Response: '$body'
           """.stripMargin)
      F.raiseError(SparqlFailure.fromStatusCode(resp.status, body))
    }

  protected def addCredentials(req: HttpRequest): HttpRequest = credentials match {
    case None        => req
    case Some(value) => req.addCredentials(value)
  }
}

object HttpSparqlClient {

  def apply[F[_]](endpoint: Uri, credentials: Option[HttpCredentials])(implicit F: MonadError[F, Throwable],
                                                                       cl: UntypedHttpClient[F],
                                                                       rsJson: HttpClient[F, Json],
                                                                       rsSet: HttpClient[F, ResultSet],
                                                                       ec: ExecutionContext): SparqlClient[F] =
    new HttpSparqlClient[F](endpoint, credentials)

}
