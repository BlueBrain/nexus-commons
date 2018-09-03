package ch.epfl.bluebrain.nexus.commons.es.client

import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Query
import cats.MonadError
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.show._
import ch.epfl.bluebrain.nexus.commons.es.client.ElasticBaseClient._
import ch.epfl.bluebrain.nexus.commons.es.client.ElasticQueryClient._
import ch.epfl.bluebrain.nexus.commons.http.{HttpClient, UnexpectedUnsuccessfulHttpResponse}
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.types.search._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.syntax._
import io.circe.{Encoder, Json}

import scala.concurrent.ExecutionContext

/**
  * ElasticSearch query client implementation that uses a RESTful API endpoint for interacting with a ElasticSearch deployment.
  *
  * @param base the base uri of the ElasticSearch endpoint
  * @tparam F the monadic effect type
  */
private[client] class ElasticQueryClient[F[_]](base: Uri)(implicit
                                                          cl: UntypedHttpClient[F],
                                                          ec: ExecutionContext,
                                                          F: MonadError[F, Throwable])
    extends ElasticBaseClient[F] {

  private[client] val searchPath = "_search"

  private[client] val shards = Json.obj(
    "total"      -> Json.fromInt(0),
    "successful" -> Json.fromInt(0),
    "skipped"    -> Json.fromInt(0),
    "failed"     -> Json.fromInt(0)
  )

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
  def apply[A](query: Json,
               indices: Set[String] = Set.empty,
               qp: Query = Query(ignoreUnavailable -> "true", allowNoIndices -> "true"))(
      page: Pagination,
      fields: Set[String] = Set.empty,
      sort: SortList = SortList.Empty)(implicit
                                       rs: HttpClient[F, QueryResults[A]]): F[QueryResults[A]] =
    rs(
      Post((base / indexPath(indices) / searchPath).withQuery(qp),
           query.addPage(page).addSources(fields).addSort(sort)))

  /**
    * Search ElasticSearch using provided query and return ES response with ''_shards'' information removed
    *
    * @param query search query
    * @param indices indices to search
    * @param qp the optional query parameters
    * @return ES response JSON
    */
  def searchRaw(query: Json,
                indices: Set[String] = Set.empty,
                qp: Query = Query(ignoreUnavailable -> "true", allowNoIndices -> "true"))(
      implicit
      rs: HttpClient[F, Json]): F[Json] =
    rs(Post((base / indexPath(indices) / searchPath).withQuery(qp), query))
      .map { esResponse =>
        esResponse.mapObject(_.add("_shards", shards))
      }
      .recoverWith {
        case UnexpectedUnsuccessfulHttpResponse(r) => ElasticFailure.fromResponse(r).flatMap(F.raiseError)
        case other                                 => F.raiseError(other)
      }
}
object ElasticQueryClient {

  /**
    * Construct a [[ElasticQueryClient]] from the provided ''base'' uri
    *
    * @param base        the base uri of the ElasticSearch endpoint
    * @tparam F the monadic effect type
    */
  final def apply[F[_]](base: Uri)(implicit
                                   cl: UntypedHttpClient[F],
                                   ec: ExecutionContext,
                                   F: MonadError[F, Throwable]): ElasticQueryClient[F] =
    new ElasticQueryClient(base)

  private[client] implicit class JsonOpsSearch(query: Json) {

    private implicit val sortEncoder: Encoder[Sort] =
      Encoder.encodeJson.contramap(sort => Json.obj(s"${sort.value}" -> Json.fromString(sort.order.show)))

    /**
      * Adds pagination to the query
      *
      * @param page the pagination information
      */
    def addPage(page: Pagination): Json =
      query deepMerge Json.obj("from" -> Json.fromLong(page.from), "size" -> Json.fromInt(page.size))

    /**
      * Adds sources to the query, which defines what fields are going to be present in the response
      *
      * @param fields the fields we want to show in the response
      */
    def addSources(fields: Set[String]): Json =
      if (fields.isEmpty) query
      else query deepMerge Json.obj(source -> fields.asJson)

    /**
      * Adds sort to the query
      *
      * @param sortList the list of sorts
      */
    def addSort(sortList: SortList): Json =
      sortList match {
        case SortList.Empty  => query
        case SortList(sorts) => query deepMerge Json.obj("sort" -> sorts.asJson)
      }
  }
}
