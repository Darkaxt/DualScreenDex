package com.darkaxt.dualdex.knowledge

import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.google.gson.Gson
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

interface KnowledgeRepository {
    fun read(romIdentity: String, saveIdentity: String): KnowledgeLedger?
    fun write(romIdentity: String, saveIdentity: String, ledger: KnowledgeLedger)
}

class FileKnowledgeRepository(
    private val root: File,
    gson: Gson = Gson(),
) : KnowledgeRepository {
    private val codec = KnowledgeLedgerJsonCodec(gson)

    override fun read(romIdentity: String, saveIdentity: String): KnowledgeLedger? {
        val identity = normalize(romIdentity, "ROM")
        val save = normalize(saveIdentity, "save")
        val document = root.resolve("$identity.$save.json")
        if (!document.isFile) return null
        val stored = runCatching { codec.decodeDocument(document.readBytes()) }.getOrNull()
            ?: return null
        if (
            !stored.romIdentity.equals(identity, ignoreCase = true) ||
            !stored.saveIdentity.equals(save, ignoreCase = true)
        ) return null
        return stored.ledger
    }

    override fun write(romIdentity: String, saveIdentity: String, ledger: KnowledgeLedger) {
        val identity = normalize(romIdentity, "ROM")
        val save = normalize(saveIdentity, "save")
        check(root.isDirectory || root.mkdirs()) { "knowledge directory could not be created" }
        val destination = root.resolve("$identity.$save.json")
        val temporary = root.resolve("$identity.$save.tmp")
        temporary.writeBytes(codec.encodeDocument(identity, save, ledger))
        runCatching {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun normalize(identity: String, kind: String): String {
        val normalized = identity.lowercase()
        require(normalized.matches(Regex("[0-9a-f]{64}"))) { "$kind identity must be a SHA-256 hash" }
        return normalized
    }

}
