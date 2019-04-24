package ch.epfl.bluebrain.nexus.commons.shacl

import ch.epfl.bluebrain.nexus.commons.shacl.Vocabulary._
import ch.epfl.bluebrain.nexus.commons.test.Resources.jsonContentOf
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.Vocabulary._
import ch.epfl.bluebrain.nexus.rdf.instances._
import ch.epfl.bluebrain.nexus.rdf.jena.JenaConversions._
import ch.epfl.bluebrain.nexus.rdf.syntax._
import io.circe.{Encoder, Json}
import org.apache.jena.rdf.model.Resource

import scala.collection.JavaConverters._
import scala.util.Try

/**
  * Data type that represents the outcome of validating data against a shacl schema.
  *
  * @param conforms      true if the validation was successful and false otherwise
  * @param targetedNodes the number of target nodes that were touched per shape
  * @param json          the detailed message of the validator
  */
final case class ValidationReport(conforms: Boolean, targetedNodes: Int, json: Json) {

  /**
    * @param ignoreTargetedNodes flag to decide whether or not ''targetedNodes''
    *                         should be ignored from the validation logic
    * @return true if the validation report has been successful or false otherwise
    */
  def isValid(ignoreTargetedNodes: Boolean = false): Boolean =
    (ignoreTargetedNodes && conforms) || (!ignoreTargetedNodes && targetedNodes > 0 && conforms)
}

object ValidationReport {

  final def apply(report: Resource): Option[ValidationReport] =
    for {
      res     <- Try(report.getModel.listSubjectsWithProperty(iriNodeToProperty(sh.conforms)).asScala.next()).toOption
      subject <- toIriOrBNode(res).toOption
      graph   <- report.getModel.asGraph(subject).toOption
      cursor = graph.cursor()
      conforms <- cursor.downField(sh.conforms).focus.as[Boolean].toOption
      targeted <- cursor.downField(nxsh.targetedNodes).focus.as[Int].left.map(_.message).toOption
      json     <- graph.as[Json](shaclCtx).left.map(_.message).toOption
    } yield ValidationReport(conforms, targeted, json.removeKeys("@context", "@id").addContext(shaclCtxUri))

  private val shaclCtxUri: AbsoluteIri = url"https://bluebrain.github.io/nexus/contexts/shacl-20170720.json"
  private val shaclCtx: Json           = jsonContentOf("/shacl-context-resp.json")

  implicit val reportEncoder: Encoder[ValidationReport] = Encoder.instance(_.json)
}
