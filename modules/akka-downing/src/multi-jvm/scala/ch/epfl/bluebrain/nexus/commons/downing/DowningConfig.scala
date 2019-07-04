package ch.epfl.bluebrain.nexus.commons.downing
import java.util.UUID

import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeConfig
import com.typesafe.config.ConfigFactory
import ch.epfl.bluebrain.nexus.commons.test.Resources

/**
  * Keep oldest akka cluster downing configuration for a 5 nodes cluster.
  * The testing setup is inspired by the code on the github repository https://github.com/arnohaase/simple-akka-downing from Arno Haase, which is licensed under Apache 2.0
  * The implementation is inspired by the code on the github repository https://github.com/arnohaase/simple-akka-downing from Arno Haase, which is licensed under Apache 2.0
  */
class DowningConfig(downIfAlone: Boolean) extends MultiNodeConfig with Resources {

  final val CLUSTER_SIZE = 5
  final val ROLE_SIZE    = 3

  private var numRoles = 0 // 'roles' in the sense of multi-jvm testing, not in the sense of Akka cluster roles

  override def role(name: String): RoleName = {
    val roleName = super.role(name)

    if (numRoles > 0) {
      var clusterRoles = Vector.empty[String]

      val managementPort = 8080 + numRoles

      if (numRoles <= ROLE_SIZE) clusterRoles :+= "with-oldest"
      if (numRoles > CLUSTER_SIZE - ROLE_SIZE) clusterRoles :+= "without-oldest"

      val configString = contentOf(
        "/config.conf",
        Map(
          "_roles_"         -> clusterRoles.mkString("[", ",", "]"),
          "_destination_"   -> s"target/flight-recorder-${UUID.randomUUID().toString}.afr",
          "_mgmntPort_"     -> managementPort.toString,
          "_down-if-alone_" -> (if (downIfAlone) "on" else "off")
        )
      )

      nodeConfig(roleName)(ConfigFactory.parseString(configString))
    }

    numRoles += 1

    roleName
  }

  val conductor = role("0")
  val node1     = role("1")
  val node2     = role("2")
  val node3     = role("3")
  val node4     = role("4")
  val node5     = role("5")

  //  commonConfig(debugConfig(true))
  testTransport(true)
}
