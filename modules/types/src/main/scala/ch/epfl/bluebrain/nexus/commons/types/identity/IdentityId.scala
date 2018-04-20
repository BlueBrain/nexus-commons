package ch.epfl.bluebrain.nexus.commons.types.identity

import cats.Show
import cats.syntax.show._
import io.circe.{Decoder, Encoder}

/**
  * An identity identifier.
  *
  * @param id the unique identity identifier
  */
final case class IdentityId(id: String)

object IdentityId {

  /**
    * The prefix of the identityId
    *
    * @param prefix the value of the prefix
    */
  final case class IdentityIdPrefix(prefix: String) {

    /**
      * Append a value to the prefix as a path
      *
      * @param value the path value to append to the prefix
      */
    def appendAsPath(value: String): String =
      this match {
        case IdentityIdPrefix.Empty                              => value
        case _ if prefix.endsWith("/") || prefix.startsWith("/") => s"$prefix$value"
        case _                                                   => s"$prefix/$value"
      }
  }
  object IdentityIdPrefix {

    /**
      * An empty prefix
      */
    val Empty: IdentityIdPrefix = IdentityIdPrefix("")
  }

  final implicit val identityIdShow: Show[IdentityId] = Show.show(_.id)

  final implicit val identityIdEncoder: Encoder[IdentityId] = Encoder.encodeString.contramap(_.show)
  final implicit val identityIdDecider: Decoder[IdentityId] = Decoder.decodeString.map(IdentityId(_))
}
