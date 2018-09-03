package ch.epfl.bluebrain.nexus.commons.sparql.client

import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.HttpCredentials
import cats.MonadError
import cats.syntax.flatMap._
import cats.syntax.functor._
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import io.circe.Json
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
        case StatusCodes.OK       => cl.discardBytes(resp.entity).map(_ => true)
        case StatusCodes.NotFound => cl.discardBytes(resp.entity).map(_ => false)
        case _                    => error(req, resp, "get namespace")
      }
    }
  }

  /**
    * Attempts to create a namespace recovering gracefully when the namespace already exists.
    *
    * @param properties the properties to use for namespace creation
    * @return ''true'' wrapped in ''F'' when namespace has been created and ''false'' wrapped in ''F'' when it already existed
    */
  def createNamespace(properties: Map[String, String]): F[Boolean] = {
    val updated = properties + ("com.bigdata.rdf.sail.namespace" -> namespace)
    val payload = updated.map { case (key, value) => s"$key=$value" }.mkString("\n")
    val req     = Post(s"$base/namespace", HttpEntity(payload))
    cl(addCredentials(req)).flatMap { resp =>
      resp.status match {
        case StatusCodes.Created  => cl.discardBytes(resp.entity).map(_ => true)
        case StatusCodes.Conflict => cl.discardBytes(resp.entity).map(_ => false)
        case _                    => error(req, resp, "create namespace")
      }
    }
  }

  /**
    * Attempts to delete a namespace recovering gracefully when the namespace does not exists.
    *
    * @return ''true'' wrapped in ''F'' when namespace has been deleted and ''false'' wrapped in ''F'' when it does not existe
    */
  def deleteNamespace: F[Boolean] = {
    val req = Delete(s"$base/namespace/$namespace")
    cl(addCredentials(req)).flatMap { resp =>
      resp.status match {
        case StatusCodes.OK       => cl.discardBytes(resp.entity).map(_ => true)
        case StatusCodes.NotFound => cl.discardBytes(resp.entity).map(_ => false)
        case _                    => error(req, resp, "delete namespace")
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
