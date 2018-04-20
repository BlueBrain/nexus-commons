package ch.epfl.bluebrain.nexus.commons.types

import java.time.Instant

import ch.epfl.bluebrain.nexus.commons.types.identity.Identity

/**
  * Type definition that aggregates ''author'' and ''timestamp'' as meta information for commands and events
  *
  * @param author  the origin of a Command or Event
  * @param instant the moment in time when a Command or Event were emitted
  */
final case class Meta(author: Identity, instant: Instant)
