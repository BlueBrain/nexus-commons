package ch.epfl.bluebrain.nexus.commons.es.client

import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.Uri
import cats.MonadError
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import ch.epfl.bluebrain.nexus.commons.es.client.ElasticBaseClient._
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.http.{HttpClient, UnexpectedUnsuccessfulHttpResponse}
import ch.epfl.bluebrain.nexus.commons.types.search._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.Json

import scala.concurrent.ExecutionContext

/**
  * ElasticSearch client implementation that uses a RESTful API endpoint for interacting with a ElasticSearch deployment.
  *
  * @param base        the base uri of the ElasticSearch endpoint
  * @param queryClient the query client
  * @tparam F the monadic effect type
  */
class ElasticClient[F[_]](base: Uri, queryClient: ElasticQueryClient[F])(implicit
                                                                         cl: UntypedHttpClient[F],
                                                                         ec: ExecutionContext,
                                                                         F: MonadError[F, Throwable])
    extends ElasticBaseClient[F] {

  /**
    * Creates a new index with the provided configuration payload.
    *
    * @param index   the index to create
    * @param payload the index configuration
    */
  def createIndex(index: String, payload: Json): F[Unit] =
    execute(Put(base.copy(path = base.path / index), payload), Set(OK, Created), "create index")

  /**
    * Verifies if an index exists, signaling an [[ElasticFailure]] error when it doesn't
    *
    * @param index   the index to verify
    */
  def existsIndex(index: String): F[Unit] =
    execute(Get(base.copy(path = base.path / index)), Set(OK), "verify the existence of an index")

  /**
    * Creates a new document inside the ''index'' and ''`type`'' with the provided ''payload''
    *
    * @param index   the index to use
    * @param `type`  the type to use
    * @param id      the id of the document
    * @param payload the document's payload
    */
  def createDocument(index: String, `type`: String, id: String, payload: Json): F[Unit] = {
    val uri = base.copy(path = base.path / index / `type` / id)
    execute(Put(uri, payload), Set(OK, Created), "create document")
  }

  /**
    * Fetch a document inside the ''index'' and ''`type`'' with the provided ''id''
    *
    * @param index   the index to use
    * @param `type`  the type to use
    */
  def get[A](index: String, `type`: String, id: String)(implicit rs: HttpClient[F, A]): F[A] = {
    val uri = base.copy(path = base.path / index / `type` / id / source)
    rs(Get(uri)).recoverWith {
      case UnexpectedUnsuccessfulHttpResponse(r) => ElasticFailure.fromResponse(r).flatMap(F.raiseError)
      case other                                 => F.raiseError(other)
    }
  }

  /**
    * Search for the provided ''query'' inside the ''indices'' and ''types''
    *
    * @param query    the initial search query
    * @param indices  the indices to use on search (if empty, searches in all the indices)
    * @param page     the paginatoin information
    * @param fields   the fields to be returned
    * @param sort     the sorting criteria
    * @tparam A the generic type to be returned
    */
  def search[A](query: Json, indices: Set[String] = Set.empty)(page: Pagination,
                                                               fields: Set[String] = Set.empty,
                                                               sort: SortList = SortList.Empty)(
      implicit
      rs: HttpClient[F, QueryResults[A]]): F[QueryResults[A]] =
    queryClient(query, indices)(page, fields, sort)
}

object ElasticClient {

  /**
    * Construct a [[ElasticClient]] from the provided ''base'' uri and the provided query client
    *
    * @param base        the base uri of the ElasticSearch endpoint
    * @param queryClient the query client
    * @tparam F the monadic effect type
    */
  final def apply[F[_]](base: Uri, queryClient: ElasticQueryClient[F])(implicit
                                                                       cl: UntypedHttpClient[F],
                                                                       ec: ExecutionContext,
                                                                       F: MonadError[F, Throwable]): ElasticClient[F] =
    new ElasticClient(base, queryClient)

}
