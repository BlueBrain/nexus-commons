package ch.epfl.bluebrain.nexus.commons.sparql.client

import akka.actor.{Actor, ActorLogging}
import ch.epfl.bluebrain.nexus.commons.sparql.client.InMemorySparqlActor.Protocol.{
  Query,
  QueryResponse,
  Update,
  UpdateResponse
}
import org.apache.jena.query._
import org.apache.jena.update.UpdateAction

import scala.util.Try

/**
  * Actor that store Jena in-memory Dataset
  */
class InMemorySparqlActor extends Actor with ActorLogging {

  private lazy val dataset = DatasetFactory.create()

  override def receive: Receive = {
    case Update(update) =>
      val result = Try {
        UpdateAction.parseExecute(update, dataset)
      }
      sender() ! UpdateResponse(result)
    case Query(query) =>
      val q          = QueryFactory.create(query)
      val qExecution = QueryExecutionFactory.create(q, dataset)
      val result = Try {
        //https://jena.apache.org/documentation/query/app_api.html#passing-a-result-set-out-of-the-processing-loop
        ResultSetFactory.copyResults(qExecution.execSelect())
      }
      qExecution.close()
      sender() ! QueryResponse(result)
  }
}

object InMemorySparqlActor {
  sealed trait Protocol extends Product with Serializable {}

  object Protocol {
    final case class Update(update: String) extends Protocol
    final case class Query(query: String)   extends Protocol
    final case class UpdateResponse(result: Try[Unit])
    final case class QueryResponse(result: Try[ResultSet]) extends Protocol
  }
}
