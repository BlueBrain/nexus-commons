package ch.epfl.bluebrain.nexus.service.commons.retrying

import akka.actor.Scheduler
import akka.pattern.after
import ch.epfl.bluebrain.nexus.common.types.Err
import ch.epfl.bluebrain.nexus.service.commons.retrying.Retryer.MaxRetriesException
import ch.epfl.bluebrain.nexus.service.commons.retrying.RetryerDelays._
import shapeless.{:+:, CNil, Coproduct, Typeable}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random
import ch.epfl.bluebrain.nexus.service.commons.retrying.Retryer._

class Retryer[C <: Coproduct](implicit C: CoproductRetryer[C], ec: ExecutionContext, s: Scheduler) {

  /**
    * Retries the [[Future]] ''f'' with the provided ''delays''.
    *
    * @param f      the [[Future]] to be retried
    * @param delays a sequence of [[FiniteDuration]] which specifies the time to wait between retries
    * @tparam T generic type of ''f''
    */
  final def apply[T](f: () => Future[T], delays: Seq[FiniteDuration]): Future[T] = C(f, delays)

  /**
    * Retries the [[Future]] ''f'' following an exponential backoff algorithm.
    *
    * @param f         the [[Future]] to be retried
    * @param retries   the number of times it will retry
    * @param minJitter the minimum jitter value
    * @param maxJitter the maximum jitter value
    * @tparam T generic type of ''f''
    */
  final def apply[T](f: () => Future[T], retries: Int, minJitter: Double = minJ, maxJitter: Double = maxJ): Future[T] =
    C(f, backoff(retries, minJitter, maxJitter))
}

object Retryer {

  /**
    * Constructs a [[Retryer]] that will retry only when the exception thrown is of some of the types present on the coproduct ''C''.
    *
    * @param C  the implicitly available [[CoproductRetryer]]
    * @param ec the implicitly available [[ExecutionContext]]
    * @param s  the implicitly available [[Scheduler]]
    * @tparam C the type of the underlying coproduct
    */
  final def apply[C <: Coproduct](implicit C: CoproductRetryer[C], ec: ExecutionContext, s: Scheduler): Retryer[C] =
    new Retryer[C]

  val minJ = 0.8
  val maxJ = 0.8

  /**
    * Signals that the amount of retries has been exhausted without any successful recovery.
    *
    * @param th the underlying exception of the last retry
    */
  final case class MaxRetriesException(th: Throwable) extends Err("Maximum number of retries exhausted")

}

trait CoproductRetryer[C <: Coproduct] {

  /**
    * Attempts to recover the [[Future]] ''f'' from the error ''th''
    * when ''th'' can be cast as some of the types in ''C'' with the provided ''delays''.
    *
    * @param th     the [[Throwable]] which provides the error that happened when ''f'' was previously attempted
    * @param f      the [[Future]] to be retried
    * @param delays a sequence of [[FiniteDuration]] which specifies the time to wait between retries
    * @tparam T generic type of ''f''
    */
  def recover[T](th: Throwable, f: () => Future[T], delays: Seq[FiniteDuration])(implicit
                                                                                 ec: ExecutionContext,
                                                                                 s: Scheduler): Future[T]

  /**
    * Adds recovery functionality to ''f''. When failed, ''f'' will be retryed if the error can be cast
    * as some of the types in ''C'' with the provided ''delays''.
    *
    * @param f      the [[Future]] to be retried
    * @param delays a sequence of [[FiniteDuration]] which specifies the time to wait between retries
    * @tparam T generic type of ''f''
    */
  final def apply[T](f: () => Future[T], delays: Seq[FiniteDuration])(implicit
                                                                      ec: ExecutionContext,
                                                                      s: Scheduler): Future[T] =
    f() recoverWith { case error => recover(error, f, delays) }

}

object CoproductRetryer {

  /**
    * Summons a [[CoproductRetryer]] instance from the implicit scope.
    *
    * @param instance the implicitly available instance
    * @tparam C the generic coproduct type of the retryer
    */
  final def apply[C <: Coproduct](implicit instance: CoproductRetryer[C]): CoproductRetryer[C] = instance

  /**
    * Base recursion case for deriving retryers.
    */
  implicit val cnilRetrying: CoproductRetryer[CNil] = new CoproductRetryer[CNil] {
    override def recover[T](th: Throwable, f: () => Future[T], delays: Seq[FiniteDuration])(implicit
                                                                                            ec: ExecutionContext,
                                                                                            s: Scheduler): Future[T] =
      Future.failed(th)
  }

  /**
    * Inductive case for deriving ''CoproductRetryer'' instances using implicitly available typeclasses.
    *
    * @param headTypeable a typeable instance for the head ''H'' type
    * @param tail         a ''CoproductRetryer'' for the tail ''T'' type
    * @tparam H the type of the coproduct head
    * @tparam T the type of the coproduct tail
    * @return a new ''CoproductRetryer'' that prepends a new type ''H'' to the list of known retryer types
    */
  final implicit def recursiveRetrying[H, T <: Coproduct](implicit headTypeable: Typeable[H],
                                                          tail: CoproductRetryer[T]): CoproductRetryer[H :+: T] =
    new CoproductRetryer[:+:[H, T]] {
      override def recover[A](th: Throwable, f: () => Future[A], delays: Seq[FiniteDuration])(
          implicit ec: ExecutionContext,
          s: Scheduler): Future[A] = {
        delays match {
          case headDelay :: tailDelays =>
            th match {
              case _ if headTypeable.cast(th).isDefined => after(headDelay, s)(apply(f, tailDelays))
              case _                                    => tail.recover(th, f, delays)
            }
          case _ => Future.failed(MaxRetriesException(th))
        }
      }
    }
}

object RetryerDelays {

  /**
    * Backoff sequence implemented using Fibonacci as the exponential function with Jitter.
    *
    * @param retries   the amount of elements in the sequence (or retries). It has to be a positive integer
    * @param minJitter the minimum jitter value. The minimum delay will be: delay * ''minJitter''
    * @param maxJitter the maximum jitter value. The maximum delay will be: delay * ''maxJitter''
    * @return an exponential sequence of [[FiniteDuration]] with Jitter
    */
  def backoff(retries: Int, minJitter: Double = minJ, maxJitter: Double = maxJ): Seq[FiniteDuration] =
    fibonacci
      .slice(1, retries + 1)
      .map(d => (d * (minJitter + (maxJitter - minJitter) * Random.nextDouble)).toMillis millis)
      .toList

  private val fibonacci: Stream[FiniteDuration] = 0.seconds #:: 1.seconds #:: (fibonacci zip fibonacci.tail).map { t =>
    t._1 + t._2
  }

}
