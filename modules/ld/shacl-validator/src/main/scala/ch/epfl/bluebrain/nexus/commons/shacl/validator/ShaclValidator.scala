package ch.epfl.bluebrain.nexus.commons.shacl.validator

import java.io.ByteArrayInputStream

import cats.MonadError
import cats.syntax.applicativeError._
import cats.syntax.cartesian._
import cats.syntax.flatMap._
import cats.syntax.functor._
import ch.epfl.bluebrain.nexus.commons.shacl.validator.ShaclValidatorErr._
import es.weso.rdf.jena.RDFAsJenaModel
import es.weso.schema._
import io.circe.Json
import journal.Logger
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.{Lang, RDFDataMgr, RDFFormat}

import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
  * ShaclValidator implementation based on ''es.weso.schema'' validator.  It does not impose the use of a particular
  * effect handling implementation.
  *
  * @param importResolver a transitive import resolver for schemas
  * @param F              an implicitly available MonadError typeclass for ''F[_]''
  * @tparam F the monadic effect type
  */
final class ShaclValidator[F[_]](importResolver: ImportResolver[F])(implicit F: MonadError[F, Throwable]) {

  private val logger = Logger[this.type]

  private val jsonLdFormatName = RDFFormat.JSONLD.getLang.getName
  private val schemaEngine = ShaclexSchema.empty.name
  private val triggerMode = TargetDeclarations.name

  /**
    * Validates ''data'' in its json representation against the specified ''schema''.  It produces a
    * ''ValidationReport'' in the ''F[_]'' context.
    *
    * @param schema the shacl schema instance against which data is validated
    * @param data   the data to be validated
    * @return a ''ValidationReport'' in the ''F[_]'' context
    */
  def apply(schema: ShaclSchema, data: Json): F[ValidationReport] =
    loadData(data) product loadSchema(schema.value) flatMap {
      case (mod, sch) => validate(mod, sch)
    } recoverWith {
      case CouldNotFindImports(missing) =>
        F.pure(ValidationReport(missing.toList.map(imp => ValidationResult(s"Could not load import '$imp'"))))
      case IllegalImportDefinition(values) =>
        F.pure(ValidationReport(values.toList.map(imp => ValidationResult(s"The provided import '$imp' is invalid"))))
      // $COVERAGE-OFF$
      case _: FailedToLoadData =>
        F.pure(ValidationReport(List(ValidationResult("The data format is invalid"))))
      // $COVERAGE-ON$
    }

  /**
    * Validates the argument ''schema'' against its specification.  It produces a ''ValidationReport'' in the ''F[_]''
    * context.
    *
    * @param schema the schema instance to be validated
    * @return a ''ValidationReport'' in the ''F[_]'' context
    */
  def apply(schema: ShaclSchema): F[ValidationReport] =
    loadSchema(schema.value)
      .map {
        case s if s.shapes.isEmpty => ValidationReport(List(ValidationResult("The schema has no shapes defined")))
        case _                     => ValidationReport(Nil)
      }
      .recoverWith {
        case CouldNotFindImports(missing) =>
          F.pure(ValidationReport(missing.toList.map(imp => ValidationResult(s"Could not load import '$imp'"))))
        case IllegalImportDefinition(values) =>
          F.pure(ValidationReport(values.toList.map(imp => ValidationResult(s"The provided import '$imp' is invalid"))))
        // $COVERAGE-OFF$
        case _: FailedToLoadShaclSchema =>
          F.pure(ValidationReport(List(ValidationResult("The schema is invalid"))))
        // $COVERAGE-ON$
      }

  private def loadSchema(schema: Json): F[Schema] = {
    logger.debug("Loading schema for validation")
    importResolver(ShaclSchema(schema))
      .flatMap { set =>
        Try {
          logger.debug(s"Loaded '${set.size}' imports, aggregating shapes")
          val model = ModelFactory.createDefaultModel()
          set.foreach { e =>
            RDFDataMgr.read(model, new ByteArrayInputStream(e.value.noSpaces.getBytes), Lang.JSONLD)
          }
          RDFDataMgr.read(model, new ByteArrayInputStream(schema.noSpaces.getBytes), Lang.JSONLD)
          model
        } flatMap { model => Schemas.fromRDF(RDFAsJenaModel(model), schemaEngine) } match {
          case Success(value) =>
            logger.debug("Schema loaded successfully")
            F.pure(value)
          case Failure(missing: CouldNotFindImports)    =>
            logger.debug(s"Failed to load schema '${schema.spaces4}' for validation, missing imports '${missing.missing}'")
            F.raiseError(missing)
          case Failure(ve: ShaclValidatorErr) =>
            F.raiseError(ve)
          case Failure(NonFatal(th)) =>
            logger.debug(s"Failed to load schema '${schema.spaces4}' for validation")
            F.raiseError(FailedToLoadShaclSchema(th))
        }
      }
  }

  private def loadData(data: Json): F[RDFAsJenaModel] =
    F.pure {
      logger.debug("Loading data for validation")
      RDFAsJenaModel.fromChars(data.noSpaces, jsonLdFormatName)
    } flatMap {
      case Success(value) =>
        logger.debug("Data loaded successfully")
        F.pure(value)
      // $COVERAGE-OFF$
      case Failure(th)    =>
        logger.debug(s"Failed to load schema '${data.spaces4}' for validation")
        F.raiseError(FailedToLoadData(th))
      // $COVERAGE-ON$
    }

  private def validate(model: RDFAsJenaModel, schema: Schema): F[ValidationReport] =
    F.pure {
      logger.debug("Validating data against schema")
      schema.validate(model, triggerMode, Map.empty, None, None, schema.pm)
    } map { result =>
      logger.debug(s"Validation result '$result'")
      if (!result.isValid) ValidationReport(result.errors.map(err => ValidationResult(err.msg)).toList)
      else if (!hasSolutions(result)) ValidationReport(List(ValidationResult("No data was selected for validation")))
      else ValidationReport(Nil)
    }

  private def hasSolutions(result: Result): Boolean =
    result.solutions.forall(_.nodes.nonEmpty)
}

object ShaclValidator {
  /**
    * Constructs a new ''ShaclValidator'' instance with an ''F[_]'' context using an implicitly available ''MonadError''
    * typeclass for ''F[_]''.
    *
    * @param importResolver a transitive import resolver for schemas
    * @param F              an implicitly available MonadError typeclass for ''F[_]''
    * @tparam F the monadic effect type
    * @return a new ''ShaclValidator'' instance with an ''F[_]'' context
    */
  final def apply[F[_]](importResolver: ImportResolver[F])(implicit F: MonadError[F, Throwable]): ShaclValidator[F] =
    new ShaclValidator[F](importResolver)
}