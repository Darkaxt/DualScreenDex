package com.enrpau.dualscreendex.parser.cli

import com.darkaxt.dualdex.catalog.CatalogDatabase
import com.darkaxt.dualdex.catalog.CatalogDatabaseFactory
import com.darkaxt.dualdex.catalog.CatalogRow
import com.darkaxt.dualdex.catalog.CatalogRows
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

object JdbcCatalogDatabaseFactory : CatalogDatabaseFactory {
    override fun open(file: File): CatalogDatabase {
        Class.forName("org.sqlite.JDBC")
        return JdbcCatalogDatabase(DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}"))
    }
}

private class JdbcCatalogDatabase(private val connection: Connection) : CatalogDatabase {
    override fun <T> transaction(block: () -> T): T {
        val original = connection.autoCommit
        connection.autoCommit = false
        return try {
            block().also { connection.commit() }
        } catch (failure: Throwable) {
            connection.rollback()
            throw failure
        } finally {
            connection.autoCommit = original
        }
    }

    override fun execute(sql: String, arguments: List<Any?>) {
        connection.prepareStatement(sql).use { statement ->
            arguments.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeUpdate()
        }
    }

    override fun <T> query(sql: String, arguments: List<Any?>, map: (CatalogRow) -> T): List<T> =
        connection.prepareStatement(sql).use { statement ->
            arguments.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(map(object : CatalogRow {
                            override fun string(column: String): String? = result.getString(column)
                            override fun long(column: String): Long? = result.getLong(column).takeUnless { result.wasNull() }
                            override fun bytes(column: String): ByteArray? = result.getBytes(column)
                        }))
                    }
                }
            }
        }

    override fun <T> streamQuery(sql: String, arguments: List<Any?>, consume: (CatalogRows) -> T): T =
        connection.prepareStatement(sql).use { statement ->
            arguments.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeQuery().use { result ->
                consume(CatalogRows {
                    if (!result.next()) null else object : CatalogRow {
                        override fun string(column: String): String? = result.getString(column)
                        override fun long(column: String): Long? = result.getLong(column).takeUnless { result.wasNull() }
                        override fun bytes(column: String): ByteArray? = result.getBytes(column)
                    }
                })
            }
        }

    override fun close() = connection.close()
}
