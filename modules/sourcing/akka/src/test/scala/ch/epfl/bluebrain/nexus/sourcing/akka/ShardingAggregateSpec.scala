package ch.epfl.bluebrain.nexus.sourcing.akka

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import ch.epfl.bluebrain.nexus.sourcing.FixturesAggregate.Command._
import ch.epfl.bluebrain.nexus.sourcing.FixturesAggregate.Event._
import ch.epfl.bluebrain.nexus.sourcing.FixturesAggregate.State._
import ch.epfl.bluebrain.nexus.sourcing.FixturesAggregate._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

class ShardingAggregateSpec
    extends TestKit(ActorSystem("ShardingAggregateSpec"))
    with WordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  override implicit val patienceConfig = PatienceConfig(3 seconds, 50 millis)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    Cluster(system).join(Cluster(system).selfAddress)
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  private implicit val mt = ActorMaterializer()
  private implicit val ec = system.dispatcher

  private val aggregate =
    ShardingAggregate("permission", SourcingAkkaSettings(journalPluginId = "inmemory-read-journal"))(initial,
                                                                                                     next,
                                                                                                     eval)

  "A ShardingAggregate" should {
    "return a 0 sequence number for an empty event log" in {
      aggregate.lastSequenceNr("unknown").futureValue shouldEqual 0L
    }
    "return a initial state for an empty event log" in {
      val id = genId
      aggregate.currentState(id).futureValue shouldEqual initial
    }
    "append an event to the log" in {
      val id = genId
      aggregate.currentState(id).futureValue shouldEqual initial
      aggregate.append(id, PermissionsWritten(own)).futureValue shouldEqual 1L
      aggregate.currentState(id).futureValue shouldEqual Current(own)

    }
    "retrieve the appended events from the log" in {
      val id = genId
      aggregate.append(id, PermissionsAppended(own)).futureValue
      aggregate.append(id, PermissionsAppended(read)).futureValue
      aggregate
        .foldLeft(id, List.empty[Event]) {
          case (acc, ev) => ev :: acc
        }
        .futureValue shouldEqual List(PermissionsAppended(read), PermissionsAppended(own))
    }

    "retrieve the appended events only from the log for the same type" in {
      val id = genId
      aggregate.append(id, PermissionsAppended(own)).futureValue
      aggregate.append(id, PermissionsAppended(read)).futureValue
      val anotherAggregate = ShardingAggregate(
        "permission2",
        SourcingAkkaSettings(journalPluginId = "inmemory-read-journal"))(initial, next, eval)
      anotherAggregate.append(id, PermissionsAppended(ownRead)).futureValue
      aggregate
        .foldLeft(id, List.empty[Event]) {
          case (acc, ev) => ev :: acc
        }
        .futureValue shouldEqual List(PermissionsAppended(read), PermissionsAppended(own))
    }

    "reject out of order commands" in {
      val id = genId
      aggregate.eval(id, DeletePermissions).futureValue match {
        case Left(_: Rejection) => ()
        case Right(_)           => fail("should have rejected deletion on initial state")
      }
    }
    "return the current computed state" in {
      val id = genId
      val returned = for {
        _      <- aggregate.eval(id, AppendPermissions(own))
        second <- aggregate.eval(id, AppendPermissions(read))
      } yield second
      returned.futureValue shouldEqual Right(Current(ownRead))
      aggregate.currentState(id).futureValue shouldEqual Current(ownRead)
    }

    "query over ids that are not url segment safe" in {
      val id       = s"/$genId/sub"
      val appended = PermissionsAppended(own)
      aggregate.append(id, appended).futureValue
      val returned = aggregate.foldLeft(id, Option.empty[Event]) {
        case (_, ev) => Some(ev)
      }
      returned.futureValue shouldEqual Some(appended)
    }
  }
}
