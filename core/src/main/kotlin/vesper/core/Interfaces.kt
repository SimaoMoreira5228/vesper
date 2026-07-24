package vesper.core

import vesper.core.memory.Address

interface ICpu {
    fun step()
    fun reset()
    val pc: Address
}

interface IMemoryBus {
    fun read8(address: Address): Int
    fun read16(address: Address): Int
    fun read32(address: Address): Int
    fun write8(address: Address, value: Int)
    fun write16(address: Address, value: Int)
    fun write32(address: Address, value: Int)
    fun readBytes(address: Address, size: Int): ByteArray
    fun writeBytes(address: Address, data: ByteArray)
    fun contains(address: Address): Boolean
}

interface IKernel {
    fun handleSyscall(id: Int, cpu: ICpu): Int
}
