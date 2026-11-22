package com.darkaxt.dualdex.storage

import android.content.ContentResolver
import android.net.Uri
import android.os.CancellationSignal
import com.enrpau.dualscreendex.parser.io.LoadedRom
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import java.io.FileInputStream

internal object AndroidRomSourceLoader {
    fun load(resolver: ContentResolver, uri: Uri, name: String): LoadedRom = providerOperation(uri) { cancellation ->
        SafProviderResults.requireValue(
            resolver.openFileDescriptor(uri, "r", cancellation),
            "document provider did not open $name",
        ).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { input ->
                if (name.substringAfterLast('.', "").equals("7z", ignoreCase = true)) {
                    val channel = input.channel
                    val seekable = runCatching {
                        val position = channel.position()
                        channel.position(position)
                        channel.size()
                    }.isSuccess
                    if (seekable) return@providerOperation RomSourceLoader.load(name, channel)
                }
                RomSourceLoader.load(name, input)
            }
        }
    }

    private fun <T> providerOperation(uri: Uri, operation: (CancellationSignal) -> T): T {
        val cancellation = CancellationSignal()
        return SafProviderOperations.shared.forUri(uri).await(
            onTimeout = cancellation::cancel,
        ) {
            operation(cancellation)
        }
    }
}
