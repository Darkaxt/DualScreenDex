package com.darkaxt.dualdex.catalog

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.darkaxt.dualdex.catalog.CatalogDatabase
import com.darkaxt.dualdex.catalog.CatalogDatabaseFactory
import com.darkaxt.dualdex.catalog.CatalogRow
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
    override fun <T> transaction(block: () -> T): T {
        database.beginTransaction()
        return try {
            block().also { database.setTransactionSuccessful() }
        } finally {
            database.endTransaction()
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

    override fun close() = database.close()
}

private class AndroidCatalogRow(private val cursor: Cursor) : CatalogRow {
    override fun string(column: String): String? = columnIndex(column).takeUnless(cursor::isNull)?.let(cursor::getString)
    override fun long(column: String): Long? = columnIndex(column).takeUnless(cursor::isNull)?.let(cursor::getLong)
    override fun bytes(column: String): ByteArray? = columnIndex(column).takeUnless(cursor::isNull)?.let(cursor::getBlob)

    private fun columnIndex(column: String): Int = cursor.getColumnIndexOrThrow(column)
}
