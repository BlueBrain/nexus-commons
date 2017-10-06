package ch.epfl.bluebrain.nexus.sourcing.akka

import ch.epfl.bluebrain.nexus.commons.types.Err

/**
  * Top level error type definition for the akka persistence backed sourcing implementation.
  *
  * @param msg a descriptive message of the error
  */
@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
sealed abstract class EventLogError(msg: String) extends Err(msg)

@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
object EventLogError {

  /**
    * Error definition to signal that a type mismatch (received / expected) has occurred for a persistent id.
    *
    * @param id       the persistent id for which the error occurred
    * @param expected the expected message type
    * @param received the received message
    */
  final case class TypeError(id: String, expected: String, received: Any)
      extends EventLogError(s"Received an unexpected '$received', expected '$expected' type for action on '$id'")

  /**
    * Signals a timeout while waiting for the argument action to be evaluated on the argument id.
    *
    * @param id     the persistent id for which the error occurred
    * @param action the action that was performed
    * @tparam A the type of the action performed
    */
  final case class TimeoutError[A](id: String, action: A)
      extends EventLogError(s"Timed out while expecting reply for action on '$id'")

  /**
    * Error definition to signal that the underlying persistent actor has sent an unexpected message for an intended
    * actions.
    *
    * @param id     the persistent id for which the error occurred
    * @param action the action that was performed
    * @param reply  the reply received
    * @tparam A the type of the action performed
    * @tparam B the type of the reply received
    */
  final case class UnexpectedReply[A, B](id: String, action: A, reply: B)
      extends EventLogError(s"Received an unexpected reply for action on '$id")

  /**
    * Error definition to signal an unknown error that occurred materialized as an exception during the processing of
    * an action against an event log.
    *
    * @param id     the persistent id for which the error occurred
    * @param action the action that was performed
    * @param th     the exception thrown
    * @tparam A the type of the action performed
    */
  final case class UnknownError[A](id: String, action: A, th: Throwable)
      extends EventLogError(s"Unknown error occurred while executing action on '$id'")

}
