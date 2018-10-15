package ch.epfl.bluebrain.nexus.commons.es.client

import akka.http.scaladsl.model.{HttpRequest, StatusCode}
import cats.MonadError
import cats.syntax.flatMap._
import cats.syntax.functor._
import ch.epfl.bluebrain.nexus.commons.es.client.ElasticBaseClient._
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import journal.Logger

/**
  * ElasticBaseClient provides the common methods and vals used for elastic clients.
  *
  * @tparam F the monadic effect type
  */
abstract class ElasticBaseClient[F[_]](implicit
                                       cl: UntypedHttpClient[F],
                                       F: MonadError[F, Throwable]) {
  private[client] val log = Logger[this.type]

  private[client] def execute(req: HttpRequest, expectedCodes: Set[StatusCode], intent: String): F[Unit] =
    execute(req, expectedCodes, Set.empty, intent).map(_ => ())

  private[client] def execute(req: HttpRequest,
                              expectedCodes: Set[StatusCode],
                              ignoredCodes: Set[StatusCode],
                              intent: String): F[Boolean] =
    cl(req).flatMap { resp =>
      if (expectedCodes.contains(resp.status)) cl.discardBytes(resp.entity).map(_ => true)
      else if (ignoredCodes.contains(resp.status)) cl.discardBytes(resp.entity).map(_ => false)
      else
        ElasticFailure.fromResponse(resp).flatMap { f =>
          cl.toString(req.entity).flatMap { reqBody =>
            log.error(
              s"Unexpected ElasticSearch response for intent '$intent':\nRequest: '${req.method} ${req.uri}' \nBody: '$reqBody'\nStatus: '${resp.status}'\nResponse: '${f.body}'")
            F.raiseError(f)
          }
        }
    }

  private[client] def indexPath(indices: Set[String]): String =
    if (indices.isEmpty) anyIndexPath
    else indices.map(sanitize(_, allowWildCard = true)).mkString(",")

  /**
    * Replaces the characters ' "\<>|,/?' in the provided index with '_' and drops all '_' prefixes.
    * The wildcard (*) character will be only dropped when ''allowWildCard'' is set to false.
    *
    * @param index the index name to sanitize
    * @param allowWildCard flag to allow wildcard (*) or not.
    */
  private[client] def sanitize(index: String, allowWildCard: Boolean = false): String = {
    val regex = if (allowWildCard) """[\s|"|\\|<|>|\||,|/|?]""" else """[\s|"|*|\\|<|>|\||,|/|?]"""
    index.replaceAll(regex, "_").dropWhile(_ == '_')
  }
}

object ElasticBaseClient {
  private[client] val source            = "_source"
  private[client] val anyIndexPath      = "_all"
  private[client] val ignoreUnavailable = "ignore_unavailable"
  private[client] val allowNoIndices    = "allow_no_indices"
}
