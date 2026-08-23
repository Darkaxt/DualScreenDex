package com.darkaxt.dualdex.catalog

object CatalogMigration {
    fun prepare(database: CatalogDatabase) {
        val version = database.query("PRAGMA user_version") { row -> row.long("user_version")?.toInt() ?: 0 }
            .singleOrNull() ?: 0
        database.transaction {
            if (version != 0 && version != CatalogSchema.version) {
                CatalogSchema.dropStatements.forEach(database::execute)
            }
            CatalogSchema.createStatements.forEach(database::execute)
            database.execute("PRAGMA user_version = ${CatalogSchema.version}")

            val storedParserVersion = database.query(
                "SELECT parser_schema_version FROM catalog_metadata WHERE id = 1",
            ) { row -> row.long("parser_schema_version")?.toInt() }.singleOrNull()
            if (storedParserVersion != null && storedParserVersion != CatalogSchema.parserSchemaVersion) {
                database.execute("DELETE FROM catalog_section_chunks")
                database.execute("DELETE FROM catalog_sections")
                database.execute("DELETE FROM catalog_metadata")
            }
        }
    }
}
