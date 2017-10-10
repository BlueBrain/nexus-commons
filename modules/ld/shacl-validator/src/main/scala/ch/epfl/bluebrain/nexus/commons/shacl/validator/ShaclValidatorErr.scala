package ch.epfl.bluebrain.nexus.commons.shacl.validator

import ch.epfl.bluebrain.nexus.commons.types.Err

/**
  * Top level error type for the sealed hierarchy of shacl validation errors.
  *
  * @param message a text describing the reason as to why this exception has been raised
  */
@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
sealed abstract class ShaclValidatorErr(message: String) extends Err(message)

@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
object ShaclValidatorErr {

  /**
    * An error that describes the failure to load a shacl schema into the validator.
    *
    * @param cause the underlying cause for this exception
    */
  final case class FailedToLoadShaclSchema(cause: Throwable) extends ShaclValidatorErr("Failed to load a shacl schema")

  /**
    * An error that describes the failure to load data into the validator.
    *
    * @param cause the underlying cause for this exception
    */
  final case class FailedToLoadData(cause: Throwable) extends ShaclValidatorErr("Failed to load the data graph")

  /**
    * An error that describes a failed attempt to load referenced schemas.
    *
    * @param missing the schema addresses that could not be loaded
    */
  final case class CouldNotFindImports(missing: Set[String])
      extends ShaclValidatorErr("Failed to load referenced schemas")

  /**
    * An error that describes a failure to follow a schema import.
    *
    * @param values the illegal imports
    */
  final case class IllegalImportDefinition(values: Set[String])
      extends ShaclValidatorErr("Failed to follow schema imports, illegal definition")
}
