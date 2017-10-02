package ch.epfl.bluebrain.nexus.commons.sparql.client

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlClientFixture._

import scala.annotation.tailrec
import scala.util.Random

class SparqlClientFixture {

  val rand: String  = genString(length = 8)
  val ctx: Uri      = Uri(s"http://$localhost:8080/graphs/$rand")
  val id: String    = genString()
  val label: String = genString()
  val value: String = genString()
}

object SparqlClientFixture {

  val localhost = "127.0.0.1"

  final def genString(length: Int = 16, pool: IndexedSeq[Char] = Vector.range('a', 'z')): String = {
    val size = pool.size

    @tailrec
    def inner(acc: String, remaining: Int): String =
      if (remaining <= 0) acc
      else inner(acc + pool(Random.nextInt(size)), remaining - 1)

    inner("", length)
  }
}
