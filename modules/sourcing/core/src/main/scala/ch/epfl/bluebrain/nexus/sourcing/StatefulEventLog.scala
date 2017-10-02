package ch.epfl.bluebrain.nexus.sourcing

/**
  * An [[EventLog]] that maintains a default state.  As new events are appended to the log, the state of the log is
  * automatically updated to reflect the new events.
  *
  * @see [[EventLog]]
  * @tparam F the monadic effect type
  */
trait StatefulEventLog[F[_]] extends EventLog[F] {

  /**
    * The event log state type.
    */
  type State

  /**
    * Returns the precomputed state of the event log identified by the argument ''id''.
    *
    * @param id the identifier of the unique event log
    * @return the state of the selected event log instance
    */
  def currentState(id: Identifier): F[State]
}

object StatefulEventLog {

  /**
    * Lifts abstract type members of an StatefulEventLog as type parameters.
    *
    * @tparam F  the monadic effect type
    * @tparam Id the event log identifier type
    * @tparam Ev the event log event type
    * @tparam St the event log state type
    */
  type Aux[F[_], Id, Ev, St] = StatefulEventLog[F] {
    type Identifier = Id
    type Event      = Ev
    type State      = St
  }
}
