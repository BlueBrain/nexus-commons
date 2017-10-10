package ch.epfl.bluebrain.nexus.commons.service.persistence

import akka.Done
import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.event.Logging
import akka.persistence.cassandra.CassandraPluginConfig
import akka.persistence.cassandra.session.scaladsl.CassandraSession
import akka.persistence.query.{NoOffset, Offset}
import com.datastax.driver.core.Session
import com.typesafe.config.Config

import scala.concurrent.{ExecutionContext, Future}

/**
  * Contract defining interface for the projection storage that allows storing an offset against a projection identifier
  * and querying the last known offset for an identifier.
  */
trait ProjectionStorage {

  /**
    * Records the specified offset against a projection identifier.
    *
    * @param identifier an unique identifier for a projection
    * @param offset     the offset to record
    * @return a future () value
    */
  def storeOffset(identifier: String, offset: Offset): Future[Unit]

  /**
    * Retrieves the last known offset for the specified projection identifier.  If there is no record of an offset
    * the [[akka.persistence.query.NoOffset]] is returned.
    *
    * @param identifier an unique identifier for a projection
    * @return a future offset value for the specified projection identifier
    */
  def fetchLatestOffset(identifier: String): Future[Offset]
}

/**
  * Cassandra backed [[ch.epfl.bluebrain.nexus.commons.service.persistence.ProjectionStorage]] implementation as an
  * Akka extension that piggybacks on Akka Persistence Cassandra for configuration and session management.
  *
  * @param session  a cassandra session
  * @param keyspace the keyspace under which the projection storage operates
  * @param table    the table where projection offsets are stored
  * @param ec       an implicitly available execution context
  */
final class CassandraProjectionStorage(session: CassandraSession, keyspace: String, table: String)(
    implicit ec: ExecutionContext)
    extends ProjectionStorage
    with Extension
    with OffsetCodec {
  import io.circe.parser._

  override def storeOffset(identifier: String, offset: Offset): Future[Unit] = {
    val stmt = s"update $keyspace.$table set offset = ? where identifier = ?"
    session.executeWrite(stmt, offsetEncoder(offset).noSpaces, identifier).map(_ => ())
  }

  override def fetchLatestOffset(identifier: String): Future[Offset] = {
    val stmt = s"select offset from $keyspace.$table where identifier = ?"
    session.selectOne(stmt, identifier).flatMap {
      case Some(row) => Future.fromTry(decode[Offset](row.getString("offset")).toTry)
      case None      => Future.successful(NoOffset)
    }
  }
}

object ProjectionStorage extends ExtensionId[CassandraProjectionStorage] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = ProjectionStorage

  override def createExtension(system: ExtendedActorSystem): CassandraProjectionStorage = {
    implicit val ec     = system.dispatcher
    val journalConfig   = lookupConfig(system)
    val projectionTable = journalConfig.getString("projection-table")
    val config          = new CassandraPluginConfig(system, journalConfig)
    val log             = Logging(system, "ProjectionStorage")

    val session = new CassandraSession(
      system,
      config.sessionProvider,
      config.sessionSettings,
      ec,
      log,
      metricsCategory = "projection-storage",
      init = session => executeCreateKeyspaceAndTable(session, config, projectionTable)
    )
    new CassandraProjectionStorage(session, config.keyspace, projectionTable)
  }

  private def createKeyspace(config: CassandraPluginConfig) = s"""
      CREATE KEYSPACE IF NOT EXISTS ${config.keyspace}
      WITH REPLICATION = { 'class' : ${config.replicationStrategy} }
    """

  private def createTable(config: CassandraPluginConfig, name: String) = s"""
      CREATE TABLE IF NOT EXISTS ${config.keyspace}.$name (
        identifier varchar primary key, offset text)
     """

  private def executeCreateKeyspaceAndTable(session: Session, config: CassandraPluginConfig, tableName: String)(
      implicit ec: ExecutionContext): Future[Done] = {
    import akka.persistence.cassandra.listenableFutureToFuture
    val keyspace: Future[Done] =
      if (config.keyspaceAutoCreate) session.executeAsync(createKeyspace(config)).map(_ => Done)
      else Future.successful(Done)

    if (config.tablesAutoCreate) {
      for {
        _    <- keyspace
        done <- session.executeAsync(createTable(config, tableName)).map(_ => Done)
      } yield done
    } else keyspace
  }

  private def lookupConfig(system: ExtendedActorSystem): Config =
    system.settings.config.getConfig("cassandra-journal")
}
