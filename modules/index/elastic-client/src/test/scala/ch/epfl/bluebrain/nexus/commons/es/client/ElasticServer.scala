package ch.epfl.bluebrain.nexus.commons.es.client

import java.nio.file.Files
import java.util.Arrays._

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import ch.epfl.bluebrain.nexus.commons.es.client.ElasticServer.MyNode
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import org.apache.commons.io.FileUtils
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.node.Node
import org.elasticsearch.plugins.Plugin
import org.elasticsearch.transport.Netty3Plugin
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

abstract class ElasticServer
    extends TestKit(ActorSystem("ElasticServer"))
    with WordSpecLike
    with BeforeAndAfterAll
    with Randomness {

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    startElastic()
  }

  override protected def afterAll(): Unit = {
    stopElastic()
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  val esUri       = Uri(s"http://localhost:9200")
  implicit val mt = ActorMaterializer()
  implicit val ec = system.dispatcher

  private val clusterName = "elasticsearch"

  private val dataDir = Files.createTempDirectory("elasticsearch_data_").toFile
  private val settings = Settings
    .builder()
    .put("path.home", dataDir.toString)
    .put("transport.type", "local")
    .put("http.enabled", true)
    .put("cluster.name", clusterName)
    .put("http.type", "netty3")
    .build

  private lazy val node = new MyNode(settings, asList(classOf[Netty3Plugin]))

  def startElastic(): Unit = {
    node.start()
    ()
  }

  def stopElastic(): Unit = {
    node.close()

    try {
      FileUtils.forceDelete(dataDir)
    } catch {
      case _: Exception => // dataDir cleanup failed
    }
    ()
  }
}

object ElasticServer {

  import java.util

  import org.elasticsearch.node.InternalSettingsPreparer

  private class MyNode(preparedSettings: Settings, classpathPlugins: util.Collection[Class[_ <: Plugin]])
      extends Node(InternalSettingsPreparer.prepareEnvironment(preparedSettings, null), classpathPlugins)
}
