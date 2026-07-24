package vesper.core.loader

import vesper.core.memory.Address

data class ElfImage(
    val entryPoint: Address,
    val segments: List<Segment>,
    val moduleName: String = "",
    val sdkVersion: Int = 0,
)

data class ModuleInfo(
    val name: String,
    val gp: Int,
    val entryFunction: Int,
    val exitFunction: Int,
) {
    companion object {
        const val SIZE = 48

        fun read(bytes: ByteArray, offset: Int): ModuleInfo {
            val nameOffset = bytes.read16(offset).toInt()
            val gp = bytes.read32(offset + 4)
            val entry = bytes.read32(offset + 8)
            val exit = bytes.read32(offset + 12)
            val name = readCString(bytes, offset + 16 + nameOffset)
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

        private fun readCString(bytes: ByteArray, offset: Int): String {
            val sb = StringBuilder()
            var i = offset
            while (i < bytes.size && bytes[i].toInt() != 0) {
                sb.append(bytes[i].toInt().toChar())
                i++
            }
            return sb.toString()
        }
    }
}
