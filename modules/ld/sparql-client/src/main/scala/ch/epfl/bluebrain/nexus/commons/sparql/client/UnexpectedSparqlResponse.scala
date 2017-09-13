package ch.epfl.bluebrain.nexus.commons.sparql.client

import akka.http.scaladsl.model.StatusCode
import ch.epfl.bluebrain.nexus.common.types.Err

/**
  * An unexpected failure when attempting to communicate with a sparql endpoint.
  *
  * @param status the status returned by the sparql endpoint
  * @param body   the response body returned by the sparql endpoint
  */
@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
final case class UnexpectedSparqlResponse(status: StatusCode, body: String) extends Err("Unexpected status code")