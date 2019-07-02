package ch.epfl.bluebrain.nexus.commons.downing

import akka.actor.{Actor, ActorLogging, ActorSystem, Cancellable, Props}
import akka.cluster.ClusterEvent._
import akka.cluster.{Cluster, DowningProvider, Member, MemberStatus, UniqueAddress}
import ch.epfl.bluebrain.nexus.commons.downing.config.{DowningConfig, Settings}

import scala.concurrent.duration._

class KeepOldestAkkaDowningProvider(system: ActorSystem) extends DowningProvider {
  private val config = Settings(system).downingConfig

  override val downRemovalMargin: FiniteDuration = config.downRemovalMargin

  override def downingActorProps: Option[Props] = Some(Props(new KeepOldestAkkaDowningActor(config)))
}

private[downing] case class MemberInfo(uniqueAddress: UniqueAddress, roles: Set[String], member: Member)
private[downing] case class ClusterState(upMembers: Set[MemberInfo], unreachable: Set[UniqueAddress]) {
  lazy val oldestRelevant = upMembers.toList.sortBy(_.member)(Member.ageOrdering).headOption
  lazy val upReachable    = upMembers.filterNot(x => unreachable(x.uniqueAddress))
}

private[downing] case class SplitDetected(state: ClusterState, downAll: Boolean)

private[downing] case class TimedCancellable(value: Cancellable, timestampMillis: Long) {
  def cancel(): Boolean       = value.cancel()
  def elapsed: FiniteDuration = (System.currentTimeMillis() - timestampMillis) millis
}

private[downing] class KeepOldestAkkaDowningActor(config: DowningConfig) extends Actor with ActorLogging {
  import context.dispatcher

  private val system              = context.system
  private val cluster             = Cluster.get(system)
  private var state: ClusterState = _

  // Scheduled timer to decide which members will survive and which will terminate after some members became unreachable
  private var splitDeciderTimer = Option.empty[TimedCancellable]
  // Accumulative time between the splitDeciderTimer started and it was stopped
  private var splitDeciderTimerStopAcc: FiniteDuration = 0 millis

  override def preStart(): Unit =
    cluster.subscribe(self, classOf[ClusterDomainEvent])

  override def postStop(): Unit = {
    super.postStop()
    cluster.unsubscribe(self)
    splitDeciderTimer.foreach(_.cancel())
  }

  override def receive: Actor.Receive = {
    case CurrentClusterState(members, unreachable, _, _, _) =>
      val upMembers = members.filter(_.status == MemberStatus.Up).map(m => MemberInfo(m.uniqueAddress, m.roles, m))
      state = ClusterState(upMembers, unreachable.map(_.uniqueAddress))
      triggerTerminatingTimer()
    case MemberUp(m) =>
      state = state.copy(upMembers = state.upMembers + MemberInfo(m.uniqueAddress, m.roles, m))
      triggerTerminatingTimer()
    case MemberLeft(m) =>
      state = state.copy(upMembers = state.upMembers.filterNot(_.uniqueAddress == m.uniqueAddress))
      triggerTerminatingTimer()
    case ReachableMember(m) =>
      state = state.copy(unreachable = state.unreachable - m.uniqueAddress)
      triggerTerminatingTimer()
    case UnreachableMember(m) =>
      state = state.copy(unreachable = state.unreachable + m.uniqueAddress)
      triggerTerminatingTimer()
    case SplitDetected(_, true) =>
      log.warning(
        "Network is unstable. After detecting several changes within a time range of '{}' all the nodes are being terminated. Terminating myself",
        config.computedDownAll
      )
      system.terminate()
      context.become(Actor.emptyBehavior)
    case SplitDetected(clusterState, _) if !survive(clusterState) =>
      log.error(
        "Network partition detected. I am not in the surviving partition. Terminating myself. Unreachable nodes: '{}'",
        cluster.state.unreachable
      )

      system.terminate()
      context.become(Actor.emptyBehavior)
    case SplitDetected(clusterState, _) if downingCoordinator(clusterState) =>
      log.warning(
        "Network partition detected. I am the downing coordinator node in the surviving partition. Terminating unreachable nodes '{}'",
        cluster.state.unreachable
      )
      cluster.state.unreachable.foreach(m => cluster.down(m.address))
    case _: SplitDetected =>
      log.info(
        "Network partition detected. I am in the surviving partition, but I am not the responsible node, so nothing needs to be done. Unreachable nodes: '{}'",
        cluster.state.unreachable
      )
  }

  private def triggerTerminatingTimer(): Unit = {
    val timeFromCancellation = splitDeciderTimer
      .map { t =>
        t.cancel()
        t.elapsed
      }
      .getOrElse(0 millis)

    splitDeciderTimer = None

    splitDeciderTimerStopAcc =
      if (timeFromCancellation > config.computeDownAllReset || state.unreachable.isEmpty) 0 millis
      else splitDeciderTimerStopAcc + timeFromCancellation

    if (splitDeciderTimerStopAcc > config.computedDownAll)
      self ! SplitDetected(state, downAll = true)
    else if (state.unreachable.nonEmpty) {
      val cancellable = system.scheduler.scheduleOnce(config.stableAfter, self, SplitDetected(state, downAll = false))
      splitDeciderTimer = Some(TimedCancellable(cancellable, System.currentTimeMillis()))
    }
  }

  private def downingCoordinator(state: ClusterState): Boolean =
    state.upReachable.map(_.member).toList.sorted.headOption.exists(_.uniqueAddress.address == cluster.selfAddress)

  /**
    * Survive implementation for KeepOldest.
    *
    * This strategy will down the part that does not contain the oldest member. The oldest member is interesting because the active Cluster Singleton instance is running on the oldest member.
    * There is one exception to this rule if down-if-alone is configured to on. Then, if the oldest node has partitioned from all other nodes the oldest will down itself and keep all other nodes running.
    * The strategy will not down the single oldest node when it is the only remaining node in the cluster.
    *
    * Note that if the oldest node crashes the others will remove it from the cluster when down-if-alone is on,
    * otherwise they will down themselves if the oldest node crashes, i.e. shut down the whole cluster together with the oldest node.
    *
    * @param state the current cluster state
    * @return true if the current member should survive. false if it should terminate
    */
  private def survive(state: ClusterState): Boolean =
    state.oldestRelevant match {
      case Some(oldest) =>
        if (config.downIfAlone) {
          // format: off
          if (state.upReachable == Set(oldest)) false                       // Terminate when you are the oldest member and there are no unreachable nodes in the cluster
          else if (state.unreachable == Set(oldest.uniqueAddress)) true     // Survive when you are on the cluster where the only unreachable member is the oldest
          else !state.unreachable.contains(oldest.uniqueAddress)            // Survive when you are on the cluster where the oldest member is reachable
        }
        else !state.unreachable.contains(oldest.uniqueAddress)              // Survive when your are on the cluster where the oldest member is reachable
      // format: on
      case None => false
    }
}
