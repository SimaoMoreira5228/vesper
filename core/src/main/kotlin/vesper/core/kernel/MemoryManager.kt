package vesper.core.kernel

import vesper.common.Loggable
import vesper.common.warn

data class MemoryPartition(
    val id: Int,
    val name: String,
    val type: Int,
    val baseAddr: Int,
    val size: Int,
    var allocated: Boolean = false,
)

class MemoryManager : Loggable {

    override val tag: String get() = "MemoryManager"

    private var nextPartitionId: Int = 1
    private val partitions = mutableListOf<MemoryPartition>()
    private val allocs = mutableMapOf<Int, MemoryPartition>()

    private val heapBase = 0x08800000
    private val heapSize = 0x00800000

    init {
        partitions.add(
            MemoryPartition(
                id = 0,
                name = "User",
                type = 1,
                baseAddr = heapBase,
                size = heapSize,
                allocated = false,
            )
        )
    }

    fun allocPartitionMemory(
        name: String,
        type: Int,
        size: Int,
        addr: Int = -1,
    ): Int {
        val alignedSize = (size + 0xFF) and 0xFFFFF00
        for (part in partitions) {
            if (!part.allocated && part.size >= alignedSize) {
                val alloc = MemoryPartition(
                    id = nextPartitionId++,
                    name = name,
                    type = type,
                    baseAddr = if (addr >= 0) addr else part.baseAddr,
                    size = alignedSize,
                    allocated = true,
                )
                allocs[alloc.id] = alloc
                part.allocated = true
                return alloc.id
            }
        }
        warn { "Failed to allocate ${alignedSize} bytes for $name" }
        return -1
    }

    fun freePartitionMemory(blockId: Int): Int {
        val alloc = allocs.remove(blockId) ?: return -1
        alloc.allocated = false
        return 0
    }

    fun getBlockAddress(blockId: Int): Int {
        return allocs[blockId]?.baseAddr ?: -1
    }

    fun getBlockSize(blockId: Int): Int {
        return allocs[blockId]?.size ?: 0
    }

    fun maxFreeMemSize(): Int {
        return partitions.filter { !it.allocated }.maxOfOrNull { it.size } ?: 0
    }

    fun totalFreeMemSize(): Int {
        return partitions.filter { !it.allocated }.sumOf { it.size }
    }
}
