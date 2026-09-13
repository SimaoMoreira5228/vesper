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

data class FixedPool(
    val id: Int,
    val blockSize: Int,
    val blockCount: Int,
    val base: Int,
    val used: BooleanArray = BooleanArray(blockCount),
)

class MemoryManager : Loggable {

    override val tag: String get() = "MemoryManager"

    private var nextPartitionId: Int = 1
    private val partitions = mutableListOf<MemoryPartition>()
    private val allocs = mutableMapOf<Int, MemoryPartition>()

    private val heapBase = 0x08900000
    private val heapSize = 0x01600000

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
        val used = allocs.values.sumOf { it.size }
        val remaining = heapSize - used
        if (remaining >= alignedSize) {
            val alloc = MemoryPartition(
                id = nextPartitionId++,
                name = name,
                type = type,
                baseAddr = if (addr >= 0) addr else (heapBase + used),
                size = alignedSize,
                allocated = true,
            )
            allocs[alloc.id] = alloc
            return alloc.id
        }
        warn { "Failed to allocate ${alignedSize} bytes for $name (remaining=$remaining)" }
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
        val used = allocs.values.sumOf { it.size }
        return (heapSize - used).coerceAtLeast(0)
    }

    fun totalFreeMemSize(): Int {
        val used = allocs.values.sumOf { it.size }
        return (heapSize - used).coerceAtLeast(0)
    }

    private var nextFixedPoolId: Int = 1
    private val fixedPools = mutableMapOf<Int, FixedPool>()

    fun createFixedPool(name: String, blockSize: Int, blockCount: Int): Int {
        if (blockSize <= 0 || blockCount <= 0) return -1
        val partition = allocPartitionMemory(name, 2, blockSize * blockCount)
        if (partition < 0) return -1
        val id = nextFixedPoolId++
        fixedPools[id] = FixedPool(id, blockSize, blockCount, getBlockAddress(partition))
        return id
    }

    fun allocateFixedPoolBlock(fplId: Int): Int {
        val pool = fixedPools[fplId] ?: return -1
        val index = pool.used.indexOfFirst { !it }
        if (index < 0) return -1
        pool.used[index] = true
        return pool.base + index * pool.blockSize
    }

    fun freeFixedPoolBlock(fplId: Int, address: Int): Int {
        val pool = fixedPools[fplId] ?: return -1
        val index = (address - pool.base) / pool.blockSize
        if (index < 0 || index >= pool.blockCount || !pool.used[index]) return -1
        pool.used[index] = false
        return 0
    }

    fun deleteFixedPool(fplId: Int): Int {
        fixedPools.remove(fplId) ?: return -1
        return 0
    }
}
