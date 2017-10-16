package ch.epfl.bluebrain.nexus.commons.service.persistence

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.persistence.cassandra.session.scaladsl.CassandraSession
import akka.persistence.query.Offset
import akka.stream.scaladsl.Source
import io.circe.parser._
import io.circe.{Decoder, Encoder}

import scala.concurrent.{ExecutionContext, Future}

trait IndexFailuresStorage {

  /**
    * Record a specific event against a index failures log identifier.
    *
    * @param identifier the unique identifier for the index failures log
    * @param offset     the offset to record
    * @param event      the event to be recorded
    * @tparam T the generic type of the ''event''s
    */
  def storeEvent[T](identifier: String, offset: Offset, event: T)(implicit E: Encoder[T]): Future[Unit]

  /**
    * Retrieve the events for the provided index failures log identifier.
    *
    * @param identifier the unique identifier for the skipped log
    * @tparam T the generic type of the returned ''event''s
    * @return a list of the failed events on this identifier
    */
  def fetchEvents[T](identifier: String)(implicit D: Decoder[T]): Source[T, _]

  /**
    * Retrieve the events with offset for the provided index failures log identifier.
    *
    * @param identifier the unique identifier for the index failures log
    * @tparam T the generic type of the returned ''event''s
    * @return a list of the failed events with it's offset on this identifier
    */
  def fetchEventsWithOffset[T](identifier: String)(implicit D: Decoder[T]): Source[(Offset, T), _]
}

final class CassandraIndexFailuresStorage(session: CassandraSession, keyspace: String, table: String)(
    implicit
    ec: ExecutionContext)
    extends IndexFailuresStorage
    with Extension
    with OffsetCodec {

  override def storeEvent[T](identifier: String, offset: Offset, event: T)(implicit E: Encoder[T]): Future[Unit] = {
    val stmt = s"insert into $keyspace.$table (identifier, offset, event) VALUES (? , ?, ?) IF NOT EXISTS"
    session.executeWrite(stmt, identifier, offsetEncoder(offset).noSpaces, E(event).noSpaces).map(_ => ())
  }

  override def fetchEvents[T](identifier: String)(implicit D: Decoder[T]): Source[T, _] = {
    val stmt = s"select offset, event from $keyspace.$table where identifier = ?"
    session
      .select(stmt, identifier)
      .map(row => decode[T](row.getString("event")))
      .collect { case Right(evt) => evt }
  }

  override def fetchEventsWithOffset[T](identifier: String)(implicit D: Decoder[T]): Source[(Offset, T), _] = {
    val stmt = s"select offset, event from $keyspace.$table where identifier = ?"
    session
      .select(stmt, identifier)
      .map(row => decode[Offset](row.getString("offset")) -> decode[T](row.getString("event")))
      .collect { case (Right(offset), Right(evt)) => (offset, evt) }
  }
}

object IndexFailuresStorage extends ExtensionId[CassandraIndexFailuresStorage] with ExtensionIdProvider {

  override def lookup(): ExtensionId[_ <: Extension] = IndexFailuresStorage

  override def createExtension(system: ExtendedActorSystem): CassandraIndexFailuresStorage = {
    val (session, keyspace, table) =
      CassandraStorageHelper("index-failures",
                             "identifier varchar, offset text, event text, PRIMARY KEY (identifier, event)",
                             system)
    new CassandraIndexFailuresStorage(session, keyspace, table)(system.dispatcher)
  }

}
