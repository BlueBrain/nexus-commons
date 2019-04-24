package ch.epfl.bluebrain.nexus.commons.rdf

import ch.epfl.bluebrain.nexus.commons.rdf.GraphNexusSyntax.NexusSubjectGraphOps
import ch.epfl.bluebrain.nexus.rdf.Node.{IriNode, IriOrBNode}
import ch.epfl.bluebrain.nexus.rdf.RootedGraph
import ch.epfl.bluebrain.nexus.rdf.Vocabulary._
import ch.epfl.bluebrain.nexus.rdf.cursor.GraphCursor
import ch.epfl.bluebrain.nexus.rdf.instances._

trait GraphNexusSyntax {

  final implicit def nexusSubjectGraphSyntax(graph: RootedGraph): NexusSubjectGraphOps =
    new NexusSubjectGraphOps(graph)

}
object GraphNexusSyntax {

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
