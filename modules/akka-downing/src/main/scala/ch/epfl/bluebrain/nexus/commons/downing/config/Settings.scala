package ch.epfl.bluebrain.nexus.commons.downing.config

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.typesafe.config.Config
import pureconfig.generic.auto._
import pureconfig._

import scala.concurrent.duration._

/**
  * Akka settings extension to expose application configuration.  It typically uses the configuration instance of the
  * actor system as the configuration root.
  *
  * @param config the configuration instance to read
  */
@SuppressWarnings(Array("LooksLikeInterpolatedString", "OptionGet"))
class Settings(config: Config) extends Extension {

  val downingConfig: DowningConfig =
    ConfigSource.fromConfig(config).at("akka.custom-downing.keep-oldest").loadOrThrow[DowningConfig]
}

object Settings extends ExtensionId[Settings] with ExtensionIdProvider {

  override def lookup(): ExtensionId[_ <: Extension] = Settings

  override def createExtension(system: ExtendedActorSystem): Settings = apply(system.settings.config)

  def apply(config: Config): Settings = new Settings(config)
}

/**
  *
  * @param stableAfter            Time margin after which shards or singletons that belonged to a downed/removed
  *                               partition are created in surviving partition
  * @param downIfAlone            Flag to decide whether or not the oldest node should be killed when left alone in a cluster.
  *                               If set to true and the oldest node has partitioned from all other nodes the oldest will down itself and keep all other nodes running
  * @param downRemovalMargin      Time margin after which shards or singletons that belonged to a downed/removed
  *                               partition are created in surviving partition
  * @param downAllWhenUnstable    Time margin to decide downing of all cluster members. If there is instability on the cluster for more than downAllWhenUnstable + stableAfter, all the members of the cluster will be downed
  */
final case class DowningConfig(
    stableAfter: FiniteDuration,
    downIfAlone: Boolean,
    downRemovalMargin: FiniteDuration,
    downAllWhenUnstable: Option[FiniteDuration]
) {
  require(stableAfter > 0.seconds)
  require(downRemovalMargin > 0.seconds)
  downAllWhenUnstable.foreach(v => require(v > 0.seconds))
  val computedDownAll: Duration     = downAllWhenUnstable.getOrElse(stableAfter * 0.75) + stableAfter
  val computeDownAllReset: Duration = 2 * stableAfter
  require(computedDownAll < (2 * stableAfter))
}
