package ch.epfl.bluebrain.nexus.commons.service.persistence

import java.util.concurrent.TimeUnit._

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.event.Logging
import akka.persistence.query.scaladsl.EventsByTagQuery
import akka.persistence.query.{EventEnvelope, Offset, PersistenceQuery}
import akka.stream.scaladsl.Source
import ch.epfl.bluebrain.nexus.commons.service.retryer.RetryStrategy
import ch.epfl.bluebrain.nexus.commons.service.retryer.RetryStrategy.Backoff
import ch.epfl.bluebrain.nexus.commons.service.retryer.RetryOps._
import ch.epfl.bluebrain.nexus.commons.service.stream.SingletonStreamCoordinator
import io.circe.Encoder
import shapeless.Typeable

import scala.concurrent.Future
import scala.concurrent.duration.Duration

/**
  * Generic tag indexer that uses the specified resumable projection to iterate over the collection of events selected
  * via the specified tag and apply the argument indexing function.  It starts as a singleton actor in a
  * clustered deployment.  If the event type is not compatible with the events deserialized from the persistence store
  * the events are skipped.
  */
object SequentialTagIndexer {

  private[persistence] def initialize(underlying: () => Future[Unit], projectionId: String)(
      implicit as: ActorSystem): () => Future[Offset] = {
    import as.dispatcher
    val projection = ResumableProjection(projectionId)
    () =>
      underlying().flatMap(_ => projection.fetchLatestOffset)
  }

  private[persistence] def source[T](index: T => Future[Unit], id: String, pluginId: String, tag: String)(
      implicit as: ActorSystem,
      T: Typeable[T],
      E: Encoder[T]): Offset => Source[Unit, NotUsed] = {
    import as.dispatcher
    val log        = Logging(as, SequentialTagIndexer.getClass)
    val projection = ResumableProjection(id)
    val failureLog = IndexFailuresLog(id)

    implicit val (retries, backoff) = lookupRetriesConfig
    (offset: Offset) =>
      PersistenceQuery(as)
        .readJournalFor[EventsByTagQuery](pluginId)
        .eventsByTag(tag, offset)
        .mapAsync(1) {
          case EventEnvelope(off, persistenceId, sequenceNr, event) =>
            log.debug("Processing event for persistence id '{}', seqNr '{}'", persistenceId, sequenceNr)
            T.cast(event) match {
              case Some(value) =>
                (() => index(value))
                  .retry(retries)
                  .recoverWith { case _ => failureLog.storeEvent(persistenceId, off, value) }
                  .map(_ => off)
              case None =>
                log.debug(s"Event not compatible with type '${T.describe}, skipping...'")
                Future.successful(off)
            }
        }
        .mapAsync(1)(offset => projection.storeLatestOffset(offset))
  }
  private def lookupRetriesConfig(implicit as: ActorSystem): (Int, RetryStrategy) = {
    val config  = as.settings.config.getConfig("indexing.retry")
    val retries = config.getInt("max-count")
    val backoff =
      Backoff(Duration(config.getDuration("max-duration", SECONDS), SECONDS), config.getDouble("random-factor"))
    (retries, backoff)
  }

  /**
    * Generic tag indexer that uses the specified resumable projection to iterate over the collection of events selected
    * via the specified tag and apply the argument indexing function.  It starts as a singleton actor in a
    * clustered deployment.  If the event type is not compatible with the events deserialized from the persistence store
    * the events are skipped.
    *
    * @param init     an initialization function that is run before the indexer is (re)started
    * @param index    the indexing function
    * @param id       the id of the resumable projection and indexing log to use
    * @param pluginId the persistence query plugin id
    * @param tag      the tag to use while selecting the events from the store
    * @param name     the name of this indexer
    * @param as       an implicitly available actor system
    * @param T        a Typeable instance for the event type T
    * @param E        an implicitly available [[Encoder]] for T
    * @tparam T the event type
    */
  // $COVERAGE-OFF$
  final def start[T](init: () => Future[Unit],
                     index: T => Future[Unit],
                     id: String,
                     pluginId: String,
                     tag: String,
                     name: String)(implicit as: ActorSystem, T: Typeable[T], E: Encoder[T]): ActorRef =
    SingletonStreamCoordinator.start(initialize(init, pluginId), source(index, id, pluginId, tag), name)

  /**
    * Generic tag indexer that uses the specified resumable projection to iterate over the collection of events selected
    * via the specified tag and apply the argument indexing function.  It starts as a singleton actor in a
    * clustered deployment.  If the event type is not compatible with the events deserialized from the persistence store
    * the events are skipped.
    *
    * @param index    the indexing function
    * @param id       the id of the resumable projection and indexing log to use
    * @param pluginId the persistence query plugin id
    * @param tag      the tag to use while selecting the events from the store
    * @param name     the name of this indexer
    * @param as       an implicitly available actor system
    * @param T        a Typeable instance for the event type T
    * @param E        an implicitly available [[Encoder]] for T
    * @tparam T the event type
    */
  final def start[T](index: T => Future[Unit], id: String, pluginId: String, tag: String, name: String)(
      implicit as: ActorSystem,
      T: Typeable[T],
      E: Encoder[T]): ActorRef =
    start(() => Future.successful(()), index, id, pluginId, tag, name)
  // $COVERAGE-ON$
}
