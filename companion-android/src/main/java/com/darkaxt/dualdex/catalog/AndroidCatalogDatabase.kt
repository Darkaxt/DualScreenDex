package com.darkaxt.dualdex.catalog

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.ParcelFileDescriptor
import com.darkaxt.dualdex.catalog.CatalogDatabase
import com.darkaxt.dualdex.catalog.CatalogDatabaseFactory
import com.darkaxt.dualdex.catalog.CatalogRow
import com.darkaxt.dualdex.catalog.CatalogRows
import com.darkaxt.dualdex.catalog.readBoundedBytes
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import java.io.File

object AndroidCatalogDatabaseFactory : CatalogDatabaseFactory {
    override fun open(file: File): CatalogDatabase {
        require(file.parentFile?.isDirectory == true || file.parentFile?.mkdirs() == true) {
            "catalog database directory could not be created: ${file.parentFile}"
        }
        return AndroidCatalogDatabase(SQLiteDatabase.openOrCreateDatabase(file, null))
    }
}

private class AndroidCatalogDatabase(private val database: SQLiteDatabase) : CatalogDatabase {
    override fun <T> transaction(block: () -> T): T =
        transaction(ParserCancellationToken.NONE, block)

    override fun <T> transaction(
        cancellation: ParserCancellationToken,
        block: () -> T,
    ): T {
        database.beginTransaction()
        var ended = false
        return try {
            val result = block()
            cancellation.publish {
                database.setTransactionSuccessful()
                try {
                    database.endTransaction()
                } finally {
                    ended = true
                }
            }
            result
        } finally {
            if (!ended) {
                database.endTransaction()
            }
        }
    }

    override fun execute(sql: String, arguments: List<Any?>) {
        if (arguments.isEmpty()) database.execSQL(sql) else database.execSQL(sql, arguments.toTypedArray())
    }

    override fun <T> query(sql: String, arguments: List<Any?>, map: (CatalogRow) -> T): List<T> {
        require(arguments.none { it is ByteArray }) { "blob query arguments are not supported" }
        val selection = arguments.map { it?.toString() }.toTypedArray()
        return database.rawQuery(sql, selection).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(map(AndroidCatalogRow(cursor)))
            }
        }
    }

    override fun readBlob(sql: String, arguments: List<Any?>, maximumBytes: Int): ByteArray? =
        database.compileStatement(sql).use { statement ->
            arguments.forEachIndexed { index, value ->
                val parameter = index + 1
                when (value) {
                    null -> statement.bindNull(parameter)
                    is ByteArray -> statement.bindBlob(parameter, value)
                    is Float -> statement.bindDouble(parameter, value.toDouble())
                    is Double -> statement.bindDouble(parameter, value)
                    is Number -> statement.bindLong(parameter, value.toLong())
                    else -> statement.bindString(parameter, value.toString())
                }
            }
            val descriptor = statement.simpleQueryForBlobFileDescriptor() ?: return@use null
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                readBoundedBytes(input, maximumBytes)
            }
        }

    override fun <T> streamQuery(sql: String, arguments: List<Any?>, consume: (CatalogRows) -> T): T {
        require(arguments.none { it is ByteArray }) { "blob query arguments are not supported" }
        val selection = arguments.map { it?.toString() }.toTypedArray()
        return database.rawQuery(sql, selection).use { cursor ->
            consume(CatalogRows { if (cursor.moveToNext()) AndroidCatalogRow(cursor) else null })
        }
    }

    override fun close() = database.close()
}

private class AndroidCatalogRow(private val cursor: Cursor) : CatalogRow {
    override fun string(column: String): String? = columnIndex(column).takeUnless(cursor::isNull)?.let(cursor::getString)
    override fun long(column: String): Long? = columnIndex(column).takeUnless(cursor::isNull)?.let(cursor::getLong)
    override fun bytes(column: String): ByteArray? =
        error("cursor-backed blob retrieval is forbidden; use CatalogDatabase.readBlob")

    private fun columnIndex(column: String): Int = cursor.getColumnIndexOrThrow(column)
}
