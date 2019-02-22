package ch.epfl.bluebrain.nexus.commons.search

/**
  * Request pagination data type.
  *
  * @param from the start offset
  * @param size the maximum number of results per page
  */
final case class Pagination(from: Long, size: Int)
