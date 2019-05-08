package ch.epfl.bluebrain.nexus.commons.search

import io.circe.Json

/**
  * Base request pagination data type.
  *
  */
trait Pagination {

  /**
    *
    * @return size the maximum number of results per page
    */
  def size: Int
}

/**
  * Request pagination data type using `from`.
  *
  * @param from the start offset
  * @param size the maximum number of results per page
  */
final case class FromPagination(from: Int, size: Int) extends Pagination

/**
  * Request pagination data type using `search_after`.
  *
  * @param searchAfter  [[Json]] array to use as ElasticSearch `search_after` field.
  * @param size         the maximum number of results per page
  */
final case class SearchAfterPagination(searchAfter: Seq[Json], size: Int) extends Pagination

object Pagination {

  def apply(size: Int): Pagination                         = FromPagination(0, size)
  def apply(from: Int, size: Int): Pagination              = FromPagination(from, size)
  def apply(searchAfter: Seq[Json], size: Int): Pagination = SearchAfterPagination(searchAfter, size)
}
