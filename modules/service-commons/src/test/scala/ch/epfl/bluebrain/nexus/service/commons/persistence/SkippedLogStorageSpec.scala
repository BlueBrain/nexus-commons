package ch.epfl.bluebrain.nexus.service.commons.persistence

import java.util.UUID

import akka.testkit.{TestKit, TestKitBase}
import ch.epfl.bluebrain.nexus.service.commons.persistence.SkippedLogStorageSpec.SomeEvent
import io.circe.generic.auto._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, DoNotDiscover, Matchers, WordSpecLike}

import scala.concurrent.duration._

//noinspection TypeAnnotation
@DoNotDiscover
class SkippedLogStorageSpec
    extends TestKitBase
    with WordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  implicit lazy val system = SystemBuilder.persistence("SkippedLogStorageSpec")

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  "A SkippedLogStorage" should {
    val id = UUID.randomUUID().toString

    "store an event" in {
      SkippedEventLog(id).storeEvent(SomeEvent(1L, "description")).futureValue
    }

    "store another event" in {
      SkippedEventLog(id).storeEvent(SomeEvent(2L, "description2")).futureValue
    }

    "retrieve stored events" in {
      SkippedEventLog(id).fetchEvents[SomeEvent].futureValue shouldEqual Seq(SomeEvent(1L, "description"),
                                                                             SomeEvent(2L, "description2"))
    }

    "retrieve empty list of events for unknown skipped log" in {
      SkippedEventLog(UUID.randomUUID().toString).fetchEvents[SomeEvent].futureValue shouldEqual List.empty[SomeEvent]
    }
  }

  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(30 seconds, 1 second)
}
object SkippedLogStorageSpec {
  case class SomeEvent(rev: Long, description: String)
}
