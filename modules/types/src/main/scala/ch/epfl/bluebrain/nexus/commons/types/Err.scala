package ch.epfl.bluebrain.nexus.commons.types

/**
  * Top level error type that does not fill in the stack trace when thrown.  It also enforces the presence of a message.
  *
  * @param message a text describing the reason as to why this exception has been raised.
  */
@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
abstract class Err(val message: String) extends Exception {
  override def fillInStackTrace(): Err = this
  override val getMessage: String      = message
}
