package com.darkaxt.dualdex.save.cli

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeNoException
import org.junit.Test

class SaveCliPathPolicyTest {
    @Test
    fun rejectsNormalizedOutputInputAliasesBeforeChangingInputs() {
        val root = Files.createTempDirectory("dualdex-save-cli-direct")
        try {
            val rom = write(root.resolve("game.gba"), byteArrayOf(1, 2, 3))
            val save = write(root.resolve("game.srm"), byteArrayOf(4, 5, 6))
            val beforeRom = Files.readAllBytes(rom)
            val beforeSave = Files.readAllBytes(save)
            val aliasedJson = root.resolve("nested").resolve("..").resolve("game.gba")

            assertThrows(IllegalArgumentException::class.java) {
                SaveCliPathPolicy.validate(options(rom, save, aliasedJson, root.resolve("report.md")))
            }

            assertArrayEquals(beforeRom, Files.readAllBytes(rom))
            assertArrayEquals(beforeSave, Files.readAllBytes(save))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun rejectsExistingHardLinkAliasesBeforeChangingInputs() {
        val root = Files.createTempDirectory("dualdex-save-cli-hard-link")
        try {
            val rom = write(root.resolve("game.gba"), byteArrayOf(1))
            val save = write(root.resolve("game.srm"), byteArrayOf(7, 8, 9))
            val output = Files.createLink(root.resolve("report.json"), save)
            val before = Files.readAllBytes(save)

            assertThrows(IllegalArgumentException::class.java) {
                SaveCliPathPolicy.validate(options(rom, save, output, root.resolve("report.md")))
            }

            assertArrayEquals(before, Files.readAllBytes(save))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun rejectsSymlinkedParentAliasesWhenSupported() {
        val root = Files.createTempDirectory("dualdex-save-cli-symlink")
        try {
            val real = Files.createDirectories(root.resolve("real"))
            val alias = root.resolve("alias")
            try {
                Files.createSymbolicLink(alias, real)
            } catch (failure: Exception) {
                assumeNoException(failure)
            }
            val rom = write(real.resolve("game.gba"), byteArrayOf(1, 2))
            val save = write(real.resolve("game.srm"), byteArrayOf(3, 4))

            assertThrows(IllegalArgumentException::class.java) {
                SaveCliPathPolicy.validate(
                    options(rom, save, alias.resolve("game.gba"), root.resolve("report.md")),
                )
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun rejectsOutputCollisionsAndParentAliases() {
        val root = Files.createTempDirectory("dualdex-save-cli-outputs")
        try {
            val rom = write(root.resolve("game.gba"), byteArrayOf(1))
            val save = write(root.resolve("game.srm"), byteArrayOf(2))
            val report = root.resolve("report.json")
            assertThrows(IllegalArgumentException::class.java) {
                SaveCliPathPolicy.validate(options(rom, save, report, report))
            }

            val inputDirectory = Files.createDirectories(root.resolve("probe-input"))
            assertThrows(IllegalArgumentException::class.java) {
                SaveCliPathPolicy.validate(
                    SaveCliOptions(
                        pairs = emptyList(),
                        probes = listOf(inputDirectory),
                        json = inputDirectory.resolve("report.json"),
                        markdown = root.resolve("report.md"),
                    ),
                )
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun options(rom: Path, save: Path, json: Path, markdown: Path) = SaveCliOptions(
        pairs = listOf(SaveInputPair(rom, save)),
        probes = emptyList(),
        json = json,
        markdown = markdown,
    )

    private fun write(path: Path, bytes: ByteArray): Path = Files.write(path, bytes)
}
