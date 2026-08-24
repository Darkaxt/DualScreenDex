package com.darkaxt.dualdex.live

import com.darkaxt.dualdex.battle.BattleEncounterKind
import com.darkaxt.dualdex.battle.Gen3LiveMemoryReader
import com.darkaxt.dualdex.battle.Gen3LivePointers
import com.darkaxt.dualdex.battle.LiveBattleState
import com.darkaxt.dualdex.battle.LiveClockPhase
import com.darkaxt.dualdex.battle.LiveClockState
import com.darkaxt.dualdex.battle.RuntimeMapPosition
import com.darkaxt.dualdex.save.SaveSnapshot
import com.darkaxt.dualdex.save.TrainerSnapshot
import com.darkaxt.dualdex.web.ProductionCompanionRuntime
import com.enrpau.dualscreendex.companion.api.SaveRamView
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import com.google.gson.Gson
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class UnifiedGameStateRealControlTest {
    @Test
    fun establishedOfficialAndHackIdentitiesMatchTheManifestInPlace() {
        val manifest = manifest()
        val officialRoot = officialRoot()
        val actualOfficial = Files.walk(officialRoot).use { paths ->
            paths.filter(Files::isRegularFile)
                .filter { path -> path.fileName.toString().substringAfterLast('.', "").lowercase() in ROM_EXTENSIONS }
                .map { officialRoot.relativize(it).toString().replace('\\', '/') }
                .sorted()
                .toList()
        }
        assertEquals(manifest.official.map { requireNotNull(it.relativePath) }.sorted(), actualOfficial)
        assertEquals(manifest.official.size, manifest.official.map(IdentityControl::sha256).distinct().size)

        manifest.official.forEach { control ->
            assertIdentity(control, officialRoot.resolve(requireNotNull(control.relativePath)))
        }
        manifest.hacks.forEach { control ->
            assertIdentity(control, hackPath(control))
        }
    }

    @Test
    fun realCatalogsPublishTheirExactUnifiedLiveAvailability() {
        val manifest = manifest()
        val officialRoot = officialRoot()
        val controls = manifest.official.map { it to officialRoot.resolve(requireNotNull(it.relativePath)) } +
            manifest.hacks.map { it to hackPath(it) }

        controls.forEach { (control, path) ->
            val loaded = RomSourceLoader.load(path)
            val parsed = requireNotNull(CatalogParser.parse(loaded.rom).catalog) { "${control.id} did not produce a catalog" }
            val owner = UnifiedGameStateDecoder()
            ProductionCompanionRuntime(transientGameState = owner).use { runtime ->
                runtime.loadCatalog(loaded.displayName, parsed)
                val context = requireNotNull(runtime.battleCatalogContext()) { "${control.id} has no live context" }
                assertEquals(control.generation ?: 3, context.generation)
                assertTrue("${control.id} lost battle species", context.catalog.species.isNotEmpty())
                assertTrue("${control.id} lost battle moves", context.catalog.moves.isNotEmpty())

                if (context.generation < 3) {
                    val area = requireNotNull(context.liveAreaMemoryLayout) { "${control.id} lost its live area ABI" }
                    assertNotNull("${control.id} lost live X", area.positionXWramOffset)
                    assertNotNull("${control.id} lost live Y", area.positionYWramOffset)
                    assertNull(context.gen3RuntimeMemoryLayout)
                    owner.beginSession(
                        TransientGameStateContext(
                            romIdentity = loaded.rom.sha256,
                            generation = context.generation,
                            catalog = context.catalog,
                            gen2TimeOfDayWramOffset = context.gen2TimeOfDayWramOffset,
                            liveAreaMemoryLayout = area,
                        ),
                    )
                    owner.acceptExistingGenerationSample(
                        sampleId = 1,
                        battle = LiveBattleState(false, null, BattleEncounterKind.UNKNOWN),
                        areaBaseId = 1,
                        mapPosition = RuntimeMapPosition(2, 3),
                        clock = context.gen2TimeOfDayWramOffset?.let { LiveClockState(phase = LiveClockPhase.NIGHT) },
                    )
                    val snapshot = requireNotNull(owner.current)
                    assertEquals(ResolvedValueSource.LIVE, snapshot.battle.source)
                    assertEquals(ResolvedValueSource.LIVE, snapshot.location.areaBaseId.source)
                    assertEquals(ResolvedValueSource.LIVE, snapshot.location.position.source)
                    assertEquals(
                        if (context.generation == 2) ResolvedValueSource.LIVE else ResolvedValueSource.UNAVAILABLE,
                        snapshot.clock.source,
                    )
                    assertEquals(ResolvedValueSource.UNAVAILABLE, snapshot.trainer.identity.source)
                    assertEquals(ResolvedValueSource.UNAVAILABLE, snapshot.pokedex.seenDexNumbers.source)
                    assertEquals(ResolvedValueSource.UNAVAILABLE, snapshot.party.source)
                    println(
                        "UNIFIED_CONTROL ${control.id} generation=${context.generation} " +
                            "trainer=0 dex=0 party=0 battle=1 area=1 position=1 " +
                            "clock=${if (context.generation == 2) 1 else 0} bag=0 flags=0",
                    )
                } else {
                    assertGen3LiveAndRecovery(control.id, loaded.rom.sha256, context, owner)
                }
            }
        }
    }

    private fun assertGen3LiveAndRecovery(
        id: String,
        romIdentity: String,
        context: com.darkaxt.dualdex.battle.BattleCatalogContext,
        owner: UnifiedGameStateDecoder,
    ) {
        val layout = requireNotNull(context.gen3RuntimeMemoryLayout) { "$id lost its Gen III runtime layout" }
        val saveContext = requireNotNull(context.saveParseContext) { "$id lost its save parse context" }
        val saveAbi = requireNotNull(saveContext.gen3SaveRuntimeAbi) { "$id lost its typed save ABI" }
        assertTrue(
            "$id lost SaveBlock1 addressing",
            layout.saveBlock1Address != null || layout.saveBlock1PointerAddress != null,
        )
        assertTrue(
            "$id lost SaveBlock2 addressing",
            layout.saveBlock2Address != null || layout.saveBlock2PointerAddress != null,
        )
        assertNotNull("$id lost Party count", layout.playerPartyCountAddress)
        assertNotNull("$id lost Party records", layout.playerPartyAddress)
        assertEquals(
            "$id changed its source-proven live-clock availability",
            id in GEN3_LIVE_CLOCK_CONTROLS,
            layout.liveClockAddress != null,
        )

        owner.beginSession(
            TransientGameStateContext(
                romIdentity = romIdentity,
                generation = 3,
                catalog = context.catalog,
                gen3RuntimeMemoryLayout = layout,
                saveParseContext = saveContext,
            ),
        )
        val saveBlock1 = ByteArray(saveAbi.saveBlock1Size)
        val saveBlock2 = ByteArray(saveAbi.saveBlock2Size)
        val trainer = saveAbi.trainer
        saveBlock2[trainer.playerNameOffset] = 0xBB.toByte()
        saveBlock2[trainer.playerNameOffset + 1] = 0xFF.toByte()
        saveBlock2[trainer.genderOffset] = 0
        saveBlock2.putU16le(trainer.trainerIdOffset, 1)
        saveBlock2.putU16le(trainer.playTimeHoursOffset, 1)
        saveBlock2[trainer.playTimeMinutesOffset] = 2
        trainer.encryptionKeyOffset?.let { saveBlock2.putU32le(it, 0) }
        saveBlock1.putU32le(trainer.moneyOffset, 0)
        saveBlock1[layout.saveBlock1MapGroupOffset] = 1
        saveBlock1[layout.saveBlock1MapNumberOffset] = 2
        val regions = mutableMapOf(
            Gen3LiveMemoryReader.SAVE_BLOCK1_ID to saveBlock1,
            Gen3LiveMemoryReader.SAVE_BLOCK2_ID to saveBlock2,
            Gen3LiveMemoryReader.PARTY_COUNT_ID to byteArrayOf(0),
            Gen3LiveMemoryReader.PARTY_ID to ByteArray(
                requireNotNull(layout.playerPartyCapacity) * requireNotNull(layout.playerPartyRecordSize),
            ),
        )
        layout.liveClockAddress?.let {
            regions[Gen3LiveMemoryReader.CLOCK_ID] = byteArrayOf(0, 0, 12, 34, 56)
        }
        layout.extendedSaveSize?.let { size ->
            regions[Gen3LiveMemoryReader.EXTENDED_SAVE_ID] = ByteArray(size)
        }
        owner.acceptGen3LiveSample(
            sampleId = 1,
            regions = regions,
            battle = LiveBattleState(false, null, BattleEncounterKind.UNKNOWN),
            areaBaseId = null,
            mapPosition = RuntimeMapPosition(3, 4),
        )

        val live = requireNotNull(owner.current)
        assertEquals(ResolvedValueSource.LIVE, live.trainer.identity.source)
        assertEquals(ResolvedValueSource.LIVE, live.trainer.publicTrainerId.source)
        assertEquals(ResolvedValueSource.LIVE, live.trainer.money.source)
        assertEquals(ResolvedValueSource.LIVE, live.trainer.playTime.source)
        assertEquals(ResolvedValueSource.LIVE, live.trainer.badgeFlags.source)
        assertEquals(ResolvedValueSource.UNAVAILABLE, live.trainer.stars.source)
        assertEquals(ResolvedValueSource.LIVE, live.pokedex.seenDexNumbers.source)
        assertEquals(ResolvedValueSource.LIVE, live.pokedex.caughtDexNumbers.source)
        assertEquals(ResolvedValueSource.LIVE, live.party.source)
        assertEquals(ResolvedValueSource.LIVE, live.battle.source)
        assertEquals(ResolvedValueSource.LIVE, live.location.areaBaseId.source)
        assertEquals(ResolvedValueSource.LIVE, live.location.position.source)
        assertEquals(
            if (layout.liveClockAddress != null) ResolvedValueSource.LIVE else ResolvedValueSource.UNAVAILABLE,
            live.clock.source,
        )
        assertEquals(
            if (saveAbi.eventFlags != null) ResolvedValueSource.LIVE else ResolvedValueSource.UNAVAILABLE,
            live.eventFlags.source,
        )
        live.bag.values.forEach { pocket -> assertEquals(ResolvedValueSource.LIVE, pocket.source) }

        val recovery = RecoveryProjection(
            snapshot = SaveSnapshot(
                romIdentity = romIdentity,
                saveIdentity = "sanitized-$id",
                saveGeneration = 3,
                saveCounter = 1,
                currentArea = null,
                seenDexNumbers = setOf(1),
                caughtDexNumbers = setOf(1),
                party = emptyList(),
                storedIndividuals = emptyList(),
                capabilities = emptyMap(),
                trainer = TrainerSnapshot(
                    name = "B",
                    gender = 1,
                    publicTrainerId = 2,
                    money = 3,
                    playTimeHours = 4,
                    playTimeMinutes = 5,
                    badgeFlags = 6,
                    dexSeen = 1,
                    dexCaught = 1,
                    stars = 3,
                ),
            ),
            saveRam = SaveRamView(status = "MATCHED"),
        )
        assertTrue(owner.acceptRecovery(recovery).accepted)
        val merged = requireNotNull(owner.current)
        assertEquals("A", merged.trainer.identity.value?.name)
        assertEquals(ResolvedValueSource.LIVE, merged.trainer.identity.source)
        assertEquals(3, merged.trainer.stars.value)
        assertEquals(ResolvedValueSource.RECOVERY, merged.trainer.stars.source)

        saveBlock1.fill(0x7F)
        saveBlock2.fill(0x7F)
        assertEquals(0x0102, owner.current?.location?.areaBaseId?.value)
        assertEquals("A", owner.current?.trainer?.identity?.value?.name)
        assertFalse(owner.javaClass.declaredFields.any { it.type == ByteArray::class.java })

        val pointerWindows = owner.gen3PointerReadPlan(layout)
        val resolvedPointers = Gen3LivePointers(
            layout.saveBlock1Address ?: 0x02000000,
            layout.saveBlock2Address ?: 0x02010000,
        )
        val valueWindows = owner.gen3ValueReadPlan(
            layout,
            resolvedPointers,
        )
        val passiveBytes = valueWindows
            .filter { it.id == Gen3LiveMemoryReader.EXTENDED_SAVE_ID }
            .sumOf { it.byteCount }
        println(
            "UNIFIED_CONTROL $id generation=3 trainer=1 dex=1 party=1 battle=1 area=1 position=1 " +
                "clock=${if (layout.liveClockAddress != null) 1 else 0} bag=1 " +
                "flags=${if (saveAbi.eventFlags != null) 1 else 0} " +
                "windows=${pointerWindows.size + valueWindows.size} " +
                "bytes=${pointerWindows.sumOf { it.byteCount } + valueWindows.sumOf { it.byteCount }} " +
                "passiveAddedBytes=$passiveBytes retainedRawBytes=0",
        )
    }

    private fun assertIdentity(control: IdentityControl, path: Path) {
        assertTrue("${control.id} is absent: $path", Files.isRegularFile(path))
        val loaded = RomSourceLoader.load(path)
        assertEquals(control.bytes, loaded.rom.size)
        assertEquals(control.sha256, loaded.rom.sha256)
        val header = RomHeaderReader.read(loaded.rom)
        assertEquals(control.title, header.title)
        assertEquals(control.gameCode, header.gameCode)
        assertEquals(control.revision, header.revision)
    }

    private fun manifest(): IdentityManifest {
        val resource = requireNotNull(javaClass.getResourceAsStream("/unified-state/official-rom-identities.json"))
        return resource.reader().use { Gson().fromJson(it, IdentityManifest::class.java) }
    }

    private fun officialRoot(): Path {
        val root = Path.of(
            System.getenv("DUALDEX_OFFICIAL_ROM_ROOT")
                ?: "D:/Temp/PokemonHacks/roms/official",
        )
        assumeTrue("official ROM root does not exist: $root", Files.isDirectory(root))
        return root
    }

    private fun hackPath(control: IdentityControl): Path {
        val configured = control.environmentVariable?.let(System::getenv)
            ?: HACK_FALLBACKS.getValue(control.id)
        val path = Path.of(configured)
        assumeTrue("${control.id} ROM does not exist: $path", Files.isRegularFile(path))
        return path
    }

    private fun ByteArray.putU16le(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }

    private fun ByteArray.putU32le(offset: Int, value: Long) {
        repeat(4) { index -> this[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private data class IdentityManifest(
        val schemaVersion: Int,
        val official: List<IdentityControl>,
        val hacks: List<IdentityControl>,
    )

    private data class IdentityControl(
        val id: String,
        val generation: Int? = 3,
        val relativePath: String? = null,
        val environmentVariable: String? = null,
        val bytes: Int,
        val sha256: String,
        val title: String,
        val gameCode: String? = null,
        val revision: Int,
    )

    private companion object {
        val ROM_EXTENSIONS = setOf("gb", "gbc", "gba", "zip", "7z")
        val HACK_FALLBACKS = mapOf(
            "modern-emerald" to "D:/Temp/PokemonHacks/corpus/expanded/roms/0116-a0b4e5e9c0c4/Modern Emerald (v3.5).gba",
            "unbound" to "D:/Temp/PokemonHacks/corpus/expanded/roms/0199-a275be0f927e/Unbound (v2.1.1.1).gba",
            "odyssey" to "D:/Temp/PokemonHacks/corpus/expanded/roms/0123-5e7ce46db2ce/Odyssey (v4.1.1).gba",
        )
        val GEN3_LIVE_CLOCK_CONTROLS = setOf("ruby", "sapphire", "emerald", "modern-emerald", "unbound")
    }
}
