package ch.epfl.bluebrain.nexus.commons.shacl.validator

import cats.MonadError

/**
  * Type definition that transitively imports referenced schemas.
  *
  * @tparam F the monadic effect type
  */
trait ImportResolver[F[_]] {

  /**
    * Transitively resolves the schema imports referenced in the argument ''schema''.
    *
    * @param schema the schema for which imports are being resolved
    * @return the collection of imported schemas
    */
  def apply(schema: ShaclSchema): F[Set[ShaclSchema]]

}

object ImportResolver {

  /**
    * Noop ''ImportResolver'' the returns an empty set of schemas.
    *
    * @param F  an implicitly available MonadError typeclass for ''F[_]''
    * @tparam F the monadic effect type
    */
  final def noop[F[_]](implicit F: MonadError[F, Throwable]): ImportResolver[F] =
    (_: ShaclSchema) => F.pure(Set.empty)
}
