package ch.epfl.bluebrain.nexus.sourcing.akka

import akka.actor.ActorSystem
import akka.cluster.sharding.ClusterShardingSettings

import scala.concurrent.duration._

/**
  * Data type to represent general settings for the akka based sourcing implementation.
  *
  * @param journalPluginId    the plugin id for streaming events from the persistent store
  * @param shardingSettings   optional cluster sharding settings
  * @param shards             the number of shards to create for an aggregate
  * @param passivationTimeout the inactivity timeout after which an actor will request to be stopped
  * @param askTimeout         the future completion timeout while interacting with a persistent actor
  */
final case class SourcingAkkaSettings(journalPluginId: String,
                                      shardingSettings: Option[ClusterShardingSettings] = None,
                                      shards: Int = 100,
                                      passivationTimeout: FiniteDuration = 30 seconds,
                                      askTimeout: FiniteDuration = 15 seconds) {

  /**
    * Computes a ClusterShardingSettings value.
    *
    * @param as an actor system
    * @return the defined value if it exists, of the defaults configured on the argument actor system
    */
  def shardingSettingsOrDefault(as: ActorSystem): ClusterShardingSettings =
    shardingSettings.getOrElse(ClusterShardingSettings(as))
}
