package ch.epfl.bluebrain.nexus.service.commons

import java.io.File

import akka.persistence.cassandra.testkit.CassandraLauncher
import ch.epfl.bluebrain.nexus.service.commons.persistence.{ResumableProjectionSpec, SequentialTagIndexerSpec}
import org.scalatest.{BeforeAndAfterAll, Suites}

class CassandraSpec
    extends Suites(
      new SequentialTagIndexerSpec,
      new ResumableProjectionSpec,
    )
    with BeforeAndAfterAll {

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val cassandraDirectory = new File("target/cassandra")
    CassandraLauncher.start(
      cassandraDirectory,
      configResource = CassandraLauncher.DefaultTestConfigResource,
      clean = true,
      port = 0,
      CassandraLauncher.classpathForResources("logback-test.xml")
    )
  }

  override protected def afterAll(): Unit = {
    CassandraLauncher.stop()
    super.afterAll()
  }
}
