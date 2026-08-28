package com.darkaxt.dualdex.catalog

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

interface CatalogRow {
    fun string(column: String): String?
    fun long(column: String): Long?
    fun bytes(column: String): ByteArray?
}

fun interface CatalogRows {
    fun next(): CatalogRow?
}

interface CatalogDatabase : AutoCloseable {
    fun <T> transaction(block: () -> T): T
    fun <T> transaction(
        cancellation: ParserCancellationToken,
        block: () -> T,
    ): T = transaction {
        block().also { cancellation.throwIfCancellationRequested() }
    }
    fun execute(sql: String, arguments: List<Any?> = emptyList())
    fun <T> query(sql: String, arguments: List<Any?> = emptyList(), map: (CatalogRow) -> T): List<T>
    fun readBlob(
        sql: String,
        arguments: List<Any?> = emptyList(),
        maximumBytes: Int,
    ): ByteArray? = query(sql, arguments) { row -> row.bytes("payload") }
        .singleOrNull()
        ?.also { payload -> require(payload.size <= maximumBytes) { "database blob limit exceeded" } }
    fun <T> streamQuery(
        sql: String,
        arguments: List<Any?> = emptyList(),
        consume: (CatalogRows) -> T,
    ): T
}

fun readBoundedBytes(input: InputStream, maximumBytes: Int): ByteArray {
    require(maximumBytes > 0) { "database blob limit must be positive" }
    val output = ByteArrayOutputStream(minOf(maximumBytes, DEFAULT_BUFFER_SIZE))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        require(total <= maximumBytes.toLong() - count) { "database blob limit exceeded" }
        output.write(buffer, 0, count)
        total += count
    }
    return output.toByteArray()
}

fun interface CatalogDatabaseFactory {
    fun open(file: File): CatalogDatabase
}
