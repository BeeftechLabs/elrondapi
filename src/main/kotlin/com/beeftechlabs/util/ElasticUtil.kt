package com.beeftechlabs.util

import co.elastic.clients.elasticsearch.core.SearchRequest
import co.elastic.clients.transport.ElasticsearchTransport
import java.io.ByteArrayOutputStream

fun SearchRequest.serializeBody(transport: ElasticsearchTransport): String {
    val baos = ByteArrayOutputStream()
    val mapper = transport.jsonpMapper()
    val gen = mapper.jsonProvider().createGenerator(baos)
    mapper.serialize(this, gen)
    gen.close()
    return baos.toString()
}