package vesper.core.loader

import vesper.core.memory.Address

data class ElfImage(
    val entryPoint: Address,
    val segments: List<Segment>,
    val moduleName: String = "",
    val sdkVersion: Int = 0,
    val imports: List<ImportEntry> = emptyList(),
    val importModules: List<ImportModule> = emptyList(),
    val relocGroups: List<SegmentRelocs> = emptyList(),
    val moduleAttr: Int = 0,
    val numSegments: Int = 0,
    val bssSize: UInt = 0u,
    val isPrx: Boolean = false,
    val pspHeader: PspHeader? = null,
    val globalPointer: UInt = 0u,
) {
    val totalBssSize: UInt get() {
        val implicit = segments.sumOf { it.size.toLong() - it.data.size.toLong() }.toUInt()
        return implicit + bssSize
    }
}

data class ModuleInfo(
    val name: String,
    val gp: Int,
    val entryFunction: Int,
    val exitFunction: Int,
) {
    companion object {
        const val SIZE = 48

        fun read(bytes: ByteArray, offset: Int): ModuleInfo {
            val name = readCString(bytes, offset + 4, 28)
            val gp = bytes.read32(offset + 32)
            val entry = bytes.read32(offset + 36)
            val exit = bytes.read32(offset + 40)
            return ModuleInfo(
                name = name,
                gp = gp,
                entryFunction = entry,
                exitFunction = exit,
            )
        }

        private fun ByteArray.read16(offset: Int): Int {
            return (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)
        }

        private fun ByteArray.read32(offset: Int): Int {
            return (this[offset].toInt() and 0xFF) or
                    ((this[offset + 1].toInt() and 0xFF) shl 8) or
                    ((this[offset + 2].toInt() and 0xFF) shl 16) or
                    ((this[offset + 3].toInt() and 0xFF) shl 24)
        }

        private fun readCString(bytes: ByteArray, offset: Int, maxLength: Int): String {
            val sb = StringBuilder()
            var i = offset
            while (i < minOf(bytes.size, offset + maxLength) && bytes[i].toInt() != 0) {
                sb.append(bytes[i].toInt().toChar())
                i++
            }
            return sb.toString()
        }
    }
}
