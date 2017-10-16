package ch.epfl.bluebrain.nexus.commons.sparql.client

import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes.{ClientError, ServerError}
import ch.epfl.bluebrain.nexus.commons.types.{Err, RetriableErr}

@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
trait SparqlFailure extends Err

@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
object SparqlFailure {

  /**
    * Generates a SPARQL server failure from the HTTP response status ''code''.
    *
    * @param code the HTTP response status ''code''
    * @param body the HTTP response payload
    */
  def fromStatusCode(code: StatusCode, body: String): SparqlFailure =
    code match {
      case _: ServerError => SparqlServerError(code, body)
      case _: ClientError => SparqlClientError(code, body)
      case _              => SparqlUnexpectedError(code, body)
    }

  /**
    * An unexpected server failure when attempting to communicate with a sparql endpoint.
    *
    * @param status the status returned by the sparql endpoint
    * @param body   the response body returned by the sparql endpoint
    */
  final case class SparqlServerError(status: StatusCode, body: String)
      extends Err(s"Server error with status code '$status'")
      with SparqlFailure

  /**
    * A client failure when attempting to communicate with a sparql endpoint.
    *
    * @param status the status returned by the sparql endpoint
    * @param body   the response body returned by the sparql endpoint
    */
  final case class SparqlClientError(status: StatusCode, body: String)
      extends RetriableErr(s"Client error with status code '$status'")
      with SparqlFailure

  /**
    * An unexpected failure when attempting to communicate with a sparql endpoint.
    *
    * @param status the status returned by the sparql endpoint
    * @param body   the response body returned by the sparql endpoint
    */
  final case class SparqlUnexpectedError(status: StatusCode, body: String)
      extends RetriableErr(s"Unexpected status code '$status'")
      with SparqlFailure

}
