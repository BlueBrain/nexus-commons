package ch.epfl.bluebrain.nexus.sourcing.mem

import java.util.concurrent.ConcurrentHashMap

import cats.{Applicative, Id}
import ch.epfl.bluebrain.nexus.sourcing.Aggregate
import ch.epfl.bluebrain.nexus.sourcing.mem.MemoryAggregate.Value

/**
  * An in-memory aggregate while keeping track of the event, state and command ch.epfl.bluebrain.nexus.commons.iam.types defined.
  *
  * @param name    the name (or type) of the aggregate
  * @param initial the initial state
  * @param next    function to compute the next state considering a current state and a new event
  * @param eval    function to evaluate a new command considering a current state
  * @tparam Evt the type of events supported by this aggregate
  * @tparam St  the type of state maintained by this aggregate
  * @tparam Cmd the type of commands considered by this aggregate
  * @tparam Rej the type of rejections returned by this aggregate
  */
final class MemoryAggregate[Evt, St, Cmd, Rej](override val name: String,
                                               initial: St,
                                               next: (St, Evt) => St,
                                               eval: (St, Cmd) => Either[Rej, Evt])
    extends Aggregate[Id] {

  override type Identifier = String
  override type Event      = Evt
  override type State      = St
  override type Command    = Cmd
  override type Rejection  = Rej

  private val newStore = new ConcurrentHashMap[String, Value[State, Event, Rejection]]()

  override def append(id: Identifier, event: Event): Long = {
    newStore
      .merge(id,
             Value(next(initial, event), Vector(event)),
             (current, _) => Value(next(current.state, event), current.events :+ event))
      .events
      .size
      .toLong
  }

  override def lastSequenceNr(id: Identifier): Long = {
    valueOrEmpty(id).events.size.toLong
  }

  override def foldLeft[B](id: Identifier, z: B)(f: (B, Event) => B): B = {
    valueOrEmpty(id).events.foldLeft(z)(f)
  }

  override def currentState(id: Identifier): State =
    valueOrEmpty(id).state

  override def eval(id: Identifier, cmd: Command): Either[Rejection, State] = {
    val value = newStore.merge(
      id, {
        eval(initial, cmd) match {
          case Left(rejection) => empty.copy(rejection = Some(rejection))
          case Right(ev)       => Value(next(initial, ev), Vector(ev))
        }
      },
      (current, _) => {
        eval(current.state, cmd) match {
          case Left(rejection) => current.copy(rejection = Some(rejection))
          case Right(ev)       => Value(next(current.state, ev), current.events :+ ev)
        }
      }
    )
    value.rejection.map(rej => Left(rej)).getOrElse(Right(value.state))
  }

  private val empty = Value[State, Event, Rejection](initial, Vector.empty)

  private def valueOrEmpty(id: Identifier): Value[State, Event, Rejection] =
    newStore.getOrDefault(id, empty)
}

object MemoryAggregate {

  /**
    * Constructs a new in-memory aggregate while keeping track of the event, state and command ch.epfl.bluebrain.nexus.commons.iam.types defined.
    *
    * @param name    the name (or type) of the aggregate
    * @param initial the initial state
    * @param next    function to compute the next state considering a current state and a new event
    * @param eval    function to evaluate a new command considering a current state
    * @tparam Event     the type of events supported by this aggregate
    * @tparam State     the type of state maintained by this aggregate
    * @tparam Command   the type of commands considered by this aggregate
    * @tparam Rejection the type of rejections returned by this aggregate
    * @return a new aggregate instance
    */
  final def apply[Event, State, Command, Rejection](name: String)(
      initial: State,
      next: (State, Event) => State,
      eval: (State, Command) => Either[Rejection, Event]
  ): Aggregate.Aux[Id, String, Event, State, Command, Rejection] =
    new MemoryAggregate(name, initial, next, eval)

  private[mem] final case class Value[State, Event, Rejection](state: State,
                                                               events: Vector[Event],
                                                               rejection: Option[Rejection] = None)

  private def convertToF[F[_]: Applicative, Ident, Evt, St, Cmd, Rej](
      agg: Aggregate.Aux[Id, Ident, Evt, St, Cmd, Rej]) =
    new Aggregate[F] {
      override type Identifier = Ident
      override type Event      = Evt
      override type State      = St
      override type Command    = Cmd
      override type Rejection  = Rej

      import cats.syntax.applicative._

      override val name: String = agg.name

      override def eval(id: Identifier, cmd: Command): F[Either[Rejection, State]] =
        agg.eval(id, cmd).pure

      override def currentState(id: Identifier): F[State] =
        agg.currentState(id).pure

      override def append(id: Identifier, event: Event): F[Long] =
        agg.append(id, event).pure

      override def lastSequenceNr(id: Identifier): F[Long] =
        agg.lastSequenceNr(id).pure

      override def foldLeft[B](id: Identifier, z: B)(f: (B, Event) => B): F[B] =
        agg.foldLeft(id, z)(f).pure
    }

  final implicit class AggregateOps[Ident, Evt, St, Cmd, Rej](val agg: Aggregate.Aux[Id, Ident, Evt, St, Cmd, Rej]) {
    def toF[F[_]: Applicative]: Aggregate.Aux[F, Ident, Evt, St, Cmd, Rej] = convertToF(agg)
  }
}
