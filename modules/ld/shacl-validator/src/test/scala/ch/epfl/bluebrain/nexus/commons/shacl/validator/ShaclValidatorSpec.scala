package ch.epfl.bluebrain.nexus.commons.shacl.validator

import java.io.ByteArrayInputStream

import cats.instances.try_._
import es.weso.rdf.jena.RDFAsJenaModel
import es.weso.schema.{Schemas, ShaclexSchema}
import io.circe.Json
import io.circe.parser._
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.{Lang, RDFDataMgr, RDFFormat}
import org.scalatest.{EitherValues, Matchers, TryValues, WordSpecLike}

import scala.io.Source
import scala.util.Try

class ShaclValidatorSpec extends WordSpecLike with Matchers with TryValues with EitherValues {

  private def contentOf(file: String): String =
    Source.fromInputStream(getClass.getResourceAsStream(file)).mkString

  private def jsonContentOf(file: String): Json =
    parse(contentOf(file)).toTry.success.value

  "A ShaclValidator" should {
    val validator = ShaclValidator[Try](ImportResolver.noop)

    "validate data against a schema both in json-ld format" in {
      val data   = jsonContentOf("/subject-data.json")
      val schema = ShaclSchema(jsonContentOf("/subject-schema.json"))

      validator(schema, data).success.value.conforms shouldEqual true
    }

    "validate the subject schema" in {
      validator(ShaclSchema(jsonContentOf("/subject-schema.json"))).success.value.conforms shouldEqual true
    }

    "fail to validate empty json object against subject schema" in {
      val data   = jsonContentOf("/empty.json")
      val schema = ShaclSchema(jsonContentOf("/subject-schema.json"))
      validator(schema, data).success.value.conforms shouldEqual false
    }

    "fail to validate invalid data against subject schema" in {
      val data   = jsonContentOf("/invalid-subject-data.json")
      val schema = ShaclSchema(jsonContentOf("/subject-schema.json"))
      validator(schema, data).success.value.conforms shouldEqual false
    }

    "fail to validate empty schema" in {
      validator(ShaclSchema(jsonContentOf("/empty.json"))).success.value.conforms shouldEqual false
    }

    "fail to validate invalid schema" in {
      validator(ShaclSchema(jsonContentOf("/invalid-subject-schema.json"))).success.value.conforms shouldEqual false
    }

    "validate data against a schema and data in as Jena model" in {
      val data = RDFAsJenaModel
        .fromChars(jsonContentOf("/subject-data.json").noSpaces, RDFFormat.JSONLD.getLang.getName)
        .right
        .value
      val jsonSchema = jsonContentOf("/subject-schema.json")
      val model      = ModelFactory.createDefaultModel()
      RDFDataMgr.read(model, new ByteArrayInputStream(jsonSchema.noSpaces.getBytes), Lang.JSONLD)
      val schema = Schemas.fromRDF(RDFAsJenaModel(model), ShaclexSchema.empty.name).right.value

      validator(schema, data).success.value.conforms shouldEqual true
    }

//    "validate the subject schema against the shacl schema" in {
//      val data = jsonContentOf("/subject-schema.json")
//      val schema = ShaclSchema(jsonContentOf("/shacl-schema.json"))
//
//      val result = validator(schema, data).success.value
//      pprint.log(result)
//      result shouldEqual ValidationReport(Nil)
//    }

//    "validate the shacl schema against itself" in {
//      val data = jsonContentOf("/shacl-schema.json")
//      val schema = ShaclSchema(jsonContentOf("/shacl-schema.json"))
//
//      val result = validator(schema, data).success.value
//      pprint.log(result)
//      result shouldEqual ValidationReport(Nil)
//    }
  }
}
