package ch.epfl.bluebrain.nexus.commons.rdf.syntax

import cats.instances.either._
import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.{Graph, Node}
import ch.epfl.bluebrain.nexus.rdf.Node.{BNode, IriNode, IriOrBNode, blank}
import ch.epfl.bluebrain.nexus.rdf.Vocabulary.rdf
import GraphSyntaxSpec.nxv
import ch.epfl.bluebrain.nexus.rdf.instances._
import ch.epfl.bluebrain.nexus.rdf.syntax._
import io.circe.Json
import org.scalatest._
import ch.epfl.bluebrain.nexus.rdf.MarshallingError._
import ch.epfl.bluebrain.nexus.rdf.encoder.GraphEncoder.EncoderResult

class GraphSyntaxSpec
    extends WordSpecLike
    with Matchers
    with TryValues
    with OptionValues
    with EitherValues
    with Resources {

  "A GraphSyntax" should {
    val self         = jsonContentOf("/rdf/self-reference.json")
    val json         = jsonContentOf("/rdf/no_id.json")
    val typedJson    = jsonContentOf("/rdf/id_and_types.json")
    val arrayJson    = jsonContentOf("/rdf/array.json")
    val arrayOneJson = jsonContentOf("/rdf/array-one.json")

    val fRootNode: Graph => EncoderResult[IriOrBNode] = _.rootNode.toRight(rootNotFound())

    "find @id from a Json-LD without it" in {
      json.asGraph(blank).right.value.rootNode shouldBe a[BNode]
      json.asGraph(blank).right.value.rootBNode.value shouldBe a[BNode]
    }

    "find @id from Json-LD with it" in {
      typedJson
        .asGraph(fRootNode)
        .right
        .value
        .rootNode shouldEqual url"http://example.org/cars/for-sale#tesla"
      typedJson
        .asGraph(blank)
        .right
        .value
        .rootIriNode
        .value shouldEqual url"http://example.org/cars/for-sale#tesla"
    }

    "fail to find an @id when it is self-referenced" in {
      self.asGraph(fRootNode).left.value shouldEqual rootNotFound()
    }

    "find the @type from the Json-LD without @id" in {
      json.asGraph(fRootNode).right.value.rootTypes shouldEqual Set(url"http://google.com/a")

    }

    "find no types when it is self-referenced" in {
      self.asGraph(blank).right.value.rootTypes shouldEqual Set.empty
    }

    "find the @type from the Json-LD with @id" in {
      typedJson.asGraph(blank).right.value.types(url"http://example.org/cars/for-sale#tesla") shouldEqual Set(
        url"http://purl.org/goodrelations/v1#Offering",
        url"http://www.w3.org/2002/07/owl#Ontology")
      typedJson.asGraph(fRootNode).right.value.rootTypes shouldEqual Set(url"http://purl.org/goodrelations/v1#Offering",
                                                                         url"http://www.w3.org/2002/07/owl#Ontology")
    }

    "find return no types for id which doesn't have type predicates" in {
      typedJson
        .asGraph(blank)
        .right
        .value
        .types(url"http://example.org/cars/for-sale#other") shouldEqual Set.empty
    }

    "navigate to an element" in {
      json
        .asGraph(fRootNode)
        .right
        .value
        .cursor()
        .downField(url"http://schema.org/image")
        .focus
        .value shouldEqual (url"http://www.civil.usherbrooke.ca/cours/gci215a/empire-state-building.jpg": Node)
    }

    "return a failed cursor when @id is not found" in {
      self.asGraph(blank).right.value.cursor().downField(nxv.identities).failed shouldEqual true
    }

    "navigate a graph of array of objects" in {
      val cursor = arrayJson.asGraph(fRootNode).right.value.cursor()
      val result = cursor.downField(nxv.identities).downArray.map { cursor =>
        val r = for {
          realm <- cursor.downField(nxv.realm).focus.as[String]
          admin <- cursor.downField(nxv.admin).focus.as[Boolean]
          tpe   <- cursor.downField(rdf.tpe).values.asListOf[AbsoluteIri]
          key = if (tpe.contains(nxv.userRef.value)) nxv.user
          else if (tpe.contains(nxv.groupRef.value)) nxv.group
          else nxv.nonExists
          identity <- cursor.downField(key).focus.as[String]
        } yield (tpe, realm, identity, admin)
        r.right.value
      }
      result shouldEqual Set((List(nxv.userRef.value), "ldap2", "didac", false),
                             (List(nxv.groupRef.value), "ldap", "bbp-ou-neuroinformatics", true))
    }

    "navigate a graph of array of objects with one element" in {
      val c = arrayOneJson.asGraph(fRootNode).right.value.cursor()
      val result =
        c.downField(nxv.identities).downArray.map(_.downField(nxv.realm).focus.as[String].right.value)
      result shouldEqual Set("some-realm")
    }

  }

  def context(json: Json): Json = Json.obj("@context" -> json.hcursor.get[Json]("@context").getOrElse(Json.obj()))

}

object GraphSyntaxSpec {
  object nxv {
    val identities: IriNode = url"http://www.example.com/vocab/identities"
    val realm: IriNode      = url"http://www.example.com/vocab/realm"
    val group: IriNode      = url"http://www.example.com/vocab/group"
    val user: IriNode       = url"http://www.example.com/vocab/user"
    val userRef: IriNode    = url"http://www.example.com/vocab/UserRef"
    val admin: IriNode      = url"http://www.example.com/vocab/admin"
    val groupRef: IriNode   = url"http://www.example.com/vocab/GroupRef"
    val nonExists: IriNode  = url"http://www.example.com/vocab/nonExists"
  }
}
