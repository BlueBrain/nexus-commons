package ch.epfl.bluebrain.nexus.commons.es.client

import java.net.URLEncoder

import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.stream.Materializer
import cats.MonadError
import cats.effect.Effect
import cats.implicits._
import ch.epfl.bluebrain.nexus.commons.es.client.ElasticSearchBaseClient._
import ch.epfl.bluebrain.nexus.commons.es.client.ElasticSearchClient._
import ch.epfl.bluebrain.nexus.commons.es.client.ElasticSearchFailure.ElasticUnexpectedError
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.{UntypedHttpClient, withUnmarshaller}
import ch.epfl.bluebrain.nexus.commons.http.{HttpClient, UnexpectedUnsuccessfulHttpResponse}
import ch.epfl.bluebrain.nexus.commons.search.{Pagination, QueryResults, SortList}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.Json
import io.circe.parser.parse

import scala.concurrent.ExecutionContext
import scala.util.Try
import scala.util.control.NonFatal

/**
  * ElasticSearch client implementation that uses a RESTful API endpoint for interacting with a ElasticSearch deployment.
  *
  * @param base        the base uri of the ElasticSearch endpoint
  * @param queryClient the query client
  * @tparam F the monadic effect type
  */
class ElasticSearchClient[F[_]](base: Uri, queryClient: ElasticSearchQueryClient[F])(
    implicit
    cl: UntypedHttpClient[F],
    serviceDescClient: HttpClient[F, ServiceDescription],
    ec: ExecutionContext,
    F: MonadError[F, Throwable]
) extends ElasticSearchBaseClient[F] {

  /**
    * Fetches the service description information (name and version)
    */
  def serviceDescription: F[ServiceDescription] =
    serviceDescClient(Get(base))

  /**
    * Verifies if an index exists, recovering gracefully when the index does not exists.
    *
    * @param index   the index to verify
    * @return ''true'' wrapped in ''F'' when the index exists and ''false'' wrapped in ''F'' when the index does not exist
    */
  def existsIndex(index: String): F[Boolean] =
    execute(Get(base / sanitize(index, allowWildCard = false)), Set(OK), Set(NotFound), "get index")

  /**
    * Attempts to create an index recovering gracefully when the index already exists.
    *
    * @param index   the index
    * @param payload the payload to attach to the index when it does not exist
    * @return ''true'' wrapped in ''F'' when index has been created and ''false'' wrapped in ''F'' when it already existed
    */
  def createIndex(index: String, payload: Json = Json.obj()): F[Boolean] = {
    val sanitized = sanitize(index, allowWildCard = false)
    existsIndex(sanitized) flatMap {
      case false =>
        execute(Put(base / sanitized, payload), Set(OK, Created), Set.empty, "create index")
      case true => F.pure(false)
    }
  }

  /**
    * Attempts to update the mappings of a given index recovering gracefully when the index does not exists.
    * @param index   the index
    * @param payload the payload to attach to the index mappings when it exists
    * @return ''true'' wrapped in ''F'' when the mappings have been updated and ''false'' wrapped in ''F'' when the index does not exist
    */
  def updateMapping(index: String, payload: Json): F[Boolean] =
    execute(
      Put(base / sanitize(index, allowWildCard = false) / "_mapping", payload),
      Set(OK, Created),
      Set(NotFound),
      "update index mappings"
    )

  /**
    * Attempts to delete an index recovering gracefully when the index is not found.
    *
    * @param index the index
    * @return ''true'' when the index has been deleted and ''false'' when the index does not exist. The response is wrapped in an effect type ''F''
    */
  def deleteIndex(index: String): F[Boolean] =
    execute(Delete(base / sanitize(index, allowWildCard = true)), Set(OK), Set(NotFound), "delete index")

  /**
    * Creates a new document inside the ''index'' with the provided ''payload''
    *
    * @param index   the index to use
    * @param id      the id of the document to update
    * @param payload the document's payload
    */
  def create(index: String, id: String, payload: Json): F[Unit] =
    execute(
      Put(base / sanitize(index, allowWildCard = false) / urlEncode(id), payload),
      Set(OK, Created),
      "create document"
    )

  /**
    * Creates a bulk update with the operations defined on the provided ''ops'' argument.
    *
    * @param ops the list of operations to be included in the bulk update
    */
  def bulk(ops: List[BulkOp]): F[Unit] = ops match {
    case Nil => F.unit
    case _ =>
      val entity = HttpEntity(`application/x-ndjson`, ops.map(_.payload).mkString("", newLine, newLine))
      bulkExecute(Post(base / "_bulk", entity))
  }

  private def bulkExecute(req: HttpRequest): F[Unit] =
    cl(req).handleErrorWith(handleError(req, "bulk update")).flatMap { resp =>
      if (resp.status == OK)
        cl.toString(resp.entity).flatMap { body =>
          parse(body).flatMap(_.hcursor.get[Boolean]("errors")) match {
            case Right(false) => F.unit
            case _            => F.raiseError(ElasticUnexpectedError(BadRequest, body))
          }
        } else
        ElasticSearchFailure.fromResponse(resp).flatMap { f =>
          log.error(
            s"Unexpected ElasticSearch response for intent 'bulk update':\nRequest: '${req.method} ${req.uri}' \nBody: '${f.body}'\nStatus: '${resp.status}'\nResponse: '${f.body}'"
          )
          F.raiseError(f)
        }
    }

  /**
    * Updates an existing document with the provided payload.
    *
    * @param index   the index to use
    * @param id      the id of the document to update
    * @param payload the document's payload
    * @param qp      the optional query parameters
    */
  def update(index: String, id: String, payload: Json, qp: Query = Query.Empty): F[Unit] =
    execute(
      Post((base / sanitize(index, allowWildCard = false) / updatePath / urlEncode(id)).withQuery(qp), payload),
      Set(OK, Created),
      "update index"
    )

  /**
    * Updates every document with ''payload'' found when searching for ''query''
    *
    * @param indices the indices to use on search (if empty, searches in all the indices)
    * @param query   the query to filter which documents are going to be updated
    * @param payload the document's payload
    * @param qp      the optional query parameters
    */
  def updateDocuments(
      indices: Set[String] = Set.empty,
      query: Json,
      payload: Json,
      qp: Query = Query.Empty
  ): F[Unit] = {
    val uri = (base / indexPath(indices) / updateByQueryPath).withQuery(qp)
    execute(Post(uri, payload deepMerge query), Set(OK, Created), "update index from query")
  }

  /**
    * Deletes the document with the provided ''id''
    *
    * @param index  the index to use
    * @param id     the id to delete
    */
  def delete(index: String, id: String): F[Unit] =
    execute(Delete(base / sanitize(index, allowWildCard = false) / docType / urlEncode(id)), Set(OK), "delete document")

  /**
    * Deletes every document with that matches the provided ''query''
    *
    * @param indices the indices to use on search (if empty, searches in all the indices)
    * @param query   the query to filter which documents are going to be deleted
    */
  def deleteDocuments(indices: Set[String] = Set.empty, query: Json): F[Unit] =
    execute(Post(base / indexPath(indices) / deleteByQueryPath, query), Set(OK), "delete index from query")

  /**
    * Fetch a document inside the ''index'' with the provided ''id''
    *
    * @param index   the index to use
    * @param id      the id of the document to fetch
    * @param include the fields to be returned
    * @param exclude the fields not to be returned
    */
  def get[A](index: String, id: String, include: Set[String] = Set.empty, exclude: Set[String] = Set.empty)(
      implicit rs: HttpClient[F, A]
  ): F[Option[A]] = {
    val includeMap: Map[String, String] =
      if (include.isEmpty) Map.empty else Map(includeFieldsQueryParam -> include.mkString(","))
    val excludeMap: Map[String, String] =
      if (exclude.isEmpty) Map.empty else Map(excludeFieldsQueryParam -> exclude.mkString(","))
    val uri = base / sanitize(index, allowWildCard = false) / source / urlEncode(id)
    rs(Get(uri.withQuery(Query(includeMap ++ excludeMap)))).map(Option.apply).handleErrorWith {
      case UnexpectedUnsuccessfulHttpResponse(r, _) if r.status == StatusCodes.NotFound => F.pure[Option[A]](None)
      case UnexpectedUnsuccessfulHttpResponse(r, body) =>
        F.raiseError(ElasticSearchFailure.fromStatusCode(r.status, body))
      case NonFatal(th) =>
        log.error(s"Unexpected response for ElasticSearch 'get' call. Request: 'GET $uri'", th)
        F.raiseError(ElasticUnexpectedError(StatusCodes.InternalServerError, th.getMessage))
    }
  }

  /**
    * Search for the provided ''query'' inside the ''indices''
    *
    * @param query   the initial search query
    * @param indices the indices to use on search (if empty, searches in all the indices)
    * @param page    the pagination information
    * @param fields  the fields to be returned
    * @param sort    the sorting criteria
    * @param qp      the optional query parameters
    * @tparam A the generic type to be returned
    */
  def search[A](
      query: Json,
      indices: Set[String] = Set.empty,
      qp: Query = Query(ignoreUnavailable -> "true", allowNoIndices -> "true")
  )(page: Pagination, totalHits: Boolean = true, fields: Set[String] = Set.empty, sort: SortList = SortList.Empty)(
      implicit
      rs: HttpClient[F, QueryResults[A]]
  ): F[QueryResults[A]] =
    queryClient(query, indices, qp)(page, fields, sort, totalHits)

  /**
    * Search ElasticSearch using provided query and return ES response with ''_shards'' information removed
    *
    * @param query search query
    * @param indices indices to search
    * @param qp the optional query parameters
    * @return ES response JSON
    */
  def searchRaw(
      query: Json,
      indices: Set[String] = Set.empty,
      qp: Query = Query(ignoreUnavailable -> "true", allowNoIndices -> "true")
  )(
      implicit
      rs: HttpClient[F, Json]
  ): F[Json] =
    queryClient.searchRaw(query, indices, qp)

  private def urlEncode(value: String): String =
    Try(URLEncoder.encode(value, "UTF-8")).getOrElse(value)
}

object ElasticSearchClient {

  /**
    * Enumeration type for all possible bulk operations
    */
  sealed trait BulkOp extends Product with Serializable {

    /**
      * @return the index to use for the current bulk operation
      */
    def index: String

    /**
      * @return the id of the document for the current bulk operation
      */
    def id: String

    /**
      * @return the payload for the current bulk operation
      */
    def payload: String

    private[ElasticSearchClient] def json: Json =
      Json.obj("_index" -> Json.fromString(index), "_id" -> Json.fromString(id))
  }

  object BulkOp {
    final case class Index(index: String, id: String, content: Json) extends BulkOp {
      def payload: String = Json.obj("index" -> json).noSpaces + newLine + content.noSpaces
    }
    final case class Create(index: String, id: String, content: Json) extends BulkOp {
      def payload: String = Json.obj("create" -> json).noSpaces + newLine + content.noSpaces
    }
    final case class Update(index: String, id: String, content: Json, retry: Int = 0) extends BulkOp {
      val modified = if (retry > 0) json deepMerge Json.obj("retry_on_conflict" -> Json.fromInt(retry)) else json

      def payload: String = Json.obj("update" -> modified).noSpaces + newLine + content.noSpaces
    }
    final case class Delete(index: String, id: String) extends BulkOp {
      def payload: String = Json.obj("delete" -> json).noSpaces + newLine
    }
  }

  private[client] val updatePath              = "_update"
  private[client] val updateByQueryPath       = "_update_by_query"
  private[client] val deleteByQueryPath       = "_delete_by_query"
  private[client] val includeFieldsQueryParam = "_source_includes"
  private[client] val excludeFieldsQueryParam = "_source_excludes"
  private[client] val newLine                 = System.lineSeparator()
  private[client] val `application/x-ndjson`: MediaType.WithFixedCharset =
    MediaType.applicationWithFixedCharset("x-ndjson", HttpCharsets.`UTF-8`, "json")

  /**
    * Construct a [[ElasticSearchClient]] from the provided ''base'' uri and the provided query client
    *
    * @param base        the base uri of the ElasticSearch endpoint
    * @tparam F the monadic effect type
    */
  final def apply[F[_]: Effect](base: Uri)(
      implicit
      cl: UntypedHttpClient[F],
      ec: ExecutionContext,
      mt: Materializer
  ): ElasticSearchClient[F] = {
    implicit val serviceDesc: HttpClient[F, ServiceDescription] = withUnmarshaller[F, ServiceDescription]
    new ElasticSearchClient(base, ElasticSearchQueryClient(base))
  }

}
