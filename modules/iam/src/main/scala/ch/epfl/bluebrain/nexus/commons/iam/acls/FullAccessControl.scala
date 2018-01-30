package ch.epfl.bluebrain.nexus.commons.iam.acls

import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity

/**
  * Type definition that contains the definition of an access control
  *
  * @param identity    the identity for the ACLs
  * @param path        the path where the ACLs apply
  * @param permissions the permissions that are contained on the ACLs
  */
final case class FullAccessControl(identity: Identity, path: Path, permissions: Permissions)
