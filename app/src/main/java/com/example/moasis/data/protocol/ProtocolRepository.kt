package com.example.moasis.data.protocol

import com.example.moasis.domain.model.Protocol
import com.example.moasis.domain.model.Tree

class ProtocolRepository(
    private val dataSource: JsonProtocolDataSource,
) {
    fun getTree(treeId: String): Tree? {
        return dataSource.loadTrees().firstOrNull { it.treeId == treeId }
    }

    fun getProtocol(protocolId: String): Protocol? {
        return dataSource.loadProtocols().firstOrNull { it.protocolId == protocolId }
    }
}
