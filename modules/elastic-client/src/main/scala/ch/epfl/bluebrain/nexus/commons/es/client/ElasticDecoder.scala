package ch.epfl.bluebrain.nexus.commons.es.client

import ch.epfl.bluebrain.nexus.commons.types.search.QueryResult.{ScoredQueryResult, UnscoredQueryResult}
import ch.epfl.bluebrain.nexus.commons.types.search.QueryResults.{ScoredQueryResults, UnscoredQueryResults}
import ch.epfl.bluebrain.nexus.commons.types.search.{QueryResult, QueryResults}
import io.circe.{Decoder, Json}

class ElasticDecoder[A](implicit D: Decoder[A]) {

  private type ErrorOrResults = Either[Json, List[QueryResult[A]]]

  private def queryResults(json: Json, scored: Boolean): ErrorOrResults = {
    def queryResult(result: Json): Option[QueryResult[A]] =
      result.hcursor.get[A]("_source") match {
        case Right(source) =>
          if (scored) Some(ScoredQueryResult(result.hcursor.get[Float]("_score").getOrElse(0F), source))
          else Some(UnscoredQueryResult(source))
        // $COVERAGE-OFF$
        case _ => None
        // $COVERAGE-ON$
      }
    json.hcursor
      .downField("hits")
      .downField("hits")
      .focus
      .flatMap(_.asArray)
      .getOrElse(Vector.empty)
      .foldLeft[ErrorOrResults](Right(List.empty)) {
        case (Left(prev), _) =>
          // $COVERAGE-OFF$
          Left(prev)
        // $COVERAGE-ON$
        case (Right(acc), result) =>
          queryResult(result) match {
            case Some(qr) =>
              Right(qr :: acc)
            // $COVERAGE-OFF$
            case _ => Left(result)
            // $COVERAGE-ON$
          }
      }
      .map(_.reverse)
  }

  private def decodeScoredQueryResults(maxScore: Float): Decoder[QueryResults[A]] =
    Decoder.decodeJson.emap { json =>
      val total = json.hcursor.downField("hits").get[Long]("total").getOrElse(0L)
      queryResults(json, scored = true) match {
        case Right(list) => Right(ScoredQueryResults(total, maxScore, list))
        // $COVERAGE-OFF$
        case Left(errJson) => Left(s"Could not decode source from value '$errJson'")
        // $COVERAGE-ON$
      }
    }

  private val decodeUnscoredResults: Decoder[QueryResults[A]] =
    Decoder.decodeJson.emap { json =>
      val total = json.hcursor.downField("hits").get[Long]("total").getOrElse(0L)
      queryResults(json, scored = false) match {
        case Right(list) => Right(UnscoredQueryResults(total, list))
        // $COVERAGE-OFF$
        case Left(errJson) => Left(s"Could not decode source from value '$errJson'")
        // $COVERAGE-ON$
      }
    }

  val decodeQueryResults: Decoder[QueryResults[A]] =
    Decoder.decodeJson.flatMap { json =>
      json.hcursor
        .downField("hits")
        .get[Float]("max_score")
        .toOption
        .filterNot(f => f.isInfinite || f.isNaN)
        .map(decodeScoredQueryResults)
        .getOrElse(decodeUnscoredResults)
    }
}

object ElasticDecoder {

  /**
    * Construct a [Decoder] for [QueryResults] of the generic type ''A''
    *
    * @param D the implicitly available decoder for ''A''
    * @param E the implicitly available encoder for ''A''
    * @tparam A the generic type for the decoder
    */
  final def apply[A](implicit D: Decoder[A]): Decoder[QueryResults[A]] =
    new ElasticDecoder[A].decodeQueryResults
}
