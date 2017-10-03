package ch.epfl.bluebrain.nexus.sourcing

/**
  * Typeclass definition that models an interaction with an event log.  The definition does not impose the use of a
  * specific identifier, event type or effect handling.
  *
  * @tparam F the monadic effect type
  */
trait EventLog[F[_]] extends Serializable {

  /**
    * The event log identifier type.
    */
  type Identifier

  /**
    * The event log event type.
    */
  type Event

  /**
    * The name can be used to discriminate between two event log instances that share parts of an event ADT.
    *
    * @return the name of this event log.
    */
  def name: String

  /**
    * Appends the argument event instance to the event log identified by the argument identifier.
    *
    * @param id    the identifier of the unique event log
    * @param event the event instance to append to the log
    * @return the sequence number that corresponds to this appended event
    */
  def append(id: Identifier, event: Event): F[Long]

  /**
    * The last known sequence number for the event log identified by the argument identifier.  A value of ''0L'' implies
    * there is no record of the selected event log.
    *
    * @param id the identifier of the unique event log
    * @return the last sequence number of the event log.
    */
  def lastSequenceNr(id: Identifier): F[Long]

  /**
    * Applies the fold function ''f'' over the sequence of events, oldest to latest, aggregating into a value of type
    * ''B''.  If there is no record of an event log instance with the specified identifier the function will return the
    * initial value ''z: B''.  Effects are represented under the type ''F[_]''.
    *
    * @param id the identifier of the unique event log
    * @param z  the initial (zero) value
    * @param f  the fold function
    * @tparam B the type of the resulting value
    * @return an aggregate value computed by folding ''f'' over the selected event log event sequence
    */
  def foldLeft[B](id: Identifier, z: B)(f: (B, Event) => B): F[B]
}

object EventLog {

  /**
    * Lifts abstract type members of an EventLog as type parameters.
    *
    * @tparam F  the monadic effect type
    * @tparam Id the event log identifier type
    * @tparam Ev the event log event type
    */
  type Aux[F[_], Id, Ev] = EventLog[F] {
    type Identifier = Id
    type Event      = Ev
  }
}
