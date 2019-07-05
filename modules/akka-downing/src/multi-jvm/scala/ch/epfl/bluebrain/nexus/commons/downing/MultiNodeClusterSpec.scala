package ch.epfl.bluebrain.nexus.commons.downing

import akka.actor.{ActorSystem, Address, AddressFromURIString}
import akka.cluster.Member.addressOrdering
import akka.cluster._
import akka.http.scaladsl.model.HttpRequest
import akka.management.scaladsl.AkkaManagement
import akka.remote.DefaultFailureDetectorRegistry
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeSpec, MultiNodeSpecCallbacks}
import akka.remote.transport.ThrottlerTransportAdapter.Direction
import akka.stream.{ActorMaterializer, StreamTcpException}
import akka.testkit.TestEvent.Mute
import akka.testkit.{EventFilter, ImplicitSender}
import cats.implicits._
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.syntax._
import io.circe.generic.auto._
import io.circe.{Decoder, Encoder}
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpecLike}

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

/**
  * Keep oldest akka cluster downing multi node testing.
  * The testing setup is inspired by the code on the github repository https://github.com/arnohaase/simple-akka-downing from Arno Haase, which is licensed under Apache 2.0
  * Based on test code of Akka cluster itself
  */
abstract class MultiNodeClusterSpec(config: DowningConfig)
    extends MultiNodeSpec(config)
    with MultiNodeSpecCallbacks
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ImplicitSender
    with OptionValues {

  private implicit val ec  = system.dispatcher
  private implicit val mat = ActorMaterializer()
  private implicit val ul  = HttpClient.untyped[Future]
  private implicit val nodeDec: Decoder[Set[NodeMember]] =
    Decoder.instance(_.get[List[NodeMember]]("members").map(_.toSet))
  private val client: HttpClient[Future, Set[NodeMember]] = HttpClient.withUnmarshaller[Future, Set[NodeMember]]

  override def beforeAll(): Unit = multiNodeSpecBeforeAll()
  override def afterAll(): Unit  = multiNodeSpecAfterAll()

  def initialParticipants = roles.size

  def cluster: Cluster = Cluster(system)

  val cachedAddresses = {
    roles.map(r => r -> node(r).address).toMap
  }

  implicit def address(role: RoleName): Address = cachedAddresses(role)

  implicit val clusterOrdering: Ordering[RoleName] = (x: RoleName, y: RoleName) =>
    addressOrdering.compare(address(x), address(y))

  def assertLeader(nodesInCluster: RoleName*): Unit =
    if (nodesInCluster.contains(myself)) assertLeaderIn(nodesInCluster.to[immutable.Seq])

  def assertLeaderIn(nodesInCluster: immutable.Seq[RoleName]): Unit =
    if (nodesInCluster.contains(myself)) {
      nodesInCluster.length should not be 0
      val expectedLeader = roleOfLeader(nodesInCluster)
      val leader         = cluster.state.leader
      val isLeader       = leader.contains(cluster.selfAddress)
      assert(isLeader == isNode(expectedLeader),
             "expectedLeader [%s], got leader [%s], members [%s]".format(expectedLeader, leader, cluster.state.members))
      cluster.selfMember.status should (be(MemberStatus.Up) or be(MemberStatus.Leaving))
      ()
    }

  def roleOfLeader(nodesInCluster: immutable.Seq[RoleName] = roles): RoleName = {
    nodesInCluster.length should not be 0
    nodesInCluster.min
  }

  def awaitClusterUp(roles: RoleName*): Unit = {
    runOn(roles.head) {
      // make sure that the node-to-join is started before other join
      startClusterNode()
    }
    enterBarrier(roles.head.name + "-started")
    if (roles.tail.contains(myself)) {
      cluster.join(roles.head)
    }
    if (roles.contains(myself)) {
      AkkaManagement(system).start()
      awaitMembersUp(numberOfMembers = roles.length)
    }
    enterBarrier(roles.map(_.name).mkString("-") + "-joined")
    Thread.sleep(5000)
    enterBarrier("after-" + roles.map(_.name).mkString("-") + "-joined")
  }

  def httpPort(node: RoleName) = {
    val nodeNo = roles.indexOf(node)
    require(nodeNo > 0)
    8080 + nodeNo
  }

  def awaitMembersUp(numberOfMembers: Int,
                     canNotBePartOfMemberRing: Set[Address] = Set.empty,
                     timeout: FiniteDuration = 25.seconds): Unit = {
    within(timeout) {
      if (canNotBePartOfMemberRing.nonEmpty) // don't run this on an empty set
        awaitAssert(canNotBePartOfMemberRing foreach (a ⇒ cluster.state.members.map(_.address) should not contain a))
      awaitAssert(cluster.state.members.size should ===(numberOfMembers))
      awaitAssert(cluster.state.members.map(_.status) should ===(Set(MemberStatus.Up)))
      // clusterView.leader is updated by LeaderChanged, await that to be updated also
      val expectedLeader = cluster.state.members.collectFirst {
        case m if m.dataCenter == cluster.settings.SelfDataCenter ⇒ m.address
      }
      awaitAssert(cluster.state.leader should ===(expectedLeader))
      ()
    }
  }

  def startClusterNode(): Unit = {
    val _ = if (cluster.state.members.isEmpty) {
      cluster join myself
      awaitAssert(cluster.state.members.map(_.address) should contain(address(myself)))
    } else {
      cluster.selfMember
    }
    ()

  }

  def createNetworkPartition(side1: Seq[RoleName], side2: Seq[RoleName]): Unit = {
    Thread.sleep(5.seconds.toMillis)

    runOn(roles.head) {
      for (r1 <- side1; r2 <- side2) testConductor.blackhole(r1, r2, Direction.Both).await
    }

    runOn(side1: _*) {
      val _ = within(30.seconds) {
        awaitAssert { cluster.state.unreachable.size shouldBe side2.size }
      }
    }
    runOn(side2: _*) {
      val _ = within(30.seconds) {
        awaitAssert { cluster.state.unreachable.size shouldBe side1.size }
      }
    }
  }

  def healNetworkPartition(): Unit = {
    runOn(roles.head) {
      for (r1 <- roles; r2 <- roles) testConductor.passThrough(r1, r2, Direction.Both).await
    }
  }

  /**
    * Marks a node as available in the failure detector
    */
  def markNodeAsAvailable(address: Address): Unit =
    failureDetectorPuppet(address) foreach (_.markNodeAsAvailable())

  /**
    * Marks a node as unavailable in the failure detector
    */
  def markNodeAsUnavailable(address: Address): Unit = {
    // before marking it as unavailable there should be at least one heartbeat
    // to create the FailureDetectorPuppet in the FailureDetectorRegistry
    cluster.failureDetector.heartbeat(address)
    failureDetectorPuppet(address) foreach (_.markNodeAsUnavailable())
  }

  private def failureDetectorPuppet(address: Address): Option[FailureDetectorPuppet] = {
    cluster.failureDetector match {
      case reg: DefaultFailureDetectorRegistry[Address] =>
        // do reflection magic because 'failureDetector' is only visible from within "akka" and sub packages
        val failureDetectorMethod = reg.getClass.getMethods.find(_.getName == "failureDetector").get
        failureDetectorMethod.invoke(reg, address) match {
          case Some(p: FailureDetectorPuppet) => Some(p)
          case _                              => None
        }
      //          reg.failureDetector(address) collect { case p: FailureDetectorPuppet ⇒ p }
      case _ => None
    }
  }

  def muteLog(sys: ActorSystem = system): Unit = {
    if (!sys.log.isDebugEnabled) {
      Seq(
        ".*Cluster Node.* - registered cluster JMX MBean.*",
        ".*Cluster Node.* Welcome.*",
        ".*Cluster Node.* is JOINING.*",
        ".*Cluster Node.* Leader can .*",
        ".*Cluster Node.* Leader is moving node.*",
        ".*Cluster Node.* - is starting up.*",
        ".*Shutting down cluster Node.*",
        ".*Cluster node successfully shut down.*",
        ".*Ignoring received gossip .*",
        ".*Using a dedicated scheduler for cluster.*"
      ) foreach { s ⇒
        sys.eventStream.publish(Mute(EventFilter.info(pattern = s)))
      }

      muteDeadLetters(classOf[AnyRef])(sys)
    }
  }

  def muteMarkingAsUnreachable(sys: ActorSystem = system): Unit =
    if (!sys.log.isDebugEnabled)
      sys.eventStream.publish(Mute(EventFilter.error(pattern = ".*Marking.* as UNREACHABLE.*")))

  def muteMarkingAsReachable(sys: ActorSystem = system): Unit =
    if (!sys.log.isDebugEnabled)
      sys.eventStream.publish(Mute(EventFilter.info(pattern = ".*Marking.* as REACHABLE.*")))

  private def portToNode(port: Int) = roles.filter(address(_).port contains port).head

  def upNodesFor(node: RoleName) =
    client(HttpRequest(uri = s"http://127.0.0.1:${httpPort(node)}/cluster/members/"))
      .map(_.filter(_.status == Status.Up).map(n => portToNode(n.addr.port.value)))
      .recoverWith {
        case _: StreamTcpException => Future.successful(Set.empty[RoleName])
        case other                 => Future.failed(other)
      }
}

final case class NodeMember(addr: Address, status: Status)

object NodeMember {
  implicit val encAddressName: Encoder[Address] = Encoder.encodeString.contramap(_.toString)
  implicit val decAddressName: Decoder[Address] = Decoder.decodeString.emapTry(s => Try(AddressFromURIString(s)))
  implicit val nodeEnc: Encoder[NodeMember]     = Encoder.instance(_.addr.asJson)
  implicit val nodeDec: Decoder[NodeMember] = Decoder.instance { hc =>
    (hc.get[Address]("node"), hc.get[Status]("status")).mapN(NodeMember(_, _))
  }
}

import java.util.concurrent.atomic.AtomicReference

import akka.remote.FailureDetector
import ch.epfl.bluebrain.nexus.commons.downing.Status._
import io.circe.{Decoder, Encoder}

/**
  * User controllable "puppet" failure detector.
  */
class FailureDetectorPuppet extends FailureDetector {

  private val status: AtomicReference[Status] = new AtomicReference(Unknown)

  def markNodeAsUnavailable(): Unit =
    status.set(Down)

  def markNodeAsAvailable(): Unit =
    status.set(Up)

  override def isAvailable: Boolean =
    status.get match {
      case Unknown | Up ⇒ true
      case Down         ⇒ false
    }

  override def isMonitoring: Boolean =
    status.get != Unknown

  override def heartbeat(): Unit = {
    val _ = status.compareAndSet(Unknown, Up)
    ()
  }

}

sealed trait Status
object Status {

  implicit val encStatus: Encoder[Status] = Encoder.encodeString.contramap {
    case Up      => "Up"
    case Down    => "Down"
    case Unknown => "Unknown"
  }
  implicit val decStatus: Decoder[Status] = Decoder.decodeString.emap {
    case "Up"      => Right(Up)
    case "Down"    => Right(Down)
    case "Unknown" => Right(Unknown)
    case other     => Left(s"Not defined status '$other'")
  }

  final object Up      extends Status
  final object Down    extends Status
  final object Unknown extends Status

}
