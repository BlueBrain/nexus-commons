package ch.epfl.bluebrain.nexus.sourcing.akka

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ShardRegion.{ExtractEntityId, ExtractShardId}
import akka.event.{Logging, LoggingAdapter}
import akka.pattern.{AskTimeoutException, ask}
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.scaladsl.CurrentEventsByPersistenceIdQuery
import akka.stream.Materializer
import akka.util.Timeout
import ch.epfl.bluebrain.nexus.sourcing.Aggregate
import ch.epfl.bluebrain.nexus.sourcing.akka.EventLogError._
import ch.epfl.bluebrain.nexus.sourcing.akka.Msg._
import shapeless.{Typeable, the}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/**
  * An aggregate implementation backed by akka persistent actors sharded across a cluster.
  */
final class ShardingAggregate[Evt: Typeable, St: Typeable, Cmd, Rej: Typeable](override val name: String,
                                                                               ref: ActorRef,
                                                                               pq: CurrentEventsByPersistenceIdQuery,
                                                                               logger: LoggingAdapter)(
    implicit
    ec: ExecutionContext,
    mt: Materializer,
    tm: Timeout)
    extends Aggregate[Future] {

  override type Identifier = String
  override type Event      = Evt
  override type State      = St
  override type Command    = Cmd
  override type Rejection  = Rej

  private val Event     = the[Typeable[Event]]
  private val State     = the[Typeable[State]]
  private val Rejection = the[Typeable[Rejection]]

  override def append(id: Identifier, event: Event): Future[Long] = {
    logger.debug("Appending a new event '{}' to '{}-{}'", event, name, id)
    val action = Append(id, event)
    ref ? action recoverWith {
      // $COVERAGE-OFF$
      case _: AskTimeoutException =>
        logger.error("Timed out while appending a new event to '{}-{}'", name, id)
        Future.failed(TimeoutError(id, action))
      case NonFatal(th) =>
        logger.error(th, "Unexpected exception while a new event to '{}-{}'", name, id)
        Future.failed(UnknownError(id, action, th))
      // $COVERAGE-ON$
    } flatMap {
      case Appended(`id`, lsn) =>
        logger.debug("Appended event with seq nr '{}' to '{}-{}'", lsn, name, id)
        Future.successful(lsn)
      // $COVERAGE-OFF$
      case t: TypeError =>
        logger.error("Attempted to append an event of unexpected type '{}' to '{}-{}'", t.received, name, id)
        Future.failed(t)
      case unexpected =>
        logger.error("Received an unexpected reply '{}' while appending to '{}-{}'", unexpected, name, id)
        Future.failed(UnexpectedReply(id, action, unexpected))
      // $COVERAGE-ON$
    }
  }

  override def lastSequenceNr(id: Identifier): Future[Long] = {
    logger.debug("Fetching the last sequence nr of '{}-{}'", name, id)
    val action = GetLastSeqNr(id)
    ref ? action recoverWith {
      // $COVERAGE-OFF$
      case _: AskTimeoutException =>
        logger.error("Timed out while fetching the last sequence nr of '{}-{}'", name, id)
        Future.failed(TimeoutError(id, action))
      case NonFatal(th) =>
        logger.error(th, "Unexpected exception while fetching the last sequence nr of '{}-{}'", name, id)
        Future.failed(UnknownError(id, action, th))
      // $COVERAGE-ON$
    } flatMap {
      case LastSeqNr(`id`, lsn) =>
        logger.debug("Retrieved the last sequence nr '{}' of '{}-{}'", lsn, name, id)
        Future.successful(lsn)
      // $COVERAGE-OFF$
      case unexpected =>
        logger.error("Received an unexpected reply '{}' while fetching the last sequence nr of '{}-{}'",
                     unexpected,
                     name,
                     id)
        Future.failed(UnexpectedReply(id, action, unexpected))
      // $COVERAGE-ON$
    }
  }

  override def foldLeft[B](id: Identifier, z: B)(f: (B, Event) => B): Future[B] = {
    logger.debug("Folding over the event stream of '{}-{}'", name, id)
    pq.currentEventsByPersistenceId(id, 0L, Long.MaxValue).runFold(z) {
      case (acc, envelope) =>
        Event.cast(envelope.event) match {
          case Some(event) =>
            logger.debug("Replayed event on '{}-{}', sequence nr '{}'", name, id, envelope.sequenceNr)
            f(acc, event)
          // $COVERAGE-OFF$
          case None =>
            logger.error(
              "Received unexpected event while replaying for '{}-{}', was '{}', but expected type '{}'; skipping",
              name,
              id,
              envelope.event,
              Event.describe)
            acc
          // $COVERAGE-ON$
        }
    }
  }

  override def currentState(id: Identifier): Future[State] = {
    logger.debug("Retrieving the current state of '{}-{}'", name, id)
    val action = GetCurrentState(id)
    ref ? action recoverWith {
      // $COVERAGE-OFF$
      case _: AskTimeoutException =>
        logger.error("Timed out while fetching the current state of '{}-{}'", name, id)
        Future.failed(TimeoutError(id, action))
      case NonFatal(th) =>
        logger.error(th, "Unexpected exception while fetching the current state of '{}-{}'", name, id)
        Future.failed(UnknownError(id, action, th))
      // $COVERAGE-ON$
    } flatMap {
      case CurrentState(`id`, state) =>
        State.cast(state) match {
          case Some(st) =>
            logger.debug("Retrieved the current state '{}' of '{}-{}'", st, name, id)
            Future.successful(st)
          // $COVERAGE-OFF$
          case None =>
            logger.error("Received an unknown current state '{}' from '{}-{}'", state, name, id)
            Future.failed(TypeError(id, State.describe, state))
          // $COVERAGE-ON$
        }
      // $COVERAGE-OFF$
      case unexpected =>
        logger.error("Received an unexpected reply '{}' while fetching the current state of '{}-{}'",
                     unexpected,
                     name,
                     id)
        Future.failed(UnexpectedReply(id, action, unexpected))
      // $COVERAGE-ON$
    }
  }

  override def eval(id: Identifier, cmd: Command): Future[Either[Rejection, State]] = {
    logger.debug("Evaluating command '{}' on '{}-{}'", cmd, name, id)
    val action = Eval(id, cmd)
    ref ? action recoverWith {
      // $COVERAGE-OFF$
      case _: AskTimeoutException =>
        logger.error("Timed out while evaluating command '{}' on '{}-{}'", cmd, name, id)
        Future.failed(TimeoutError(id, action))
      case NonFatal(th) =>
        logger.error(th, "Unexpected exception while evaluating command '{}' on '{}-{}'", cmd, name, id)
        Future.failed(UnknownError(id, action, th))
      // $COVERAGE-ON$
    } flatMap {
      case Evaluated(`id`, Left(rejection)) =>
        Rejection.cast(rejection) match {
          case Some(rej) =>
            logger.debug("Rejected command '{}' due to '{}' on '{}-{}'", cmd, rejection, name, id)
            Future.successful(Left(rej))
          // $COVERAGE-OFF$
          case None =>
            logger.error("Received an unknown rejection '{}' from '{}-{}'", rejection, name, id)
            Future.failed(TypeError(id, Rejection.describe, rejection))
          // $COVERAGE-ON$
        }
      case Evaluated(`id`, Right(state)) =>
        State.cast(state) match {
          case Some(st) =>
            logger.debug("Accepted command '{}' resulting in '{}' on '{}-{}'", cmd, st, name, id)
            Future.successful(Right(st))
          // $COVERAGE-OFF$
          case None =>
            logger.error("Received an unknown current state '{}' from '{}-{}'", state, name, id)
            Future.failed(TypeError(id, State.describe, state))
          // $COVERAGE-ON$
        }
      // $COVERAGE-OFF$
      case unexpected =>
        logger.error("Received an unexpected reply '{}' while evaluating command '{}' '{}-{}'",
                     unexpected,
                     cmd,
                     name,
                     id)
        Future.failed(UnexpectedReply(id, action, unexpected))
      // $COVERAGE-ON$
    }
  }
}

object ShardingAggregate {

  private[akka] def shardExtractor(shards: Int): ExtractShardId = {
    case msg: Msg => math.abs(msg.id.hashCode) % shards toString
  }

  private[akka] val entityExtractor: ExtractEntityId = {
    case msg: Msg => (msg.id, msg)
  }

  /**
    * Constructs a new sharded aggregate while keeping track of the event, state and command types defined.
    *
    * @param name     the name (or type) of the aggregate
    * @param settings instructions on how to configure the aggregate
    * @param initial  the initial state
    * @param next     function to compute the next state considering a current state and a new event
    * @param eval     function to evaluate a new command considering a current state
    * @param as       the underlying actor system
    * @param mt       the underlying materializer
    * @tparam Event   the type of events supported by this aggregate
    * @tparam State   the type of state maintained by this aggregate
    * @tparam Command the type of commands considered by this aggregate
    * @tparam Rejection the type of rejection returned by this aggregate
    * @return a new aggregate instance
    */
  final def apply[Event: Typeable, State: Typeable, Command: Typeable, Rejection: Typeable](
      name: String,
      settings: SourcingAkkaSettings)(initial: State,
                                      next: (State, Event) => State,
                                      eval: (State, Command) => Either[Rejection, Event])(
      implicit as: ActorSystem,
      mt: Materializer): ShardingAggregate[Event, State, Command, Rejection] = {

    implicit val ec = as.dispatcher
    implicit val tm = Timeout(settings.askTimeout)

    val logger = Logging(as, s"ShardingAggregate($name)")
    val props = Props[AggregateActor[Event, State, Command, Rejection]](
      new AggregateActor(initial, next, eval, settings.passivationTimeout))
    val clusterShardingSettings = settings.shardingSettingsOrDefault(as)
    val pq                      = PersistenceQuery(as).readJournalFor[CurrentEventsByPersistenceIdQuery](settings.journalPluginId)

    val ref = ClusterSharding(as)
      .start(name, props, clusterShardingSettings, entityExtractor, shardExtractor(settings.shards))

    new ShardingAggregate(name, ref, pq, logger)
  }
}
