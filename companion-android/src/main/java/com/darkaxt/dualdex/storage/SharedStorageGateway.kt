package com.darkaxt.dualdex.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File

class SharedStorageGateway(
    private val accessCheck: () -> Boolean,
    private val rootProvider: () -> List<File>,
) {
    fun isGranted(): Boolean = accessCheck()

    fun roots(): List<File> {
        if (!isGranted()) return emptyList()
        return rootProvider().mapNotNull { root ->
            runCatching { root.canonicalFile }.getOrNull()?.takeIf(File::isDirectory)
        }.distinctBy { it.path }.sortedBy { it.path.lowercase() }
    }

    companion object {
        fun android(context: Context): SharedStorageGateway = SharedStorageGateway(
            accessCheck = {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()
            },
            rootProvider = {
                buildList {
                    add(Environment.getExternalStorageDirectory())
                    context.getExternalFilesDirs(null).mapNotNullTo(this, ::mountedRootOf)
                }
            },
        )

        fun mountedRootOf(appSpecificDirectory: File?): File? {
            val androidDirectory = generateSequence(appSpecificDirectory) { it.parentFile }
                .firstOrNull { it.name.equals("Android", ignoreCase = true) }
            return androidDirectory?.parentFile
        }
    }
}
