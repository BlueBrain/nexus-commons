package ch.epfl.bluebrain.nexus.commons.http

import akka.http.scaladsl.server.Directives.{complete, get}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.{OrderedKeys, _}
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupportSpec._
import ch.epfl.bluebrain.nexus.commons.test.Resources
import io.circe.Json
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import org.scalatest.{Inspectors, Matchers, WordSpecLike}

import scala.collection.mutable.LinkedHashSet

class JsonLdCirceSupportSpec extends WordSpecLike with Matchers with Resources with Inspectors with ScalatestRouteTest {

  "A JsonLdCirceSupport" when {
    implicit val config = Configuration.default.withDiscriminator("@type")

    "dealing with KG data" should {
      val list = List(
        (jsonContentOf("/kg_json/activity_schema.json")   -> jsonContentOf("/kg_json/activity_schema_ordered.json")),
        (jsonContentOf("/kg_json/activity_instance.json") -> jsonContentOf("/kg_json/activity_instance_ordered.json")),
        (jsonContentOf("/kg_json/activity_instance_att.json") -> jsonContentOf(
          "/kg_json/activity_instance_att_ordered.json"))
      )
      implicit val _ = OrderedKeys(
        List(
          "@context",
          "@id",
          "@type",
          "",
          "nxv:rev",
          "nxv:originalFileName",
          "nxv:contentType",
          "nxv:size",
          "nxv:unit",
          "nxv:digest",
          "nxv:alg",
          "nxv:value",
          "nxv:published",
          "nxv:deprecated",
          "nxv:links",
          "nxv:rel",
          "nxv:href"
        ))

      "order jsonLD input" in {
        forAll(list) {
          case (unordered, expected) =>
            sortKeys(unordered).spaces2 shouldEqual expected.spaces2
        }
      }

      "generated jsonLD HTTP" in {
        val route = get {
          complete(
            KgResponse(
              "cValue",
              true,
              1L,
              "aValue",
              List(Links("http://localhost/link1", "self"), Links("http://localhost/link2", "schema")),
              "https://bbp-nexus.epfl.ch/dev/v0/schemas/bbp/core/schema/v0.1.0",
              false,
              "owl:Ontology",
              "https://bbp-nexus.epfl.ch/dev/v0/contexts/bbp/core/context/v0.1.0"
            ))
        }
        Get("/") ~> route ~> check {
          contentType shouldEqual RdfMediaTypes.`application/ld+json`.toContentType
          entityAs[Json].spaces2 shouldEqual jsonContentOf("/kg_json/kg_fake_schema.json").spaces2
        }
      }
    }

    "dealing with IAM data" should {
      val list = List(
        (jsonContentOf("/iam_json/acls.json") -> jsonContentOf("/iam_json/acls_ordered.json")),
        (jsonContentOf("/iam_json/user.json") -> jsonContentOf("/iam_json/user_ordered.json"))
      )

      implicit val _ = OrderedKeys(
        List(
          "@context",
          "@id",
          "@type",
          "identity",
          "permissions",
          "realm",
          "",
        ))

      "order jsonLD input" in {
        forAll(list) {
          case (unordered, expected) =>
            sortKeys(unordered).spaces2 shouldEqual expected.spaces2
        }
      }

      "generated jsonLD HTTP" in {
        val route = get {
          complete(
            AuthenticatedUser(LinkedHashSet(
              GroupRef("bbp-user-one", "BBP", "https://nexus.example.com/v0/realms/BBP/groups/bbp-user-one"),
              GroupRef("bbp-svc-two", "BBP", "https://nexus.example.com/v0/realms/BBP/groups/bbp-svc-two"),
              Anonymous("https://nexus.example.com/v0/anonymous"),
              UserRef("f:434t3-134e-4444-aa74-bdf00f48dfce:some",
                      "BBP",
                      "https://nexus.example.com/v0/realms/BBP/users/f:434t3-134e-4444-aa74-bdf00f48dfce:some"),
              AuthenticatedRef(Some("BBP"), "https://nexus.example.com/v0/realms/BBP/authenticated"),
            )): User)
        }
        Get("/") ~> route ~> check {
          contentType shouldEqual RdfMediaTypes.`application/ld+json`.toContentType
          entityAs[Json].spaces2 shouldEqual jsonContentOf("/iam_json/user_ordered.json").spaces2

        }
      }
    }
  }
}

object JsonLdCirceSupportSpec {
  final case class KgResponse(c: String,
                              `nxv:published`: Boolean,
                              `nxv:rev`: Long,
                              a: String,
                              `nxv:links`: List[Links],
                              `@id`: String,
                              `nxv:deprecated`: Boolean,
                              `@type`: String,
                              `@context`: String)
  final case class Links(href: String, rel: String)

  sealed trait User extends Product with Serializable {
    def identities: LinkedHashSet[Identity]
  }
  final case class AuthenticatedUser(identities: LinkedHashSet[Identity]) extends User
  sealed trait Identity extends Product with Serializable {
    def `@id`: String
  }
  final case class GroupRef(group: String, realm: String, `@id`: String)  extends Identity
  final case class UserRef(sub: String, realm: String, `@id`: String)     extends Identity
  final case class AuthenticatedRef(realm: Option[String], `@id`: String) extends Identity
  final case class Anonymous(`@id`: String)                               extends Identity
}
