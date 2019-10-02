package ch.epfl.bluebrain.nexus.commons.sparql.client

import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.http.scaladsl.unmarshalling.PredefinedFromEntityUnmarshallers._

/**
  * Information about the deployed service
  *
  * @param name    service name
  * @param version service version
  */
final case class ServiceDescription(name: String, version: String)

object ServiceDescription {
  private val regex = """buildVersion[^,<]*""".r
  private val name  = "blazegraph"

  implicit val serviceDescDecoder: FromEntityUnmarshaller[ServiceDescription] = stringUnmarshaller.map {
    regex.findFirstIn(_) match {
      case None          => throw new IllegalArgumentException(s"'version' not found using regex $regex")
      case Some(version) => ServiceDescription(name, version.replace("""buildVersion">""", ""))
    }
  }

}
