package vesper.core.kernel

import vesper.common.Loggable
import vesper.core.IMemoryBus
import vesper.core.memory.Address
import kotlin.math.min

data class KSemaphore(
    val id: Int,
    val name: String,
    val attr: Int,
    val initialCount: Int,
    var count: Int,
    val maxCount: Int,
    val waitingThreads: MutableList<Pair<Int, Int>> = mutableListOf(),
)

data class KMutex(
    val id: Int,
    var lockedBy: Int? = null,
    var lockCount: Int = 0,
    val waitingThreads: MutableList<Int> = mutableListOf(),
)

data class KEventFlag(
    val id: Int,
    var bits: Int = 0,
    val waitingThreads: MutableList<Pair<Int, Int>> = mutableListOf(),
)

class SynchPrimitives(
    private val scheduler: Scheduler,
) : Loggable {
    override val tag: String get() = "SynchPrimitives"
    private var nextSemaId: Int = 1
    private var nextMutexId: Int = 1
    private var nextEventFlagId: Int = 1
    private val semaInfoSize: Int = 56
    private val unknownSema: Int = 0x80020199.toInt()

    private val semaphores = mutableMapOf<Int, KSemaphore>()
    private val mutexes = mutableMapOf<Int, KMutex>()
    private val eventFlags = mutableMapOf<Int, KEventFlag>()

    fun createSemaphore(
        name: String,
        attr: Int,
        initialCount: Int,
        maxCount: Int,
    ): Int {
        val id = nextSemaId++
        semaphores[id] =
            KSemaphore(
                id = id,
                name = name,
                attr = attr,
                initialCount = initialCount.coerceIn(0, maxCount),
                count = initialCount.coerceIn(0, maxCount),
                maxCount = maxCount,
            )
        return id
    }

    fun referSemaphoreStatus(
        semaId: Int,
        infoPtr: Address,
        memory: IMemoryBus,
    ) {
        val sema = semaphores[semaId] ?: return
        memory.write32(infoPtr, semaInfoSize)
        writeName(memory, infoPtr + 4, sema.name)
        memory.write32(infoPtr + 0x24, sema.attr)
        memory.write32(infoPtr + 0x28, sema.initialCount)
        memory.write32(infoPtr + 0x2C, sema.count)
        memory.write32(infoPtr + 0x30, sema.maxCount)
        memory.write32(infoPtr + 0x34, sema.waitingThreads.size)
    }

    private fun writeName(
        memory: IMemoryBus,
        ptr: Address,
        name: String,
    ) {
        val bytes = ByteArray(32)
        name.encodeToByteArray().copyInto(bytes, endIndex = minOf(name.length, 31))
        memory.writeBytes(ptr, bytes)
    }

    fun deleteSemaphore(semaId: Int): Int {
        val sema = semaphores.remove(semaId) ?: return unknownSema
        for ((tid, _) in sema.waitingThreads) {
            scheduler.makeReady(tid)
        }
        scheduler.reschedule()
        return 0
    }

    fun signalSemaphore(
        semaId: Int,
        signal: Int,
    ): Int {
        val sema = semaphores[semaId] ?: return -1
        sema.count = min(sema.count + signal, sema.maxCount)
        val waiters = sema.waitingThreads.iterator()
        while (waiters.hasNext()) {
            val (tid, need) = waiters.next()
            if (sema.count >= need) {
                sema.count -= need
                scheduler.makeReady(tid)
                waiters.remove()
            }
        }
        scheduler.reschedule()
        return 0
    }

    fun waitSemaphore(
        semaId: Int,
        need: Int,
        cb: Boolean,
    ): Int {
        val sema = semaphores[semaId] ?: return -1
        if (sema.count >= need) {
            sema.count -= need
            return 0
        }
        sema.waitingThreads.add(scheduler.currentThreadId to need)
        scheduler.getThread(scheduler.currentThreadId)?.status = ThreadStatus.Waiting("sema")
        scheduler.reschedule()
        return 0
    }

    fun pollSemaphore(semaId: Int): Int {
        val sema = semaphores[semaId] ?: return -1
        return if (sema.count > 0) {
            sema.count--
            0
        } else {
            -1
        }
    }

    fun createMutex(recursive: Boolean = true): Int {
        val id = nextMutexId++
        mutexes[id] = KMutex(id = id)
        return id
    }

    fun lockMutex(mutexId: Int): Int {
        val mutex = mutexes[mutexId] ?: return -1
        if (mutex.lockedBy == null) {
            mutex.lockedBy = scheduler.currentThreadId
            mutex.lockCount = 1
            return 0
        }
        if (mutex.lockedBy == scheduler.currentThreadId) {
            mutex.lockCount++
            return 0
        }
        mutex.waitingThreads.add(scheduler.currentThreadId)
        val thread = scheduler.getThread(scheduler.currentThreadId)
        if (thread != null) thread.status = ThreadStatus.Waiting("mutex")
        scheduler.reschedule()
        return 0
    }

    fun unlockMutex(mutexId: Int): Int {
        val mutex = mutexes[mutexId] ?: return -1
        if (mutex.lockedBy != scheduler.currentThreadId) return -1
        mutex.lockCount--
        if (mutex.lockCount == 0) {
            mutex.lockedBy = null
            if (mutex.waitingThreads.isNotEmpty()) {
                val next = mutex.waitingThreads.removeFirst()
                mutex.lockedBy = next
                mutex.lockCount = 1
                scheduler.makeReady(next)
                scheduler.reschedule()
            }
        }
        return 0
    }

    fun createEventFlag(initBits: Int = 0): Int {
        val id = nextEventFlagId++
        eventFlags[id] = KEventFlag(id = id, bits = initBits)
        return id
    }

    fun setEventFlag(
        flagId: Int,
        bits: Int,
    ): Int {
        val flag = eventFlags[flagId] ?: return -1
        flag.bits = flag.bits or bits
        val toRemove = mutableListOf<Pair<Int, Int>>()
        for ((tid, pattern) in flag.waitingThreads) {
            if (flag.bits and pattern == pattern) {
                scheduler.makeReady(tid)
                toRemove.add(Pair(tid, pattern))
            }
        }
        flag.waitingThreads.removeAll(toRemove.toSet())
        scheduler.reschedule()
        return 0
    }

    fun clearEventFlag(
        flagId: Int,
        bits: Int,
    ): Int {
        val flag = eventFlags[flagId] ?: return -1
        flag.bits = flag.bits and bits.inv()
        return 0
    }

    fun waitEventFlag(
        flagId: Int,
        pattern: Int,
        waitSet: Int,
        clearBits: Int,
        timeout: Int,
    ): Int {
        val flag = eventFlags[flagId] ?: return -1
        if (flag.bits and pattern == pattern) {
            flag.bits = flag.bits and clearBits.inv()
            return 0
        }
        flag.waitingThreads.add(Pair(scheduler.currentThreadId, pattern))
        val thread = scheduler.getThread(scheduler.currentThreadId)
        if (thread != null) thread.status = ThreadStatus.Waiting("eventflag")
        scheduler.reschedule()
        return 0
    }

    fun deleteEventFlag(flagId: Int): Int {
        val flag = eventFlags.remove(flagId) ?: return -1
        for ((tid, _) in flag.waitingThreads) {
            scheduler.makeReady(tid)
        }
        return 0
    }
}
