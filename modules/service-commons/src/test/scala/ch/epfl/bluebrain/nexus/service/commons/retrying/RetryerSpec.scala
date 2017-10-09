package ch.epfl.bluebrain.nexus.service.commons.retrying

import java.util.concurrent.atomic.AtomicLong

import akka.actor.ActorSystem
import akka.testkit.TestKit
import ch.epfl.bluebrain.nexus.common.types.Err
import ch.epfl.bluebrain.nexus.service.commons.retrying.Retryer.MaxRetriesException
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time._
import org.scalatest.{Matchers, WordSpecLike}
import shapeless._
import ch.epfl.bluebrain.nexus.service.commons.retrying.RetryerSpec._

import scala.concurrent.Future
import scala.concurrent.duration._

class RetryerSpec extends TestKit(ActorSystem("RetryerSpec")) with WordSpecLike with Matchers with ScalaFutures {

  "A Retryer" should {
    implicit val ec = system.dispatcher
    implicit val sc = system.scheduler
    val ret         = Retryer[Allowed]

    trait Context {
      val countSuccess          = new AtomicLong(0L)
      val countFailed           = new AtomicLong(0L)
      val countFailedAny        = new AtomicLong(0L)
      val countFailedNotAllowed = new AtomicLong(0L)

      val fSuccess          = () => Future(countSuccess.incrementAndGet())
      val fFailed           = () => Future.failed(Allowed1(countFailed.incrementAndGet()))
      val fFailedAny        = () => Future.failed(NotAllowed(countFailedAny.incrementAndGet()))
      val fFailedNotAllowed = () => Future.failed(NotAllowed(countFailedNotAllowed.incrementAndGet()))

    }

    "return the right sequence of backoff without jitter" in {
      val expected = List(1 second, 1 second, 2 seconds, 3 seconds, 5 seconds, 8 seconds)
      RetryerDelays.backoff(6, randomFactor = 0.0).toList shouldEqual expected
    }

    "return empty backoff" in {
      RetryerDelays.backoff(0).toList shouldEqual List()
    }

    "return the right sequence of backoff with jitter" in {
      RetryerDelays
        .backoff(6, randomFactor = 0.2)
        .toList
        .last
        .toNanos shouldEqual (8 seconds).toNanos +- (1600 millis).toNanos
    }

    "return the right sequence of backoff capped to 5 seconds" in {
      RetryerDelays
        .backoff(7, randomFactor = 0.0, maxDelay = 5 seconds)
        .toList
        .map(_.toSeconds seconds) shouldEqual List(1 seconds,
                                                   1 seconds,
                                                   2 seconds,
                                                   3 seconds,
                                                   5 seconds,
                                                   5 seconds,
                                                   5 seconds)
    }

    "return the right response when future does not fail" in new Context {
      ret.apply(fSuccess, Seq(1 seconds, 2 seconds, 2 seconds, 2 seconds)).futureValue shouldEqual 1L
    }

    "return the right response when future does not fail even with empty delays" in new Context {
      val f = Retryer[CNil].apply(fSuccess, Seq())
      f.futureValue shouldEqual 1
    }

    "not retry when the exception is not part of the coproduct" in new Context {
      val f = ret.apply(fFailedNotAllowed, Seq(1 seconds, 2 seconds))
      ScalaFutures.whenReady(f.failed) { e =>
        e shouldEqual NotAllowed(1L)
      }
    }

    "not retry if there are no types supported on the retryer" in new Context {
      val f = Retryer[CNil].apply(fFailed, Seq(1 seconds, 2 seconds))
      ScalaFutures.whenReady(f.failed) { e =>
        e shouldEqual Allowed1(1L)
      }
    }

    "not retry if the delay sequence is empty" in new Context {
      val f = ret.apply(fFailed, Seq())
      ScalaFutures.whenReady(f.failed, timeout(Span(300, Millis))) { e =>
        e shouldEqual MaxRetriesException(Allowed1(1L))
      }
    }

    "retry when exception is part of the coproduct" in new Context {
      val f = ret.apply(fFailed, Seq(1 seconds, 2 seconds))
      ScalaFutures.whenReady(f.failed, timeout(Span(3500, Millis))) { e =>
        e shouldBe a[MaxRetriesException]
        countFailed.get() shouldEqual 3L
      }
    }

    "retry for any type" in new Context {
      val f = Retryer.any.apply(fFailedAny, Seq(1 seconds, 1 seconds))
      ScalaFutures.whenReady(f.failed, timeout(Span(2500, Millis))) { e =>
        e shouldBe a[MaxRetriesException]
        countFailedAny.get() shouldEqual 3L
      }
    }

    "retry when exception is part of the coproduct using backoff" in new Context {
      val f = ret.apply(fFailed, 2)
      ScalaFutures.whenReady(f.failed, timeout(Span(3000, Millis))) { e =>
        e shouldBe a[MaxRetriesException]
        countFailed.get() shouldEqual 3L
      }
    }
  }

}

object RetryerSpec {

  case class Allowed1(count: Long) extends Err("allowed")

  case class NotAllowed(count: Long) extends Err("not allowed")

  type Allowed = Allowed1 :+: CNil

}
