package ch.epfl.bluebrain.nexus.commons.iam.acls

/**
  * Type defining a pair of the field ''path'' and its associated ''permissions''.
  *
  * @param path        the path of the permissions
  * @param permissions the permissions
  **/
final case class PathAccessControl(path: Path, permissions: Permissions)
