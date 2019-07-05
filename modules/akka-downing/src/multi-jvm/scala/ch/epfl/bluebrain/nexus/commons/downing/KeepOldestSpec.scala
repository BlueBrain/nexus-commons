package ch.epfl.bluebrain.nexus.commons.downing

import akka.remote.testconductor.RoleName
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.duration._

/**
  * Keep oldest akka cluster downing multi jvm testing.
  * The testing setup is inspired by the code on the github repository https://github.com/arnohaase/simple-akka-downing from Arno Haase, which is licensed under Apache 2.0
  * The implementation is inspired by the code on the github repository https://github.com/arnohaase/simple-akka-downing from Arno Haase, which is licensed under Apache 2.0
  */
abstract class KeepOldestSpec(config: DowningConfig, survivors: Int*)
    extends MultiNodeClusterSpec(config)
    with ScalaFutures {
  val side1 = survivors.map(s => RoleName(s"$s")).toVector //  Vector (node1, node2, node3)
  val side2 = roles.tail.filterNot(side1.contains) //Vector (node4, node5)

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(20 seconds, 300 milliseconds)

  "A cluster of five nodes" should {

    "reach initial convergence" in {
      muteLog()
      muteMarkingAsUnreachable()
      muteMarkingAsReachable()

      awaitClusterUp(side1 ++ side2: _*)
      enterBarrier("after-1")
    }

    "detect a network partition and shut down one partition after a timeout" in {
      enterBarrier("before-durable-partition")

      // mark nodes across the partition as mutually unreachable, and wait until that is reflected in all nodes' local cluster state
      createNetworkPartition(side1, side2)
      enterBarrier("durable-partition")

      // five second timeout until our downing strategy kicks in - plus additional delay to be on the safe side
      Thread.sleep(30000)

      runOn(config.conductor) {
        for (r <- side1) upNodesFor(r).futureValue shouldEqual side1.toSet
        for (r <- side2) upNodesFor(r).futureValue shouldEqual Set.empty[RoleName]
      }

      // some additional time to ensure cluster node JVMs stay alive during the previous checks
      Thread.sleep(20000)
    }
  }
}

//Multi JVM tests naming: {TestName}MultiJvm{NodeName}

class KeepOldest1MultiJvm0 extends KeepOldestSpec(new DowningConfig(downIfAlone = true), 1, 2)
class KeepOldest1MultiJvm1 extends KeepOldestSpec(new DowningConfig(downIfAlone = true), 1, 2)
class KeepOldest1MultiJvm2 extends KeepOldestSpec(new DowningConfig(downIfAlone = true), 1, 2)
class KeepOldest1MultiJvm3 extends KeepOldestSpec(new DowningConfig(downIfAlone = true), 1, 2)
class KeepOldest1MultiJvm4 extends KeepOldestSpec(new DowningConfig(downIfAlone = true), 1, 2)
class KeepOldest1MultiJvm5 extends KeepOldestSpec(new DowningConfig(downIfAlone = true), 1, 2)

class KeepOldest2MultiJvm0 extends KeepOldestSpec(new DowningConfig(downIfAlone = true), 1, 5)
class KeepOldest2MultiJvm1 extends KeepOldestSpec(new DowningConfig(downIfAlone = true), 1, 5)
class KeepOldest2MultiJvm2 extends KeepOldestSpec(new DowningConfig(downIfAlone = true), 1, 5)
class KeepOldest2MultiJvm3 extends KeepOldestSpec(new DowningConfig(downIfAlone = true), 1, 5)
class KeepOldest2MultiJvm4 extends KeepOldestSpec(new DowningConfig(downIfAlone = true), 1, 5)
class KeepOldest2MultiJvm5 extends KeepOldestSpec(new DowningConfig(downIfAlone = true), 1, 5)

class KeepOldestDownIfAloneMultiJvm0 extends KeepOldestSpec(new DowningConfig(downIfAlone = true), 2, 3, 4, 5)
class KeepOldestDownIfAloneMultiJvm1 extends KeepOldestSpec(new DowningConfig(downIfAlone = true), 2, 3, 4, 5)
class KeepOldestDownIfAloneMultiJvm2 extends KeepOldestSpec(new DowningConfig(downIfAlone = true), 2, 3, 4, 5)
class KeepOldestDownIfAloneMultiJvm3 extends KeepOldestSpec(new DowningConfig(downIfAlone = true), 2, 3, 4, 5)
class KeepOldestDownIfAloneMultiJvm4 extends KeepOldestSpec(new DowningConfig(downIfAlone = true), 2, 3, 4, 5)
class KeepOldestDownIfAloneMultiJvm5 extends KeepOldestSpec(new DowningConfig(downIfAlone = true), 2, 3, 4, 5)

class KeepOldestNoDownIfAloneMultiJvm0 extends KeepOldestSpec(new DowningConfig(downIfAlone = false), 1)
class KeepOldestNoDownIfAloneMultiJvm1 extends KeepOldestSpec(new DowningConfig(downIfAlone = false), 1)
class KeepOldestNoDownIfAloneMultiJvm2 extends KeepOldestSpec(new DowningConfig(downIfAlone = false), 1)
class KeepOldestNoDownIfAloneMultiJvm3 extends KeepOldestSpec(new DowningConfig(downIfAlone = false), 1)
class KeepOldestNoDownIfAloneMultiJvm4 extends KeepOldestSpec(new DowningConfig(downIfAlone = false), 1)
class KeepOldestNoDownIfAloneMultiJvm5 extends KeepOldestSpec(new DowningConfig(downIfAlone = false), 1)
