package com.darkaxt.dualdex.catalog

import com.enrpau.dualscreendex.parser.catalog.*
import com.enrpau.dualscreendex.parser.language.*
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.*
import org.junit.Test

class SpeciesCategoryStoreTest {
    @Test fun categoryOverlaySurvivesWholeCodecAndSqliteReopen() {
        val catalog = fixture()
        val codec = CatalogSectionCodec()
        val payload = codec.encode(catalog, CatalogSectionPlan.from(catalog.languageManifest).sections)
        val decoded = codec.decode(catalog.romSha256, catalog.romCrc32, catalog.family, catalog.platform, payload)
        assertEquals("SEED", decoded.defaultTextProjection().speciesCategory(1))
        assertEquals(catalog, decoded)
        val root = Path.of("build/tmp/category-tests")
        Files.createDirectories(root)
        val file = Files.createTempFile(root, "category-", ".sqlite").toFile()
        JdbcCatalogDatabaseFactory.open(file).use { database ->
            CatalogWriter(database).write(catalog, CatalogSourceMetadata.direct("synthetic.gba", 4096, "SYNTHETIC"),
                CatalogWriteProgress.complete())
        }
        val reopened = JdbcCatalogDatabaseFactory.open(file).use { database ->
            requireNotNull(CatalogReader(database).readComplete()).catalog
        }
        assertEquals(catalog, reopened)
        assertEquals(CatalogLogicalDigest.sha256(catalog), CatalogLogicalDigest.sha256(reopened))
        val prior = catalog.defaultLocalizedText()!!
        val changedOverlay = CatalogLanguageOverlay(prior.language, prior.overlayVersion, prior.localizedCapabilities,
            speciesCategories = mapOf(1 to CatalogField.available("MOUSE")))
        val changed = catalog.copy(localization = CatalogLocalization(catalog.languageManifest,
            mapOf(prior.language to changedOverlay)))
        assertNotEquals(CatalogLogicalDigest.sha256(catalog), CatalogLogicalDigest.sha256(changed))
    }

    @Test fun missingNullAndUnfulfilledCategoryPayloadsCannotReopen() {
        val catalog = fixture()
        val codec = CatalogSectionCodec()
        val baseline = codec.encode(catalog, CatalogSectionPlan.from(catalog.languageManifest).sections)
        for (section in listOf("species", "language_overlay:en")) for (mutation in 0..2) {
            val json = GZIPInputStream(baseline.getValue(section).inputStream()).reader().use {
                JsonParser.parseReader(it).asJsonObject
            }
            if (section == "species") {
                val record = json.getAsJsonObject("1")
                when (mutation) {
                    0 -> record.remove("category")
                    1 -> record.add("category", null)
                    else -> record.getAsJsonObject("category").addProperty("status", "AVAILABLE")
                }
            } else {
                when (mutation) {
                    0 -> json.remove("speciesCategories")
                    1 -> json.add("speciesCategories", null)
                    else -> json.getAsJsonObject("speciesCategories").add("1", null)
                }
            }
            val encoded = ByteArrayOutputStream().also { output ->
                GZIPOutputStream(output).writer().use { it.write(json.toString()) }
            }.toByteArray()
            assertThrows("$section mutation $mutation", IllegalArgumentException::class.java) {
                codec.decode(catalog.romSha256, catalog.romCrc32, catalog.family, catalog.platform,
                    baseline + (section to encoded))
            }
        }
    }

    @Test fun parser91CachesCannotSilentlyAcquireCategories() {
        val catalog = fixture()
        val root = Path.of("build/tmp/category-tests")
        Files.createDirectories(root)
        val file = Files.createTempFile(root, "prior-category-", ".sqlite").toFile()
        JdbcCatalogDatabaseFactory.open(file).use { database ->
            CatalogWriter(database).write(catalog, CatalogSourceMetadata.direct("synthetic.gba", 4096, "SYNTHETIC"),
                CatalogWriteProgress.complete())
            database.execute("UPDATE catalog_metadata SET parser_schema_version = 91 WHERE id = 1")
            assertNull(CatalogReader(database).readComplete())
        }
    }

    private fun fixture(): ParsedCatalog {
        val species = SpeciesRecord(1, dexNumber = CatalogField.available(1), name = CatalogField.notApplicable("localized"),
            typeIds = CatalogField.notFound("not selected"), baseStats = CatalogField.notFound("not selected"),
            sprite = CatalogField.notFound("not selected"), category = CatalogField.notFound("localized"))
        val states = LocalizedTextCapability.entries.associateWith { LocalizedCapabilityState.notFound("absent") }.toMutableMap()
        states[LocalizedTextCapability.SPECIES_NAMES] = LocalizedCapabilityState.notFound("absent", 1)
        states[LocalizedTextCapability.SPECIES_DESCRIPTIONS] = LocalizedCapabilityState.notFound("absent", 1)
        states[LocalizedTextCapability.SPECIES_CATEGORIES] = LocalizedCapabilityState.available(1)
        val overlay = CatalogLanguageOverlay(LanguageTag.ENGLISH, 2, states,
            speciesCategories = mapOf(1 to CatalogField.available("SEED")))
        val codec = PokemonTextCodec.gbaEnglish
        val manifest = RomLanguageManifest(codec.language, listOf(RomLanguageProjection(codec.language,
            codec.id, codec.version, LocalizedTableLayout(), emptyList(), LanguageResolutionStatus.RESOLVED)),
            LanguageResolutionStatus.RESOLVED)
        return ParsedCatalog("0".repeat(64), EngineFamily.EMERALD, Platform.GBA,
            speciesById = mapOf(1 to species), movesById = emptyMap(), romCrc32 = "00000000",
            localization = CatalogLocalization(manifest, mapOf(LanguageTag.ENGLISH to overlay)))
    }
}
