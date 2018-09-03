package ch.epfl.bluebrain.nexus.commons.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMessage.DiscardedEntity
import akka.http.scaladsl.model.StatusCodes.ServerError
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.stream.Materializer
import akka.util.ByteString
import cats.MonadError
import cats.instances.future._
import cats.syntax.flatMap._
import journal.Logger
import monix.eval.Task

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

/**
  * Contract definition for an HTTP client based on the akka http model.
  *
  * @tparam F the monadic effect type
  * @tparam A the unmarshalled return type of a request
  */
trait HttpClient[F[_], A] {

  /**
    * Execute the argument request and unmarshal the response into an ''A''.
    *
    * @param req the request to execute
    * @return an unmarshalled ''A'' value in the ''F[_]'' context
    */
  def apply(req: HttpRequest): F[A]

  /**
    * Discard the response bytes of the entity, if any.
    *
    * @param entity the entity that needs to be discarded
    * @return a discarded entity
    */
  def discardBytes(entity: HttpEntity): F[DiscardedEntity]

  /**
    * Attempt to transform the entity bytes (if any) into an UTF-8 string representation.  If the entity has no bytes
    * an empty string will be returned instead.
    *
    * @param entity the entity to transform into a string representation
    * @return the entity bytes (if any) into an UTF-8 string representation
    */
  def toString(entity: HttpEntity): F[String]

  private[http] def handleError(req: HttpRequest, resp: HttpResponse)(implicit F: MonadError[F, Throwable],
                                                                      log: Logger): F[A] =
    discardBytes(resp.entity).flatMap { _ =>
      resp.status match {
        case _: ServerError =>
          log.error(s"Server Error HTTP response for '${req.uri}', status: '${resp.status}', discarding bytes")
          F.raiseError(UnexpectedUnsuccessfulHttpResponse(resp))
        case StatusCodes.BadRequest =>
          log.warn(s"BadRequest HTTP response for '${req.uri}', discarding bytes")
          F.raiseError(UnexpectedUnsuccessfulHttpResponse(resp))
        case _ =>
          log.debug(s"HTTP response for '${req.uri}', status: '${resp.status}', discarding bytes")
          F.raiseError(UnexpectedUnsuccessfulHttpResponse(resp))
      }
    }

}

object HttpClient {

  /**
    * Type alias for [[ch.epfl.bluebrain.nexus.commons.http.HttpClient]] that has the unmarshalled return type
    * the [[akka.http.scaladsl.model.HttpResponse]] itself.
    *
    * @tparam F the monadic effect type
    */
  type UntypedHttpClient[F[_]] = HttpClient[F, HttpResponse]

  /**
    * Constructs an [[ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient]] instance using an
    * underlying akka http client.
    *
    * @param as an implicit actor system
    * @param mt an implicit materializer
    * @return an untyped http client based on akka http transport
    */
  // $COVERAGE-OFF$
  final def akkaHttpClient(implicit as: ActorSystem, mt: Materializer): UntypedHttpClient[Future] =
    new HttpClient[Future, HttpResponse] {
      import as.dispatcher

      override def apply(req: HttpRequest): Future[HttpResponse] =
        Http().singleRequest(req)

      override def discardBytes(entity: HttpEntity): Future[DiscardedEntity] =
        Future.successful(entity.discardBytes())

      override def toString(entity: HttpEntity): Future[String] =
        entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(_.utf8String)
    }

  /**
    * Constructs an [[ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient]] of a [[Task]]
    * instance using an underlying akka http client.
    *
    * @param as an implicit actor system
    * @param mt an implicit materializer
    * @return an untyped http client based on akka http transport wrapped in a [[Task]]
    */
  final def taskHttpClient(implicit as: ActorSystem, mt: Materializer): UntypedHttpClient[Task] = {
    val underlying = akkaHttpClient

    new HttpClient[Task, HttpResponse] {
      override def apply(req: HttpRequest): Task[HttpResponse] =
        Task.deferFuture(underlying(req))

      override def discardBytes(entity: HttpEntity): Task[DiscardedEntity] =
        Task.deferFuture(underlying.discardBytes(entity))

      override def toString(entity: HttpEntity): Task[String] =
        Task.deferFuture(underlying.toString(entity))
    }
  }

  /**
    * Constructs a typed ''HttpClient[Future, A]'' from an ''UntypedHttpClient[Future]'' by attempting to unmarshal the
    * response entity into the specific type ''A'' using an implicit ''FromEntityUnmarshaller[A]''.
    *
    * Delegates all calls to the underlying untyped http client.
    *
    * If the response status is not successful, the entity bytes will be discarded instead.
    *
    * @param ec an implicit execution context
    * @param mt an implicit materializer
    * @param cl an implicit untyped http client
    * @param um an implicit ''FromEntityUnmarshaller[A]''
    * @tparam A the specific type to which the response entity should be unmarshalled into
    */
  final implicit def withAkkaUnmarshaller[A: ClassTag](implicit
                                                       ec: ExecutionContext,
                                                       mt: Materializer,
                                                       cl: UntypedHttpClient[Future],
                                                       um: FromEntityUnmarshaller[A]): HttpClient[Future, A] =
    new HttpClient[Future, A] {

      private implicit val log = Logger(s"TypedHttpClient[${implicitly[ClassTag[A]]}]")

      override def apply(req: HttpRequest): Future[A] =
        cl(req).flatMap { resp =>
          if (resp.status.isSuccess()) um(resp.entity)
          else handleError(req, resp)
        }

      override def discardBytes(entity: HttpEntity): Future[DiscardedEntity] =
        cl.discardBytes(entity)

      override def toString(entity: HttpEntity): Future[String] =
        cl.toString(entity)
    }

  /**
    * Constructs a typed ''HttpClient[Task, A]'' from an ''UntypedHttpClient[Task]'' by attempting to unmarshal the
    * response entity into the specific type ''A'' using an implicit ''FromEntityUnmarshaller[A]''.
    *
    * Delegates all calls to the underlying untyped http client.
    *
    * If the response status is not successful, the entity bytes will be discarded instead.
    *
    * @tparam A the specific type to which the response entity should be unmarshalled into
    */
  final implicit def withTaskUnmarshaller[A: ClassTag](implicit
                                                       ec: ExecutionContext,
                                                       mt: Materializer,
                                                       cl: UntypedHttpClient[Task],
                                                       um: FromEntityUnmarshaller[A]): HttpClient[Task, A] = {
    new HttpClient[Task, A] {

      private implicit val log = Logger(s"TypedHttpClient[${implicitly[ClassTag[A]]}]")

      override def apply(req: HttpRequest): Task[A] =
        cl(req).flatMap { resp =>
          if (resp.status.isSuccess()) Task.deferFuture(um(resp.entity))
          else handleError(req, resp)
        }

      override def discardBytes(entity: HttpEntity): Task[DiscardedEntity] =
        cl.discardBytes(entity)

      override def toString(entity: HttpEntity): Task[String] =
        cl.toString(entity)
    }
  }
  // $COVERAGE-ON$
}
