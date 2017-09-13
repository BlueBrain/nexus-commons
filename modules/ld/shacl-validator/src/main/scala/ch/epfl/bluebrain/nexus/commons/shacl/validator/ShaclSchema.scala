package ch.epfl.bluebrain.nexus.commons.shacl.validator

import io.circe.Json

/**
  * Value class to tag json instances that are meant to contain shacl schema definitions.
  *
  * @param value the underlying shacl schema json
  */
final class ShaclSchema(val value: Json) extends AnyVal

object ShaclSchema {

  /**
    * Builds a new ''ShaclSchema'' instance from the argument json value
    *
    * @param value the json value that represents a shacl schema
    * @return a new ''ShaclSchema'' instance
    */
  final def apply(value: Json): ShaclSchema =
    new ShaclSchema(value)
}