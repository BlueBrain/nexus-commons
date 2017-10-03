package ch.epfl.bluebrain.nexus.sourcing

import cats.Functor

/**
  * Convenience class that allows interacting with [[Aggregate]]s, [[StatefulEventLog]]s and [[EventLog]]s based on
  * instances implicitly available in scope.
  *
  * @tparam F  the monadic effect type
  * @tparam Id the log identifier type
  */
trait EventStore[F[_], Id] {

  /**
    * Returns the precomputed state of the event log identified by the argument ''id''.
    *
    * @param id the identifier of the unique event log
    * @param E  an implicitly available stateful event log
    * @tparam Ev the event type of the event log
    * @return the state of the selected event log instance
    */
  def currentState[Ev](id: Id)(implicit E: StatefulEventLog.Aux[F, Id, Ev, _]): F[E.State] =
    E.currentState(id)

  /**
    * Appends the argument event instance to the event log identified by the argument identifier.
    *
    * @param id    the identifier of the unique event log
    * @param E     an implicitly available event log
    * @param event the event instance to append to the log
    * @tparam Ev the event type of the event log
    * @return the sequence number that corresponds to this appended event
    */
  def append[Ev](id: Id, event: Ev)(implicit E: EventLog.Aux[F, Id, Ev]): F[Long] =
    E.append(id, event)

  /**
    * The last known sequence number for the event log identified by the argument identifier.  A value of ''0L'' implies
    * there is no record of the selected event log.
    *
    * @param id the identifier of the unique event log
    * @param E  an implicitly available event log
    * @tparam Ev the event type of the event log
    * @return the last sequence number of the event log
    */
  def lastSequenceNr[Ev](id: Id)(implicit E: EventLog.Aux[F, Id, Ev]): F[Long] =
    E.lastSequenceNr(id)

  /**
    * Returns whether there's a record of the event log identified by the argument ''id''.
    *
    * @param id the identifier of the unique event log
    * @param E  an implicitly available event log
    * @param F  an implicitly available functor for the effect type ''F''
    * @tparam Ev the event type of the event log
    * @return true if there is at least an event in the selected log or false otherwise
    */
  def exists[Ev](id: Id)(implicit E: EventLog.Aux[F, Id, Ev], F: Functor[F]): F[Boolean] =
    F.map(E.lastSequenceNr(id))(_ > 0L)

  /**
    * Evaluates the argument command against the aggregate identified by the argument ''id''.
    *
    * @param id  the identifier of the unique aggregate
    * @param cmd the command to evaluate
    * @param E   an implicitly available aggregate
    * @tparam Cmd the command type of the aggregate
    * @tparam Ev  the event type of the aggregate
    * @return the outcome of the command evaluation
    */
  def eval[Cmd, Ev](id: Id, cmd: Cmd)(implicit E: Aggregate[F] {
    type Identifier = Id
    type Event      = Ev
    type Command >: Cmd
  }): F[Either[E.Rejection, E.State]] = E.eval(id, cmd)

  /**
    * Applies the fold function ''f'' over the sequence of events, oldest to latest, aggregating into a value of type
    * ''B''.  If there is no record of an event log instance with the specified identifier the function will return the
    * initial value ''z: B''.  Effects are represented under the type ''F[_]''.
    *
    * @param id the identifier of the unique event log
    * @param z  the initial (zero) value
    * @param f  the fold function
    * @tparam B  the type of the resulting value
    * @tparam Ev the event type of the event log
    * @return an aggregate value computed by folding ''f'' over the selected event log event sequence
    */
  def foldLeft[B, Ev](id: Id, z: B)(f: (B, Ev) => B)(implicit E: EventLog.Aux[F, Id, Ev]): F[B] =
    E.foldLeft(id, z)(f)
}

object EventStore {

  /**
    * Constructs a new [[EventStore]] instance.
    *
    * @tparam F  the monadic effect type
    * @tparam Id the log identifier type
    * @return the new [[EventStore]] instance
    */
  final def apply[F[_], Id]: EventStore[F, Id] =
    new EventStore[F, Id] {}
}
