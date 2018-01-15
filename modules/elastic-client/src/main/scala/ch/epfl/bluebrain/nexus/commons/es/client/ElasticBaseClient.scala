package ch.epfl.bluebrain.nexus.commons.es.client

import akka.http.scaladsl.model.{HttpRequest, StatusCode}
import cats.MonadError
import cats.syntax.flatMap._
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.{HttpResponseSyntax, UntypedHttpClient}
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

  private[client] def execute(req: HttpRequest, expectedCodes: Set[StatusCode], intent: => String): F[Unit] =
    cl(req).discardOnCodesOr(expectedCodes) { resp =>
      ElasticFailure.fromResponse(resp).flatMap { f =>
        log.error(
          s"Unexpected ElasticSearch response for intent '$intent':\nRequest: '${req.method} ${req.uri}'\nStatus: '${resp.status}'\nResponse: '${f.body}'")
        F.raiseError(f)
      }
    }
}

object ElasticBaseClient {
  private[client] val source = "_source"
}
