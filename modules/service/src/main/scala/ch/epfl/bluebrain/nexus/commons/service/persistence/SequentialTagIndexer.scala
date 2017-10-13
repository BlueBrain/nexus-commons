package ch.epfl.bluebrain.nexus.commons.service.persistence

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.event.Logging
import akka.persistence.query.scaladsl.EventsByTagQuery
import akka.persistence.query.{EventEnvelope, Offset, PersistenceQuery}
import akka.stream.scaladsl.Source
import ch.epfl.bluebrain.nexus.commons.service.stream.SingletonStreamCoordinator
import shapeless.Typeable

import scala.concurrent.Future

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

  private[persistence] def source[T](index: T => Future[Unit], projectionId: String, pluginId: String, tag: String)(
      implicit as: ActorSystem,
      T: Typeable[T]): Offset => Source[Unit, NotUsed] = {
    import as.dispatcher
    val log        = Logging(as, SequentialTagIndexer.getClass)
    val projection = ResumableProjection(projectionId)

    (offset: Offset) =>
      PersistenceQuery(as)
        .readJournalFor[EventsByTagQuery](pluginId)
        .eventsByTag(tag, offset)
        .mapAsync(1) {
          case EventEnvelope(off, persistenceId, sequenceNr, event) =>
            log.debug("Processing event for persistence id '{}', seqNr '{}'", persistenceId, sequenceNr)
            T.cast(event) match {
              case Some(value) =>
                index(value).map(_ => off)
              case None =>
                log.debug(s"Event not compatible with type '${T.describe}, skipping...'")
                Future.successful(off)
            }
        }
        .mapAsync(1)(offset => projection.storeLatestOffset(offset))
  }

  /**
    * Generic tag indexer that uses the specified resumable projection to iterate over the collection of events selected
    * via the specified tag and apply the argument indexing function.  It starts as a singleton actor in a
    * clustered deployment.  If the event type is not compatible with the events deserialized from the persistence store
    * the events are skipped.
    *
    * @param init         an initialization function that is run before the indexer is (re)started
    * @param index        the indexing function
    * @param projectionId the id of the resumable projection to use
    * @param pluginId     the persistence query plugin id
    * @param tag          the tag to use while selecting the events from the store
    * @param name         the name of this indexer
    * @param as           an implicitly available actor system
    * @param T            a Typeable instance for the event type T
    * @tparam T the event type
    */
  // $COVERAGE-OFF$
  final def start[T](init: () => Future[Unit],
                     index: T => Future[Unit],
                     projectionId: String,
                     pluginId: String,
                     tag: String,
                     name: String)(implicit as: ActorSystem, T: Typeable[T]): ActorRef =
    SingletonStreamCoordinator.start(initialize(init, pluginId), source(index, projectionId, pluginId, tag), name)

  /**
    * Generic tag indexer that uses the specified resumable projection to iterate over the collection of events selected
    * via the specified tag and apply the argument indexing function.  It starts as a singleton actor in a
    * clustered deployment.  If the event type is not compatible with the events deserialized from the persistence store
    * the events are skipped.
    *
    * @param index        the indexing function
    * @param projectionId the id of the resumable projection to use
    * @param pluginId     the persistence query plugin id
    * @param tag          the tag to use while selecting the events from the store
    * @param name         the name of this indexer
    * @param as           an implicitly available actor system
    * @param T            a Typeable instance for the event type T
    * @tparam T the event type
    */
  final def start[T](index: T => Future[Unit], projectionId: String, pluginId: String, tag: String, name: String)(
      implicit as: ActorSystem,
      T: Typeable[T]): ActorRef =
    start(() => Future.successful(()), index, projectionId, pluginId, tag, name)
  // $COVERAGE-ON$
}
