package com.darkaxt.dualdex.save.cli

import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.SaveParseResult
import com.darkaxt.dualdex.save.SaveParser
import com.darkaxt.dualdex.save.SaveSpeciesContext
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.system.exitProcess

private const val USAGE = "save-cli [--pair <rom-or-zip> <save>] [--probe <save>] --json <path> --markdown <path>"

fun main(arguments: Array<String>) {
    if (arguments.any { it == "--help" || it == "-h" }) {
        println(USAGE)
        return
    }
    val options = try {
        SaveCliOptions.parse(arguments)
    } catch (failure: IllegalArgumentException) {
        System.err.println("${failure.message}\n$USAGE")
        exitProcess(2)
    }
    val report = SaveCompatibilityReport(options.pairs.map(::evaluate) + options.probes.map(::probe))
    write(options.json, GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n")
    write(options.markdown, SaveReportWriter.markdown(report))
    report.results.forEach { result ->
        println("${result.saveName}: ${result.status}, revision ${result.saveCounter ?: "-"}, ${result.party ?: 0} party, ${result.stored ?: 0} stored")
    }
    println("JSON: ${options.json.toAbsolutePath()}")
    println("Markdown: ${options.markdown.toAbsolutePath()}")
}

private fun probe(save: Path): SaveCompatibilityResult {
    val saveName = save.fileName.toString()
    val bytes = runCatching { Files.readAllBytes(save) }.getOrElse { failure ->
        return SaveCompatibilityResult(
            romName = "ROM unavailable",
            saveName = saveName,
            saveSha256Before = "UNREADABLE",
            saveSha256After = "UNREADABLE",
            sourceUnchanged = true,
            status = "ERROR",
            reasons = listOf(failure.message ?: failure.javaClass.simpleName),
        )
    }
    val beforeHash = sha256(bytes)
    val context = SaveParseContext(
        romIdentity = "structural-probe",
        speciesById = (1..2048).associateWith { SaveSpeciesContext(it, null, null) },
    )
    val parsed = SaveParser.parse(bytes, context)
    val afterHash = runCatching { sha256(Files.readAllBytes(save)) }.getOrDefault("UNREADABLE")
    return when (parsed) {
        is SaveParseResult.Parsed -> SaveCompatibilityResult(
            romName = "ROM unavailable",
            saveName = saveName,
            saveSha256Before = beforeHash,
            saveSha256After = afterHash,
            sourceUnchanged = beforeHash == afterHash,
            family = "GEN_III_STRUCTURAL",
            status = "STRUCTURAL_ONLY",
            saveCounter = parsed.snapshot.saveCounter,
            party = parsed.snapshot.party.size,
            stored = parsed.snapshot.storedIndividuals.size,
            currentArea = parsed.snapshot.currentArea?.let { "${it.mapGroup}:${it.mapNumber}" },
            capabilities = parsed.snapshot.capabilities.values
                .filterNot { it.capability.name == "SEEN" || it.capability.name == "CAUGHT" }
                .toList(),
            reasons = listOf("Seen/caught and species names require the matching parsed ROM catalog."),
        )
        is SaveParseResult.Unsupported -> SaveCompatibilityResult(
            romName = "ROM unavailable",
            saveName = saveName,
            saveSha256Before = beforeHash,
            saveSha256After = afterHash,
            sourceUnchanged = beforeHash == afterHash,
            status = "UNSUPPORTED",
            reasons = parsed.reasons,
        )
    }
}

private fun evaluate(pair: SaveInputPair): SaveCompatibilityResult {
    val romName = pair.rom.fileName.toString()
    val saveName = pair.save.fileName.toString()
    val saveBefore = runCatching { Files.readAllBytes(pair.save) }.getOrElse { failure ->
        return SaveCompatibilityResult(
            romName,
            saveName,
            saveSha256Before = "UNREADABLE",
            saveSha256After = "UNREADABLE",
            sourceUnchanged = true,
            status = "ERROR",
            reasons = listOf(failure.message ?: failure.javaClass.simpleName),
        )
    }
    val beforeHash = sha256(saveBefore)
    return runCatching {
        val loaded = RomSourceLoader.load(pair.rom)
        val catalog = requireNotNull(CatalogParser.parse(loaded.rom).catalog) {
            "ROM did not produce a supported catalog"
        }
        val context = SaveParseContext(
            romIdentity = catalog.romSha256,
            speciesById = catalog.speciesById.mapValues { (id, species) ->
                SaveSpeciesContext(id, species.dexNumber.value, species.growthRate.value, species.formId)
            },
            captureBallIds = catalog.captureBallsById.keys.ifEmpty { (1..15).toSet() },
        )
        val parsed = SaveParser.parse(saveBefore, context)
        val afterHash = sha256(Files.readAllBytes(pair.save))
        when (parsed) {
            is SaveParseResult.Parsed -> SaveCompatibilityResult(
                romName = romName,
                saveName = saveName,
                romSha256 = catalog.romSha256,
                saveSha256Before = beforeHash,
                saveSha256After = afterHash,
                sourceUnchanged = beforeHash == afterHash,
                family = catalog.family.name,
                status = "PARSED",
                saveCounter = parsed.snapshot.saveCounter,
                seen = parsed.snapshot.seenDexNumbers.size,
                caught = parsed.snapshot.caughtDexNumbers.size,
                party = parsed.snapshot.party.size,
                stored = parsed.snapshot.storedIndividuals.size,
                currentArea = parsed.snapshot.currentArea?.let { "${it.mapGroup}:${it.mapNumber}" },
                capabilities = parsed.snapshot.capabilities.values.toList(),
            )
            is SaveParseResult.Unsupported -> SaveCompatibilityResult(
                romName,
                saveName,
                catalog.romSha256,
                beforeHash,
                afterHash,
                beforeHash == afterHash,
                catalog.family.name,
                "UNSUPPORTED",
                reasons = parsed.reasons,
            )
        }
    }.getOrElse { failure ->
        val afterHash = runCatching { sha256(Files.readAllBytes(pair.save)) }.getOrDefault("UNREADABLE")
        SaveCompatibilityResult(
            romName,
            saveName,
            saveSha256Before = beforeHash,
            saveSha256After = afterHash,
            sourceUnchanged = beforeHash == afterHash,
            status = "ERROR",
            reasons = listOf(failure.message ?: failure.javaClass.simpleName),
        )
    }
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it) }

private fun write(path: Path, text: String) {
    path.toAbsolutePath().parent?.let(Files::createDirectories)
    Files.writeString(path, text)
}
