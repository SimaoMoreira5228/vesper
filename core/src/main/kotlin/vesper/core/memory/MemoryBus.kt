@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package vesper.core.memory

import vesper.common.Logger
import vesper.core.IMemoryBus

class IoRegisterFile {
    private val registers = UIntArray(0x4000)

    fun read32(offset: UInt): Int = registers.getOrElse(offset.toInt() shr 2) { 0u }.toInt()
    fun write32(offset: UInt, value: Int) {
        val idx = offset.toInt() shr 2
        if (idx in registers.indices) registers[idx] = value.toUInt()
    }

    fun read8(offset: UInt): Int {
        val word = read32(offset and 0xFFFFFFFCu)
        return (word shr ((offset.toInt() and 3) * 8)) and 0xFF
    }

    fun write8(offset: UInt, value: Int) {
        val idx = offset.toInt() shr 2
        if (idx !in registers.indices) return
        val shift = (offset.toInt() and 3) * 8
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

    override fun contains(address: Address): Boolean = MemoryRegion.resolve(address) != null

    override fun read8(address: Address): Int {
        val region = MemoryRegion.resolve(address) ?: return ioRegisters.read8(address.value - MemoryRegion.IO.base.value)
        return when (region) {
            MemoryRegion.SCRATCHPAD, MemoryRegion.SCRATCHPAD_K1 -> scratchpad[region.offset(address).toInt()].toInt() and 0xFF
            MemoryRegion.VRAM, MemoryRegion.VRAM_K0 -> vram[region.offset(address).toInt()].toInt() and 0xFF
            MemoryRegion.RAM_LOW, MemoryRegion.UNCACHED_RAM_LOW, MemoryRegion.RAM_HIGH, MemoryRegion.UNCACHED_RAM_HIGH -> ram[region.offset(address).toInt()].toInt() and 0xFF
            MemoryRegion.IO, MemoryRegion.IO_ALT -> ioRegisters.read8(region.offset(address))
        }
    }

    override fun read16(address: Address): Int {
        val v = read8(address)
        val v2 = read8(address + 1)
        return (v and 0xFF) or ((v2 and 0xFF) shl 8)
    }

    override fun read32(address: Address): Int {
        val b0 = read8(address).toUInt() and 0xFFu
        val b1 = read8(address + 1).toUInt() and 0xFFu
        val b2 = read8(address + 2).toUInt() and 0xFFu
        val b3 = read8(address + 3).toUInt() and 0xFFu
        return (b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)).toInt()
    }

    override fun write8(address: Address, value: Int) {
        val region = MemoryRegion.resolve(address) ?: return
        val offset = region.offset(address).toInt()
        when (region) {
            MemoryRegion.SCRATCHPAD, MemoryRegion.SCRATCHPAD_K1 -> if (offset in scratchpad.indices) scratchpad[offset] = value.toByte()
            MemoryRegion.VRAM, MemoryRegion.VRAM_K0 -> if (offset in vram.indices) vram[offset] = value.toByte()
            MemoryRegion.RAM_LOW, MemoryRegion.UNCACHED_RAM_LOW, MemoryRegion.RAM_HIGH, MemoryRegion.UNCACHED_RAM_HIGH -> if (offset in ram.indices) ram[offset] = value.toByte()
            MemoryRegion.IO, MemoryRegion.IO_ALT -> ioRegisters.write8(region.offset(address), value)
        }
    }

    override fun write16(address: Address, value: Int) {
        write8(address, value and 0xFF)
        write8(address + 1, (value shr 8) and 0xFF)
    }

    override fun write32(address: Address, value: Int) {
        write8(address, value and 0xFF)
        write8(address + 1, (value shr 8) and 0xFF)
        write8(address + 2, (value shr 16) and 0xFF)
        write8(address + 3, (value shr 24) and 0xFF)
    }

    override fun readBytes(address: Address, size: Int): ByteArray {
        val result = ByteArray(size)
        for (i in 0 until size) result[i] = read8(address + i).toByte()
        return result
    }

    override fun writeBytes(address: Address, data: ByteArray) {
        for (i in data.indices) write8(address + i, data[i].toInt() and 0xFF)
    }

    fun loadSegment(baseAddress: Address, data: ByteArray) {
        writeBytes(baseAddress, data)
    }

    companion object {
        private val logTag = "MemoryBus"
    }
}
