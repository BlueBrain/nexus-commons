package ch.epfl.bluebrain.nexus.commons.shacl.validator

/**
  * Data type that represents the outcome of validating data against a shacl schema.  A non empty list of validation
  * results implies that the data does not conform to the schema.
  *
  * @param result a collection of validation results that describe constraint violations
  */
final case class ValidationReport(result: List[ValidationResult]) {

  /**
    * Whether the data conforms to the schema.
    */
  lazy val conforms: Boolean = result.isEmpty
}
