package ch.epfl.bluebrain.nexus.commons.es.client

import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{HttpEntity, StatusCodes, Uri}
import cats.MonadError
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import ch.epfl.bluebrain.nexus.commons.es.client.ElasticBaseClient._
import ch.epfl.bluebrain.nexus.commons.es.client.ElasticClient._
import ch.epfl.bluebrain.nexus.commons.es.client.ElasticFailure.ElasticClientError
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
    * Verifies if an index exists, signaling an [[ElasticFailure]] error when it doesn't
    *
    * @param index   the index to verify
    */
  def existsIndex(index: String): F[Unit] =
    execute(Get(base.copy(path = base.path / index)), Set(OK))

  /**
    * Attempts to create an index recovering gracefully when the index already exists.
    *
    * @param index   the index
    * @param payload the payload to attach to the index when it does not exist
    * @return ''true'' wrapped in ''F'' when index has been created and ''false'' wrapped in ''F'' when it already existed
    */
  def createIndex(index: String, payload: Json = Json.obj()): F[Boolean] =
    existsIndex(index).map(_ => false).recoverWith {
      case ElasticClientError(StatusCodes.NotFound, _) =>
        execute(Put(base.copy(path = base.path / index), payload), Set(OK, Created), "create index").map(_ => true)
      case other => F.raiseError(other)
    }

  /**
    * Attempts to update the mappings of a given index recovering gracefully when the index does not exists.
    * @param index   the index
    * @param `type`  the type to use
    * @param payload the payload to attach to the index mappings when it exists
    * @return ''true'' wrapped in ''F'' when the mappings have been updated and ''false'' wrapped in ''F'' when the index does not exist
    */
  def updateMapping(index: String, `type`: String, payload: Json): F[Boolean] =
    execute(Put(base.copy(path = base.path / index / "_mapping" / `type`), payload),
            Set(OK, Created),
            "update index mappings").map(_ => true).recoverWith {
      case ElasticClientError(StatusCodes.NotFound, _) => F.pure(false)
      case other                                       => F.raiseError(other)
    }

  /**
    * Attempts to delete an index recovering gracefully when the index is not found.
    *
    * @param index the index
    * @return ''true'' when the index has been deleted and ''false'' when the index does not exist. The response is wrapped in an effect type ''F''
    */
  def deleteIndex(index: String): F[Boolean] =
    execute(Delete(base.copy(path = base.path / index)), Set(OK)).map(_ => true).recoverWith {
      case ElasticClientError(StatusCodes.NotFound, _) =>
        F.pure(false)
      case other => F.raiseError(other)
    }

  /**
    * Creates a new document inside the ''index'' and ''`type`'' with the provided ''payload''
    *
    * @param index   the index to use
    * @param `type`  the type to use
    * @param id      the id of the document to update
    * @param payload the document's payload
    */
  def create(index: String, `type`: String, id: String, payload: Json): F[Unit] = {
    val uri = base.copy(path = base.path / index / `type` / id)
    execute(Put(uri, payload), Set(OK, Created), "create document")
  }

  /**
    * Updates an existing document with the provided payload.
    *
    * @param index   the index to use
    * @param `type`  the type to use
    * @param id      the id of the document to update
    * @param payload the document's payload
    * @param qp      the optional query parameters
    */
  def update(index: String, `type`: String, id: String, payload: Json, qp: Query = Query.Empty): F[Unit] = {
    val uri = base.copy(path = base.path / index / `type` / id / updatePath).withQuery(qp)
    execute(Post(uri, payload), Set(OK, Created), "update index")
  }

  /**
    * Updates every document with ''payload'' found when searching for ''query''
    *
    * @param indices the indices to use on search (if empty, searches in all the indices)
    * @param query   the query to filter which documents are going to be updated
    * @param payload the document's payload
    * @param qp      the optional query parameters
    */
  def updateDocuments(indices: Set[String] = Set.empty,
                      query: Json,
                      payload: Json,
                      qp: Query = Query.Empty): F[Unit] = {
    val uri = base.copy(path = base.path / indexPath(indices) / updateByQueryPath).withQuery(qp)
    execute(Post(uri, payload deepMerge query), Set(OK, Created), "update index from query")
  }

  /**
    * Deletes the document with the provided ''id''
    *
    * @param index  the index to use
    * @param `type` the type to use
    * @param id     the id to delete
    */
  def delete(index: String, `type`: String, id: String): F[Unit] =
    execute(Delete(base.copy(path = base.path / index / `type` / id)), Set(OK), "delete index")

  /**
    * Updates every document with that matches the provided ''query''
    *
    * @param indices the indices to use on search (if empty, searches in all the indices)
    * @param query   the query to filter which documents are going to be deleted
    */
  def deleteDocuments(indices: Set[String] = Set.empty, query: Json): F[Unit] = {
    val uri = base.copy(path = base.path / indexPath(indices) / deleteByQueryPath)
    execute(Post(uri, query), Set(OK), "delete index from query")

  }

  /**
    * Fetch a document inside the ''index'' and ''`type`'' with the provided ''id''
    *
    * @param index   the index to use
    * @param `type`  the type to use
    * @param id      the id of the document to fetch
    * @param include the fields to be returned
    * @param exclude the fields not to be returned
    */
  def get[A](index: String,
             `type`: String,
             id: String,
             include: Set[String] = Set.empty,
             exclude: Set[String] = Set.empty)(implicit rs: HttpClient[F, A]): F[A] = {
    val uri = base.copy(path = base.path / index / `type` / id / source)
    val includeMap: Map[String, String] =
      if (include.isEmpty) Map.empty else Map(includeFieldsQueryParam -> include.mkString(","))
    val excludeMap: Map[String, String] =
      if (exclude.isEmpty) Map.empty else Map(excludeFieldsQueryParam -> exclude.mkString(","))
    rs(Get(uri.withQuery(Query(includeMap ++ excludeMap)))).recoverWith {
      case UnexpectedUnsuccessfulHttpResponse(r) => ElasticFailure.fromResponse(r).flatMap(F.raiseError)
      case other                                 => F.raiseError(other)
    }
  }

  /**
    * Search for the provided ''query'' inside the ''indices'' and ''types''
    *
    * @param query   the initial search query
    * @param indices the indices to use on search (if empty, searches in all the indices)
    * @param page    the pagination information
    * @param fields  the fields to be returned
    * @param sort    the sorting criteria
    * @param qp      the optional query parameters
    * @tparam A the generic type to be returned
    */
  def search[A](query: Json,
                indices: Set[String] = Set.empty,
                qp: Query = Query(ignoreUnavailable -> "true", allowNoIndices -> "true"))(
      page: Pagination,
      fields: Set[String] = Set.empty,
      sort: SortList = SortList.Empty)(implicit
                                       rs: HttpClient[F, QueryResults[A]]): F[QueryResults[A]] =
    queryClient(query, indices, qp)(page, fields, sort)

  /**
    * Search ElasticSearch using provided query and return ES response with ''_shards'' information removed
    *
    * @param query search query
    * @param indices indices to search
    * @param qp the optional query parameters
    * @param unmarshall the function to unmarshall HTTP response entity into JSON
    * @return ES response JSON
    */
  def searchRaw(query: Json,
                indices: Set[String] = Set.empty,
                qp: Query = Query(ignoreUnavailable -> "true", allowNoIndices -> "true"))(
      unmarshall: HttpEntity => F[Json]): F[Json] =
    queryClient.searchRaw(query, indices, qp)(unmarshall)
}

object ElasticClient {

  private[client] val updatePath              = "_update"
  private[client] val updateByQueryPath       = "_update_by_query"
  private[client] val deleteByQueryPath       = "_delete_by_query"
  private[client] val includeFieldsQueryParam = "_source_include"
  private[client] val excludeFieldsQueryParam = "_source_exclude"

  /**
    * Construct a [[ElasticClient]] from the provided ''base'' uri and the provided query client
    *
    * @param base        the base uri of the ElasticSearch endpoint
    * @tparam F the monadic effect type
    */
  final def apply[F[_]](base: Uri)(implicit
                                   cl: UntypedHttpClient[F],
                                   ec: ExecutionContext,
                                   F: MonadError[F, Throwable]): ElasticClient[F] =
    new ElasticClient(base, ElasticQueryClient(base))

}
