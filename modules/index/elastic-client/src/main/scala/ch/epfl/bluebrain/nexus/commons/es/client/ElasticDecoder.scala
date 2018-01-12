package ch.epfl.bluebrain.nexus.commons.es.client

import ch.epfl.bluebrain.nexus.commons.types.search.QueryResult.{ScoredQueryResult, UnscoredQueryResult}
import ch.epfl.bluebrain.nexus.commons.types.search.QueryResults.{ScoredQueryResults, UnscoredQueryResults}
import ch.epfl.bluebrain.nexus.commons.types.search.{QueryResult, QueryResults}
import io.circe.optics.JsonPath
import io.circe.optics.JsonPath.root
import io.circe.{Decoder, Encoder, Json}

class ElasticDecoder[A](implicit D: Decoder[A], E: Encoder[A]) {

  private def getValueOr[E](key: JsonPath, json: Json)(default: E)(implicit E: Encoder[E], D: Decoder[E]) =
    key.as[E].getOption(json).getOrElse(default)

  private type ErrorOrResults = Either[Json, List[QueryResult[A]]]

  private def queryResults(json: Json, scored: Boolean): ErrorOrResults = {
    def queryResult(result: Json): Option[QueryResult[A]] =
      root._source.as[A].getOption(result) match {
        case Some(source) =>
          if (scored) Some(ScoredQueryResult(getValueOr[Float](root._score, result)(0F), source))
          else Some(UnscoredQueryResult(source))
        // $COVERAGE-OFF$
        case _ => None
        // $COVERAGE-ON$
      }

    root.hits.hits.each
      .as[Json]
      .getAll(json)
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
      val total = getValueOr(root.hits.total, json)(0L)
      queryResults(json, scored = true) match {
        case Right(list) => Right(ScoredQueryResults(total, maxScore, list))
        // $COVERAGE-OFF$
        case Left(errJson) => Left(s"Could not decode source from value '$errJson'")
        // $COVERAGE-ON$
      }
    }

  private val decodeUnscoredResults: Decoder[QueryResults[A]] =
    Decoder.decodeJson.emap { json =>
      val total = getValueOr(root.hits.total, json)(0L)
      queryResults(json, scored = false) match {
        case Right(list) => Right(UnscoredQueryResults(total, list))
        // $COVERAGE-OFF$
        case Left(errJson) => Left(s"Could not decode source from value '$errJson'")
        // $COVERAGE-ON$
      }
    }

  val decodeQueryResults: Decoder[QueryResults[A]] =
    Decoder.decodeJson.flatMap { json =>
      root.hits.max_score
        .as[Float]
        .getOption(json)
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
  final def apply[A](implicit D: Decoder[A], E: Encoder[A]): Decoder[QueryResults[A]] =
    new ElasticDecoder[A].decodeQueryResults
}
