package ch.epfl.bluebrain.nexus.commons.test

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.testkit._
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.Promise
import scala.concurrent.duration._

class ActorSystemFixture(name: String, startCluster: Boolean = false, configs: Vector[Config] = Vector.empty)
    extends TestKit(
      ActorSystem(
        name,
        (configs :+ ConfigFactory.load("service-test.conf"))
          .foldLeft(ConfigFactory.empty()) { case (c, e) => c withFallback e }
          .resolve()
      )
    )
    with WordSpecLike
    with PatienceConfiguration
    with BeforeAndAfterAll {

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(3.seconds.dilated, 100.milliseconds.dilated)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    if (startCluster) {
      val promise = Promise[Unit]
      Cluster(system).registerOnMemberUp(promise.success(()))
      Cluster(system).join(Cluster(system).selfAddress)
      promise.future.futureValue
    }
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }
}
