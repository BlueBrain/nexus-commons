package ch.epfl.bluebrain.nexus.commons.types.search

import akka.http.scaladsl.model.Uri
import cats.Show
import ch.epfl.bluebrain.nexus.commons.types.search.Sort.OrderType._
import ch.epfl.bluebrain.nexus.commons.types.search.Sort._

import scala.util.Try

/**
  * Data type of a ''value'' to be sorted
  *
  * @param order the order (ascending or descending) of the sorting value
  * @param value the value to be sorted
  */
final case class Sort(order: OrderType, value: Uri)

object Sort {

  private def validAbsoluteUri(value: String): Option[Uri] =
    Try(Uri(value)).filter(uri => uri.isAbsolute && uri.toString().indexOf("/") > -1).toOption

  /**
    * Attempt to construct a [[Sort]] from a string
    *
    * @param value the string
    */
  final def apply(value: String): Option[Sort] =
    value take 1 match {
      case "-" => validAbsoluteUri(value.drop(1)).map(Sort(Desc, _))
      case "+" => validAbsoluteUri(value.drop(1)).map(Sort(Asc, _))
      case _   => validAbsoluteUri(value).map(Sort(Asc, _))
    }

  /**
    * Enumeration type for all possible ordering
    */
  sealed trait OrderType extends Product with Serializable

  object OrderType {

    /**
      * Descending ordering
      */
    final case object Desc extends OrderType

    /**
      * Ascending ordering
      */
    final case object Asc extends OrderType

    implicit val showOrderType: Show[OrderType] = Show.show {
      case Asc  => "asc"
      case Desc => "desc"
    }

  }

}
