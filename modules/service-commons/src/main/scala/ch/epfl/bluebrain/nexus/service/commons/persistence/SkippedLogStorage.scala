package ch.epfl.bluebrain.nexus.service.commons.persistence

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.persistence.cassandra.session.scaladsl.CassandraSession
import io.circe.{Decoder, Encoder}

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import io.circe.parser._

trait SkippedLogStorage {

  /**
    * Record a specific event against a skipped log identifier.
    *
    * @param identifier the unique identifier for the skipped log
    * @param event      the event to be recorded
    * @tparam T the generic type of the ''event''s
    */
  def storeEvent[T](identifier: String, event: T)(implicit E: Encoder[T]): Future[Unit]

  /**
    * Retrieve the events for the provided skipped log identifier.
    *
    * @param identifier the unique identifier for the skipped log
    * @tparam T the generic type of the returned ''event''s
    * @return a list of the skipped events on this identifier
    */
  def fetchEvents[T](identifier: String)(implicit D: Decoder[T]): Future[Seq[T]]
}

final class CassandraSkippedLogStorage(session: CassandraSession, keyspace: String, table: String)(implicit
                                                                                                   ec: ExecutionContext)
    extends SkippedLogStorage
    with Extension {

  override def storeEvent[T](identifier: String, event: T)(implicit E: Encoder[T]): Future[Unit] = {
    val stmt = s"insert into $keyspace.$table (identifier, event) VALUES (? , ?) IF NOT EXISTS"
    session.executeWrite(stmt, identifier, E(event).noSpaces).map(_ => ())
  }

  override def fetchEvents[T](identifier: String)(implicit D: Decoder[T]): Future[immutable.Seq[T]] = {
    val stmt = s"select event from $keyspace.$table where identifier = ?"
    session.selectAll(stmt, identifier).flatMap {
      case Nil =>
        Future.successful(List.empty[T])
      case seq =>
        Future.fromTry {
          Try {
            seq
              .map(row => decode[T](row.getString("event")))
              .map {
                case Right(evt) => evt
                case Left(err)  => throw err
              }
          }
        }
    }
  }
}

object SkippedLogStorage extends ExtensionId[CassandraSkippedLogStorage] with ExtensionIdProvider {

  override def lookup(): ExtensionId[_ <: Extension] = SkippedLogStorage

  override def createExtension(system: ExtendedActorSystem): CassandraSkippedLogStorage = {
    val (session, config, table) =
      CassandraStorageHelper("skippedLog", "identifier varchar, event text, PRIMARY KEY (identifier, event)", system)
    new CassandraSkippedLogStorage(session, config.keyspace, table)(system.dispatcher)
  }

}
