package ch.epfl.bluebrain.nexus.service.commons.persistence

import akka.actor.ActorSystem
import io.circe.{Decoder, Encoder}

import scala.concurrent.Future

trait SkippedEventLog {

  /**
    * @return an unique identifier for this skipped log
    */
  def identifier: String

  /**
    * Records the skipped event against this skipped log.
    *
    * @param event the event to record
    * @tparam T the generic type of the ''event''
    * @return a future () value upon success or a failure otherwise
    */
  def storeEvent[T](event: T)(implicit E: Encoder[T]): Future[Unit]

  /**
    * Retrieve the events for this skipped log.
    *
    * @tparam T the generic type of the returned ''event''s
    */
  def fetchEvents[T](implicit D: Decoder[T]): Future[Seq[T]]

}

object SkippedEventLog {
  private[persistence] def apply(id: String, storage: SkippedLogStorage): SkippedEventLog = new SkippedEventLog {

    override val identifier = id

    override def storeEvent[T](event: T)(implicit E: Encoder[T]) =
      storage.storeEvent(identifier, event)

    override def fetchEvents[T](implicit D: Decoder[T]) = storage.fetchEvents(identifier)
  }

  /**
    * Constructs a new `SkippedEventLog` instance with the specified identifier.  Calls to store or query the
    * current event are delegated to the underlying
    * [[ch.epfl.bluebrain.nexus.service.commons.persistence.SkippedLogStorage]] extension.
    *
    * @param id an identifier for the skipped log
    * @param as an implicitly available actor system
    */
  final def apply(id: String)(implicit as: ActorSystem): SkippedEventLog =
    apply(id, SkippedLogStorage(as))
}
