package ch.epfl.bluebrain.nexus.commons.sparql.client
import akka.actor.ActorRef
import akka.http.scaladsl.model.Uri
import cats.MonadError
import org.apache.jena.query.ResultSet
import akka.pattern.ask
import akka.util.Timeout
import ch.epfl.bluebrain.nexus.commons.sparql.client.InMemorySparqlActor.Protocol.{
  Query,
  QueryResponse,
  Update,
  UpdateResponse
}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * SPARQL client which stores data and performs all the queries on in-memory Jena Dataset
  *
  * @param inMemoryActor ActorRef for InMemorySparqlActor
  */
class InMemorySparqlClient(inMemoryActor: ActorRef)(implicit F: MonadError[Future, Throwable],
                                                    ec: ExecutionContext,
                                                    tm: Timeout)
    extends SparqlClient[Future] {

  /**
    * Executes the argument ''query'' against the underlying Jena Dataset.
    *
    * @param query the query to execute
    * @return the query result set
    */
  override def query(query: String): Future[ResultSet] = {
    (inMemoryActor ? Query(query)).flatMap {
      case QueryResponse(Success(rs)) => F.pure(rs)
      case QueryResponse(Failure(ex)) => F.raiseError(ex)
    }

  }

  /**
    * Executes the argument update query against the underlying Jena Dataset
    * @param graph Graph targeted in the update, unused in this implementation
    * @param query the update query
    * @return successful Future[Unit] if update succeeded, failure otherwise
    */
  override protected def executeUpdate(graph: Uri, query: String): Future[Unit] = {
    (inMemoryActor ? Update(query)).flatMap {
      case UpdateResponse(Success(_))  => F.pure(())
      case UpdateResponse(Failure(ex)) => F.raiseError(ex)
    }
  }
}

object InMemorySparqlClient {

  def apply(inMemoryActor: ActorRef)(implicit F: MonadError[Future, Throwable],
                                     ec: ExecutionContext,
                                     tm: Timeout): InMemorySparqlClient = new InMemorySparqlClient(inMemoryActor)
}
