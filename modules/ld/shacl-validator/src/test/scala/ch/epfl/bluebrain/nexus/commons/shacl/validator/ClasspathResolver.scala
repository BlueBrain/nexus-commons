package ch.epfl.bluebrain.nexus.commons.shacl.validator

import java.io.ByteArrayInputStream
import java.util.regex.Pattern

import cats.MonadError
import cats.instances.list._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import ch.epfl.bluebrain.nexus.commons.shacl.validator.ShaclValidatorErr.{CouldNotFindImports, IllegalImportDefinition}
import org.apache.jena.rdf.model.{ModelFactory, ResourceFactory}
import org.apache.jena.riot.{Lang, RDFDataMgr}

import scala.collection.JavaConverters._
import scala.io.Source
import io.circe.parser._
import io.circe.Json

class ClasspathResolver[F[_]](base: String)(implicit F: MonadError[F, Throwable]) extends ImportResolver[F] {

  private lazy val imports = ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#imports")

  override def apply(schema: ShaclSchema): F[Set[ShaclSchema]] = {
    def loadImports(loaded: Map[String, ShaclSchema], imp: Set[String]): F[Set[ShaclSchema]] = {
      if (imp.isEmpty) F.pure(loaded.values.toSet)
      else {
        val diff = imp.filterNot(i => loaded.contains(i))
        if (diff.isEmpty) F.pure(loaded.values.toSet)
        else {
          for {
            seq <- diff.toList.map(i => load(i).map(sch => (i, sch))).sequence[F, (String, ShaclSchema)]
            sch = seq.map(_._2)
            imp <- importsOfAll(sch)
            res <- loadImports(loaded ++ seq, imp)
          } yield res
        }
      }
    }
    importsOf(schema).flatMap(imps => loadImports(Map.empty, imps))
  }

  private def importsOfAll(schemas: List[ShaclSchema]): F[Set[String]] =
    schemas.foldLeft[F[Set[String]]](F.pure(Set.empty[String])) {
      case (acc, elem) =>
        for {
          set <- acc
          imp <- importsOf(elem)
        } yield set ++ imp
    }

  private def importsOf(schema: ShaclSchema): F[Set[String]] = {
    val model = ModelFactory.createDefaultModel()
    RDFDataMgr.read(model, new ByteArrayInputStream(schema.value.noSpaces.getBytes), Lang.JSONLD)
    val nodes          = model.listObjectsOfProperty(imports).asScala.toSet
    val illegalImports = nodes.filter(!_.isURIResource)
    if (illegalImports.nonEmpty) F.raiseError(IllegalImportDefinition(illegalImports.map(n => n.toString)))
    else F.pure(nodes.map(_.asResource().getURI))
  }

  private def load(uri: String): F[ShaclSchema] = {
    if (uri.startsWith(base)) {
      val address = uri.substring(base.length) + ".json"
      val stream  = getClass.getResourceAsStream(address)
      if (stream == null) F.raiseError(CouldNotFindImports(Set(uri)))
      else {
        parse(Source.fromInputStream(stream).mkString.replaceAll(ClasspathResolver.token, base))
          .fold[F[ShaclSchema]](err => F.raiseError(err), json => F.pure(ShaclSchema(json)))
          .map { (sch: ShaclSchema) =>
            ShaclSchema(sch.value deepMerge schemaJson deepMerge shaclJson)
          }
      }
    } else F.raiseError(CouldNotFindImports(Set(uri)))
  }

  val schemaJson: Json =
    parse(
      Source
        .fromInputStream(getClass.getResourceAsStream("/defaults/schema.json"))
        .mkString
    ).toTry.get

  val shaclJson: Json =
    parse(
      Source
        .fromInputStream(getClass.getResourceAsStream("/defaults/shacl.json"))
        .mkString
    ).toTry.get

  def instanceJson(base: String): Json =
    parse(
      Source
        .fromInputStream(getClass.getResourceAsStream("/defaults/instance.json"))
        .mkString
        .replaceAll(ClasspathResolver.token, base)
    ).toTry.get
}

object ClasspathResolver {
  final def apply[F[_]](base: String)(implicit F: MonadError[F, Throwable]): ClasspathResolver[F] =
    new ClasspathResolver[F](base)

  val token: String = Pattern.quote("{{SERVICE_BASE}}")
}
