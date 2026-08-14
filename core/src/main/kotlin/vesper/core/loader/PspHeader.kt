package vesper.core.loader

object PspHeaderConstants {
    val PSP_MAGIC = byteArrayOf('~'.code.toByte(), 'P'.code.toByte(), 'S'.code.toByte(), 'P'.code.toByte())
    val ELF_MAGIC = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())

    const val PSP_HEADER_SIZE = 0x150 // 336 bytes

    const val COMPRESSION_PLAIN = 0x300
    const val COMPRESSION_GZIP = 0x000
    const val COMPRESSION_KL4E = 0x200
    const val COMPRESSION_2RLZ = 0x100
}

data class PspHeader(
    val magic: ByteArray,
    val moduleAttr: Int,
    val compressionType: Int,
    val minorVersion: Int,
    val majorVersion: Int,
    val moduleName: String,
    val moduleVersion: Int,
    val numSegments: Int,
    val elfOffset: UInt,
    val dataSize: UInt,
    val totalSize: UInt,
    val bootEntry: UInt,
    val moduleInfoOffset: Int,
    val bssSize: UInt,
    val segmentAddresses: List<UInt>,
    val segmentSizes: List<UInt>,
    val sdkVersion: Int,
    val encryptType: Int,
) {
    val isUserModule: Boolean get() = moduleInfoOffset >= 0
    val isKernelModule: Boolean get() = moduleInfoOffset < 0
    val entryPoint: UInt
        get() = if (segmentAddresses.isNotEmpty()) segmentAddresses[0] + bootEntry else bootEntry

    companion object {
        fun parse(bytes: ByteArray): PspHeader? {
            if (bytes.size < PspHeaderConstants.PSP_HEADER_SIZE) return null
            val magic = bytes.sliceArray(0 until 4)
            if (!magic.contentEquals(PspHeaderConstants.PSP_MAGIC)) return null

            val moduleAttr = bytes.read16(4)
            val compression = bytes.read16(6)
            val minorVer = bytes[8].toInt() and 0xFF
            val majorVer = bytes[9].toInt() and 0xFF
            val nameBytes = bytes.sliceArray(0x0A until (0x0A + 28))
            val nameEnd = nameBytes.indexOf(0)
            val moduleName = if (nameEnd >= 0) String(nameBytes, 0, nameEnd, Charsets.US_ASCII) else String(nameBytes, Charsets.US_ASCII)
            val moduleVer = bytes[0x26].toInt() and 0xFF
            val numSeg = bytes[0x27].toInt() and 0xFF

            val elfFileOffset = if (compression == PspHeaderConstants.COMPRESSION_PLAIN) PspHeaderConstants.PSP_HEADER_SIZE.toUInt() else 0u

            val dataSize = bytes.readU32(0x28)
            val totalSize = bytes.readU32(0x2C)
            val bootEntry = bytes.readU32(0x30)
            val moduleInfoOff = bytes.readS32(0x34)
            val bssSize = bytes.readU32(0x38)

            val segAddrs = mutableListOf<UInt>()
            val segSizes = mutableListOf<UInt>()
            for (i in 0 until 4) {
                val addr = bytes.readU32(0x44 + i * 4)
                val size = bytes.readU32(0x54 + i * 4)
                segAddrs.add(addr)
                segSizes.add(size)
            }

            val sdkVer = bytes.readS32(0x78)
            val encryptType = bytes.read16(0x7C)

            return PspHeader(
                magic = magic,
                moduleAttr = moduleAttr,
                compressionType = compression,
                minorVersion = minorVer,
                majorVersion = majorVer,
                moduleName = moduleName,
                moduleVersion = moduleVer,
                numSegments = numSeg,
                elfOffset = elfFileOffset,
                dataSize = dataSize,
                totalSize = totalSize,
                bootEntry = bootEntry,
                moduleInfoOffset = moduleInfoOff,
                bssSize = bssSize,
                segmentAddresses = segAddrs,
                segmentSizes = segSizes,
                sdkVersion = sdkVer,
                encryptType = encryptType,
            )
        }

        private fun ByteArray.read16(offset: Int): Int {
            return (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)
        }

        private fun ByteArray.readU32(offset: Int): UInt {
            return ((this[offset].toInt() and 0xFF).toUInt() or
                    ((this[offset + 1].toInt() and 0xFF).toUInt() shl 8) or
                    ((this[offset + 2].toInt() and 0xFF).toUInt() shl 16) or
                    ((this[offset + 3].toInt() and 0xFF).toUInt() shl 24))
        }

        private fun ByteArray.readS32(offset: Int): Int {
            return (this[offset].toInt() and 0xFF) or
                    ((this[offset + 1].toInt() and 0xFF) shl 8) or
                    ((this[offset + 2].toInt() and 0xFF) shl 16) or
                    ((this[offset + 3].toInt() and 0xFF) shl 24)
        }
    }
}
