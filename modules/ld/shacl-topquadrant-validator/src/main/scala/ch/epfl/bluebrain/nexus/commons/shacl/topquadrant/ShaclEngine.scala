package ch.epfl.bluebrain.nexus.commons.shacl.topquadrant

import java.net.URI
import java.util
import java.util.UUID

import ch.epfl.bluebrain.nexus.commons.shacl.topquadrant.Vocabulary._
import ch.epfl.bluebrain.nexus.commons.test.Resources._
import ch.epfl.bluebrain.nexus.rdf.Node.IriNode
import org.apache.jena.query.Dataset
import org.apache.jena.rdf.model._
import org.apache.jena.riot.system.StreamRDFLib
import org.apache.jena.riot.{Lang, RDFParser}
import org.topbraid.jenax.util.{ARQFactory, JenaDatatypes}
import org.topbraid.shacl.arq.SHACLFunctions
import org.topbraid.shacl.engine.{Constraint, ShapesGraph}
import org.topbraid.shacl.validation.{ValidationEngine, ValidationEngineConfiguration, ValidationUtil}

import scala.util.Try

/**
  * Extend the [[ValidationEngine]] form TopQuadrant in order to add triples to the report
  * with the number of targetedNodes
  *
  * @param dataset        the Dataset to operate on
  * @param shapesGraphURI the URI of the shapes graph (must be in the dataset)
  * @param shapesGraph    the ShapesGraph with the shapes to validate against
  */
// $COVERAGE-OFF$
@SuppressWarnings(Array("NullParameter"))
final class ShaclEngine private (dataset: Dataset, shapesGraphURI: URI, shapesGraph: ShapesGraph)
    extends ValidationEngine(dataset, shapesGraphURI, shapesGraph, null) {
  private var targetedNodes = 0

  override def validateNodesAgainstConstraint(focusNodes: util.List[RDFNode], constraint: Constraint): Unit = {
    super.validateNodesAgainstConstraint(focusNodes, constraint)
    targetedNodes += 1
  }

  override def validateAll() = {
    val r = super.validateAll()
    if (r != null) r.addLiteral(toProperty(nxsh.targetedNodes), JenaDatatypes.createInteger(targetedNodes)) else r
  }

  private def toProperty(iriNode: IriNode): Property =
    ResourceFactory.createProperty(iriNode.value.asString)
}

object ShaclEngine {

  private val shaclModel = {
    val m      = ModelFactory.createDefaultModel
    val stream = StreamRDFLib.graph(m.getGraph)
    RDFParser.create.fromString(contentOf("/shacl-shacl.ttl")).base("").lang(Lang.TTL).parse(stream)
    val finalModel = ValidationUtil.ensureToshTriplesExist(m)
    SHACLFunctions.registerFunctions(finalModel)
    finalModel
  }

  /**
    * Validates a given data Model against the SHACL shapes spec.
    *
    * @param shapesModel   the shapes Model to test against the SHACL shapes spec
    * @param reportDetails true to also include the sh:detail (more verbose) and false to omit them
    * @return an option of [[ValidationReport]] with the validation results
    */
  def apply(shapesModel: Model, reportDetails: Boolean): Option[ValidationReport] =
    applySkipShapesCheck(shapesModel, shaclModel, validateShapes = true, reportDetails = reportDetails)

  /**
    * Validates a given data Model against all shapes from a given shapes Model.
    *
    * @param dataModel      the data Model
    * @param shapesModel    the shapes Model
    * @param validateShapes true to also validate any shapes in the data Model (false is faster)
    * @param reportDetails  true to also include the sh:detail (more verbose) and false to omit them
    * @return an option of [[ValidationReport]] with the validation results
    */
  def apply(dataModel: Model,
            shapesModel: Model,
            validateShapes: Boolean,
            reportDetails: Boolean): Option[ValidationReport] = {

    val finalShapesModel = ValidationUtil.ensureToshTriplesExist(shapesModel)
    // Make sure all sh:Functions are registered
    SHACLFunctions.registerFunctions(finalShapesModel)
    applySkipShapesCheck(dataModel, finalShapesModel, validateShapes, reportDetails)
  }

  private def applySkipShapesCheck(dataModel: Model,
                                   finalShapesModel: Model,
                                   validateShapes: Boolean,
                                   reportDetails: Boolean): Option[ValidationReport] = {
    // Create Dataset that contains both the data model and the shapes model
    // (here, using a temporary URI for the shapes graph)
    val shapesGraphURI = URI.create("urn:x-shacl-shapes-graph:" + UUID.randomUUID.toString)
    val dataset        = ARQFactory.get.getDataset(dataModel)
    dataset.addNamedModel(shapesGraphURI.toString, finalShapesModel)
    val shapesGraph = new ShapesGraph(finalShapesModel)
    val engine      = new ShaclEngine(dataset, shapesGraphURI, shapesGraph)
    engine.setConfiguration(
      new ValidationEngineConfiguration().setReportDetails(reportDetails).setValidateShapes(validateShapes))
    Try {
      engine.applyEntailments()
      engine.validateAll()
    }.toOption.flatMap(ValidationReport.apply)
  }
}
// $COVERAGE-ON$
