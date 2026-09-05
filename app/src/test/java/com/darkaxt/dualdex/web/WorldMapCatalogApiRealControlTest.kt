package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.catalog.CatalogCache
import com.darkaxt.dualdex.catalog.CatalogDatabase
import com.darkaxt.dualdex.catalog.CatalogDatabaseFactory
import com.darkaxt.dualdex.catalog.CatalogRow
import com.darkaxt.dualdex.catalog.CatalogSchema
import com.enrpau.dualscreendex.parser.catalog.TypeSemanticRole
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import com.enrpau.dualscreendex.companion.battle.AppliedDamageCondition
import com.enrpau.dualscreendex.companion.battle.SemanticProof
import com.darkaxt.dualdex.catalog.CatalogSourceMetadata
import com.darkaxt.dualdex.catalog.CatalogWriteProgress
import com.enrpau.dualscreendex.parser.catalog.AbilityMechanicKind
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.CatalogLanguageOverlay
import com.enrpau.dualscreendex.parser.catalog.CatalogLocalization
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.LocalizedCapabilityState
import com.enrpau.dualscreendex.parser.catalog.LocalizedTextCapability
import com.enrpau.dualscreendex.parser.catalog.LocalMapAssetRenderer
import com.enrpau.dualscreendex.parser.catalog.MapLighting
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import com.enrpau.dualscreendex.parser.catalog.defaultTextProjection
import com.enrpau.dualscreendex.parser.catalog.textProjection
import com.enrpau.dualscreendex.parser.io.LoadedRom
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.language.LocalizedTableLayout
import com.enrpau.dualscreendex.parser.language.RomLanguageManifest
import com.enrpau.dualscreendex.parser.language.RomLanguageProjection
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomCapability
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.util.Comparator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class WorldMapCatalogApiRealControlTest {
    @Test
    fun exactReferenceThemesSurviveCatalogStoreAndApiProjection() {
        themeControls.forEach { control ->
            val configured = System.getenv(control.environmentVariable)
            assumeTrue("set ${control.environmentVariable} to run this exact theme control", !configured.isNullOrBlank())
            val path = Path.of(requireNotNull(configured))
            assumeTrue("real ROM does not exist: $path", Files.isRegularFile(path))
            val rom = RomImage(Files.readAllBytes(path))
            assertEquals(control.romSha256, rom.sha256)
            val catalog = requireNotNull(CatalogParser.parse(rom).catalog)
            val root = newRoot()
            try {
                val cache = CatalogCache(root.toFile(), JdbcTestCatalogDatabaseFactory)
                cache.write(
                    catalog,
                    CatalogSourceMetadata.direct(path.fileName.toString(), rom.size, "THEME-CONTROL"),
                    CatalogWriteProgress.complete(),
                )
                val reopened = requireNotNull(cache.readComplete(rom.sha256)).catalog
                assertEquals(catalog.theme, reopened.theme)

                val runtime = ProductionCompanionRuntime().apply { loadCatalog(path.fileName.toString(), reopened) }
                val apiTheme = requireNotNull(runtime.bootstrap().catalog).theme
                assertEquals(reopened.theme.method.name, apiTheme.method)
                assertEquals(reopened.theme.assetClasses.sortedBy { it.ordinal }.map { it.name }, apiTheme.assetClasses)
                assertEquals(reopened.theme.contrastCorrected, apiTheme.contrastCorrected)
                assertEquals("#%06x".format(reopened.theme.tokens.field), apiTheme.tokens.field)
                assertEquals("#%06x".format(reopened.theme.tokens.header), apiTheme.tokens.header)
                assertEquals("#%06x".format(reopened.theme.tokens.panel), apiTheme.tokens.panel)
                assertEquals("#%06x".format(reopened.theme.tokens.accent), apiTheme.tokens.accent)
                assertDatabaseIntegrity(cache.fileFor(rom.sha256))
            } finally {
                deleteTree(root)
            }
        }
    }

    @Test
    fun persistedLocalizedOverlaysReopenIntoBootstrapWithoutParserInvocation() {
        val rom = RomImage(ByteArray(0x200) { index -> (index * 31).toByte() })
        val languages = listOf(LanguageTag.ENGLISH, LanguageTag.FRENCH)
        val manifest = RomLanguageManifest(
            defaultLanguage = LanguageTag.ENGLISH,
            projections = languages.map { language ->
                RomLanguageProjection(
                    language = language,
                    codecId = "fixture-${language.value}",
                    codecVersion = 1,
                    localizedTables = LocalizedTableLayout(),
                    evidence = emptyList(),
                    status = LanguageResolutionStatus.RESOLVED,
                )
            },
            status = LanguageResolutionStatus.RESOLVED,
        )
        fun overlay(language: LanguageTag, version: Long, name: String) = CatalogLanguageOverlay(
            language = language,
            overlayVersion = version,
            localizedCapabilities = LocalizedTextCapability.entries.associateWith { capability ->
                when (capability) {
                    LocalizedTextCapability.SPECIES_NAMES -> LocalizedCapabilityState.available(1)
                    LocalizedTextCapability.SPECIES_DESCRIPTIONS ->
                        LocalizedCapabilityState.notFound("fixture missing", 1)
                    else -> LocalizedCapabilityState.unavailable(
                        CapabilityStatus.NOT_APPLICABLE,
                        expectedRecords = 0,
                        confidence = 1.0,
                    )
                }
            },
            speciesNames = mapOf(1 to CatalogField.available(name)),
        )
        val catalog = ParsedCatalog(
            romSha256 = rom.sha256,
            romCrc32 = rom.crc32,
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            speciesById = mapOf(
                1 to SpeciesRecord(
                    id = 1,
                    dexNumber = CatalogField.available(1),
                    name = CatalogField.notApplicable("stored in language overlay"),
                    typeIds = CatalogField.notFound("fixture"),
                    baseStats = CatalogField.notFound("fixture"),
                    sprite = CatalogField.notFound("fixture"),
                ),
            ),
            localization = CatalogLocalization(
                manifest,
                mapOf(
                    LanguageTag.ENGLISH to overlay(LanguageTag.ENGLISH, 7, "Bulbasaur"),
                    LanguageTag.FRENCH to overlay(LanguageTag.FRENCH, 8, "Bulbizarre"),
                ),
            ),
        )
        val root = newRoot()
        try {
            val cache = CatalogCache(root.toFile(), JdbcTestCatalogDatabaseFactory)
            cache.write(
                catalog,
                CatalogSourceMetadata.direct("fixture.gba", rom.size, "FIXTURE"),
                CatalogWriteProgress.complete(),
            )
            val reopened = requireNotNull(cache.readComplete(rom.sha256)).catalog
            assertEquals("Bulbasaur", reopened.defaultTextProjection().speciesName(1))
            assertEquals("Bulbizarre", reopened.textProjection(LanguageTag.FRENCH)?.speciesName(1))

            val parserInvocations = AtomicInteger()
            val completion = AtomicReference<Result<Unit>?>()
            val completed = CountDownLatch(1)
            ProductionCompanionRuntime(
                catalogRepository = cache,
                parseCatalogWithCancellation = { _, _, _, _ ->
                    parserInvocations.incrementAndGet()
                    error("persisted overlay bootstrap must not invoke the parser")
                },
            ).use { runtime ->
                runtime.load(LoadedRom("fixture.gba", rom)) { result ->
                    completion.set(result)
                    completed.countDown()
                }
                assertTrue("catalog reopen did not complete", completed.await(10, TimeUnit.SECONDS))
                requireNotNull(completion.get()).getOrThrow()

                val bootstrap = runtime.bootstrap()
                assertEquals(0, parserInvocations.get())
                assertTrue(bootstrap.state.catalogReady)
                assertEquals("CACHE_REOPEN", bootstrap.state.loading.phase)
                assertEquals(rom.sha256, bootstrap.catalog?.hash)
                assertEquals("Bulbasaur", bootstrap.catalog?.species?.single()?.name)
                assertEquals("en", bootstrap.language?.activeLanguage)
                assertEquals(7L, bootstrap.language?.activeOverlayVersion)
                assertEquals(listOf("en", "fr"), bootstrap.language?.projections?.map { it.language })
            }
        } finally {
            deleteTree(root)
        }
    }

    // Separate JUnit cases deliberately attempt all nine exact inputs even when an earlier cell is red.
    @Test fun nativeOfficialJapaneseRedBlue() = assertNativeRoundTrip(nativeControls[0])
    @Test fun nativeOfficialJapaneseYellow() = assertNativeRoundTrip(nativeControls[1])
    @Test fun nativeOfficialJapaneseGoldSilver() = assertNativeRoundTrip(nativeControls[2])
    @Test fun nativeOfficialJapaneseCrystal() = assertNativeRoundTrip(nativeControls[3])
    @Test fun nativeOfficialJapaneseRubySapphire() = assertNativeRoundTrip(nativeControls[4])
    @Test fun nativeOfficialJapaneseEmerald() = assertNativeRoundTrip(nativeControls[5])
    @Test fun nativeOfficialJapaneseFireRedLeafGreen() = assertNativeRoundTrip(nativeControls[6])
    @Test fun nativeOfficialKoreanGold() = assertNativeRoundTrip(nativeControls[7])
    @Test fun nativeOfficialKoreanSilver() = assertNativeRoundTrip(nativeControls[8])

    @Test
    fun nativeOfficialJapaneseFireRedUnprovenMoveProseFailsClosed() {
        // Negative safety evidence only. nativeOfficialJapaneseFireRedLeafGreen remains the
        // mandatory positive acceptance case and must stay red until its real prose ABI is proved.
        val control = nativeControls[6]
        val configured = System.getenv("DUALDEX_NATIVE_CONTROLS")
        assumeTrue("set DUALDEX_NATIVE_CONTROLS for the exact native control", !configured.isNullOrBlank())
        val path = Files.list(Path.of(requireNotNull(configured)).resolve(control.folder)).use { paths ->
            paths.filter { Files.isRegularFile(it) }.toList().single()
        }
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals(control.sha256, rom.sha256)
        val parsed = requireNotNull(CatalogParser.parse(rom).catalog)
        fun assertUnavailable(catalog: ParsedCatalog) {
            assertEquals(EngineFamily.FIRERED_LEAFGREEN, catalog.family)
            val overlay = requireNotNull(catalog.localizedText(LanguageTag.JAPANESE))
            val state = overlay.localizedCapabilities.getValue(LocalizedTextCapability.MOVE_DESCRIPTIONS)
            assertEquals(CapabilityStatus.NOT_FOUND, state.status)
            assertEquals(354, state.expectedRecords)
            assertEquals(0, state.coveredRecords)
            assertTrue(overlay.moveDescriptions.isEmpty())
            assertTrue(catalog.movesById.values.all { it.effectText.value == null })
        }
        assertUnavailable(parsed)
        val cache = CatalogCache(newRoot().toFile(), JdbcTestCatalogDatabaseFactory)
        cache.write(parsed, CatalogSourceMetadata.direct("native-control", rom.size, "NATIVE-CONTROL"), CatalogWriteProgress.complete())
        val reopened = requireNotNull(cache.readComplete(rom.sha256)).catalog
        assertUnavailable(reopened)
        assertEquals(parsed, reopened)
        ProductionCompanionRuntime().use { runtime ->
            runtime.loadCatalog("native-control", reopened)
            val api = requireNotNull(runtime.bootstrap().catalog)
            assertTrue(api.moves.isNotEmpty())
            assertTrue(api.moves.all { it.description == null })
        }
        assertDatabaseIntegrity(cache.fileFor(rom.sha256))
        println("NATIVE_MOVE_PROSE_NEGATIVE ${control.folder} sha256=${control.sha256} failClosed=PASS requiredPositive=BLOCKED")
    }

    private fun assertNativeMoveProseSamples(catalog: ParsedCatalog) {
        val overlay = requireNotNull(catalog.localizedText(LanguageTag.JAPANESE))
        val state = overlay.localizedCapabilities.getValue(LocalizedTextCapability.MOVE_DESCRIPTIONS)
        assertEquals(CapabilityStatus.AVAILABLE, state.status)
        assertEquals(354, state.expectedRecords)
        assertEquals(354, state.coveredRecords)
        assertEquals((1..354).toSet(), overlay.moveDescriptions.keys)
        nativeMoveProseHashes.forEach { (id, expected) ->
            val prose = requireNotNull(catalog.defaultTextProjection().moveDescription(id))
            assertEquals("independent move-prose digest for move $id", expected, sha256(prose.toByteArray(StandardCharsets.UTF_8)))
        }
    }

    private fun assertNativeRoundTrip(control: NativeControl) {
        val configured = System.getenv("DUALDEX_NATIVE_CONTROLS")
        assumeTrue("set DUALDEX_NATIVE_CONTROLS for the nine exact native controls", !configured.isNullOrBlank())
        val checks = NativeChecks(control)
        try {
            val rom = checks.attempt("input.sha256") {
                val directory = Path.of(requireNotNull(configured)).resolve(control.folder)
                val path = Files.list(directory).use { paths ->
                    paths.filter { Files.isRegularFile(it) }.toList().single()
                }
                RomImage(Files.readAllBytes(path)).also { assertEquals(control.sha256, it.sha256) }
            } ?: return
            val attempt = checks.attempt("parse") { CatalogParser.parseCatching(rom) } ?: return
            checks.attempt("selection") {
                assertEquals(SelectionStatus.SELECTED, attempt.analysis.status)
                assertEquals(control.family, attempt.analysis.selectedFamily)
            }
            val catalog = checks.attempt("materialize") { requireNotNull(attempt.catalog).getOrThrow() } ?: return
            val language = if (control.folder.startsWith("ja/")) LanguageTag.JAPANESE else LanguageTag.KOREAN
            checks.attempt("authority.exact-overlay") {
                assertEquals(control.family, catalog.family)
                assertEquals(LanguageResolutionStatus.RESOLVED, catalog.languageManifest.status)
                assertEquals(language, catalog.languageManifest.defaultLanguage)
                val projection = catalog.languageManifest.projections.single()
                assertEquals(language, projection.language)
                assertEquals(control.codecId, projection.codecId)
                assertEquals(1, projection.codecVersion)
                assertEquals(LanguageResolutionStatus.RESOLVED, projection.status)
                assertEquals(setOf(language), catalog.localization.overlays.keys)
                assertTrue(catalog.localizedText(LanguageTag.ENGLISH) == null)
            }
            val overlay = catalog.localizedText(language)
            val text = catalog.defaultTextProjection()
            val directMoveProseControl = control.family in setOf(EngineFamily.RUBY_SAPPHIRE, EngineFamily.EMERALD)
            if (directMoveProseControl) checks.attempt("LNG-B002.move-prose.independent-samples") {
                assertNativeMoveProseSamples(catalog)
            }
            // Gen III dexNumber can be regional: source SPECIES_BULBASAUR and the compiled name row are 1.
            // Selecting dexNumber == 1 there would test Treecko, not the independently pinned Bulbasaur sample.
            val bulbasaur = if (control.generation == 3) catalog.speciesById[1]
                else catalog.speciesById.values.singleOrNull { it.dexNumber.value == 1 }
            val speciesId = bulbasaur?.id
            println("NATIVE_E2E_COUNTS ${control.folder} species=${catalog.speciesById.size} moves=${catalog.movesById.size} " +
                "types=${catalog.typesById.size} semanticTypes=${catalog.typesById.values.count { it.semanticRole.value != null }} " +
                "worldLocations=${catalog.worldMaps.regions.sumOf { it.locations.size }} localMaps=${catalog.localMaps.maps.size}")
            checks.attempt("capabilities.inventory") {
                assertEquals(15, LocalizedTextCapability.entries.size)
                assertEquals(LocalizedTextCapability.entries.toSet(), requireNotNull(overlay).localizedCapabilities.keys)
            }
            LocalizedTextCapability.entries.forEach { capability ->
                val state = overlay?.localizedCapabilities?.get(capability)
                println("NATIVE_E2E_CAPABILITY ${control.folder} $capability ${state?.status} ${state?.coveredRecords}/${state?.expectedRecords}")
                checks.attempt("capability.$capability") {
                    requireNotNull(state)
                    assertTrue(state.coveredRecords in 0..state.expectedRecords)
                    if (state.status in setOf(CapabilityStatus.NOT_FOUND, CapabilityStatus.NOT_APPLICABLE, CapabilityStatus.AMBIGUOUS)) {
                        assertEquals(0, state.coveredRecords)
                    }
                    if (control.generation == 1 && capability == LocalizedTextCapability.MOVE_DESCRIPTIONS ||
                        control.generation < 3 && capability in setOf(LocalizedTextCapability.ABILITY_NAMES, LocalizedTextCapability.ABILITY_DESCRIPTIONS)) {
                        assertEquals(CapabilityStatus.NOT_APPLICABLE, state.status)
                    }
                }
            }
            checks.attempt("sample.species-name") { assertEquals(control.speciesName, text.speciesName(requireNotNull(speciesId))) }
            checks.attempt("sample.move-name") { assertEquals(control.moveName, text.moveName(1)) }
            checks.attempt("LNG-B002.sample.species-description") {
                assertTrue(requireNotNull(text.speciesDescription(requireNotNull(speciesId))).contains(control.dexFragment))
            }
            if (control.generation >= 2) checks.attempt("LNG-B002.move-description") {
                assertTrue(!text.moveDescription(1).isNullOrBlank())
                if (language == LanguageTag.KOREAN) assertTrue(requireNotNull(text.moveDescription(1)).contains("손과 꼬리 등을 사용해서"))
            }
            if (control.generation == 3) {
                checks.attempt("LNG-B002.sample.species-dimensions") {
                    // Independently decoded native row 1 dimensions; regional row 203 is 15/415.
                    assertEquals(7, requireNotNull(bulbasaur).height.value)
                    assertEquals(69, bulbasaur.weight.value)
                }
                checks.attempt("LNG-B002.ability-description") {
                    val abilityIds = requireNotNull(bulbasaur).abilityIds.value.orEmpty().filter { it > 0 }
                    assertTrue(abilityIds.isNotEmpty())
                    assertTrue(abilityIds.all { !text.abilityName(it).isNullOrBlank() && !text.abilityDescription(it).isNullOrBlank() })
                    assertEquals("しんりょく", text.abilityName(65))
                    assertEquals("ピンチに くさの いりょくが あがる", text.abilityDescription(65))
                }
            }
            checks.attempt("LNG-D005.complete-type-semantics") {
                assertEquals(if (control.generation == 1) 15 else 18, catalog.typesById.size)
                assertTrue(catalog.typesById.values.all { it.semanticRole.status == CapabilityStatus.AVAILABLE && !text.typeName(it.id).isNullOrBlank() })
                // Resolve IDs from independently expected labels, never from presumed numeric type order.
                control.typeSamples.forEach { (name, role) ->
                    val type = catalog.typesById.values.single { text.typeName(it.id) == name }
                    assertEquals(role, type.semanticRole.value)
                }
            }
            checks.attempt("LNG-B002.sample.world-location") {
                assertTrue(requireNotNull(overlay).worldLocationNames.values.any { it.value == control.worldLocationName })
            }
            checks.attempt("LNG-B002.sample.local-location") {
                assertTrue(requireNotNull(overlay).localMapNames.values.any { it.value == control.locationName })
            }
            checks.attempt("isolation.shared-text") {
                assertTrue(catalog.speciesById.values.all { it.name.value == null && it.description.value == null })
                assertTrue(catalog.movesById.values.all { it.name.value == null && it.effectText.value == null })
                assertTrue(catalog.typesById.values.all { it.name.value == null })
                assertTrue(catalog.abilitiesById.values.all { it.name.value == null && it.description.value == null })
                assertTrue(catalog.localMaps.maps.all { it.displayName == null })
                assertTrue(catalog.worldMaps.regions.all { region -> region.locations.all { it.displayName == null } })
            }

            // This opt-in diagnostic retains its private SQLite artifacts; it never copies a ROM to the repository.
            val cache = checks.attempt("sqlite.create") { CatalogCache(newRoot().toFile(), JdbcTestCatalogDatabaseFactory) } ?: return
            checks.attempt("sqlite.write-close") {
                cache.write(catalog, CatalogSourceMetadata.direct("native-control", rom.size, "NATIVE-CONTROL"), CatalogWriteProgress.complete())
            } ?: return
            // CatalogCache opens and closes a JDBC connection for each operation; this is not an in-memory round trip.
            val stored = checks.attempt("sqlite.reopen-close") { requireNotNull(cache.readComplete(rom.sha256)) } ?: return
            val reopened = stored.catalog
            if (directMoveProseControl) checks.attempt("sqlite.move-prose.independent-samples") {
                assertNativeMoveProseSamples(reopened)
            }
            checks.attempt("sqlite.whole-catalog-equality") { assertTrue("whole catalog differs", catalog == reopened) }
            checks.attempt("sqlite.sections") {
                assertEquals(CatalogSchema.requiredSections + "language_overlay:${language.value}", stored.committedSections)
            }
            checks.attempt("sqlite.integrity") { assertDatabaseIntegrity(cache.fileFor(rom.sha256)) }
            checks.attempt(OFFICIAL_FORECAST_BOUNDARY) {
                val parsedPolicies = assertOfficialConditionalWeatherPolicies(catalog, control)
                val reopenedPolicies = assertOfficialConditionalWeatherPolicies(reopened, control)
                assertEquals(parsedPolicies, reopenedPolicies)
                println("NATIVE_E2E_FORECAST_POLICY ${control.folder} referencedMoves=${reopenedPolicies.size} " +
                    "scope=CONDITIONAL_TYPE_POLICY engineWeatherApplicability=NOT_TESTED")
            }
            checks.attempt(FAULT_INJECTED_FORECAST_BOUNDARY) {
                // Deliberately altered catalogs are negative fault injections, never official positive evidence.
                val reopenedText = reopened.defaultTextProjection()
                control.typeSamples.keys.forEach { name ->
                    val type = reopened.typesById.values.single { reopenedText.typeName(it.id) == name }
                    val moves = reopened.movesById.values.filter { it.typeId.value == type.id }
                    assertTrue("no actual move references for the fault-injected type", moves.isNotEmpty())
                    val withoutAuthority = reopened.copy(typesById = reopened.typesById + (type.id to type.copy(
                        semanticRole = CatalogField.notFound("fault-injected missing semantic authority"),
                    )))
                    assertEquals(reopened.movesById, withoutAuthority.movesById)
                    assertEquals(reopened.localization, withoutAuthority.localization)
                    moves.forEach { move ->
                        val policy = DamageForecastAssembler.conditionalWeatherPolicy(
                            withoutAuthority, requireNotNull(move.typeId.value),
                        )
                        assertTrue(policy.boundedAlternatives.isEmpty())
                        assertEquals(
                            listOf("Weather interaction for this move's type is unresolved."),
                            policy.unboundedUnknowns,
                        )
                    }
                }
            }
            checks.attempt("api.cache-bootstrap") {
                val parserInvocations = AtomicInteger()
                val completion = AtomicReference<Result<Unit>?>()
                val completed = CountDownLatch(1)
                ProductionCompanionRuntime(
                    catalogRepository = cache,
                    parseCatalogWithCancellation = { _, _, _, _ ->
                        parserInvocations.incrementAndGet()
                        error("native cache bootstrap must not reparse")
                    },
                ).use { runtime ->
                    runtime.load(LoadedRom("native-control", rom)) { result ->
                        completion.set(result)
                        completed.countDown()
                    }
                    assertTrue("native cache reopen timed out", completed.await(30, TimeUnit.SECONDS))
                    requireNotNull(completion.get()).getOrThrow()
                    val bootstrap = runtime.bootstrap()
                    checks.attempt("api.authority") {
                        assertEquals(0, parserInvocations.get())
                        assertTrue(bootstrap.state.catalogReady)
                        assertEquals("CACHE_REOPEN", bootstrap.state.loading.phase)
                        assertEquals(rom.sha256, bootstrap.catalog?.hash)
                        assertEquals("ROM_DEFAULT", bootstrap.language?.authority)
                        assertEquals(language.value, bootstrap.language?.defaultLanguage)
                        assertEquals(language.value, bootstrap.language?.activeLanguage)
                        assertEquals(overlay?.overlayVersion, bootstrap.language?.activeOverlayVersion)
                        assertEquals(listOf(language.value), bootstrap.language?.projections?.map { it.language })
                    }
                    val api = requireNotNull(bootstrap.catalog)
                    if (directMoveProseControl) checks.attempt("api.move-prose.independent-samples") {
                        nativeMoveProseHashes.forEach { (id, expected) ->
                            val prose = requireNotNull(api.moves.single { it.id == id }.description)
                            assertEquals("independent API move-prose digest for move $id", expected,
                                sha256(prose.toByteArray(StandardCharsets.UTF_8)))
                        }
                    }
                    checks.attempt("api.sample.species") {
                        val species = api.species.single { it.id == speciesId }
                        assertEquals(control.speciesName, species.name)
                        assertTrue(requireNotNull(species.description).contains(control.dexFragment))
                        assertEquals(reopened.defaultTextProjection().speciesDescription(requireNotNull(speciesId)), species.description)
                        if (control.generation == 3) {
                            assertEquals(7, species.height)
                            assertEquals(69, species.weight)
                            val ability = species.abilities.single { it.id == 65 }
                            assertEquals("しんりょく", ability.name)
                            assertEquals("ピンチに くさの いりょくが あがる", ability.description)
                        }
                    }
                    checks.attempt("api.sample.move") {
                        val move = api.moves.single { it.id == 1 }
                        assertEquals(control.moveName, move.name)
                        assertEquals(reopened.defaultTextProjection().moveDescription(1), move.description)
                        if (control.generation >= 2) assertTrue(!move.description.isNullOrBlank())
                    }
                    checks.attempt("api.sample.types") {
                        control.typeSamples.keys.forEach { name -> assertTrue(api.types.any { it.name == name }) }
                    }
                    checks.attempt("api.LNG-B002.sample.world-location") {
                        assertTrue(api.worldMaps.flatMap { it.locations }.any { it.displayName == control.worldLocationName })
                    }
                    checks.attempt("api.LNG-B002.sample.local-location") {
                        assertTrue(api.localMaps.any { it.displayName == control.locationName })
                    }
                    checks.attempt("api.capabilities") {
                        val summary = requireNotNull(bootstrap.language).projections.single().localizedCapabilities
                        assertEquals(LocalizedTextCapability.entries.map { it.name }.toSet(), summary.keys)
                        requireNotNull(overlay).localizedCapabilities.forEach { (capability, state) ->
                            assertEquals(state.status.name, summary.getValue(capability.name).status)
                            assertEquals(state.coveredRecords, summary.getValue(capability.name).coveredRecords)
                            assertEquals(state.expectedRecords, summary.getValue(capability.name).expectedRecords)
                        }
                    }
                    // Equality checks every projected optional field, including unavailable text, against the reopened catalog.
                    checks.attempt("api.reopened-projection-parity") {
                        assertTrue(api == com.enrpau.dualscreendex.companion.api.ApiViewBuilder.catalog(reopened))
                    }
                }
            }
        } finally {
            checks.finish()
        }
    }

    private fun assertOfficialConditionalWeatherPolicies(
        catalog: ParsedCatalog,
        control: NativeControl,
    ): Map<Int, DamageForecastAssembler.ConditionalWeatherPolicy> {
        val text = catalog.defaultTextProjection()
        return buildMap {
            control.typeSamples.forEach { (name, expectedRole) ->
                // Labels are independently pinned test oracles. IDs and move references come from this catalog.
                val type = catalog.typesById.values.single { text.typeName(it.id) == name }
                assertEquals(CapabilityStatus.AVAILABLE, type.semanticRole.status)
                assertEquals(expectedRole, type.semanticRole.value)
                val moves = catalog.movesById.values.filter { it.typeId.value == type.id }
                assertTrue("no actual move references for the independently decoded type", moves.isNotEmpty())
                moves.forEach { move ->
                    assertEquals(CapabilityStatus.AVAILABLE, move.typeId.status)
                    val policy = DamageForecastAssembler.conditionalWeatherPolicy(catalog, requireNotNull(move.typeId.value))
                    assertTrue(policy.unboundedUnknowns.isEmpty())
                    // This tests the forecast consumer's conditional policy, not weather existing in this engine.
                    when (expectedRole) {
                        TypeSemanticRole.FIRE, TypeSemanticRole.WATER -> {
                            assertEquals(1, policy.boundedAlternatives.size)
                            val modifier = policy.boundedAlternatives.single()
                            assertEquals(AppliedDamageCondition.WEATHER, modifier.kind)
                            assertEquals(1, modifier.minimumNumerator)
                            assertEquals(3, modifier.maximumNumerator)
                            assertEquals(2, modifier.denominator)
                            assertEquals(SemanticProof.SOURCE_VALIDATED, modifier.proof)
                        }
                        TypeSemanticRole.NORMAL -> assertTrue(policy.boundedAlternatives.isEmpty())
                        else -> error("native forecast sample needs an independent policy expectation")
                    }
                    put(move.id, policy)
                }
            }
        }
    }

    private class NativeChecks(private val control: NativeControl) {
        private val passed = mutableListOf<String>()
        private val failed = mutableListOf<String>()
        fun <T> attempt(boundary: String, block: () -> T): T? = try {
            block().also { passed += boundary }
        } catch (failure: AssertionError) {
            failed += "$boundary:${failure.javaClass.simpleName}"
            null
        } catch (failure: Exception) {
            failed += "$boundary:${failure.javaClass.simpleName}"
            null
        }
        fun finish() {
            val forecastStatus = when {
                failed.any { it.startsWith("$OFFICIAL_FORECAST_BOUNDARY:") || it.startsWith("$FAULT_INJECTED_FORECAST_BOUNDARY:") } -> "FAIL"
                OFFICIAL_FORECAST_BOUNDARY !in passed || FAULT_INJECTED_FORECAST_BOUNDARY !in passed -> "NOT_RUN"
                failed.isNotEmpty() -> "NOT_ACCEPTED"
                else -> "PASS"
            }
            // Only public control labels, hashes, statuses and boundary names: never paths, payloads or exception messages.
            println("NATIVE_E2E_RESULT ${control.folder} sha256=${control.sha256} passed=${passed.joinToString(",")} failed=${failed.joinToString(",")} " +
                "officialRomSemanticForecast=$forecastStatus liveBattleForecast=NOT_RUN")
            assertTrue("${control.folder}: ${failed.joinToString(",")}", failed.isEmpty())
        }
    }

    @Test
    fun redSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[0])

    @Test
    fun blueSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[1])

    @Test
    fun yellowSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[2])

    @Test
    fun goldRegionsSurviveCatalogStoreAndServeExactPngBytes() = assertRoundTrip(controls[3])

    @Test
    fun silverRegionsSurviveCatalogStoreAndServeExactPngBytes() = assertRoundTrip(controls[4])

    @Test
    fun crystalRegionsSurviveCatalogStoreAndServeExactPngBytes() = assertRoundTrip(controls[5])

    @Test
    fun officialEmeraldSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[6])

    @Test
    fun modernEmeraldSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[7])

    @Test
    fun modernEmeraldAbilityMechanicsSurviveCatalogStoreAndApi() {
        val control = controls[7]
        val configured = System.getenv(control.environmentVariable)
        assumeTrue("set ${control.environmentVariable} to run this real-ROM control", !configured.isNullOrBlank())
        val romPath = Path.of(requireNotNull(configured))
        assumeTrue("real ROM does not exist: $romPath", Files.isRegularFile(romPath))
        val rom = RomImage(Files.readAllBytes(romPath))
        assertEquals(control.romSha256, rom.sha256)

        val catalog = requireNotNull(CatalogParser.parse(rom).catalog)
        val expectedIds = (1..81).toSet()
        assertEquals(expectedIds, catalog.abilitiesById.filterValues { it.mechanics.value?.isNotEmpty() == true }.keys)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.ABILITY_DESCRIPTIONS).status)
        assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.SPRITES).status)
        assertEquals("Helps repel wild Pokémon.", catalog.defaultTextProjection().abilityDescription(1))

        val root = newRoot()
        var server: AndroidLoopbackServer? = null
        try {
            val cache = CatalogCache(root.toFile(), JdbcTestCatalogDatabaseFactory)
            cache.write(
                catalog,
                CatalogSourceMetadata.direct(romPath.fileName.toString(), rom.size, "REAL-CONTROL"),
                CatalogWriteProgress.complete(),
            )
            assertDatabaseIntegrity(cache.fileFor(rom.sha256))
            val reopened = requireNotNull(cache.readComplete(rom.sha256)).catalog
            assertEquals(
                catalog.abilitiesById.mapValues { it.value.mechanics },
                reopened.abilitiesById.mapValues { it.value.mechanics },
            )
            assertEquals(catalog.localization, reopened.localization)
            assertEquals(CapabilityStatus.AVAILABLE, reopened.capabilities.getValue(RomCapability.SPRITES).status)

            val runtime = ProductionCompanionRuntime().apply { loadCatalog(romPath.fileName.toString(), reopened) }
            val apiCatalog = requireNotNull(runtime.bootstrap().catalog)
            assertEquals("AVAILABLE", apiCatalog.capabilities.getValue(RomCapability.SPRITES.name))
            val apiAbilities = apiCatalog.species
                .flatMap { it.abilities }
                .associateBy { it.id }
            val referencedExpectedIds = reopened.speciesById.values
                .flatMap { it.abilityIds.value.orEmpty() }
                .toSet()
                .intersect(expectedIds)
            assertEquals(expectedIds, referencedExpectedIds)
            assertEquals(referencedExpectedIds, apiAbilities.keys.intersect(expectedIds))
            assertEquals("Helps repel wild Pokémon.", apiAbilities.getValue(1).description)
            assertEquals(
                "Switch-in",
                apiAbilities.getValue(22).mechanics.single { it.kind == "STAT_STAGE" }.conditions.single().label,
            )
            assertEquals(
                "While affected by status",
                apiAbilities.getValue(62).mechanics.single { it.kind == "MULTIPLIER" }.conditions.single().label,
            )
            if (81 in referencedExpectedIds) {
                assertEquals(
                    "Damaging Normal-type moves",
                    apiAbilities.getValue(81).mechanics.single { it.kind == "TYPE_CHANGE" }.conditions.single().label,
                )
            }

            server = AndroidLoopbackServer(runtime) { null }.also { it.start() }
            val json = URI("http://127.0.0.1:${server.address.port}/api/bootstrap").toURL().readText()
            assertTrue(json.contains("Helps repel wild Pokémon."))
            assertTrue(json.contains("\"SPRITES\":\"AVAILABLE\""))
            assertTrue(json.contains("\"name\":\"Intimidate\""))
            assertTrue(json.contains("\"label\":\"Switch-in\""))
            if (81 in referencedExpectedIds) {
                assertTrue(json.contains("\"name\":\"Pixilate\""))
                assertTrue(json.contains("\"value\":\"Normal → Fairy\""))
            }
        } finally {
            server?.close()
            deleteTree(root)
        }
    }

    @Test
    fun officialGen3AbilityDescriptionsSurviveOverlayWithoutEnteringSharedMechanics() {
        val controls = listOf(
            Triple(
                "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - Ruby Version (USA, Europe) (Rev 2).gba",
                "0fdd36e92b75bed65d09df4635ab0b707b288c2bf1dc4c6e7a4a4f0eebe9d64c",
                "Ruby",
            ),
            Triple(
                "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - Sapphire Version (USA, Europe) (Rev 2).gba",
                "02ca41513580a8b780989dee428df747b52a0b1a55bec617886b4059eb1152fb",
                "Sapphire",
            ),
            Triple(
                "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - Emerald Version (USA, Europe).gba",
                "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af",
                "Emerald",
            ),
            Triple(
                "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - FireRed Version (USA, Europe) (Rev 1).gba",
                "729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059",
                "FireRed",
            ),
            Triple(
                "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - LeafGreen Version (USA, Europe) (Rev 1).gba",
                "2f978f635b9593f6ca26ec42481c53a6b39f6cddd894ad5c062c1419fac58825",
                "LeafGreen",
            ),
        )
        controls.forEach { (rawPath, expectedSha, label) ->
            val romPath = Path.of(rawPath)
            assumeTrue("real ROM does not exist: $romPath", Files.isRegularFile(romPath))
            val rom = RomImage(Files.readAllBytes(romPath))
            assertEquals(label, expectedSha, rom.sha256)
            val catalog = requireNotNull(CatalogParser.parse(rom).catalog)
            assertEquals(label, (1..77).toSet(), catalog.abilitiesById.keys)
            val text = catalog.defaultTextProjection()
            assertTrue(
                "$label retained description-derived behavior in shared mechanics",
                catalog.abilitiesById.all { (abilityId, ability) ->
                    val description = text.abilityDescription(abilityId)
                    ability.mechanics.value.orEmpty().none { mechanic ->
                        mechanic.kind == AbilityMechanicKind.BEHAVIOR && mechanic.value == description
                    }
                },
            )
            assertEquals(
                label,
                "Defined but inactive in this engine",
                catalog.abilitiesById.getValue(76).mechanics.value.orEmpty()
                    .single { it.kind == AbilityMechanicKind.BEHAVIOR }.value,
            )

            val root = newRoot()
            try {
                val cache = CatalogCache(root.toFile(), JdbcTestCatalogDatabaseFactory)
                cache.write(
                    catalog,
                    CatalogSourceMetadata.direct(romPath.fileName.toString(), rom.size, label),
                    CatalogWriteProgress.complete(),
                )
                val reopened = requireNotNull(cache.readComplete(rom.sha256)).catalog
                assertEquals(label, catalog.abilitiesById, reopened.abilitiesById)
                assertEquals(label, catalog.localization, reopened.localization)
                assertEquals(
                    label,
                    "Defined but inactive in this engine",
                    reopened.abilitiesById.getValue(76).mechanics.value.orEmpty()
                        .single { it.kind == AbilityMechanicKind.BEHAVIOR }.value,
                )
                val api = requireNotNull(
                    ProductionCompanionRuntime().apply {
                        loadCatalog(romPath.fileName.toString(), reopened)
                    }.bootstrap().catalog,
                )
                val referencedAbilities = api.species.flatMap { it.abilities }.distinctBy { it.id }
                assertTrue(
                    "$label API omitted source-backed descriptions",
                    referencedAbilities.all { ability -> !ability.description.isNullOrBlank() },
                )
                assertTrue(
                    "$label API exposed description-derived behavior as shared mechanics",
                    referencedAbilities.none { ability ->
                        ability.mechanics.any { mechanic ->
                            mechanic.kind == "BEHAVIOR" && mechanic.value == ability.description
                        }
                    },
                )
            } finally {
                deleteTree(root)
            }
        }
    }

    @Test
    fun classicSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[8])

    @Test
    fun fireRedFourRegionsSurviveCatalogStoreAndServeExactPngBytes() = assertRoundTrip(controls[9])

    @Test
    fun leafGreenFourRegionsSurviveCatalogStoreAndServeExactPngBytes() = assertRoundTrip(controls[10])

    @Test
    fun darkCryDungeonBindingSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[11])

    @Test
    fun darkVioletFourRegionsSurviveCatalogStoreAndServeExactPngBytes() = assertRoundTrip(controls[12])

    @Test
    fun cloverFourRegionsSurviveCatalogStoreAndServeExactPngBytes() = assertRoundTrip(controls[13])

    @Test
    fun orangeRegionSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[14])

    @Test
    fun shinRedSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[15])

    @Test
    fun beyondRedSurvivesCatalogStoreAndServesExactPngBytes() = assertRoundTrip(controls[16])

    @Test
    fun unboundMapsSurviveCatalogStoreAndServeExactPngBytes() = assertRoundTrip(controls[17])

    @Test
    fun odysseyMapsSurviveCatalogStoreAndServeExactPngBytes() = assertRoundTrip(controls[18])

    private fun assertRoundTrip(control: Control) {
        val configured = System.getenv(control.environmentVariable)
        assumeTrue("set ${control.environmentVariable} to run this real-ROM control", !configured.isNullOrBlank())
        val romPath = Path.of(requireNotNull(configured))
        assumeTrue("real ROM does not exist: $romPath", Files.isRegularFile(romPath))
        val rom = RomImage(Files.readAllBytes(romPath))
        assertEquals(control.romSha256, rom.sha256)

        val catalog = requireNotNull(CatalogParser.parse(rom).catalog)
        assertEquals(control.pngHashes.size, catalog.worldMaps.regions.size)
        assertEquals(
            control.regionKeys,
            catalog.worldMaps.regions.map { it.key },
        )

        val root = newRoot()
        var server: AndroidLoopbackServer? = null
        try {
            val cache = CatalogCache(root.toFile(), JdbcTestCatalogDatabaseFactory)
            cache.write(
                catalog,
                CatalogSourceMetadata.direct(romPath.fileName.toString(), rom.size, "REAL-CONTROL"),
                CatalogWriteProgress.complete(),
            )
            assertDatabaseIntegrity(cache.fileFor(rom.sha256))
            val reopened = requireNotNull(cache.readComplete(rom.sha256)).catalog
            assertEquals(catalog.worldMaps, reopened.worldMaps)
            assertEquals(catalog.abilitiesById, reopened.abilitiesById)
            val parsedEvolutionEdges = catalog.navigableSpecies().associate { species ->
                species.id to species.evolutionEdges.value.orEmpty()
            }
            val reopenedEvolutionEdges = reopened.navigableSpecies().associate { species ->
                species.id to species.evolutionEdges.value.orEmpty()
            }
            assertEquals(parsedEvolutionEdges, reopenedEvolutionEdges)

            val runtime = ProductionCompanionRuntime().apply { loadCatalog(romPath.fileName.toString(), reopened) }
            val apiCatalog = requireNotNull(runtime.bootstrap().catalog)
            val referencedAbilities = apiCatalog.species.flatMap { it.abilities }.associateBy { it.id }
            val expectedReferencedAbilityIds = reopened.navigableSpecies()
                .flatMap { it.abilityIds.value.orEmpty() }
                .filter(reopened.abilitiesById::containsKey)
                .toSet()
            assertEquals(expectedReferencedAbilityIds, referencedAbilities.keys)
            if (reopened.capabilities.getValue(RomCapability.ABILITY_MECHANICS).status == CapabilityStatus.AVAILABLE) {
                assertTrue(
                    "${control.environmentVariable} omitted persisted ability mechanics from the API",
                    referencedAbilities.values.all { it.mechanics.isNotEmpty() },
                )
            }
            assertEquals(
                reopenedEvolutionEdges.values.sumOf(List<*>::size),
                apiCatalog.species.sumOf { it.evolutions.size },
            )
            server = AndroidLoopbackServer(runtime) { null }.also { it.start() }
            val base = "http://127.0.0.1:${server.address.port}"
            val actualPngHashes = reopened.worldMaps.regions.map { region ->
                val key = URLEncoder.encode(region.imageAssetKey, StandardCharsets.UTF_8)
                val response = URI("$base/api/maps/$key.png").toURL().openConnection() as HttpURLConnection
                assertEquals(region.imageAssetKey, 200, response.responseCode)
                assertEquals(region.imageAssetKey, "image/png", response.contentType)
                val bytes = response.inputStream.use { it.readBytes() }
                assertTrue(region.imageAssetKey, bytes.copyOfRange(1, 4).contentEquals("PNG".toByteArray()))
                sha256(bytes)
            }
            assertEquals(control.pngHashes, actualPngHashes)
            val localMapsToServe = buildList {
                control.localBaseAreaId?.let { baseAreaId ->
                    add(reopened.localMaps.maps.single { it.baseAreaId == baseAreaId })
                }
                reopened.localMaps.maps.firstOrNull()?.let(::add)
            }.distinctBy { it.imageAssetKey }
            localMapsToServe.forEach { localMap ->
                val key = URLEncoder.encode(localMap.imageAssetKey, StandardCharsets.UTF_8)
                val response = URI("$base/api/maps/$key.png").toURL().openConnection() as HttpURLConnection
                assertEquals(localMap.imageAssetKey, 200, response.responseCode)
                assertEquals(localMap.imageAssetKey, "image/png", response.contentType)
                val bytes = response.inputStream.use { it.readBytes() }
                val expected = requireNotNull(
                    LocalMapAssetRenderer.render(reopened.localMaps, localMap.imageAssetKey, MapLighting.DAY),
                ).bytes
                assertTrue(localMap.imageAssetKey, bytes.contentEquals(expected))
            }
        } finally {
            server?.close()
            deleteTree(root)
        }
    }

    private fun assertDatabaseIntegrity(file: File) {
        JdbcTestCatalogDatabaseFactory.open(file).use { database ->
            assertEquals(
                listOf("ok"),
                database.query("PRAGMA quick_check") { row -> row.string("quick_check") },
            )
            assertTrue(
                database.query("PRAGMA foreign_key_check") { row -> row.string("table") }.isEmpty(),
            )
        }
    }

    private fun newRoot(): Path {
        val configuredRoot = System.getenv("DUALDEX_TEST_TEMP_ROOT")?.takeIf(String::isNotBlank)
        val parent = configuredRoot?.let(Path::of)
        if (parent != null) Files.createDirectories(parent)
        return if (parent == null) Files.createTempDirectory("dualdex-map-roundtrip-")
        else Files.createTempDirectory(parent, "dualdex-map-roundtrip-")
    }

    private fun deleteTree(root: Path) {
        if (!Files.exists(root)) return
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private data class Control(
        val environmentVariable: String,
        val romSha256: String,
        val regionKeys: List<String>,
        val pngHashes: List<String>,
        val localBaseAreaId: Int? = null,
    )

    private data class ThemeControl(val environmentVariable: String, val romSha256: String)

    private data class NativeControl(
        val folder: String,
        val sha256: String,
        val family: EngineFamily,
        val codecId: String,
        val generation: Int,
        val dexFragment: String,
        val locationName: String,
    ) {
        // Gen I World is an encounter-point domain: the town sample belongs to Local, not World.
        val worldLocationName = if (generation == 1) "トキワのもり" else locationName
        val speciesName = if (folder.startsWith("ko/")) "이상해씨" else "フシギダネ"
        val moveName = if (folder.startsWith("ko/")) "막치기" else "はたく"
        val typeSamples = if (folder.startsWith("ko/")) mapOf(
            "노말" to TypeSemanticRole.NORMAL, "화염" to TypeSemanticRole.FIRE, "물" to TypeSemanticRole.WATER,
        ) else mapOf(
            "ノーマル" to TypeSemanticRole.NORMAL, "ほのお" to TypeSemanticRole.FIRE, "みず" to TypeSemanticRole.WATER,
        )
    }

    private companion object {
        // UTF-8 digests of independently decoded, whitespace-normalized compiled Ruby/Emerald
        // move records using the pinned pokeruby charmap below; not production-parser baselines.
        // Covers first/last moves plus short-learnset false negatives. No raw ROM prose is retained here.
        val nativeMoveProseHashes = mapOf(
            1 to "6a560e56dbc81ff4d54a063ea1f3a39346aa1cd1667e62f8be962c72ec67edad",
            11 to "d67892aff9e60a4434cc332043dd0dd17490d9ab44a06d83b0ba04811c097379",
            72 to "5a90513a9d1c5eaba24fed32f8707e28538d9c8d2aee0cb642668a4bac5b1ff6",
            253 to "1b2b0799fe0bc62b6a3516352bc429acfd3f2ae2605598417a1b8975192e1f71",
            354 to "e77157a51610a80036160bd8b7c61ca486b1b705664f00c1c4240c31a746cc1c",
        )
        const val OFFICIAL_FORECAST_BOUNDARY = "LNG-D005.forecast.official-rom-semantic-policy"
        const val FAULT_INJECTED_FORECAST_BOUNDARY = "LNG-D005.forecast.fault-injected-authority-removed"

        // Exact inputs match NativeOfficialLanguageLiveRomTest; hashes are test identities, never production routing.
        // Independent text oracles (not the production parser's output):
        // https://github.com/Narishma-gb/pokeyellow-jp/tree/f282e72ae26232790fdb780aa5a5db7ec8ebf572
        //   data/{pokemon/names,pokemon/dex_entries,moves/names,types/names,maps/names}.asm
        // https://github.com/Narishma-gb/pokegold-kr/tree/7743877dc9fa8603f4b6eaebe904a7ba03fdb9e4
        //   data/{pokemon/names,moves/names,moves/descriptions,types/names,maps/landmarks}.asm
        //   data/pokemon/dex_entries/{gold,silver}/bulbasaur.asm (distinct descriptions).
        // JA compiled-control snippets were independently decoded with pinned public charmaps, without parser imports:
        // https://github.com/luckytyphlosion/pokered-jp/blob/258d1a89ec49a2a0ccfbdd232ac0e5d96d00899a/charmap.asm
        // https://github.com/scr-trees/pokegold_jpcrystalvc/blob/f2b5db1deb0b8f2009d7e9d50b3bcb05ef8a9f53/charmap.asm
        // https://github.com/pret/pokeruby/blob/63a8cbf0016b351a4e68f7036fa0b77e23d2f2c1/charmap.txt
        // https://github.com/pret/pokeruby/blob/63a8cbf0016b351a4e68f7036fa0b77e23d2f2c1/include/constants/species.h
        //   SPECIES_BULBASAUR = 1, independently corroborated in all three six-byte compiled name tables.
        // Bulbasaur description starts: RB 0x421a2; Yellow 0x4061f; Gold 0x443e7; Crystal 0x44403;
        // Ruby 0x37db9c; Emerald 0x539c70; FireRed 0x404f1c. These are evidence metadata, never lookup code.
        // Native row 1 height/weight = 7/69 at roots Ruby 0x38474c, Emerald 0x54069c, FireRed 0x409c00.
        // Native ability row 65 independently decoded from paired 8-byte names / 19-byte prose tables:
        // Ruby 0x1cbc44/0x1cbeb4; Emerald 0x2ebdc4/0x2ec034; FireRed 0x207a8c/0x207cfc.
        // Gen II charmap checkout's English game text is NOT used as a Japanese description oracle.
        // Location labels: Gen I source names; JA Gen II compiled landmark names at 0x92632/0x926d7;
        // JA Gen III compiled names at Ruby 0x3becb0/Emerald 0x57c6e0 (and FireRed's native map-name table).
        // Applicable native town/description absence is LNG-B002, not waived by historical Western NOT_FOUND.
        // Forecast acceptance here is ROM-only conditional type policy through actual move references and SQLite reopen.
        // No battle sample/formula is supplied; live damage accuracy and engine weather applicability are not claimed.
        val nativeControls = listOf(
            NativeControl("ja/RED_BLUE", "3f0dc460ca8d06be1c9ac96307c939c0ea7baa366b40c2f1f4ad63242b6c4816", EngineFamily.RED_BLUE,
                "gb-gen1-ja-red-blue", 1, "うまれたときから", "マサラ"),
            NativeControl("ja/YELLOW", "1349408f328f633b33e059e654edabd19810530df9c883eda03a85d5bb10161a", EngineFamily.YELLOW,
                "gb-gen1-ja-yellow", 1, "なんにちだって", "マサラ"),
            NativeControl("ja/GOLD_SILVER", "27a07a1d3faf9c6a0b1b60d5e88ee3a4159a751a47b4c46ab09f1202d52bac3e", EngineFamily.GOLD_SILVER,
                "gb-gen2-ja", 2, "たっぷり。たねは", "ワカバタウン"),
            NativeControl("ja/CRYSTAL", "136ada06cb68656b7de475fa4b278d37dbeff8f5257e7dfdf7f4a4aec19a90f3", EngineFamily.CRYSTAL,
                "gb-gen2-ja", 2, "うまれて しばらく", "ワカバタウン"),
            NativeControl("ja/RUBY_SAPPHIRE", "a7ea012b67a27da2893bfdfcb5f64915607b26904b4fc635a1055e8e40e692ab", EngineFamily.RUBY_SAPPHIRE,
                "gba-gen3-ja-ruby-sapphire", 3, "ひなたで ひるねを", "ミシロタウン"),
            NativeControl("ja/EMERALD", "33f5610b9186b4add09fef68895deb00f552b997b3d133b5a961e5123506343c", EngineFamily.EMERALD,
                "gba-gen3-ja-emerald-frlg", 3, "ひなたで ひるねを", "ミシロタウン"),
            NativeControl("ja/FIRERED_LEAFGREEN", "cec5fc4dbe38cd8026bd6664a1a041d9dc91e8d4249bab04e7bde70c3cdf4e06", EngineFamily.FIRERED_LEAFGREEN,
                "gba-gen3-ja-emerald-frlg", 3, "うまれたときから", "マサラタウン"),
            NativeControl("ko/GOLD", "9c273e86e6120c6a038160ccb0153b8b20425b84fc08a496281c1d1bcac492f6", EngineFamily.GOLD_SILVER,
                "gb-gen2-ko", 2, "등의 씨앗 안에는", "연두마을"),
            NativeControl("ko/SILVER", "ebbac63c0c4309c82dbb6723e7163369784f962b4fd3e2f486075307c3008a22", EngineFamily.GOLD_SILVER,
                "gb-gen2-ko", 2, "태어날 때부터 등에 씨앗을", "연두마을"),
        )
        val themeControls = listOf(
            ThemeControl("DUALDEX_POKERED_ROM", "5ca7ba01642a3b27b0cc0b5349b52792795b62d3ed977e98a09390659af96b7b"),
            ThemeControl("DUALDEX_POKECRYSTAL_ROM", "fdcc3c8c43813cf8731fc037d2a6d191bac75439c34b24ba1c27526e6acdc8a2"),
            ThemeControl("DUALDEX_OFFICIAL_EMERALD_ROM", "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af"),
            ThemeControl("DUALDEX_UNBOUND_ROM", "7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7"),
            ThemeControl("DUALDEX_ODYSSEY_ROM", "44c7e3eafab19c39df7c39d54bafb78a1d9caf7c371244b6f5efb12cfd98d0d0"),
        )
        val GEN2_PNGS = listOf(
            "23739bddf01b2c98a03ca1c4af28ade7d751623ec8063311dd2b8b366c81c516",
            "c06748683d60a89e4d2984bbcb565dc854ddd7942295d5039b80bcabe223258d",
        )
        val controls = listOf(
            Control(
                "DUALDEX_POKERED_ROM",
                "5ca7ba01642a3b27b0cc0b5349b52792795b62d3ed977e98a09390659af96b7b",
                listOf("gen1-kanto"),
                listOf("aa70952cb3c34789bc63639861d304b05b1c034dfb57e58720520de72d2ed098"),
            ),
            Control(
                "DUALDEX_POKEBLUE_ROM",
                "2a951313c2640e8c2cb21f25d1db019ae6245d9c7121f754fa61afd7bee6452d",
                listOf("gen1-kanto"),
                listOf("aa70952cb3c34789bc63639861d304b05b1c034dfb57e58720520de72d2ed098"),
            ),
            Control(
                "DUALDEX_POKEYELLOW_ROM",
                "8cbaa499397e4f1a679c992ea9382a2dd7942ab398b48c19829c2d9529de47bf",
                listOf("gen1-kanto"),
                listOf("aa70952cb3c34789bc63639861d304b05b1c034dfb57e58720520de72d2ed098"),
            ),
            Control(
                "DUALDEX_POKEGOLD_ROM",
                "fb0016d27b1e5374e1ec9fcad60e6628d8646103b5313ca683417f52b97e7e4e",
                listOf("gen2-johto", "gen2-kanto"),
                GEN2_PNGS,
            ),
            Control(
                "DUALDEX_POKESILVER_ROM",
                "72b190859a59623cbef6c49d601f8de52c1d2331b4f08a8d2acc17274fc19a8c",
                listOf("gen2-johto", "gen2-kanto"),
                GEN2_PNGS,
            ),
            Control(
                "DUALDEX_POKECRYSTAL_ROM",
                "fdcc3c8c43813cf8731fc037d2a6d191bac75439c34b24ba1c27526e6acdc8a2",
                listOf("gen2-johto", "gen2-kanto"),
                GEN2_PNGS,
            ),
            Control(
                "DUALDEX_OFFICIAL_EMERALD_ROM",
                "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af",
                listOf("gen3-region-0"),
                listOf("c9d5f2a5c77c0df16c14c73a15577f0c6f4a05794c191ebe72ed5a24724aadc6"),
            ),
            Control(
                "DUALDEX_MODERN_EMERALD_ROM",
                "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895",
                listOf("gen3-region-0"),
                listOf("80c4a69b9372276818768123dcd7cad09bcced88720704c8f424bc4501931ffe"),
                localBaseAreaId = 0x0009,
            ),
            Control(
                "DUALDEX_CLASSIC_ROM",
                "01c0177b2498e1842a1bf9ee2ddac145fb95275321bd3813dbf17341d63ad16c",
                listOf("gen3-region-0"),
                listOf("0c171c9fe8175629aa47de4e2854a334a2025f21b9196ba2f4c57a8cdcbc67ec"),
            ),
            Control(
                "DUALDEX_FIRERED_ROM",
                "729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059",
                (0..3).map { "gen3-region-$it" },
                listOf(
                    "c691c958253ff35595b36bf69f85d8d8940929c13deb7d0851ece717ab9d67aa",
                    "5bf5a1caf04a9bdbbbb80ea4dba5f9cdbf7d1eb046e7d29a85f6cacd392fbb70",
                    "d6f9b9aac3127691700f46e4df681ce6c1aee8a4f32c0f274b1df043dc47c160",
                    "2e1d951bf0cdf4181a43fc2e451428b067a7f9ba4307dfbc7a6eea237bf01765",
                ),
            ),
            Control(
                "DUALDEX_LEAFGREEN_ROM",
                "2f978f635b9593f6ca26ec42481c53a6b39f6cddd894ad5c062c1419fac58825",
                (0..3).map { "gen3-region-$it" },
                listOf(
                    "c691c958253ff35595b36bf69f85d8d8940929c13deb7d0851ece717ab9d67aa",
                    "5bf5a1caf04a9bdbbbb80ea4dba5f9cdbf7d1eb046e7d29a85f6cacd392fbb70",
                    "d6f9b9aac3127691700f46e4df681ce6c1aee8a4f32c0f274b1df043dc47c160",
                    "2e1d951bf0cdf4181a43fc2e451428b067a7f9ba4307dfbc7a6eea237bf01765",
                ),
            ),
            Control(
                "DUALDEX_DARK_CRY_ROM",
                "e61d4f66e2d4d39798bcd18f5abfb3db75282508fffd12401b9a1e9d0c1b08ed",
                (0..3).map { "gen3-region-$it" },
                listOf(
                    "9bc538416978211d88e36bd8440a423957517718c51c838895e0f67432ef35c0",
                    "4556ba8ff635a8a1f234c4825ed7825bfc3a50515e306bb3af1ddaf908b8b13e",
                    "6f1acba35c5bed020c07f060506bb9761bb2f8cc137fd670cf51eb3c03a580d9",
                    "cfec5c171fa388debf9fe8745be2b812589fda2cfe12ca487d7bebd5cf8e64f5",
                ),
            ),
            Control(
                "DUALDEX_DARK_VIOLET_ROM",
                "6b7e6df19c974371a4f80ea5c0f1e8d68a2cfee248faf34080a48ae3f0135e21",
                (0..3).map { "gen3-region-$it" },
                listOf(
                    "d5f07e96179d64e411ac4dec65c6b5d45fd190391b153a67c0a12927ab0a63bb",
                    "5a5da685c0211d1639f9de29c0749239db8ed22aa819b249e23bb940fa43c32c",
                    "e46976338b3b08670f1c2e846100a58bfc7f337ba92a0f989024aa357b0f8778",
                    "d7a86d7147422ba4dc09e72e14a1ba8c5bc3f2feafcb6e78cf4aac7875c7a68a",
                ),
            ),
            Control(
                "DUALDEX_CLOVER_ROM",
                "42f99abd548934d77999ac3eb563fb9bc70a34701d37a262b21b882a43a8bdd9",
                (0..3).map { "gen3-region-$it" },
                listOf(
                    "66ec72ca90e7220017cad597c5cc6be2c4901d214d467cd9c9749a1804da748a",
                    "0906e9ede556e27e166ebcd7610e3b09af40fd86cbd3066427a3d44eb95324bc",
                    "f8c5f9d281dd36609b1a62c256c777dcae3a105cb1d8eaa7052acdde728381bd",
                    "1b898a83a7cd7d677ea1aec26b6f9f5bece1dae2fc5096f5cfb2e8ab04cc34ac",
                ),
            ),
            Control(
                "DUALDEX_ORANGE_ROM",
                "037f5ba913953f2387175c5e0549347d162ef3b224d25660e8055acdac4564be",
                listOf("gen2-johto"),
                listOf("0234ec217a38e89e8c711128077bc75589327cdb3aff21de715c2b9550794244"),
            ),
            Control(
                "DUALDEX_SHIN_RED_ROM",
                "024a1c4dab1b12d0b963c6cf756d2c1082de0ccd53fe31384787dcf34edef718",
                listOf("gen1-kanto"),
                listOf("aa70952cb3c34789bc63639861d304b05b1c034dfb57e58720520de72d2ed098"),
            ),
            Control(
                "DUALDEX_BEYOND_RED_ROM",
                "3640ed0493287136cd9321cb3428f44113e87354cf90402665ba60e41c8fc61a",
                listOf("gen1-kanto"),
                listOf("8a958b8ada1dd6f1b25be40fe207f86dff88b016d443f70a20052cd6e6aa1275"),
            ),
            Control(
                "DUALDEX_UNBOUND_ROM",
                "7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7",
                listOf("gen3-region-0"),
                listOf("47e97e55526df3a85db6776d3554d84169f21d1618468fac2592238ef2e5cc7d"),
            ),
            Control(
                "DUALDEX_ODYSSEY_ROM",
                "44c7e3eafab19c39df7c39d54bafb78a1d9caf7c371244b6f5efb12cfd98d0d0",
                (0..3).map { "gen3-region-$it" },
                listOf(
                    "70b94d44f4ee45651b3147395b7f40a65092e8774c84fd3b94c23647f1ae417a",
                    "7532f93f3c1070c8fbd341315981753cb3df60dce8d8e048f49ee6b9d76bcc33",
                    "790abca2ec290c272f3a99f678158ef14c7fa615316f823574b4b107e9a0ffa7",
                    "5ecec734a5eb76c0d59997fd95151083033cc910f03814135a4f0968805d18c5",
                ),
            ),
        )
    }
}

private object JdbcTestCatalogDatabaseFactory : CatalogDatabaseFactory {
    override fun open(file: File): CatalogDatabase {
        Class.forName("org.sqlite.JDBC")
        return JdbcTestCatalogDatabase(DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}"))
    }
}

private class JdbcTestCatalogDatabase(private val connection: Connection) : CatalogDatabase {
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

    override fun <T> streamQuery(
        sql: String,
        arguments: List<Any?>,
        consume: (com.darkaxt.dualdex.catalog.CatalogRows) -> T,
    ): T = connection.prepareStatement(sql).use { statement ->
        arguments.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
        statement.executeQuery().use { result ->
            consume(com.darkaxt.dualdex.catalog.CatalogRows {
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
