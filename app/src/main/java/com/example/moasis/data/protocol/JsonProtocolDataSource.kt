package com.example.moasis.data.protocol

import com.example.moasis.domain.model.Protocol
import com.example.moasis.domain.model.Tree
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class JsonProtocolDataSource(
    private val assetTextSource: AssetTextSource,
    private val json: Json = defaultJson,
) {
    fun loadTrees(): List<Tree> {
        return loadJsonFiles()
            .filter { it.parsed.containsKey("tree_id") }
            .map { json.decodeFromJsonElement(Tree.serializer(), it.parsed) }
    }

    fun loadProtocols(): List<Protocol> {
        return loadJsonFiles()
            .filter { it.parsed.containsKey("protocol_id") }
            .map { json.decodeFromJsonElement(Protocol.serializer(), it.parsed) }
    }

    private fun loadJsonFiles(): List<LoadedJsonFile> {
        return assetTextSource.list(PROTOCOLS_DIRECTORY)
            .filter { it.endsWith(".json") }
            .map { path ->
                val content = assetTextSource.read(path)
                val parsed = json.parseToJsonElement(content).jsonObject
                LoadedJsonFile(path = path, parsed = parsed)
            }
    }

    private data class LoadedJsonFile(
        val path: String,
        val parsed: JsonObject,
    )

    companion object {
        const val PROTOCOLS_DIRECTORY = "protocols"

        val defaultJson = Json {
            ignoreUnknownKeys = true
        }
    }
}
