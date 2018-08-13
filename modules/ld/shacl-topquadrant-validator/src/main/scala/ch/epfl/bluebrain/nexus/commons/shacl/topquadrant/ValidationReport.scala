package ch.epfl.bluebrain.nexus.commons.shacl.topquadrant

import ch.epfl.bluebrain.nexus.commons.http.syntax.circe._
import ch.epfl.bluebrain.nexus.commons.shacl.topquadrant.Vocabulary._
import ch.epfl.bluebrain.nexus.commons.test.Resources.jsonContentOf
import ch.epfl.bluebrain.nexus.rdf.Graph
import ch.epfl.bluebrain.nexus.rdf.Graph._
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.Vocabulary._
import ch.epfl.bluebrain.nexus.rdf.syntax.circe._
import ch.epfl.bluebrain.nexus.rdf.syntax.circe.context._
import ch.epfl.bluebrain.nexus.rdf.syntax.jena._
import ch.epfl.bluebrain.nexus.rdf.syntax.node.encoder._
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import io.circe.Json
import journal.Logger
import org.apache.jena.rdf.model.Resource

import scala.util.{Success, Try}

/**
  * Data type that represents the outcome of validating data against a shacl schema.
  *
  * @param conforms      true if the validation was successful and false otherwise
  * @param json          the detailed message of the validator
  */
final case class ValidationReport(conforms: Boolean, targetedNodes: Int, json: Json) {

  /**
    * @return true if the validation report has been successful or false otherwise
    */
  def isValid: Boolean = targetedNodes > 0 && conforms
}

object ValidationReport {
  private val logger = Logger[this.type]

  final def apply(report: Resource): Option[ValidationReport] =
    Try((report.getModel: Graph)) match {
      case Success(graph) =>
        graph.subjects(sh.conforms, _.isLiteral).headOption.flatMap { iri =>
          val report = for {
            conforms <- graph.cursor(iri).downField(sh.conforms).focus.as[Boolean].left.map(_.message)
            targeted <- graph.cursor(iri).downField(nxsh.targetedNodes).focus.as[Int].left.map(_.message)
            json     <- graph.asJson(shaclCtx, Some(iri)).toEither.left.map(_.getMessage)
          } yield ValidationReport(conforms, targeted, json.removeKeys("@context", "@id").addContext(shaclCtxUri))
          report match {
            case Left(err) if err != null =>
              logger.error(s"The json could not be formed from the report due to '${err}'")
              None
            case Left(_)  => None
            case Right(v) => Some(v)
          }
        }
      case _ => None
    }

  private val shaclCtxUri: AbsoluteIri = url"https://bluebrain.github.io/nexus/contexts/shacl"
  private val shaclCtx: Json           = jsonContentOf("/shacl-context-resp.json")
}
