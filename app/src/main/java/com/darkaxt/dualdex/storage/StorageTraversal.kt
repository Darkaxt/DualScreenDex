package com.darkaxt.dualdex.storage

import java.io.File
import java.nio.file.Files
import java.util.ArrayDeque

data class StorageTraversalQuota(
    val maximumNodes: Int = 20_000,
    val maximumDirectories: Int = 4_000,
    val maximumFiles: Int = 16_000,
    val maximumResults: Int = 4_096,
) {
    init {
        require(maximumNodes > 0) { "storage node limit must be positive" }
        require(maximumDirectories > 0) { "storage directory limit must be positive" }
        require(maximumFiles > 0) { "storage file limit must be positive" }
        require(maximumResults > 0) { "storage result limit must be positive" }
    }
}

object StorageTraversalPolicy {
    val DEFAULT = StorageTraversalQuota()
}

class StorageTraversalLimitExceeded(message: String) : IllegalStateException(message)

internal class StorageTraversalBudget(
    private val quota: StorageTraversalQuota,
) {
    private var nodes = 0
    private var directories = 0
    private var files = 0
    private var results = 0

    fun enqueueNode() = checkLimit(++nodes, quota.maximumNodes, "node")

    fun visitDirectory() = checkLimit(++directories, quota.maximumDirectories, "directory")

    fun visitFile() = checkLimit(++files, quota.maximumFiles, "file")

    fun retainResult() = checkLimit(++results, quota.maximumResults, "result")

    private fun checkLimit(actual: Int, maximum: Int, name: String) {
        if (actual > maximum) throw StorageTraversalLimitExceeded("storage traversal exceeded the $name limit")
    }
}

class StorageTraversalOperation(
    quota: StorageTraversalQuota = StorageTraversalPolicy.DEFAULT,
) {
    internal val budget = StorageTraversalBudget(quota)
    private val visitedDirectories = mutableSetOf<String>()

    internal fun claimDirectory(identity: String): Boolean = visitedDirectories.add(identity)
}

internal object SafRomIndexRetention {
    fun retain(
        operation: StorageTraversalOperation,
        entries: MutableList<com.darkaxt.dualdex.retroarch.RomIndexEntry>,
        entry: com.darkaxt.dualdex.retroarch.RomIndexEntry,
    ) {
        operation.budget.retainResult()
        entries += entry
    }
}

internal object DirectFileTraversal {
    fun visitFiles(
        roots: Iterable<File>,
        quota: StorageTraversalQuota = StorageTraversalPolicy.DEFAULT,
        skipDirectory: (File) -> Boolean = { false },
        visitor: (File, StorageTraversalBudget) -> Unit,
    ) {
        val budget = StorageTraversalBudget(quota)
        val queue = ArrayDeque<File>()
        roots.forEach { root ->
            budget.enqueueNode()
            queue.addLast(root)
        }
        val visitedDirectories = mutableSetOf<String>()
        val visitedFiles = mutableSetOf<String>()
        while (queue.isNotEmpty()) {
            val candidate = runCatching { queue.removeFirst().canonicalFile }.getOrNull() ?: continue
            when {
                candidate.isDirectory && !skipDirectory(candidate) && visitedDirectories.add(candidate.path) -> {
                    budget.visitDirectory()
                    Files.newDirectoryStream(candidate.toPath()).use { children ->
                        children.forEach { child ->
                            budget.enqueueNode()
                            queue.addLast(child.toFile())
                        }
                    }
                }
                candidate.isFile && visitedFiles.add(candidate.path) -> {
                    budget.visitFile()
                    visitor(candidate, budget)
                }
            }
        }
    }
}
