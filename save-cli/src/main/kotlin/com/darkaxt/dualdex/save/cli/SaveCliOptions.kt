package com.darkaxt.dualdex.save.cli

import java.nio.file.Path

data class SaveInputPair(val rom: Path, val save: Path)

data class SaveCliOptions(
    val pairs: List<SaveInputPair>,
    val probes: List<Path>,
    val json: Path,
    val markdown: Path,
) {
    companion object {
        fun parse(arguments: Array<String>): SaveCliOptions {
            val pairs = mutableListOf<SaveInputPair>()
            val probes = mutableListOf<Path>()
            var json: Path? = null
            var markdown: Path? = null
            var index = 0
            while (index < arguments.size) {
                when (val argument = arguments[index]) {
                    "--pair" -> {
                        require(index + 2 < arguments.size) { "--pair requires a ROM and SaveRAM path" }
                        val rom = arguments[++index]
                        val save = arguments[++index]
                        require(!rom.startsWith("--") && !save.startsWith("--")) {
                            "--pair requires a ROM and SaveRAM path"
                        }
                        pairs += SaveInputPair(Path.of(rom), Path.of(save))
                    }
                    "--probe" -> probes.add(valueAfter(arguments, ++index, argument))
                    "--json" -> json = valueAfter(arguments, ++index, argument)
                    "--markdown" -> markdown = valueAfter(arguments, ++index, argument)
                    else -> throw IllegalArgumentException("unknown option: $argument")
                }
                index++
            }
            require(pairs.isNotEmpty() || probes.isNotEmpty()) { "at least one --pair or --probe is required" }
            return SaveCliOptions(
                pairs = pairs,
                probes = probes,
                json = requireNotNull(json) { "--json is required" },
                markdown = requireNotNull(markdown) { "--markdown is required" },
            )
        }

        private fun valueAfter(arguments: Array<String>, index: Int, option: String): Path {
            require(index < arguments.size && !arguments[index].startsWith("--")) { "$option requires a path" }
            return Path.of(arguments[index])
        }
    }
}
