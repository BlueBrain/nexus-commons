package ch.epfl.bluebrain.nexus.service.commons.persistence

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.pattern.pipe
import akka.persistence.query.scaladsl.EventsByTagQuery
import akka.persistence.query.{EventEnvelope, Offset, PersistenceQuery}
import akka.stream.scaladsl.{Flow, Keep, RestartFlow, RunnableGraph, Sink}
import akka.stream.{ActorMaterializer, KillSwitches, UniqueKillSwitch}
import ch.epfl.bluebrain.nexus.common.types.Err
import ch.epfl.bluebrain.nexus.service.commons.persistence.SequentialIndexer.{NonRetriableErr, Stop}
import io.circe.Encoder
import shapeless.Typeable

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
  * Generic tag indexer that uses the specified resumable projection to iterate over the collection of events selected
  * via the specified tag and apply the argument indexing function.  It should be started as a singleton actor in a
  * clustered deployment.  If the event type is not compatible with the events deserialized from the persistence store
  * the events are skipped.
  *
  * @param init         an initialization function that is run before the indexer is (re)started
  * @param index        the indexing function
  * @param id           the id of the resumable projection and the skipped log to use
  * @param pluginId     the persistence query plugin id
  * @param tag          the tag to use while selecting the events from the store
  * @param T            a Typeable instance for the event type T
  * @tparam T the event type
  */
class SequentialTagIndexer[T](init: () => Future[Unit],
                              index: T => Future[Unit],
                              id: String,
                              pluginId: String,
                              tag: String)(implicit T: Typeable[T], E: Encoder[T])
    extends Actor
    with ActorLogging {

  private implicit val as: ActorSystem       = context.system
  private implicit val ec: ExecutionContext  = context.dispatcher
  private implicit val mt: ActorMaterializer = ActorMaterializer()

  private val projection = ResumableProjection(id)
  private val skippedLog = SkippedEventLog(id)
  private val query      = PersistenceQuery(context.system).readJournalFor[EventsByTagQuery](pluginId)

  private def initialize(): Unit = {
    val _ = init().flatMap(_ => projection.fetchLatestOffset) pipeTo self
  }

  override def preStart(): Unit = {
    super.preStart()
    initialize()
  }

  private val indexFlow = RestartFlow.withBackoff(1 second, 30 seconds, 0.2) { () =>
    Flow[EventEnvelope].mapAsync(1) {
      case EventEnvelope(off, persistenceId, sequenceNr, event) =>
        log.info("Processing event for persistence id '{}', seqNr '{}'", persistenceId, sequenceNr)
        T.cast(event) match {
          case Some(value) =>
            index(value)
              .recoverWith {
                case _: NonRetriableErr =>
                  skippedLog.storeEvent(value)
                  Future.successful(off)
              }
              .map(_ => off)

          case None =>
            log.debug(s"Event not compatible with type '${T.describe}, skipping...'")
            Future.successful(off)
        }
    }
  }

  private val storeOffsetFlow = RestartFlow.withBackoff(1 second, 30 seconds, 0.2) { () =>
    Flow[Offset].mapAsync(1)(offset => projection.storeLatestOffset(offset))
  }

  private def buildStream(offset: Offset): RunnableGraph[(UniqueKillSwitch, Future[Done])] =
    query
      .eventsByTag(tag, offset)
      .viaMat(KillSwitches.single)(Keep.right)
      .via(indexFlow)
      .via(storeOffsetFlow)
      .toMat(Sink.ignore)(Keep.both)

  override def receive: Receive = {
    case offset: Offset =>
      log.info("Received initial offset, running the indexing function across the event stream")
      val (killSwitch, doneFuture) = buildStream(offset).run()
      doneFuture pipeTo self
      context.become(running(killSwitch))
    // $COVERAGE-OFF$
    case Stop =>
      log.info("Received stop signal while waiting for offset, stopping")
      context.stop(self)
    // $COVERAGE-ON$
  }

  private def running(killSwitch: UniqueKillSwitch): Receive = {
    case Done =>
      log.error("Stream finished unexpectedly, restarting")
      killSwitch.shutdown()
      initialize()
      context.become(receive)
    case Stop =>
      log.info("Received stop signal, stopping stream")
      killSwitch.shutdown()
      context.become(stopping)
  }

  private def stopping: Receive = {
    case Done =>
      log.info("Stream finished, stopping")
      context.stop(self)
  }
}

object SequentialIndexer {

  final case object Stop

  /**
    * Signals an error which is not going to be stored but not retried.
    *
    * @param th the underlying error
    */
  final case class NonRetriableErr(th: Throwable) extends Err(s"Non retriable error '${th.getMessage}'")

  // $COVERAGE-OFF$
  final def props[T: Typeable](init: () => Future[Unit],
                               index: T => Future[Unit],
                               id: String,
                               pluginId: String,
                               tag: String)(implicit as: ActorSystem, E: Encoder[T]): Props =
    ClusterSingletonManager.props(Props(new SequentialTagIndexer[T](init, index, id, pluginId, tag)),
                                  terminationMessage = Stop,
                                  settings = ClusterSingletonManagerSettings(as))

  final def start[T: Typeable](init: () => Future[Unit],
                               index: T => Future[Unit],
                               id: String,
                               pluginId: String,
                               tag: String,
                               name: String)(implicit as: ActorSystem, E: Encoder[T]): ActorRef =
    as.actorOf(props[T](init, index, id, pluginId, tag), name)

  final def start[T: Typeable](index: T => Future[Unit], id: String, pluginId: String, tag: String, name: String)(
      implicit as: ActorSystem,
      E: Encoder[T]): ActorRef =
    start(() => Future.successful(()), index, id, pluginId, tag, name)

  // $COVERAGE-ON$
}
