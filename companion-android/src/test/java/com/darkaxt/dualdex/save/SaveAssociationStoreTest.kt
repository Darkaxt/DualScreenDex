package com.darkaxt.dualdex.save

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class SaveAssociationStoreTest {
    @Test
    fun remembersSelectionsPerRomIdentityAndSurvivesReopen() {
        val directory = Files.createTempDirectory("dualdex-save-association").toFile()
        val file = directory.resolve("associations.json")
        try {
            val first = SaveAssociationStore(file)
            first.remember("a".repeat(64), "content://saves/a.srm")
            first.remember("b".repeat(64), "content://saves/b.srm")

            val reopened = SaveAssociationStore(file)

            assertEquals("content://saves/a.srm", reopened.selectedFor("A".repeat(64)))
            assertEquals("content://saves/b.srm", reopened.selectedFor("b".repeat(64)))
            assertNull(reopened.selectedFor("c".repeat(64)))
        } finally {
            directory.deleteRecursively()
        }
    }
}
