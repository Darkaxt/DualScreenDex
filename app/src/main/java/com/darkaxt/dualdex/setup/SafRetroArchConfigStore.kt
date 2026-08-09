package com.darkaxt.dualdex.setup

import android.content.ContentResolver
import android.net.Uri
import com.darkaxt.dualdex.retroarch.ConfigDocumentStore
import com.darkaxt.dualdex.storage.DocumentTreeAccess
import com.darkaxt.dualdex.storage.LocatedTreeDocument
import com.darkaxt.dualdex.storage.TreeDocument

class SafRetroArchConfigStore(
    resolver: ContentResolver,
    treeUri: Uri,
) : ConfigDocumentStore {
    private val access = DocumentTreeAccess(resolver, treeUri)
    private val config: LocatedTreeDocument by lazy {
        val matches = access.children(access.root)
            .filter { it.name == CONFIG_NAME }
            .map { LocatedTreeDocument(access.root, it) }
        require(matches.size == 1) {
            when {
                matches.isEmpty() -> "retroarch.cfg was not found at the root of the selected folder; select the folder that directly contains the active file"
                else -> "multiple retroarch.cfg files were found at the selected root"
            }
        }
        matches.single()
    }

    override fun readConfig(): ByteArray = access.read(config.document)

    override fun writeConfig(bytes: ByteArray) = access.write(config.document, bytes)

    override fun readRecovery(): ByteArray? = recovery()?.let(access::read)

    override fun writeRecovery(bytes: ByteArray) {
        val document = recovery() ?: access.create(config.parent, RECOVERY_NAME)
        access.write(document, bytes)
    }

    override fun deleteRecovery() {
        recovery()?.let(access::delete)
    }

    private fun recovery(): TreeDocument? = access.children(config.parent).singleOrNull { it.name == RECOVERY_NAME }

    companion object {
        const val CONFIG_NAME = "retroarch.cfg"
        const val RECOVERY_NAME = "retroarch.cfg.dualdex-recovery"
    }
}
