package ch.epfl.bluebrain.nexus.sourcing

/**
  * A [[StatefulEventLog]] that can evaluate ''Command''s to emit new events and transform its state.  Roughly
  * corresponds to the aggregate root in DDD terminology.
  *
  * @tparam F the monadic effect type
  */
trait Aggregate[F[_]] extends StatefulEventLog[F] {

  /**
    * The command type that this aggregate accepts.
    */
  type Command

  /**
    * The rejection type that is returned for commands that cannot be applied.
    */
  type Rejection

  /**
    * Evaluates the argument command against the aggregate identified by the argument ''id''.
    *
    * @param id  the identifier of the unique aggregate
    * @param cmd the command to evaluate
    * @return the outcome of the command evaluation, either a rejection or the new state derived from evaluating the
    *         command
    */
  def eval(id: Identifier, cmd: Command): F[Either[Rejection, State]]
}

object Aggregate {

  /**
    * Lifts abstract type members of an Aggregate as type parameters.
    *
    * @tparam F   the monadic effect type
    * @tparam Id  the aggregate identifier type
    * @tparam Ev  the aggregate event type
    * @tparam St  the aggregate state type
    * @tparam Cmd the aggregate command type
    * @tparam Rej the aggregate rejection type
    */
  type Aux[F[_], Id, Ev, St, Cmd, Rej] = Aggregate[F] {
    type Identifier = Id
    type Event      = Ev
    type State      = St
    type Command    = Cmd
    type Rejection  = Rej
  }
}
