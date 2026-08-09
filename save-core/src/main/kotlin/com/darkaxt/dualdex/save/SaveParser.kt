package com.darkaxt.dualdex.save

import com.darkaxt.dualdex.save.gen3.Gen3SaveReader

object SaveParser {
    fun parse(bytes: ByteArray, context: SaveParseContext): SaveParseResult =
        Gen3SaveReader.read(bytes, context)
}
