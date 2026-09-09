package com.enrpau.dualscreendex.parser.text

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.jar.JarFile

/**
 * Test-only G2 execution tooling; never official-matrix acceptance. Run CodecGoldenEvidenceTest
 * with codecGolden.output set in the Test JVM to exclusively create a canonical evidence file.
 *
 * vectorSetSha256 = SHA-256(UTF-8(canonical(oracleManifest))), WITHOUT a newline. Each manifest
 * binds the complete literal identity and ordered vectors (input, bounds, expected outcome,
 * tokens and counters); it contains NO observed output or build-dependent source pins.
 * Canonical is official_matrix.canonical's sorted-key, compact, ensure_ascii=False contract,
 * restricted to this model's string keys, Unicode strings, integers, booleans, null and lists.
 * A successful check.data is usable as codecGoldenVectors data only after independent review
 * and a future stable-source capture supplies the matrix's separate per-ROM run binding.
 */
internal object CodecGoldenEvidence {
    fun canonical(value: Any?): String = when (value) {
        null -> "null"
        is Boolean, is Int, is Long -> value.toString()
        is String -> quote(value)
        is List<*> -> value.joinToString(",", "[", "]") { canonical(it) }
        is Map<*, *> -> {
            require(value.keys.all { it is String && it.all { c -> c.code in 0x20..0x7e } })
            value.entries.sortedBy { it.key as String }.joinToString(",", "{", "}") {
                quote(it.key as String) + ":" + canonical(it.value)
            }
        }
        else -> throw IllegalArgumentException("Unsupported canonical value")
    }

    private fun quote(value: String): String {
        // Java's UTF-8 encoder replaces lone surrogates; reject rather than hash replaced text.
        var index = 0
        while (index < value.length) {
            val c = value[index++]
            if (c.isHighSurrogate()) require(index < value.length && value[index++].isLowSurrogate())
            else require(!c.isLowSurrogate())
        }
        return buildString {
            append('"')
            value.forEach { c ->
                append(when (c) {
                    '"' -> "\\\""; '\\' -> "\\\\"; '\b' -> "\\b"; '\u000c' -> "\\f"
                    '\n' -> "\\n"; '\r' -> "\\r"; '\t' -> "\\t"
                    else -> if (c.code < 32) "\\u" + c.code.toString(16).padStart(4,'0') else c.toString()
                })
            }
            append('"')
        }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2,'0') }
    fun digest(value: Any?): String = sha256(canonical(value).toByteArray(Charsets.UTF_8))

    private fun identity(i: CodecGoldenVectors.Identity): Map<String, Any?> = mapOf(
        "codecId" to i.id, "codecVersion" to i.version, "language" to i.language,
        "generations" to listOf(i.generation), "platforms" to i.platforms, "terminator" to i.terminator,
        "charset" to i.charset,
    )
    private fun decoded(d: DecodedText?): Map<String, Any?>? = d?.let { mapOf(
        "text" to it.text, "terminated" to it.terminated, "validBytes" to it.validBytes,
        "contentBytes" to it.contentBytes, "validUnits" to it.validUnits, "contentUnits" to it.contentUnits,
        "consumedBytes" to it.consumedBytes, "glyphUnits" to it.glyphUnits, "whitespaceUnits" to it.whitespaceUnits,
        "substitutionUnits" to it.substitutionUnits, "controlUnits" to it.controlUnits, "invalidUnits" to it.invalidUnits,
    ) }
    private fun expected(e: CodecGoldenVectors.Expected): Map<String, Any?> = mapOf(
        "outcome" to e.outcome, "cancellationChecks" to e.checks, "decoded" to decoded(e.decoded),
        "tokens" to e.tokens?.map { mapOf("kind" to it.kind, "text" to it.text, "byteCount" to it.byteCount) },
    )
    fun manifest(case: CodecGoldenVectors.Case): Map<String, Any?> = mapOf(
        "schemaVersion" to 1, "identity" to identity(case.identity),
        "vectors" to case.vectors.map { v -> mapOf(
            "name" to v.name, "authority" to v.authority, "bytesHex" to v.hex,
            "offset" to v.offset, "maximumBytes" to v.maximumBytes, "cancelAtCheck" to v.cancelAt,
            "expected" to expected(v.expected),
        ) },
    )

    private fun token(t: PokemonTextToken): CodecGoldenVectors.Token = when (t) {
        is PokemonTextToken.Glyph -> CodecGoldenVectors.Token("GLYPH",t.text,t.byteCount)
        is PokemonTextToken.Whitespace -> CodecGoldenVectors.Token("WHITESPACE",t.text,t.byteCount)
        is PokemonTextToken.Substitution -> CodecGoldenVectors.Token("SUBSTITUTION",t.text,t.byteCount)
        is PokemonTextToken.Control -> CodecGoldenVectors.Token("CONTROL",t.replacement,t.byteCount)
        is PokemonTextToken.Invalid -> CodecGoldenVectors.Token("INVALID","",t.byteCount)
        is PokemonTextToken.Terminator -> CodecGoldenVectors.Token("TERMINATOR","",t.byteCount)
    }

    private fun observe(codec: PokemonTextCodec, vector: CodecGoldenVectors.Vector): CodecGoldenVectors.Expected {
        val bytes = if (vector.hex.isEmpty()) byteArrayOf() else vector.hex.split(' ').map {
            require(Regex("[0-9a-f]{2}").matches(it)); it.toInt(16).toByte()
        }.toByteArray()
        val rom = RomImage(bytes)
        var checks = 0
        val result = try {
            codec.decodeDetailed(rom,vector.offset,vector.maximumBytes,ParserCancellationToken {
                if (++checks == vector.cancelAt) throw ParserCancellationException()
            })
        } catch (_: ParserCancellationException) {
            return CodecGoldenVectors.Expected(null,null,checks,"CANCELLED")
        } catch (_: IllegalArgumentException) {
            return CodecGoldenVectors.Expected(null,null,checks,"INVALID_BOUNDS")
        }
        val end = minOf(bytes.size.toLong(),vector.offset.toLong()+vector.maximumBytes).toInt()
        val tokens = mutableListOf<CodecGoldenVectors.Token>()
        var cursor = vector.offset
        while (cursor < end) {
            val t = codec.decodeToken(rom,cursor,end)
            tokens += token(t)
            cursor += t.byteCount
            if (t is PokemonTextToken.Terminator) break
        }
        return CodecGoldenVectors.Expected(result,tokens,checks)
    }

    /** Reject relabeling by checking the actual official singleton, not only its ID string. */
    private fun identityMatches(case: CodecGoldenVectors.Case): Boolean {
        val registered = CodecGoldenVectors.all.singleOrNull { it.codec === case.codec } ?: return false
        val i = case.identity
        val c = case.codec
        return i == registered.identity && c.id == i.id && c.version == i.version &&
            c.language.value == i.language && c.applicableGenerations == setOf(i.generation) &&
            c.applicablePlatforms.map { it.name }.sorted() == i.platforms.sorted() && c.terminator == i.terminator
    }

    fun execute(case: CodecGoldenVectors.Case): Map<String, Any?> {
        val valid = identityMatches(case) && case.vectors.isNotEmpty() &&
            case.vectors.map { it.name }.toSet().size == case.vectors.size
        val observations = if (valid) case.vectors.map { v ->
            val actual = observe(case.codec,v)
            mapOf("name" to v.name, "matched" to (actual == v.expected), "observed" to expected(actual))
        } else emptyList()
        val matched = observations.count { it["matched"] == true }
        val pass = valid && matched == case.vectors.size
        return mapOf(
            "status" to if (pass) "PASS" else "FAIL", "tests" to case.vectors.size,
            "failures" to if (valid) case.vectors.size-matched else 1, "errors" to 0, "skipped" to 0,
            // No data summary at all on failure: neither matrix preflight nor validator can accept it.
            "data" to if (pass) mapOf("vectorSetSha256" to digest(manifest(case)),
                "vectorCount" to case.vectors.size, "matchedCount" to matched) else null,
            "identityMatched" to identityMatches(case), "observations" to observations,
        )
    }

    private fun resource(name: String): ByteArray = requireNotNull(javaClass.getResourceAsStream("/codec-golden-sources/$name")) {
        "Missing embedded codec evidence source"
    }.use { it.readBytes() }

    fun sourceBinding(): Map<String, Any?> {
        val sources = resource("index.txt").toString(Charsets.UTF_8).lines().associateWith { name ->
            require(!name.startsWith('/') && ':' !in name && ".." !in name)
            sha256(resource(name))
        }.toSortedMap()
        val head = resource("git-head.txt").toString(Charsets.UTF_8)
        require(Regex("[0-9a-f]{40}").matches(head))
        val classes = sortedMapOf<String,String>()
        // Hash actual runtime containers, including generated Kotlin lambdas/nested classes.
        // Paths are discovered only from these two loaded classes' CodeSource, never caller input.
        for ((role, anchor) in listOf("main" to PokemonTextCodec::class.java, "test" to javaClass)) {
            val path = Path.of(anchor.protectionDomain.codeSource.location.toURI())
            fun relevant(name: String) = name.endsWith(".class") && (
                name.startsWith("com/enrpau/dualscreendex/parser/text/") ||
                name.startsWith("com/enrpau/dualscreendex/parser/io/RomImage") ||
                name.startsWith("com/enrpau/dualscreendex/parser/analysis/ParserCancellation") ||
                name.startsWith("com/enrpau/dualscreendex/parser/language/LanguageTag") ||
                name == "com/enrpau/dualscreendex/parser/model/Platform.class")
            if (Files.isDirectory(path)) {
                Files.walk(path).use { stream -> stream.filter { Files.isRegularFile(it) }.forEach {
                    val name = path.relativize(it).toString().replace('\\','/')
                    if (relevant(name)) classes["$role/$name"] = sha256(Files.readAllBytes(it))
                } }
            } else JarFile(path.toFile()).use { jar ->
                jar.entries().asSequence().filter { relevant(it.name) }.forEach {
                    classes["$role/${it.name}"] = sha256(jar.getInputStream(it).use { stream -> stream.readBytes() })
                }
            }
        }
        require(sources.isNotEmpty() && classes.keys.any { it.startsWith("main/") } && classes.keys.any { it.startsWith("test/") })
        return mapOf("gitHead" to head, "sourceState" to "WORKING_TREE_SNAPSHOT", "sources" to sources,
            "sourceSetSha256" to digest(sources), "classes" to classes, "classSetSha256" to digest(classes))
    }

    fun capture(): Map<String, Any?> {
        val rows = CodecGoldenVectors.all.sortedBy { it.identity.id }.map { case ->
            // Construct independent manifest BEFORE execution. It cannot learn observed text/counts.
            val oracle = manifest(case)
            mapOf("identity" to identity(case.identity), "oracleManifest" to oracle, "check" to execute(case))
        }
        val passed = rows.all { (it["check"] as Map<*, *>)["status"] == "PASS" }
        return mapOf("schemaVersion" to 1, "status" to if (passed) "EXECUTED" else "BLOCKED",
            "acceptance" to false, "scope" to "CODEC_GOLDEN_EXECUTION_ONLY",
            "deferred" to listOf("FINAL_STABLE_SOURCE_CAPTURE","INDEPENDENT_ORACLE_REVIEW","OFFICIAL_MATRIX_ACCEPTANCE"),
            "sourceBinding" to sourceBinding(), "codecs" to rows)
    }
}
