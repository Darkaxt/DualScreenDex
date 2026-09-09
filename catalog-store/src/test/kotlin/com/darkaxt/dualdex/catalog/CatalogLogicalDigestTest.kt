package com.darkaxt.dualdex.catalog

import com.enrpau.dualscreendex.parser.catalog.*
import com.enrpau.dualscreendex.parser.language.*
import com.enrpau.dualscreendex.parser.model.*
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

/** Fabricated catalogs only: no parser, selector, ROM loader or external control inputs. */
class CatalogLogicalDigestTest {
    @Test fun canonicalVectorUsesScalarKeyOrderExplicitNullsAndExactUtf8() {
        val value = JsonParser.parseString("""{"z":"é\n日本","b":[true,false,-0.0,1.2500],"a":null,"😀":1,"":2}""")
        val expected = """{"a":null,"b":[true,false,0,1.25],"z":"é\n日本","":2,"😀":1}""".toByteArray(Charsets.UTF_8)
        val actual = canonical(value, 68)
        assertArrayEquals(expected, actual)
        // Independently computed with Python json.dumps over normalized scalar values, then SHA-256.
        assertEquals("19de9c63730b60b8b197f2019927209306bfae29a091309ecf9349292f13757d", hash(actual))
        assertThrows(IllegalArgumentException::class.java) { canonical(value, 67) }
    }

    @Test fun canonicalRejectsNonFiniteMalformedUnicodeAndExcessiveDepth() {
        for (number in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { canonical(JsonPrimitive(number)) }
        }
        assertThrows(IllegalArgumentException::class.java) { canonical(JsonPrimitive("\uD800")) }
        assertThrows(IllegalArgumentException::class.java) { canonical(JsonPrimitive("\uDC00")) }
        val deep = JsonParser.parseString("[".repeat(70) + "0" + "]".repeat(70))
        assertThrows(IllegalArgumentException::class.java) { canonical(deep) }
        assertThrows(IllegalArgumentException::class.java) { canonical(JsonParser.parseString("1e1000000000"), 100) }
    }

    @Test fun envelopeBindsIdentitySchemasAllSectionsAndStorageDtoKeys() {
        val catalog = fixture()
        val encoded = bytes(catalog)
        val envelope = JsonParser.parseString(encoded.toString(Charsets.UTF_8)).asJsonObject
        assertEquals(setOf("digestVersion", "identity", "parserSchemaVersion", "sections", "storageSchemaVersion"), envelope.keySet())
        assertEquals(1, envelope["digestVersion"].asInt)
        assertEquals(CatalogSchema.version, envelope["storageSchemaVersion"].asInt)
        assertEquals(CatalogSchema.parserSchemaVersion, envelope["parserSchemaVersion"].asInt)
        assertEquals(catalog.romSha256, envelope.getAsJsonObject("identity")["sha256"].asString)
        val sections = envelope.getAsJsonObject("sections")
        assertEquals(CatalogSchema.requiredSections + setOf("language_overlay:en", "language_overlay:fr"), sections.keySet())
        val overlay = sections.getAsJsonObject("language_overlay:en")
        assertEquals("en", overlay["language"].asString)
        assertEquals(15, overlay.getAsJsonArray("localizedCapabilities").size())
        assertEquals(setOf("regionKey", "locationKey", "value"), overlay.getAsJsonArray("worldLocationNames")[0].asJsonObject.keySet())
        assertTrue(sections.getAsJsonObject("runtime_metadata")["gen2TimeOfDayWramOffset"].isJsonNull)
        assertEquals(hash(encoded), sha(catalog))
        assertArrayEquals(encoded, canonical(envelope))
    }

    @Test fun mapsAndAllPersistedSetDomainsIgnoreInsertionOrder() {
        val first = fixture()
        val reversed = fixture(reverse = true)
        assertEquals(first, reversed)
        assertEquals(sha(first), sha(reversed))
        assertArrayEquals(bytes(first), bytes(reversed))
    }

    @Test fun numericOverlayAndContextualDispositionMutationsChangeDigest() {
        val base = fixture()
        val species = base.speciesById.getValue(1)
        val numeric = base.copy(speciesById = base.speciesById + (1 to species.copy(height = CatalogField.available(123))))
        val changedText = fixture(label = "Changed synthetic name")
        // Swap dispositions without changing coverage counts or any other persisted section.
        val contextual = base.copy(localMaps = base.localMaps.copy(maps = base.localMaps.maps.map { map ->
            map.copy(nameDisposition = if (map.nameDisposition == LocalMapNameDisposition.CONTEXT_DEPENDENT)
                LocalMapNameDisposition.STATIC_NAME_REQUIRED else LocalMapNameDisposition.CONTEXT_DEPENDENT)
        }))
        for (changed in listOf(numeric, changedText, contextual, base.copy(romSha256 = "b".repeat(64)),
            base.copy(romCrc32 = "87654321"), base.copy(family = EngineFamily.YELLOW), base.copy(platform = Platform.GBC))) {
            assertNotEquals(sha(base), sha(changed))
        }
    }

    @Test fun meaningfulSequencesRetainTheirOrder() {
        val base = fixture()
        assertNotEquals(sha(base), sha(base.copy(diagnostics = base.diagnostics.reversed())))
        val species = base.speciesById.getValue(1)
        assertNotEquals(sha(base), sha(base.copy(speciesById = base.speciesById +
            (1 to species.copy(typeIds = CatalogField.available(listOf(2, 1)))))))
        assertNotEquals(sha(base), sha(base.copy(localMaps = base.localMaps.copy(maps = base.localMaps.maps.reversed()))))
    }

    @Test fun digestIsIndependentOfObservationAndDoesNotMutateCatalog() {
        val catalog = fixture(reverse = true)
        val prior = sha(catalog)
        val text = requireNotNull(catalog.textProjection(LanguageTag.FRENCH))
        text.speciesName(1)
        text.worldLocationName("region", "one")
        catalog.localMaps.contextDependentMapKeys
        assertEquals(prior, sha(catalog))
        assertEquals(listOf(2, 1), catalog.speciesById.keys.toList())
        assertEquals(listOf(2, 1), catalog.runtimeMetadata.areaBaseIds.toList())
    }

    @Test fun totalOutputIsBoundedAtExactByteBoundary() {
        val catalog = fixture()
        val size = bytes(catalog).size
        assertEquals(sha(catalog), sha(catalog, size))
        assertThrows(IllegalArgumentException::class.java) { sha(catalog, size - 1) }
        assertThrows(IllegalArgumentException::class.java) { sha(catalog, 0) }
        assertThrows(IllegalArgumentException::class.java) { sha(catalog, Int.MAX_VALUE) }
        val output = ByteArrayOutputStream()
        assertThrows(IllegalArgumentException::class.java) { write(catalog, output, 100) }
        assertTrue(output.size() <= 100)
    }

    @Test fun sectionLimitAndNonFiniteStoredValuesFailClosed() {
        val base = fixture()
        val huge = base.copy(diagnostics = listOf("x".repeat(32 * 1024 * 1024 + 1)))
        assertThrows(IllegalArgumentException::class.java) { sha(huge) }
        val invalid = base.copy(capabilities = mapOf(RomCapability.BASE_STATS to CapabilityEvidence(
            RomCapability.BASE_STATS, true, Double.NaN)))
        assertThrows(IllegalArgumentException::class.java) { sha(invalid) }
    }

    @Test fun actualSqliteWriteCloseReopenPreservesLogicalDigestNotMetadata() {
        val root = Files.createTempDirectory("task426-synthetic-")
        try {
            val original = fixture(reverse = true)
            val before = sha(original)
            val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
            cache.write(original, CatalogSourceMetadata.direct("synthetic", 1, "synthetic"), CatalogWriteProgress.complete())
            // New repository: only the SQLite file survives, not any writer/reader/catalog reference.
            val stored = requireNotNull(CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory).readComplete(original.romSha256))
            assertEquals(original, stored.catalog)
            assertEquals(before, sha(stored.catalog))
            assertEquals(CatalogSchema.requiredSections + setOf("language_overlay:en", "language_overlay:fr"), stored.committedSections)
            JdbcCatalogDatabaseFactory.open(cache.fileFor(original.romSha256)).use { db ->
                db.execute("UPDATE catalog_metadata SET written_at_epoch_ms = 99, source_name = 'different synthetic source' WHERE id = 1")
            }
            val changedMetadata = requireNotNull(CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory).readComplete(original.romSha256))
            assertEquals(before, sha(changedMetadata.catalog))
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }

    private fun fixture(reverse: Boolean = false, label: String = "Synthetic é"): ParsedCatalog {
        fun <T> order(values: List<T>) = if (reverse) values.reversed() else values
        val ids = order(listOf(1, 2))
        val species = ids.associateWith { id -> SpeciesRecord(id, dexNumber = CatalogField.available(id),
            name = CatalogField.notApplicable("overlay"), typeIds = CatalogField.available(listOf(1, 2)),
            baseStats = CatalogField.notFound("synthetic"), sprite = CatalogField.notFound("synthetic")) }
        val maps = LocalMapCatalog(maps = listOf(
            LocalMap("one", null, 1, 16, 16, 1, 1, "asset"),
            LocalMap("two", null, 2, 16, 16, 1, 1, "asset", LocalMapNameDisposition.CONTEXT_DEPENDENT)),
            assets = mapOf("asset" to PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))))
        val world = WorldMapCatalog(regions = listOf(WorldMapRegion("region", null, 1, 1, 2, 1, "world", listOf(
            WorldMapLocation("one", null, LinkedHashSet(ids), listOf(WorldMapCell(0, 0, 1, 1))),
            WorldMapLocation("two", null, setOf(2), listOf(WorldMapCell(1, 0, 1, 1)))))),
            assets = mapOf("world" to RgbaSprite(1, 1, intArrayOf(0xff123456.toInt()))))
        val languages = listOf(LanguageTag.ENGLISH, LanguageTag.FRENCH)
        val manifest = RomLanguageManifest(defaultLanguage = languages.first(), projections = languages.map {
            RomLanguageProjection(it, "synthetic-${it.value}", 1, LocalizedTableLayout(),
                listOf(LanguageEvidence(LanguageEvidenceKind.TABLE_RELATIONSHIP, "synthetic", 100)), LanguageResolutionStatus.RESOLVED)
        }, status = LanguageResolutionStatus.RESOLVED)
        val overlays = order(languages).associateWith { language ->
            val counts = mapOf(LocalizedTextCapability.SPECIES_NAMES to 2, LocalizedTextCapability.SPECIES_DESCRIPTIONS to 2,
                LocalizedTextCapability.AREA_NAMES to 2, LocalizedTextCapability.LOCAL_MAP_NAMES to 1,
                LocalizedTextCapability.WORLD_REGION_NAMES to 1, LocalizedTextCapability.WORLD_LOCATION_NAMES to 2,
                LocalizedTextCapability.ENCOUNTER_AREA_NAMES to 1)
            val states = order(LocalizedTextCapability.entries.toList()).associateWith { capability ->
                val total = counts[capability] ?: 0
                when {
                    capability in setOf(LocalizedTextCapability.SPECIES_NAMES, LocalizedTextCapability.WORLD_LOCATION_NAMES) -> LocalizedCapabilityState.available(total)
                    total > 0 -> LocalizedCapabilityState.notFound("synthetic absent text", total)
                    else -> LocalizedCapabilityState.notApplicable("empty synthetic domain")
                }
            }
            CatalogLanguageOverlay(language, 1, states,
                speciesNames = ids.associateWith { CatalogField.available("${language.value} $label $it") },
                worldLocationNames = order(listOf("one", "two")).associate { WorldLocationKey("region", it) to CatalogField.available("${language.value} $it") })
        }
        val theme = CatalogTheme.neutral()
        return ParsedCatalog(romSha256 = "a".repeat(64), romCrc32 = "12345678", family = EngineFamily.RED_BLUE, platform = Platform.GB,
            speciesById = species, localMaps = maps, worldMaps = world,
            encounterAreas = listOf(EncounterArea(10, CatalogField.notApplicable("overlay"), 1, emptyList(),
                LinkedHashSet(order(listOf(EncounterWindow.DAY, EncounterWindow.NIGHT))))),
            runtimeMetadata = CatalogRuntimeMetadata(areaBaseIds = LinkedHashSet(ids)),
            theme = theme.copy(method = CatalogThemeMethod.MULTI_ASSET_QUANTIZATION, assetClasses = LinkedHashSet(order(CatalogThemeAssetClass.entries.take(2)))),
            localization = CatalogLocalization(manifest, overlays), diagnostics = listOf("first synthetic diagnostic", "second synthetic diagnostic"))
    }

    // Reflection keeps the initial RED executable: absence is an assertion, never a compiler error.
    private fun type(name: String): Class<*> = try { Class.forName("com.darkaxt.dualdex.catalog.$name") }
        catch (missing: ClassNotFoundException) { throw AssertionError("Task426 required facade is absent: $name", missing) }
    private fun invoke(type: Class<*>, method: String, parameters: Array<Class<*>>, vararg values: Any): Any? =
        try { type.getMethod(method, *parameters).invoke(null, *values) }
        catch (failure: InvocationTargetException) { throw failure.targetException }
    private fun sha(catalog: ParsedCatalog, limit: Int = 128 * 1024 * 1024): String =
        invoke(type("CatalogLogicalDigest"), "sha256", arrayOf(ParsedCatalog::class.java, Int::class.javaPrimitiveType!!), catalog, limit) as String
    private fun write(catalog: ParsedCatalog, output: OutputStream, limit: Int) {
        invoke(type("CatalogLogicalDigest"), "writeCanonical", arrayOf(ParsedCatalog::class.java, OutputStream::class.java, Int::class.javaPrimitiveType!!), catalog, output, limit)
    }
    private fun bytes(catalog: ParsedCatalog): ByteArray = ByteArrayOutputStream().also { write(catalog, it, 128 * 1024 * 1024) }.toByteArray()
    private fun canonical(value: JsonElement, limit: Int = 128 * 1024 * 1024): ByteArray =
        invoke(type("CatalogCanonicalJson"), "encode", arrayOf(JsonElement::class.java, Int::class.javaPrimitiveType!!), value, limit) as ByteArray
    private fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
}
