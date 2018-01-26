package ch.epfl.bluebrain.nexus.commons.iam.acls

import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity

import scala.collection.immutable.ListMap

/**
  * Type definition representing a mapping of identities to permissions for a specific resource.
  *
  * @param acls a set of [[AccessControl]] pairs.
  */
final case class IdentityAccessControlList(acls: Set[SingleIdentityAccessControlList]) {

  /**
    * @return a ''Map'' projection of the underlying pairs of path and their permissions
    */
  def aggregatedMap: Map[Path, Permissions] = acls.foldLeft(ListMap.empty[Path, Permissions]) {
    case (acc, identityAcl) =>
      acc ++ identityAcl.aggregatedMap.map {
        case (path, perms) => path -> (acc.getOrElse(path, Permissions.empty) ++ perms)
      }
  }
}
object IdentityAccessControlList {

  /**
    * Convenience factory method to build an ACLS
    *
    * @param acls the pairs of ''identity'' and ''acls'' for that identity
    */
  final def apply(acls: (Identity, List[PathAccessControl])*): IdentityAccessControlList =
    IdentityAccessControlList(acls.map { case (identity, acl) => SingleIdentityAccessControlList(identity, acl) }.toSet)
}

final case class SingleIdentityAccessControlList(identity: Identity, acl: List[PathAccessControl]) {

  /**
    * @return a ''Map'' projection of the underlying pairs of path and their permissions
    */
  def aggregatedMap: ListMap[Path, Permissions] = acl.foldLeft(ListMap.empty[Path, Permissions]) {
    case (acc, PathAccessControl(path, perms)) => acc + (path -> perms)
  }
}
