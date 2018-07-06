package ch.epfl.bluebrain.nexus.commons.http.syntax

import ch.epfl.bluebrain.nexus.commons.http.{CirceSyntax, JsonLdCirceSupport}

object circe extends CirceSyntax {
  object marshalling extends JsonLdCirceSupport
}
