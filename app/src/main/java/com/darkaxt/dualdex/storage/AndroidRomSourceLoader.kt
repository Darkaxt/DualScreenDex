package com.darkaxt.dualdex.storage

import android.content.ContentResolver
import android.net.Uri
import com.enrpau.dualscreendex.parser.io.LoadedRom
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import java.io.FileInputStream

internal object AndroidRomSourceLoader {
    fun load(resolver: ContentResolver, uri: Uri, name: String): LoadedRom {
        if (name.substringAfterLast('.', "").equals("7z", ignoreCase = true)) {
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { file ->
                    val channel = file.channel
                    val seekable = runCatching {
                        val position = channel.position()
                        channel.position(position)
                        channel.size()
                    }.isSuccess
                    if (seekable) return RomSourceLoader.load(name, channel)
                }
            }
        }
        return resolver.openInputStream(uri)?.use { input -> RomSourceLoader.load(name, input) }
            ?: error("document provider did not open $name")
    }
}
