package it.unibo.alchemist.loader.export

import it.unibo.alchemist.model.interfaces.Environment
import it.unibo.alchemist.model.interfaces.Molecule
import it.unibo.alchemist.model.interfaces.Node
import it.unibo.alchemist.model.interfaces.Reaction
import it.unibo.alchemist.model.interfaces.Time
import org.jgrapht.alg.shortestpath.DijkstraShortestPath
import org.jgrapht.graph.SimpleGraph
import java.util.*

class GuerreroClusterIntraDistance @JvmOverloads constructor(
        val useHopCount: Boolean = false,
        leaderIdMolecule: Molecule
) : ClusterBasedMetric(leaderIdMolecule) {

    override fun <T> extractData(
        environment: Environment<T, *>,
        reaction: Reaction<T>,
        time: Time,
        step: Long,
        clusters: Map<Int, List<Node<T>>>
    ): DoubleArray = doubleArrayOf(
        clusters.map { (leaderId, clusterNodes) ->
            val center = environment.getNodeByID(leaderId)
            val shortestPath = environment.shortestPathInCluster(clusterNodes, center) { first, other ->
                if (useHopCount) 1.0 else environment.getDistanceBetweenNodes(first, other)
            }
            clusterNodes.asSequence()
                .map { shortestPath.getPathWeight(center, it) }
                .average()
        }.average()
    )

    private fun <T> Environment<T, *>.shortestPathInCluster(
        cluster: List<Node<T>>,
        center: Node<T>,
        metric: (Node<T>, Node<T>) -> Double
    ): DijkstraShortestPath<Node<T>, Double> {
        val graph = SimpleGraph<Node<T>, Double>(null, null, true)
        val visited = mutableSetOf<Node<T>>()
        val toVisit = ArrayDeque<Node<T>>().also { it.add(center) }
        while (toVisit.isNotEmpty()) {
            var current = toVisit.poll()
            graph.addVertex(current)
            val neighbors = getNeighborhood(current).neighbors.filter { it in cluster && it !in visited }
            neighbors.forEach {
                graph.addVertex(it)
                graph.addEdge(current, it, metric(current, it))
            }
            toVisit.addAll(neighbors)
        }
        return DijkstraShortestPath(graph)
    }

    override fun getNames(): List<String> = listOf("ClusterIntraDistance")

}