package ch.epfl.bluebrain.nexus.service.commons.persistence

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

import akka.Done
import akka.cluster.Cluster
import akka.stream.ActorMaterializer
import akka.testkit.{TestActorRef, TestKit, TestKitBase}
import ch.epfl.bluebrain.nexus.common.types.{Err, RetriableErr}
import ch.epfl.bluebrain.nexus.service.commons.persistence.Fixture._
import ch.epfl.bluebrain.nexus.service.commons.persistence.SequentialIndexer.Stop
import ch.epfl.bluebrain.nexus.service.commons.persistence.SequentialTagIndexerSpec.{SomeError, SomeOtherError}
import ch.epfl.bluebrain.nexus.sourcing.akka.{ShardingAggregate, SourcingAkkaSettings}
import io.circe.generic.auto._
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, DoNotDiscover, Matchers, WordSpecLike}

import scala.concurrent.Future
import scala.concurrent.duration._

//noinspection TypeAnnotation
@DoNotDiscover
class SequentialTagIndexerSpec
    extends TestKitBase
    with WordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll
    with Eventually {

  implicit lazy val system = SystemBuilder.cluster("SequentialTagIndexerSpec")
  implicit val ec          = system.dispatcher
  implicit val mt          = ActorMaterializer()

  private val cluster = Cluster(system)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    cluster.join(cluster.selfAddress)
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(35 seconds, 1 second)

  "A SequentialIndexer" should {
    val pluginId         = "cassandra-query-journal"
    val sourcingSettings = SourcingAkkaSettings(journalPluginId = pluginId)

    def initFunction(init: AtomicLong): () => Future[Unit] =
      () => {
        init.incrementAndGet()
        Future.successful(())
      }

    "index existing events" in {
      val agg = ShardingAggregate("agg", sourcingSettings)(Fixture.initial, Fixture.next, Fixture.eval)
      agg.append("first", Fixture.Executed).futureValue

      val count = new AtomicLong(0L)
      val init  = new AtomicLong(10L)
      val index = (_: Event) =>
        Future.successful[Unit] {
          val _ = count.incrementAndGet()
      }
      val projId = UUID.randomUUID().toString

      val indexer =
        TestActorRef(new SequentialTagIndexer[Event](initFunction(init), index, 3, projId, pluginId, "executed"))

      eventually {
        count.get() shouldEqual 1L
        init.get shouldEqual 11L
      }

      watch(indexer)
      indexer ! Stop
      expectTerminated(indexer)
    }

    "select only the configured event types" in {
      val agg = ShardingAggregate("selected", sourcingSettings)(Fixture.initial, Fixture.next, Fixture.eval)
      agg.append("first", Fixture.Executed).futureValue
      agg.append("second", Fixture.Executed).futureValue
      agg.append("third", Fixture.Executed).futureValue
      agg.append("selected", Fixture.OtherExecuted).futureValue
      agg.append("selected", Fixture.OtherExecuted).futureValue

      val count = new AtomicLong(0L)
      val init  = new AtomicLong(10L)

      val index = (_: OtherExecuted.type) =>
        Future.successful[Unit] {
          val _ = count.incrementAndGet()
      }
      val projId = UUID.randomUUID().toString

      val indexer =
        TestActorRef(
          new SequentialTagIndexer[OtherExecuted.type](initFunction(init), index, 3, projId, pluginId, "other"))

      eventually {
        count.get() shouldEqual 2L
        init.get shouldEqual 11L
      }

      watch(indexer)
      indexer ! Stop
      expectTerminated(indexer)
    }

    "restart the indexing if the Done is emitted" in {
      val agg = ShardingAggregate("agg", sourcingSettings)(Fixture.initial, Fixture.next, Fixture.eval)
      agg.append("first", Fixture.AnotherExecuted).futureValue

      val count = new AtomicLong(0L)
      val init  = new AtomicLong(10L)
      val index = (_: Event) =>
        Future.successful[Unit] {
          val _ = count.incrementAndGet()
      }
      val projId = UUID.randomUUID().toString

      val indexer =
        TestActorRef(new SequentialTagIndexer[Event](initFunction(init), index, 3, projId, pluginId, "another"))

      eventually {
        count.get() shouldEqual 1L
        init.get shouldEqual 11L
      }

      indexer ! Done

      agg.append("second", Fixture.AnotherExecuted).futureValue

      eventually {
        count.get() shouldEqual 2L
        init.get shouldEqual 12L
      }

      watch(indexer)
      indexer ! Stop
      expectTerminated(indexer)
    }

    "retry when index function fails" in {
      val agg = ShardingAggregate("retry", sourcingSettings)(Fixture.initial, Fixture.next, Fixture.eval)
      agg.append("retry", Fixture.RetryExecuted).futureValue

      val count = new AtomicLong(0L)
      val init  = new AtomicLong(10L)

      val index  = (_: RetryExecuted.type) => Future.failed[Unit](SomeError(count.incrementAndGet()))
      val projId = UUID.randomUUID().toString

      val indexer =
        TestActorRef(
          new SequentialTagIndexer[RetryExecuted.type](initFunction(init), index, 3, projId, pluginId, "retry"))
      eventually {
        count.get() shouldEqual 4
        init.get shouldEqual 11L
      }
      eventually {
        SkippedEventLog(projId).fetchEvents[RetryExecuted.type].futureValue shouldEqual List(RetryExecuted)
      }

      watch(indexer)
      indexer ! Stop
      expectTerminated(indexer)
    }

    "not retry when index function fails with NonRetriableErr" in {
      val agg = ShardingAggregate("ignore", sourcingSettings)(Fixture.initial, Fixture.next, Fixture.eval)
      agg.append("ignore", Fixture.IgnoreExecuted).futureValue

      val count = new AtomicLong(0L)
      val init  = new AtomicLong(10L)

      val index =
        (_: IgnoreExecuted.type) => Future.failed[Unit](SomeOtherError(count.incrementAndGet()))
      val projId = UUID.randomUUID().toString

      SkippedEventLog(projId).fetchEvents[IgnoreExecuted.type].futureValue shouldEqual List()

      val indexer =
        TestActorRef(
          new SequentialTagIndexer[IgnoreExecuted.type](initFunction(init), index, 5, projId, pluginId, "ignore"))
      eventually {
        count.get() shouldEqual 1L
        init.get shouldEqual 11L
      }
      SkippedEventLog(projId).fetchEvents[IgnoreExecuted.type].futureValue shouldEqual List(IgnoreExecuted)

      watch(indexer)
      indexer ! Stop
      expectTerminated(indexer)
    }
  }

}
object SequentialTagIndexerSpec {
  case class SomeError(count: Long)      extends RetriableErr("some error")
  case class SomeOtherError(count: Long) extends Err("some OTHER error")

}
