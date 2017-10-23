package ch.epfl.bluebrain.nexus.commons.iam.types.identity

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.commons.iam.types.auth.AuthenticatedUser
import ch.epfl.bluebrain.nexus.commons.iam.types.identity.Caller.{AnonymousCaller, AuthenticatedCaller}
import ch.epfl.bluebrain.nexus.commons.iam.types.identity.Identity.{Anonymous, GroupRef}
import org.scalatest.{Inspectors, Matchers, WordSpecLike}

class CallerSpec extends WordSpecLike with Matchers with Inspectors {

  val groupIdentity = GroupRef(Uri("https://bbpteam.epfl.ch/auth/realms/BBP"), "/bbp-ou-nexus")

  private val validToken = "validToken"
  val caller             = AuthenticatedCaller(AuthenticatedUser(Set(groupIdentity)), validToken)
  "A Caller" should {

    "not have token when the caller is anonymous" in {
      AnonymousCaller.accessToken shouldEqual None
    }

    "have an anonymous identity when caller is anonymous" in {
      AnonymousCaller.identities shouldEqual Set(Anonymous)
    }

    "have token when the caller it isn't anonymous" in {
      caller.accessToken shouldEqual Some(validToken)
    }

    "have a group identity when caller is a group caller" in {
      caller.identities shouldEqual Set(groupIdentity)
    }
  }
}
