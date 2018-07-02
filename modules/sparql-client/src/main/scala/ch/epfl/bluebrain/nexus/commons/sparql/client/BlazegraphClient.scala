package ch.epfl.bluebrain.nexus.commons.sparql.client

import akka.http.scaladsl.client.RequestBuilding.{Get, Post}
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.model.{HttpEntity, StatusCodes, Uri}
import cats.MonadError
import cats.syntax.flatMap._
import cats.syntax.functor._
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.{HttpResponseSyntax, UntypedHttpClient}
import io.circe.Json
import journal.Logger
import org.apache.jena.query.ResultSet

import scala.concurrent.ExecutionContext

/**
  * A client that exposes additional functions on top of [[ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlClient]]
  * that are specific to Blazegraph.
  *
  * @param base        the base uri of the blazegraph endpoint
  * @param namespace   the namespace that this client targets
  * @param credentials the credentials to use when communicating with the sparql endpoint
  */
class BlazegraphClient[F[_]](base: Uri, namespace: String, credentials: Option[HttpCredentials])(
    implicit F: MonadError[F, Throwable],
    cl: UntypedHttpClient[F],
    rsJson: HttpClient[F, Json],
    rsSet: HttpClient[F, ResultSet],
    ec: ExecutionContext)
    extends HttpSparqlClient[F](s"$base/namespace/$namespace/sparql", credentials) {

  private val log = Logger[this.type]

  /**
    * @param base        the base uri of the blazegraph endpoint
    * @param namespace   the namespace that this client targets
    * @param credentials the credentials to use when communicating with the sparql endpoint
    * @return a new [[BlazegraphClient]] with the provided parameters
    */
  def copy(base: Uri = this.base,
           namespace: String = this.namespace,
           credentials: Option[HttpCredentials] = this.credentials): BlazegraphClient[F] =
    new BlazegraphClient[F](base, namespace, credentials)

  /**
    * Check whether the target namespace exists.
    */
  def namespaceExists: F[Boolean] = {
    val req = Get(s"$base/namespace/$namespace")
    cl(addCredentials(req)).flatMap { resp =>
      resp.status match {
        case StatusCodes.OK =>
          cl.discardBytes(resp.entity).map(_ => true)
        case StatusCodes.NotFound =>
          cl.discardBytes(resp.entity).map(_ => false)
        case _ =>
          cl.toString(resp.entity).flatMap { body =>
            log.error(s"""Unexpected Blazegraph response for get namespace:
                 |Request: '${req.method} ${req.uri}'
                 |Status: '${resp.status}'
                 |Response: '$body'
           """.stripMargin)
            F.raiseError(SparqlFailure.fromStatusCode(resp.status, body))
          }
      }
    }
  }

  /**
    * Creates the target namespace using the provided properties.
    *
    * @param properties the properties to use for namespace creation
    */
  def createNamespace(properties: Map[String, String]): F[Unit] = {
    val updated = properties + ("com.bigdata.rdf.sail.namespace" -> namespace)
    val payload = updated.map { case (key, value) => s"$key=$value" }.mkString("\n")
    val req     = Post(s"$base/namespace", HttpEntity(payload))
    cl(addCredentials(req)).discardOnCodesOr(Set(StatusCodes.Created)) { resp =>
      SparqlFailure.fromResponse(resp).flatMap { f =>
        log.error(s"""Unexpected Blazegraph response for create namespace:
             |Request: '${req.method} ${req.uri}'
             |Status: '${resp.status}'
             |Response: '${f.body}'
           """.stripMargin)
        F.raiseError(f)
      }
    }
  }
}

object BlazegraphClient {
  def apply[F[_]](base: Uri, namespace: String, credentials: Option[HttpCredentials])(
      implicit F: MonadError[F, Throwable],
      cl: UntypedHttpClient[F],
      rsJson: HttpClient[F, Json],
      rsSet: HttpClient[F, ResultSet],
      ec: ExecutionContext): BlazegraphClient[F] =
    new BlazegraphClient[F](base, namespace, credentials)
}
