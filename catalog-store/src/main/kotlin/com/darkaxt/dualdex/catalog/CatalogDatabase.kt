package com.darkaxt.dualdex.catalog

import java.io.File

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
    fun execute(sql: String, arguments: List<Any?> = emptyList())
    fun <T> query(sql: String, arguments: List<Any?> = emptyList(), map: (CatalogRow) -> T): List<T>
    fun <T> streamQuery(
        sql: String,
        arguments: List<Any?> = emptyList(),
        consume: (CatalogRows) -> T,
    ): T
}

fun interface CatalogDatabaseFactory {
    fun open(file: File): CatalogDatabase
}
