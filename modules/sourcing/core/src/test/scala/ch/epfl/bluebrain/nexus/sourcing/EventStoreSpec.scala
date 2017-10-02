package ch.epfl.bluebrain.nexus.sourcing

import cats.Id
import ch.epfl.bluebrain.nexus.sourcing.EventStoreSpec.Cmd.{FirstCmd, SecondCmd}
import ch.epfl.bluebrain.nexus.sourcing.EventStoreSpec.Evt.{FirstEvent, SecondEvent}
import ch.epfl.bluebrain.nexus.sourcing.EventStoreSpec.St.{First, Second}
import org.scalatest.{Matchers, WordSpecLike}

class EventStoreSpec extends WordSpecLike with Matchers {

  "An EventStore" should {
    import EventStoreSpec._
    val es = EventStore[Id, String]

    "append via the implicit EventLog" in {
      es.append[Evt](id, FirstEvent) shouldEqual 3L
    }

    "return the last sequence nr via the implicit EventLog" in {
      es.lastSequenceNr[Evt](id) shouldEqual 2L
    }

    "return the current state via the implicit StatefulEventLog" in {
      es.currentState[Evt](id) shouldEqual Second
    }

    "return that a value exists if the seq number is higher that 0" in {
      es.exists[Evt](id) shouldEqual true
    }

    "return that a value does not exist if the seq number is 0" in {
      es.exists[Evt]("") shouldEqual false
    }

    "return the aggregate state when evaluating a command" in {
      es.eval(id, FirstCmd) shouldEqual Right(First)
    }

    "return the fold outcome based on the sequence of events" in {
      es.foldLeft[List[Evt], Evt](id, List.empty[Evt]) {
        case (acc, elem) => elem :: acc
      } shouldEqual List(SecondEvent, FirstEvent)
    }
  }

}

object EventStoreSpec {

  final val id: String = "id"

  sealed trait Evt extends Product with Serializable
  object Evt {
    final case object FirstEvent  extends Evt
    final case object SecondEvent extends Evt
  }

  sealed trait Cmd extends Product with Serializable
  object Cmd {
    final case object FirstCmd  extends Cmd
    final case object SecondCmd extends Cmd
  }

  sealed trait St extends Product with Serializable
  object St {
    final case object First  extends St
    final case object Second extends St
  }

  implicit val aggregate: Aggregate.Aux[Id, String, Evt, St, Cmd, String] = new Aggregate[Id] {
    override type Identifier = String
    override type Event      = Evt
    override type State      = St
    override type Command    = Cmd
    override type Rejection  = String

    override def eval(id: String, cmd: Cmd): Either[String, St] = cmd match {
      case FirstCmd  => Right(First)
      case SecondCmd => Right(Second)
    }

    override def currentState(id: String): St = Second

    override def lastSequenceNr(id: String): Long = id match {
      case EventStoreSpec.`id` => 2L
      case _                   => 0L
    }

    override def foldLeft[B](id: String, z: B)(f: (B, Event) => B): B =
      List(FirstEvent, SecondEvent).foldLeft(z)(f)

    override def name: String = "name"

    override def append(id: String, event: Event): Long = 3L
  }
}
