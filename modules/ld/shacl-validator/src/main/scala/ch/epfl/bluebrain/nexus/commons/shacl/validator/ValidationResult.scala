package ch.epfl.bluebrain.nexus.commons.shacl.validator

/**
  * A ''ValidationResult'' describes a constraint violation resulting from validating data against a shacl schema.
  *
  * @param reason a descriptive reason as to why the violation occurred
  */
final case class ValidationResult(reason: String)
