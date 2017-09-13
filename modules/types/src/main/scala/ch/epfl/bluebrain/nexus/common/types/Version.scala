package ch.epfl.bluebrain.nexus.common.types

import cats.{Eq, Order, Show}
import io.circe.{Decoder, Encoder}

import scala.util.Try
import scala.util.matching.Regex

/**
  * Describes a semantic version as defined at ''http://semver.org/''.
  *
  * @param major version when you make incompatible API changes
  * @param minor version when you add functionality in a backwards-compatible manner
  * @param patch version when you make backwards-compatible bug fixes
  * @throws IllegalArgumentException if any of the provided values is not positive
  */
final case class Version(major: Int, minor: Int, patch: Int) {
  require(major >= 0, "Version major must be positive")
  require(minor >= 0, "Version minor must be positive")
  require(patch >= 0, "Version patch must be positive")

  /**
    * @return a major.minor precision partial version
    */
  def dropPatch: PartialVersion =
    PartialVersion(major, List(minor))

  /**
    * @return a major precision partial version
    */
  def dropMinor: PartialVersion =
    PartialVersion(major, Nil)

  /**
    * Checks whether ''this'' version is compatible with the argument partial version.  A ''Version'' is considered
    * compatible with a ''PartialVersion'' when the values defined in the partial version are equal to the same values
    * in the total version.
    *
    * @param partial the partial version to check compatibility against
    * @return true if the versions are compatible, false otherwise
    */
  def isCompatible(partial: PartialVersion): Boolean =
    partial.isCompatible(this)

  /**
    * @return a new version with the patch incremented with one unit
    */
  def bumpPatch: Version =
    Version(major, minor, patch + 1)

  /**
    * @return a new version with the minor incremented with one unit and patch reset to 0.
    */
  def bumpMinor: Version =
    Version(major, minor + 1, 0)

  /**
    * @return a new version with the major incremented with one unit and minor and patch reset to 0.
    */
  def bumpMajor: Version =
    Version(major + 1, 0, 0)

  /**
    * @return this version represented as a partial version with full precision.
    */
  def asPartial: PartialVersion =
    PartialVersion(major, minor, patch)
}

object Version extends VersionInstances {

  private final val regex: Regex = """v([0-9]+)\.([0-9]+)\.([0-9]+)""".r

  /**
    * Attempts to convert the argument ''string'' into a ''Version''.  Failure to provide a proper argument will result
    * in a ''None''.
    *
    * @param string the value to attempt to convert into a version.
    * @return ''Some[Version]'' for a valid argument, ''None'' otherwise
    */
  final def apply(string: String): Option[Version] = string match {
    case regex(major, minor, patch) => Try(Version(major.toInt, minor.toInt, patch.toInt)).toOption
    case _                          => None
  }
}

trait VersionInstances {

  final implicit val versionShow: Show[Version] = Show.show { v =>
    s"v${v.major}.${v.minor}.${v.patch}"
  }

  final implicit val versionEq: Eq[Version] = Eq.fromUniversalEquals[Version]

  final implicit val versionOrder: Order[Version] = Order.from {
    case (Version(lMajor, lMinor, lPatch), Version(rMajor, rMinor, rPatch)) =>
      if (lMajor < rMajor) -1
      else if (lMajor > rMajor) 1
      else if (lMinor < rMinor) -1
      else if (lMinor > rMinor) 1
      else if (lPatch < rPatch) -1
      else if (lPatch > rPatch) 1
      else 0
  }

  final implicit val versionEncoder: Encoder[Version] = Encoder.encodeString.contramap[Version] { v =>
    versionShow.show(v)
  }

  final implicit val versionDecoder: Decoder[Version] = Decoder.decodeString.emap { string =>
    Version(string).map(v => Right(v)).getOrElse(Left("Illegal version format"))
  }
}

/**
  * Describes a semantic version as defined at ''http://semver.org/'' with a varied precision [major .. patch].
  *
  * @param major version when you make incompatible API changes
  * @param rest a possibly empty listing to contain the ''minor'' and ''patch'' values
  * @throws IllegalArgumentException if any of the provided values is not positive or if the ''rest'' size is higher
  *                                  than 2.
  */
@SuppressWarnings(Array("ListSize"))
final case class PartialVersion(major: Int, rest: List[Int]) {
  require(major >= 0, "Version major must be positive")
  require(rest.size <= 2, "Partial version must have at most 3 segments")
  require(rest.forall(_ >= 0), "Partial version must have optional segments positive")

  /**
    * Checks whether ''this'' partial version is compatible with the argument version.  A ''Version'' is considered
    * compatible with a ''PartialVersion'' when the values defined in the partial version are equal to the same values
    * in the total version.
    *
    * @param ver the version to check compatibility against
    * @return true if the versions are compatible, false otherwise
    */
  def isCompatible(ver: Version): Boolean = (this, ver) match {
    case (PartialVersion(pMajor, Nil), Version(vMajor, _, _))                               =>
      pMajor == vMajor
    case (PartialVersion(pMajor, pMinor :: Nil), Version(vMajor, vMinor, _))                =>
      pMajor == vMajor && pMinor == vMinor
    case (PartialVersion(pMajor, pMinor :: pPatch :: Nil), Version(vMajor, vMinor, vPatch)) =>
      pMajor == vMajor && pMinor == vMinor && pPatch == vPatch
    case _                                                                                  =>
      false
  }

  /**
    * @return a total ''Version'' by assigning to the missing values a 0.  I.e.:
    *         {{{
    *           PartialVersion(1, 2).toVersion == Version(1, 2, 0) // yields true
    *         }}}
    */
  def toVersion: Version = rest match {
    case Nil                   => Version(major, 0, 0)
    case minor :: Nil          => Version(major, minor, 0)
    case minor :: patch :: Nil => Version(major, minor, patch)
    case _                     => throw new IllegalStateException
  }
}

object PartialVersion extends PartialVersionInstances {
  private final val regex: Regex = """v([0-9]+)(?:\.([0-9]+))?(?:\.([0-9]+))?""".r

  /**
    * Attempts to convert the argument ''string'' into a ''PartialVersion''.  Failure to provide a proper argument will
    * result in a ''None''.  It tolerates missing minor and patch values.
    *
    * @param string the value to attempt to convert into a partial version.
    * @return ''Some[PartialVersion]'' for a valid argument, ''None'' otherwise
    */
  final def apply(string: String): Option[PartialVersion] = string match {
    case regex(major, null, null)   => Try(PartialVersion(major.toInt)).toOption
    case regex(major, minor, null)  => Try(PartialVersion(major.toInt, minor.toInt)).toOption
    case regex(major, minor, patch) => Try(PartialVersion(major.toInt, minor.toInt, patch.toInt)).toOption
    case _                          => None
  }

  /**
    * Constructs a new ''PartialVersion'' instance with the major precision supplied by the argument value.
    *
    * @param major version when you make incompatible API changes
    * @return a new ''PartialVersion'' instance with the major precision supplied by the argument value
    */
  final def apply(major: Int): PartialVersion =
    PartialVersion(major, Nil)

  /**
    * Constructs a new ''PartialVersion'' instance with the minor precision supplied by the argument values.
    *
    * @param major version when you make incompatible API changes
    * @param minor version when you add functionality in a backwards-compatible manner
    * @return a new ''PartialVersion'' instance with the minor precision supplied by the argument values
    */
  final def apply(major: Int, minor: Int): PartialVersion =
    PartialVersion(major, minor :: Nil)

  /**
    * Constructs a new ''PartialVersion'' instance with the full precision.
    *
    * @param major version when you make incompatible API changes
    * @param minor version when you add functionality in a backwards-compatible manner
    * @param patch version when you make backwards-compatible bug fixes
    * @return a new full precision ''PartialVersion'' instance
    */
  final def apply(major: Int, minor: Int, patch: Int): PartialVersion =
    PartialVersion(major, minor :: patch :: Nil)
}

trait PartialVersionInstances {
  final implicit val partialVersionShow: Show[PartialVersion] = Show.show { pv =>
    s"v${pv.major}" + pv.rest.foldLeft("") {
      case (acc, elem) => acc + "." + elem
    }
  }

  final implicit val partialVersionEq: Eq[PartialVersion] =
    Eq.fromUniversalEquals[PartialVersion]

  final implicit val partialVersionEncoder: Encoder[PartialVersion] = Encoder.encodeString.contramap { pv =>
    partialVersionShow.show(pv)
  }

  final implicit val partialVersionDecoder: Decoder[PartialVersion] = Decoder.decodeString.emap { string =>
    PartialVersion(string).map(v => Right(v)).getOrElse(Left("Illegal partial version format"))
  }
}