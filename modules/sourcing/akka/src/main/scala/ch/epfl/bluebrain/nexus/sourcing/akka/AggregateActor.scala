package ch.epfl.bluebrain.nexus.sourcing.akka

import java.net.URLDecoder

import akka.actor.{ActorLogging, PoisonPill, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.{PersistentActor, RecoveryCompleted}
import ch.epfl.bluebrain.nexus.sourcing.akka.EventLogError.TypeError
import ch.epfl.bluebrain.nexus.sourcing.akka.Msg._
import shapeless.{Typeable, the}

import scala.concurrent.duration.FiniteDuration

/**
  * Persistent actor implementation that keeps track of the State, Event and Command types while operating on them.
  *
  * @param initial            the initial state of the actor
  * @param next               function which computes the next state given the current state and a new event
  * @param eval               function which evaluates received commands and produces an event to be recorded to the log
  * @param passivationTimeout the inactivity interval after which the actor requests to be stopped
  * @tparam Event     the type of event recorded to the log
  * @tparam State     the type of the state maintained by the actor
  * @tparam Command   the type of commands accepted by the actor
  * @tparam Rejection the type of rejections issued for commands that cannot be applied
  */
class AggregateActor[Event: Typeable, State, Command: Typeable, Rejection](
    initial: State,
    next: (State, Event) => State,
    eval: (State, Command) => Either[Rejection, Event],
    passivationTimeout: FiniteDuration)
    extends PersistentActor
    with ActorLogging {

  override val persistenceId: String = URLDecoder.decode(self.path.name, "UTF-8")

  private val Event   = the[Typeable[Event]]
  private val Command = the[Typeable[Command]]

  private var state: State = initial

  override def preStart(): Unit = {
    context.setReceiveTimeout(passivationTimeout)
    super.preStart()
  }

  override def receiveCommand: Receive = {
    case Append(id, value) =>
      Event.cast(value) match {
        case Some(event) =>
          persist(event) { _ =>
            state = next(state, event)
            log.debug("Applied event '{}' to actor '{}'", event, persistenceId)
            sender() ! Appended(id, lastSequenceNr)
          }
        // $COVERAGE-OFF$
        case None =>
          log.error("Received an event '{}' incompatible with the expected type '{}'", value, Event.describe)
          sender() ! TypeError(id, Event.describe, value)
        // $COVERAGE-ON$
      }
    case Eval(id, value) =>
      Command.cast(value) match {
        case Some(cmd) =>
          val result = eval(state, cmd)
          result match {
            case Left(rejection) =>
              log.debug("Rejected command '{}' on actor '{}' because '{}'", cmd, persistenceId, rejection)
              sender() ! Evaluated(id, result)
            case Right(event) =>
              persist(event) { _ =>
                log.debug("Applied event '{}' to actor '{}'", event, persistenceId)
                state = next(state, event)
                sender() ! Evaluated(id, Right(state))
              }
          }
        // $COVERAGE-OFF$
        case None =>
          log.error("Received a command '{}' incompatible with the expected type '{}'", value, Command.describe)
          sender() ! TypeError(id, Command.describe, value)
        // $COVERAGE-ON$
      }
    case GetLastSeqNr(id) =>
      sender() ! LastSeqNr(id, lastSequenceNr)
    case GetCurrentState(id) =>
      sender() ! CurrentState(id, state)
  }

  override def receiveRecover: Receive = {
    case RecoveryCompleted =>
      log.debug("Recovery completed on actor '{}'", persistenceId)
    case value =>
      Event.cast(value) match {
        case Some(e) =>
          state = next(state, e)
          log.debug("Applied event '{}' to actor '{}'", e, persistenceId)
        // $COVERAGE-OFF$
        case None =>
          log.error("Unknown message '{}' during recovery of actor '{}', expected message of type '{}'", value, persistenceId, Event.describe)
        // $COVERAGE-ON$
      }
  }

  // $COVERAGE-OFF$
  override def unhandled(message: Any): Unit = message match {
    case ReceiveTimeout => context.parent ! Passivate(stopMessage = PoisonPill)
    case other          => super.unhandled(other)
  }
  // $COVERAGE-ON$
}
