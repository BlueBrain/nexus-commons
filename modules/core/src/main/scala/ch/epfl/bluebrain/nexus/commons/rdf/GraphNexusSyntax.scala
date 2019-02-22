package ch.epfl.bluebrain.nexus.commons.rdf

import ch.epfl.bluebrain.nexus.rdf.Node.{BNode, IriNode, IriOrBNode}
import ch.epfl.bluebrain.nexus.rdf.Vocabulary._
import GraphNexusSyntax.{NexusGraphOps, NexusSubjectGraphOps}
import ch.epfl.bluebrain.nexus.rdf.cursor.GraphCursor
import ch.epfl.bluebrain.nexus.rdf.instances._
import ch.epfl.bluebrain.nexus.rdf.{Graph, RootedGraph}

trait GraphNexusSyntax {

  final implicit def nexusGraphSyntax(graph: Graph): NexusGraphOps = new NexusGraphOps(graph)
  final implicit def nexusSubjectGraphSyntax(graph: RootedGraph): NexusSubjectGraphOps =
    new NexusSubjectGraphOps(graph)

}
object GraphNexusSyntax {

  final class NexusGraphOps(private val graph: Graph) extends AnyVal {

    /**
      * @return The optionally available root ''subject'' of the Graph. This is, the subject which is not used as an object
      */
    def rootNode: Option[IriOrBNode] =
      (graph.subjects() -- graph.objects().collect { case iri: IriOrBNode => iri }).toList match {
        case head :: Nil => Some(head)
        case _           => None
      }

    /**
      * @return The optionally available blank node root ''subject'' of the Graph. This is, the subject which is not used as an object
      */
    def rootBNode: Option[BNode] =
      rootNode.flatMap(_.asBlank)

    /**
      * @return The optionally available iri node root ''subject'' of the Graph. This is, the subject which is not used as an object
      */
    def rootIriNode: Option[IriNode] =
      rootNode.flatMap(_.asIri)

    /**
      * @return the list of objects which have the subject found from the method ''id'' and the predicate rdf:type
      */
    def rootTypes: Set[IriNode] =
      rootNode.map(types).getOrElse(Set.empty)

    /**
      * @param  id the id for which the types should be found
      * @return the list of objects which have the subject ''id'' and the predicate rdf:type
      */
    def types(id: IriOrBNode): Set[IriNode] =
      graph.objects(id, rdf.tpe).collect { case n: IriNode => n }

    /**
      * @return the initial cursor of the ''graph'', centered in the ''rootNode''
      */
    def cursor(): GraphCursor = rootNode.map(graph.cursor).getOrElse(GraphCursor.failed)
  }

  final class NexusSubjectGraphOps(private val rootedGraph: RootedGraph) extends AnyVal {

    /**
      * @return the list of objects which have the subject found from the method ''id'' and the predicate rdf:type
      */
    def rootTypes: Set[IriNode] = types(rootedGraph.rootNode)

    /**
      * @param  id the id for which the types should be found
      * @return the list of objects which have the subject ''id'' and the predicate rdf:type
      */
    def types(id: IriOrBNode): Set[IriNode] =
      rootedGraph.objects(id, rdf.tpe).collect { case n: IriNode => n }

    /**
      * @return the initial cursor of the ''graph'', centered in the ''rootNode''
      */
    def cursor(): GraphCursor = rootedGraph.cursor(rootedGraph.rootNode)
  }
}
