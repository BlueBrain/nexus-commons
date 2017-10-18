package ch.epfl.bluebrain.nexus.commons.types

/**
  * Signals an error which is going to be retried.
  *
  * @param message a text describing the reason as to why this exception has been raised.
  */
@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
class RetriableErr(message: String) extends Err(message)
