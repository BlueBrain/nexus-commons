package ch.epfl.bluebrain.nexus.sourcing.akka

/**
  * Enumeration that defines the message types exchanged with the underlying persistent actor.
  */
sealed trait Msg extends Product with Serializable {

  /**
    * @return the persistent id
    */
  def id: String
}

object Msg {

  /**
    * Message to trigger a new event to be appended to the event log.
    *
    * @param id  the persistent id
    * @param evt the event to be appended
    * @tparam Evt the type of the event
    */
  final case class Append[Evt](id: String, evt: Evt) extends Msg

  /**
    * Message to confirm that a new event has been appended to the event log.
    *
    * @param id  the persistent id
    * @param lsn the sequence number of the appended event
    */
  final case class Appended(id: String, lsn: Long) extends Msg

  /**
    * Message to retrieve the current sequence number of an event log.
    *
    * @param id the persistent id
    */
  final case class GetLastSeqNr(id: String) extends Msg

  /**
    * Message for exchanging the last known sequence number of an event log.
    *
    * @param id  the persistent id
    * @param lsn the last sequence numbers
    */
  final case class LastSeqNr(id: String, lsn: Long) extends Msg

  /**
    * Message to retrieve the current state of a stateful event log.
    *
    * @param id the persistent id
    */
  final case class GetCurrentState(id: String) extends Msg

  /**
    * Message for exchanging the current state of a stateful event log.
    *
    * @param id    the persistent id
    * @param state the current state of the event log
    * @tparam St the type of the event log state
    */
  final case class CurrentState[St](id: String, state: St) extends Msg

  /**
    * Message to evaluate a command against an aggregate.
    *
    * @param id  the persistent id
    * @param cmd the command to evaluate
    * @tparam Cmd the type of the command to evaluate
    */
  final case class Eval[Cmd](id: String, cmd: Cmd) extends Msg

  /**
    * Message for replying with the outcome of evaluating a command against an aggregate.
    *
    * @param id    the persistent id
    * @param value either a rejection or the state derived from the last command evaluation
    * @tparam Rej the type of rejection
    * @tparam St  the type of the event log state
    */
  final case class Evaluated[Rej, St](id: String, value: Either[Rej, St]) extends Msg

}
