package ch.epfl.bluebrain.nexus.commons.service.persistence

import akka.actor.ActorSystem
import akka.persistence.query.Offset
import akka.stream.scaladsl.Source
import io.circe.{Decoder, Encoder}

import scala.concurrent.Future

trait IndexFailuresLog {

  /**
    * @return an unique identifier for this failures log
    */
  def identifier: String

  /**
    * Records the failed event against this failures log.
    *
    * @param offset the offset to record
    * @param event  the event to record
    * @tparam T the generic type of the ''event''
    * @return a future of [[Unit]] upon success or a failure otherwise
    */
  def storeEvent[T](offset: Offset, event: T)(implicit E: Encoder[T]): Future[Unit]

  /**
    * Retrieve the events for this failures log.
    *
    * @tparam T the generic type of the returned ''event''s
    */
  def fetchEvents[T](implicit D: Decoder[T]): Source[T, _]

  /**
    * Retrieve the events with it's offset for this failures log.
    *
    * @tparam T the generic type of the returned ''event''s
    */
  def fetchEventsWithOffset[T](implicit D: Decoder[T]): Source[(Offset, T), _]

}

object IndexFailuresLog {
  private[persistence] def apply(id: String, storage: IndexFailuresStorage): IndexFailuresLog = new IndexFailuresLog {

    override val identifier: String = id

    override def storeEvent[T](offset: Offset, event: T)(implicit E: Encoder[T]): Future[Unit] =
      storage.storeEvent(identifier, offset, event)

    override def fetchEvents[T](implicit D: Decoder[T]): Source[T, _] = storage.fetchEvents(identifier)

    override def fetchEventsWithOffset[T](implicit D: Decoder[T]): Source[(Offset, T), _] =
      storage.fetchEventsWithOffset(identifier)

  }

  /**
    * Constructs a new `IndexFailuresLog` instance with the specified identifier.
    * Calls to store or query the current event are delegated to the underlying
    * [[IndexFailuresStorage]] extension.
    *
    * @param id an identifier for the failures log
    * @param as an implicitly available actor system
    */
  final def apply(id: String)(implicit as: ActorSystem): IndexFailuresLog =
    apply(id, IndexFailuresStorage(as))
}
