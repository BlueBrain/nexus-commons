package ch.epfl.bluebrain.nexus.service.commons.persistence

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Scheduler}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.pattern.pipe
import akka.persistence.query.scaladsl.EventsByTagQuery
import akka.persistence.query.{EventEnvelope, Offset, PersistenceQuery}
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink}
import akka.stream.{ActorMaterializer, KillSwitches, UniqueKillSwitch}
import ch.epfl.bluebrain.nexus.service.commons.persistence.SequentialIndexer.Stop
import ch.epfl.bluebrain.nexus.service.commons.retrying.{CoproductRetryer, Retryer}
import shapeless._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Generic tag indexer that uses the specified resumable projection to iterate over the collection of events selected
  * via the specified tag and apply the argument indexing function.  It should be started as a singleton actor in a
  * clustered deployment.  If the event type is not compatible with the events deserialized from the persistence store
  * the events are skipped.
  *
  * @param init         an initialization function that is run before the indexer is (re)started
  * @param index        the indexing function
  * @param retries      the amount of times ''index'' will be retried when failed if the failure is one of the types in ''C''
  * @param projectionId the id of the resumable projection to use
  * @param pluginId     the persistence query plugin id
  * @param tag          the tag to use while selecting the events from the store
  * @param T            a Typeable instance for the event type T
  * @param C            an implicitly available instance for retryer of type C
  * @tparam T the event type
  * @tparam C the generic coproduct type of the retryer
  */
class SequentialTagIndexer[T, C <: Coproduct](init: () => Future[Unit],
                                              index: T => Future[Unit],
                                              retries: Int,
                                              projectionId: String,
                                              pluginId: String,
                                              tag: String)(implicit T: Typeable[T], C: CoproductRetryer[C])
    extends Actor
    with ActorLogging {

  private implicit val as: ActorSystem       = context.system
  private implicit val ec: ExecutionContext  = context.dispatcher
  private implicit val mt: ActorMaterializer = ActorMaterializer()
  private implicit val s: Scheduler          = as.scheduler

  private val retryer    = Retryer[C]
  private val projection = ResumableProjection(projectionId)
  private val query      = PersistenceQuery(context.system).readJournalFor[EventsByTagQuery](pluginId)

  private def initialize(): Unit = {
    val _ = init().flatMap(_ => projection.fetchLatestOffset) pipeTo self
  }

  override def preStart(): Unit = {
    super.preStart()
    initialize()
  }

  private def buildStream(offset: Offset): RunnableGraph[(UniqueKillSwitch, Future[Done])] =
    query
      .eventsByTag(tag, offset)
      .viaMat(KillSwitches.single)(Keep.right)
      .mapAsync(1) {
        case EventEnvelope(off, persistenceId, sequenceNr, event) =>
          log.debug("Processing event for persistence id '{}', seqNr '{}'", persistenceId, sequenceNr)
          T.cast(event) match {
            case Some(value) =>
              retryer
                .apply[Unit](() => index(value), retries)
                .map(_ => off)
                .recoverWith {
                  case err =>
                    self ! Stop
                    Future.failed(err)
                }
            case None =>
              log.debug(s"Event not compatible with type '${T.describe}, skipping...'")
              Future.successful(off)
          }
      }
      .mapAsync(1)(offset => projection.storeLatestOffset(offset))
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

  // $COVERAGE-OFF$
  final def props[T: Typeable, C <: Coproduct](init: () => Future[Unit],
                                               index: T => Future[Unit],
                                               retries: Int,
                                               projectionId: String,
                                               pluginId: String,
                                               tag: String)(implicit as: ActorSystem, C: CoproductRetryer[C]): Props =
    ClusterSingletonManager.props(
      Props(new SequentialTagIndexer[T, C](init, index, retries, projectionId, pluginId, tag)),
      terminationMessage = Stop,
      settings = ClusterSingletonManagerSettings(as)
    )

  final def start[T: Typeable, C <: Coproduct](
      init: () => Future[Unit],
      index: T => Future[Unit],
      retries: Int,
      projectionId: String,
      pluginId: String,
      tag: String,
      name: String)(implicit as: ActorSystem, C: CoproductRetryer[C]): ActorRef =
    as.actorOf(props[T, C](init, index, retries, projectionId, pluginId, tag), name)

  final def start[T: Typeable](init: () => Future[Unit],
                               index: T => Future[Unit],
                               projectionId: String,
                               pluginId: String,
                               tag: String,
                               name: String)(implicit as: ActorSystem): ActorRef =
    as.actorOf(props[T, CNil](init, index, 0, projectionId, pluginId, tag), name)

  final def start[T: Typeable, C <: Coproduct](
      index: T => Future[Unit],
      retries: Int,
      projectionId: String,
      pluginId: String,
      tag: String,
      name: String)(implicit as: ActorSystem, C: CoproductRetryer[C]): ActorRef =
    start[T, C](() => Future.successful(()), index, retries, projectionId, pluginId, tag, name)

  final def start[T: Typeable](index: T => Future[Unit],
                               projectionId: String,
                               pluginId: String,
                               tag: String,
                               name: String)(implicit as: ActorSystem): ActorRef =
    start[T](() => Future.successful(()), index, projectionId, pluginId, tag, name)

  // $COVERAGE-ON$
}
