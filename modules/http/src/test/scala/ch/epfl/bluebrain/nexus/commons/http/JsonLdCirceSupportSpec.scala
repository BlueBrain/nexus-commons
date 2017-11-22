package ch.epfl.bluebrain.nexus.commons.http

import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.OrderedKeys
import ch.epfl.bluebrain.nexus.commons.test.Resources
import org.scalatest.{Inspectors, Matchers, WordSpecLike}
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._

class JsonLdCirceSupportSpec extends WordSpecLike with Matchers with Resources with Inspectors {

  "A JsonLdCirceSupport" when {

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
    }
  }
}
