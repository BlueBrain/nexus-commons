package ch.epfl.bluebrain.nexus.commons.service.retryer

import akka.actor.ActorSystem
import akka.testkit.TestKit
import ch.epfl.bluebrain.nexus.commons.service.retryer.RetryStrategy.{Backoff, Linear}
import ch.epfl.bluebrain.nexus.commons.service.retryer.TaskRetrySpec.SomeError
import ch.epfl.bluebrain.nexus.commons.types.RetriableErr
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.execution.atomic.AtomicLong
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time._
import org.scalatest.{Inspectors, Matchers, WordSpecLike}

import scala.concurrent.Future
import scala.concurrent.duration._

class TaskRetrySpec
    extends TestKit(ActorSystem("TaskRetrySpec"))
    with WordSpecLike
    with Matchers
    with Inspectors
    with TaskRetry
    with Eventually
    with ScalaFutures {

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(20 seconds, 400 millis)

  abstract class Context {
    val count = AtomicLong(0L)
    val task = Task.deferFuture {
      Future[Unit] {
        count.incrementAndGet()
        throw SomeError
      }
    }
  }

  "A TaskRetry" should {
    "retry a task exponentially when it fails" in new Context {
      retry(task, 3, Backoff(5 seconds, 0)).failed.futureValue(timeout(Span(7, Seconds))) shouldBe SomeError
      count.get shouldEqual 4
    }

    "retry a task exponentially when it fails capped to 1 second" in new Context {
      retry(task, 3, Backoff(1 seconds, 0)).failed.futureValue(timeout(Span(3, Seconds))) shouldBe SomeError
      count.get shouldEqual 4
    }

    "retry a task linearly when it fails" in new Context {
      retry(task, 4, Linear(5 seconds, 300 millis)).failed
        .futureValue(timeout(Span(3, Seconds))) shouldBe SomeError
      count.get shouldEqual 5
    }

    "retry a task linearly when it fails capped to 200 mills" in new Context {
      retry(task, 5, Linear(200 millis, 200 millis)).failed
        .futureValue(timeout(Span(1, Seconds))) shouldBe SomeError
      count.get shouldEqual 6
    }

    "don't retry when having a successful task" in new Context {
      val f = () => Future[Long](count.incrementAndGet())
      retry(f, 1, Backoff(5 seconds, 0.2)).futureValue shouldEqual 1L
    }
  }
}

object TaskRetrySpec {
  case object SomeError extends RetriableErr("some error")
}
