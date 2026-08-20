package com.enrpau.dualscreendex.parser.dataset.abilities.analysis

/**
 * Finds the immediate post-dominator without materializing every node's complete
 * post-dominator set. The reversed-CFG dominator representation is linear in the
 * decoded graph size and rejects graphs containing paths that cannot reach an exit.
 */
internal object ImmediatePostDominator {
    fun <N : Any> find(entry: N, exit: N, successors: Map<N, Set<N>>): N? {
        if (entry == exit || entry !in successors) return null

        val nodes = linkedSetOf<N>().apply {
            add(exit)
            successors.forEach { (node, edges) ->
                add(node)
                addAll(edges)
            }
        }
        if (nodes.any { it != exit && it !in successors }) return null

        val reverseEdges = nodes.associateWithTo(linkedMapOf()) { mutableListOf<N>() }
        successors.forEach { (node, rawEdges) ->
            if (node == exit) return@forEach
            val edges = rawEdges.ifEmpty { setOf(exit) }
            edges.forEach { successor -> reverseEdges.getValue(successor).add(node) }
        }

        val visited = mutableSetOf<N>()
        val postOrder = mutableListOf<N>()
        val traversal = ArrayDeque<Pair<N, Boolean>>().apply { addLast(exit to false) }
        while (traversal.isNotEmpty()) {
            val (node, expanded) = traversal.removeLast()
            if (expanded) {
                postOrder += node
            } else if (visited.add(node)) {
                traversal.addLast(node to true)
                reverseEdges.getValue(node).asReversed().forEach { predecessor ->
                    if (predecessor !in visited) traversal.addLast(predecessor to false)
                }
            }
        }
        if (visited.size != nodes.size) return null

        val reversePostOrder = postOrder.asReversed()
        val order = reversePostOrder.withIndex().associate { (index, node) -> node to index }
        val immediateDominators = mutableMapOf<N, N>(exit to exit)

        fun intersect(first: N, second: N): N? {
            var left = first
            var right = second
            while (left != right) {
                while (order.getValue(left) > order.getValue(right)) {
                    left = immediateDominators[left] ?: return null
                }
                while (order.getValue(right) > order.getValue(left)) {
                    right = immediateDominators[right] ?: return null
                }
            }
            return left
        }

        var changed: Boolean
        do {
            changed = false
            reversePostOrder.drop(1).forEach { node ->
                val candidates = successors.getValue(node).ifEmpty { setOf(exit) }
                    .filter { it in immediateDominators }
                if (candidates.isEmpty()) return@forEach
                var updated = candidates.first()
                candidates.drop(1).forEach { candidate ->
                    updated = intersect(candidate, updated) ?: return null
                }
                if (immediateDominators[node] != updated) {
                    immediateDominators[node] = updated
                    changed = true
                }
            }
        } while (changed)

        if (immediateDominators.size != nodes.size) return null
        return immediateDominators[entry]?.takeUnless { it == entry || it == exit }
    }
}
