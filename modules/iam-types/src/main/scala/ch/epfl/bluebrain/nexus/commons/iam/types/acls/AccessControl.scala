package ch.epfl.bluebrain.nexus.commons.iam.types.acls

import ch.epfl.bluebrain.nexus.commons.iam.types.identity.Identity

/**
  * Type definition that is essentially a pair consisting of an ''identity'' and its associated ''permissions''.
  */
final case class AccessControl(identity: Identity, permissions: Permissions)
