package com.asamm.osmTools.overviewMap.centerline

import java.util.PriorityQueue

/**
 * Lightweight undirected weighted graph backed by an adjacency list.
 * Provides Dijkstra's shortest path for centerline extraction.
 */
class WeightedGraph {

    // node ID -> (neighbor ID -> edge weight)
    private val adjacency = mutableMapOf<Int, MutableMap<Int, Double>>()

    fun addEdge(u: Int, v: Int, weight: Double) {
        adjacency.getOrPut(u) { mutableMapOf() }[v] = weight
        adjacency.getOrPut(v) { mutableMapOf() }[u] = weight
    }

    fun neighbors(node: Int): Map<Int, Double> = adjacency[node] ?: emptyMap()

    fun degree(node: Int): Int = adjacency[node]?.size ?: 0

    fun nodes(): Set<Int> = adjacency.keys

    /** Returns all nodes with exactly one neighbor (skeleton endpoints). */
    fun endNodes(): List<Int> = adjacency.keys.filter { degree(it) == 1 }

    /**
     * Dijkstra shortest path from [source] to [target].
     * @return (totalDistance, path) or null if unreachable.
     */
    fun dijkstra(source: Int, target: Int): Pair<Double, List<Int>>? {
        val dist = mutableMapOf<Int, Double>().withDefault { Double.MAX_VALUE }
        val prev = mutableMapOf<Int, Int>()
        val visited = mutableSetOf<Int>()

        dist[source] = 0.0
        // PriorityQueue of (distance, nodeId)
        val queue = PriorityQueue<Pair<Double, Int>>(compareBy { it.first })
        queue.add(0.0 to source)

        while (queue.isNotEmpty()) {
            val (d, u) = queue.poll()
            if (!visited.add(u)) continue
            if (u == target) break

            for ((v, w) in neighbors(u)) {
                if (v in visited) continue
                val alt = d + w
                if (alt < dist.getValue(v)) {
                    dist[v] = alt
                    prev[v] = u
                    queue.add(alt to v)
                }
            }
        }

        val targetDist = dist[target] ?: return null
        if (targetDist == Double.MAX_VALUE) return null

        // Reconstruct path
        val path = mutableListOf<Int>()
        var current: Int? = target
        while (current != null) {
            path.add(current)
            current = prev[current]
        }
        path.reverse()
        return targetDist to path
    }
}
