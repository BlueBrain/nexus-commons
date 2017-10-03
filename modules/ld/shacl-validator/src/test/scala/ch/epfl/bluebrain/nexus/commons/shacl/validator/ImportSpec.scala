package ch.epfl.bluebrain.nexus.commons.shacl.validator

import cats.instances.try_._
import io.circe.Json
import io.circe.parser.parse
import org.scalatest.{Matchers, TryValues, WordSpecLike}

import scala.io.Source
import scala.util.Try

class ImportSpec extends WordSpecLike with Matchers with TryValues {

  private val base     = "http://localhost/v0"
  private val resolver = ClasspathResolver[Try](base)

  private def jsonContentOf(resourcePath: String): Json =
    resolver.instanceJson(base) deepMerge
      parse(
        Source
          .fromInputStream(getClass.getResourceAsStream(resourcePath))
          .mkString
          .replaceAll(ClasspathResolver.token, base)
      ).toTry.get

  private def shaclSchema(address: String): ShaclSchema =
    ShaclSchema(resolver.shaclJson deepMerge resolver.schemaJson deepMerge jsonContentOf(address))

  private val validator = ShaclValidator[Try](ClasspathResolver[Try](base))

  "The 'nexus/core/instance' schema" should {
    val schema = shaclSchema("/schemas/nexus/core/instance/v1.0.0.json")
    "validate a proper payload" in {
      validator(schema, jsonContentOf("/instances/valid-instance.json")).success.value.conforms shouldBe true
    }
    "not validate improper payload" in {
      validator(schema, jsonContentOf("/instances/invalid-instance.json")).success.value.conforms shouldBe false
    }
  }

  "The 'bbp/core/entity/v1.0.0' schema" should {
    val schema = shaclSchema("/schemas/bbp/core/entity/v1.0.0.json")
    "validate a proper payload" in {
      validator(schema, jsonContentOf("/instances/valid-bbp-entity.json")).success.value.conforms shouldBe true
    }
    "not validate improper payload" in {
      validator(schema, jsonContentOf("/instances/invalid-bbp-entity.json")).success.value.conforms shouldBe false
    }
  }

  "The 'bbp/core/entity/v1.0.0-missing-import' schema" should {
    val schema = shaclSchema("/schemas/bbp/core/entity/v1.0.0-missing-import.json")
    "fail to load" in {
      validator(schema).success.value.conforms shouldBe false
    }
    "fail to validate" in {
      validator(schema, jsonContentOf("/instances/valid-bbp-entity.json")).success.value.conforms shouldBe false
    }
  }
}
