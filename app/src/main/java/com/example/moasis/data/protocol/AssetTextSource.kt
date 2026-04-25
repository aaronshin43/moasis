package com.example.moasis.data.protocol

import android.content.res.AssetManager
import java.io.File
import java.nio.charset.StandardCharsets

interface AssetTextSource {
    fun list(relativeDirectory: String): List<String>
    fun read(relativePath: String): String
}

class FileSystemAssetTextSource(
    private val rootDirectory: File,
) : AssetTextSource {
    override fun list(relativeDirectory: String): List<String> {
        val directory = File(rootDirectory, relativeDirectory)
        if (!directory.exists() || !directory.isDirectory) {
            return emptyList()
        }

        return directory.listFiles()
            ?.filter { it.isFile }
            ?.map { relativeDirectory.trimEnd('/', '\\') + "/" + it.name }
            ?.sorted()
            .orEmpty()
    }

    override fun read(relativePath: String): String {
        return File(rootDirectory, relativePath).readText(StandardCharsets.UTF_8)
    }
}

class AndroidAssetTextSource(
    private val assetManager: AssetManager,
) : AssetTextSource {
    override fun list(relativeDirectory: String): List<String> {
        return assetManager.list(relativeDirectory)
            ?.map { entry -> relativeDirectory.trimEnd('/', '\\') + "/" + entry }
            ?.sorted()
            .orEmpty()
    }

    override fun read(relativePath: String): String {
        return assetManager.open(relativePath).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }
}
