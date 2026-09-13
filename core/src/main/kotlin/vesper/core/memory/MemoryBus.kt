@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package vesper.core.memory

import vesper.core.IMemoryBus

enum class MemRegion { RAM, VRAM, SCRATCHPAD, IO, UNMAPPED }

class IoRegisterFile {
    private val registers = UIntArray(0x4000)

    fun read32(offset: UInt): Int = registers.getOrElse(offset.toInt() shr 2) { 0u }.toInt()

    fun write32(
        offset: UInt,
        value: Int,
    ) {
        val idx = offset.toInt() shr 2
        if (idx in registers.indices) registers[idx] = value.toUInt()
    }

    fun read8(offset: Int): Int {
        val word = read32(offset.toUInt() and 0xFFFFFFFCu)
        return (word shr ((offset and 3) * 8)) and 0xFF
    }

    fun write8(
        offset: Int,
        value: Int,
    ) {
        val idx = offset shr 2
        if (idx !in registers.indices) return
        val shift = (offset and 3) * 8
        val mask = (0xFFu shl shift).inv()
        registers[idx] = (registers[idx] and mask) or ((value.toUInt() and 0xFFu) shl shift)
    }
}

class MemoryBus(
    private val ram: ByteArray = ByteArray(0x2000000),
    private val vram: ByteArray = ByteArray(0x400000),
    private val scratchpad: ByteArray = ByteArray(0x4000),
    private val ioRegisters: IoRegisterFile = IoRegisterFile(),
) : IMemoryBus {
    override fun contains(address: Address): Boolean = regionOf(address) != MemRegion.UNMAPPED

    override fun read8(address: Address): Int {
        return when (val r = regionOf(address)) {
            MemRegion.RAM -> {
                val off = address.value.toInt() and 0x01FFFFFF
                if (off < ram.size) ram[off].toInt() and 0xFF else 0
            }
            MemRegion.VRAM -> {
                val off = address.value.toInt() and 0x003FFFFF
                if (off < vram.size) vram[off].toInt() and 0xFF else 0
            }
            MemRegion.SCRATCHPAD -> {
                val off = address.value.toInt() and 0x00003FFF
                if (off < scratchpad.size) scratchpad[off].toInt() and 0xFF else 0
            }
            MemRegion.IO -> ioRegisters.read8(address.value.toInt() and 0x0003FFFF)
            MemRegion.UNMAPPED -> 0
        }
    }

    override fun read16(address: Address): Int {
        val lo = read8(address)
        val hi = read8(address + 1)
        return (lo and 0xFF) or ((hi and 0xFF) shl 8)
    }

    override fun read32(address: Address): Int {
        val b0 = read8(address).toUInt() and 0xFFu
        val b1 = read8(address + 1).toUInt() and 0xFFu
        val b2 = read8(address + 2).toUInt() and 0xFFu
        val b3 = read8(address + 3).toUInt() and 0xFFu
        return (b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)).toInt()
    }

    override fun write8(
        address: Address,
        value: Int,
    ) {
        when (val r = regionOf(address)) {
            MemRegion.RAM -> {
                val off = address.value.toInt() and 0x01FFFFFF
                if (off < ram.size) ram[off] = value.toByte()
            }
            MemRegion.VRAM -> {
                val off = address.value.toInt() and 0x003FFFFF
                if (off < vram.size) vram[off] = value.toByte()
            }
            MemRegion.SCRATCHPAD -> {
                val off = address.value.toInt() and 0x00003FFF
                if (off < scratchpad.size) scratchpad[off] = value.toByte()
            }
            MemRegion.IO -> ioRegisters.write8(address.value.toInt() and 0x0003FFFF, value)
            MemRegion.UNMAPPED -> {} // silently ignore
        }
    }

    override fun write16(
        address: Address,
        value: Int,
    ) {
        write8(address, value and 0xFF)
        write8(address + 1, (value shr 8) and 0xFF)
    }

    override fun write32(
        address: Address,
        value: Int,
    ) {
        write8(address, value and 0xFF)
        write8(address + 1, (value shr 8) and 0xFF)
        write8(address + 2, (value shr 16) and 0xFF)
        write8(address + 3, (value shr 24) and 0xFF)
    }

    override fun readBytes(
        address: Address,
        size: Int,
    ): ByteArray {
        val result = ByteArray(size)
        for (i in 0 until size) result[i] = read8(address + i).toByte()
        return result
    }

    override fun writeBytes(
        address: Address,
        data: ByteArray,
    ) {
        for (i in data.indices) write8(address + i, data[i].toInt() and 0xFF)
    }

    fun loadSegment(
        baseAddress: Address,
        data: ByteArray,
    ) {
        writeBytes(baseAddress, data)
    }

    companion object {
        private fun regionOf(address: Address): MemRegion {
            val upper = (address.value shr 24).toInt()
            return when (upper) {
                0x00 -> if (address.value >= 0x00010000u && address.value < 0x00014000u) MemRegion.SCRATCHPAD else MemRegion.UNMAPPED
                0x04 -> if (address.value < 0x04400000u) MemRegion.VRAM else MemRegion.UNMAPPED
                0x08, 0x09 -> MemRegion.RAM
                0x40, 0x41 -> MemRegion.RAM
                0x84 -> MemRegion.VRAM
                0x88, 0x89 -> MemRegion.RAM
                0x9C, 0x9D, 0x9E, 0x9F -> MemRegion.IO
                0xA0 -> if (address.value >= 0xA0010000u && address.value < 0xA0014000u) MemRegion.SCRATCHPAD else MemRegion.UNMAPPED
                0xBC, 0xBD, 0xBE, 0xBF -> MemRegion.IO
                0xC0, 0xC1 -> MemRegion.RAM
                else -> MemRegion.UNMAPPED
            }
        }
    }
}
