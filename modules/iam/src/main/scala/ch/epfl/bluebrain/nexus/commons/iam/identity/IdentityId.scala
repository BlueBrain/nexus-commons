package ch.epfl.bluebrain.nexus.commons.iam.identity

import cats.Show
import io.circe.{Decoder, Encoder}

/**
  * An identity identifier.
  *
  * @param id the unique identity identifier
  */
final case class IdentityId(id: String)

object IdentityId {

  final implicit val identityIdShow: Show[IdentityId] = Show.show { _.id }

  final implicit val identityIdEncoder: Encoder[IdentityId] = Encoder.encodeString
    .contramap(id => identityIdShow.show(id))
  final implicit val identityIdDecider: Decoder[IdentityId] = Decoder.decodeString.map(IdentityId(_))
}
