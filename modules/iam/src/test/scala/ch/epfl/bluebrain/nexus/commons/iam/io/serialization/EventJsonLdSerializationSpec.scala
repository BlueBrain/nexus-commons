package ch.epfl.bluebrain.nexus.commons.iam.io.serialization

import java.time.Instant
import java.util.UUID

import akka.http.scaladsl.model.Uri
import cats.syntax.show._
import ch.epfl.bluebrain.nexus.commons.iam.acls.Event._
import ch.epfl.bluebrain.nexus.commons.iam.acls.Path._
import ch.epfl.bluebrain.nexus.commons.iam.acls.Permission._
import ch.epfl.bluebrain.nexus.commons.iam.acls.{AccessControlList, Event, Meta, Permissions}
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity._
import ch.epfl.bluebrain.nexus.commons.iam.identity.IdentityId
import io.circe.parser._
import io.circe.{Decoder, Encoder}
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{Matchers, WordSpecLike}
import EventJsonLdSerialization._

class EventJsonLdSerializationSpec extends WordSpecLike with Matchers with TableDrivenPropertyChecks {
  val apiUri: Uri                    = Uri("http://localhost/prefix")
  implicit val evEnc: Encoder[Event] = eventEncoder(apiUri)
  implicit val evDec: Decoder[Event] = eventDecoder
  private val m                      = jsonLdMarshaller[Event](apiUri)
  private val uuid                   = UUID.randomUUID.toString
  private val path                   = "foo" / "bar" / uuid
  private val local                  = "realm"
  private val user                   = UserRef(local, "alice")
  private val userExpanded           = user.copy(id = IdentityId(s"$apiUri/${user.id.show}"))
  private val group                  = GroupRef(local, "some-group")
  private val groupExpanded          = GroupRef(local, "some-group").copy(id = IdentityId(s"$apiUri/${group.id.show}"))
  private val authentcated           = AuthenticatedRef(Some("realm"))
  private val authenticatedExpanded  = authentcated.copy(id = IdentityId(s"$apiUri/${authentcated.id.show}"))
  private val anonymous              = Anonymous()
  private val anonymousExpanded      = anonymous.copy(id = IdentityId(s"$apiUri/${anonymous.id.show}"))

  private val meta         = Meta(user, Instant.ofEpochMilli(1))
  private val metaExpanded = Meta(userExpanded, Instant.ofEpochMilli(1))
  private val permissions  = Permissions(Own, Read, Write)

  private val context    = s""""${apiUri.withPath(apiUri.path / "context")}""""
  private val pathString = s""""${path.repr}""""
  private val groupString =
    s"""{"@id":"http://localhost/prefix/realms/realm/groups/some-group","@type":"GroupRef"}"""
  private val userString =
    s"""{"@id":"http://localhost/prefix/realms/realm/users/alice","@type":"UserRef"}"""
  private val authenticatedUser =
    s"""{"@id":"http://localhost/prefix/realms/realm/authenticated","@type":"AuthenticatedRef"}"""
  private val anonUser = s"""{"@id":"http://localhost/prefix/anonymous","@type":"Anonymous"}"""

  private val permissionsString = s"""["own","read","write"]"""
  private val acl               = s"""[{"identity":$userString,"permissions":$permissionsString}]"""
  private val metaString        = s"""{"author":$userString,"instant":"1970-01-01T00:00:00.001Z"}"""

  val results = Table(
    ("event", "eventExpanded", "json"),
    (PermissionsAdded(path, group, permissions, meta),
     PermissionsAdded(path, groupExpanded, permissions, metaExpanded),
     s"""{"@context":$context,"path":$pathString,"identity":$groupString,"permissions":$permissionsString,"meta":$metaString,"@type":"PermissionsAdded"}"""),
    (
      PermissionsSubtracted(path, user, permissions, meta),
      PermissionsSubtracted(path, userExpanded, permissions, metaExpanded),
      s"""{"@context":$context,"path":$pathString,"identity":$userString,"permissions":$permissionsString,"meta":$metaString,"@type":"PermissionsSubtracted"}"""
    ),
    (
      PermissionsCreated(path, AccessControlList(user         -> permissions), meta),
      PermissionsCreated(path, AccessControlList(userExpanded -> permissions), metaExpanded),
      s"""{"@context":$context,"path":$pathString,"acl":$acl,"meta":$metaString,"@type":"PermissionsCreated"}"""
    ),
    (PermissionsRemoved(path, group, meta),
     PermissionsRemoved(path, groupExpanded, metaExpanded),
     s"""{"@context":$context,"path":$pathString,"identity":$groupString,"meta":$metaString,"@type":"PermissionsRemoved"}"""),
    (PermissionsCleared(path, meta),
     PermissionsCleared(path, metaExpanded),
     s"""{"@context":$context,"path":$pathString,"meta":$metaString,"@type":"PermissionsCleared"}"""),
    (PermissionsAdded(path, authentcated, permissions, meta),
     PermissionsAdded(path, authenticatedExpanded, permissions, metaExpanded),
     s"""{"@context":$context,"path":$pathString,"identity":$authenticatedUser,"permissions":$permissionsString,"meta":$metaString,"@type":"PermissionsAdded"}"""),
    (PermissionsAdded(path, anonymous, permissions, meta),
     PermissionsAdded(path, anonymousExpanded, permissions, metaExpanded),
     s"""{"@context":$context,"path":$pathString,"identity":$anonUser,"permissions":$permissionsString,"meta":$metaString,"@type":"PermissionsAdded"}""")
  )

  "EventJsonLdEncoder" should {
    "encoder events to JSON" in {
      forAll(results) { (event, _, json) =>
        m(event) shouldBe json

      }
    }
  }
  "EventJsonLdDecoder" should {
    "decode events from JSON" in {
      forAll(results) { (_, event, json) =>
        decode[Event](json) shouldEqual Right(event)
      }
    }
  }
}
