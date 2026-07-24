package vesper.core.kernel

import vesper.common.Loggable
import vesper.common.warn
import vesper.core.IMemoryBus
import vesper.core.cpu.Cpu
import vesper.core.cpu.CpuState
import vesper.core.memory.Address
import kotlin.math.min

sealed interface ThreadStatus {
    data object Dormant : ThreadStatus
    data object Ready : ThreadStatus
    data object Running : ThreadStatus
    data class Waiting(val reason: String) : ThreadStatus
    data object Suspended : ThreadStatus
}

data class KThread(
    val id: Int,
    val name: String,
    var priority: Int,
    var status: ThreadStatus,
    var entryPoint: Address,
    var savedState: CpuState,
    var stackBase: Int,
    var stackSize: Int,
    var attr: Int,
    var exitStatus: Int,
    var waitQueue: MutableList<Int> = mutableListOf(),
)

data class KCallback(
    val id: Int,
    val funcPtr: Int,
    val arg: Int,
    var active: Boolean = true,
)

class Scheduler(
    private val cpu: Cpu,
    private val timer: EmulatedTimer,
) : Loggable {

    override val tag: String get() = "Scheduler"

    var currentThreadId: Int = -1
        private set

    private var nextThreadId: Int = 1
    private var nextCallbackId: Int = 1

    private val threads = mutableMapOf<Int, KThread>()
    private val callbacks = mutableMapOf<Int, KCallback>()

    private val readyQueue = mutableListOf<Int>()
    private val waitingThreads = mutableListOf<Pair<Long, Int>>()

    private var idleThreadId: Int = createIdleThread()

    private fun createIdleThread(): Int {
        val id = nextThreadId++
        threads[id] = KThread(
            id = id,
            name = "idle",
            priority = 0xFF,
            status = ThreadStatus.Ready,
            entryPoint = Address.ZERO,
            savedState = CpuState(),
            stackBase = 0,
            stackSize = 0x1000,
            attr = 0,
            exitStatus = 0,
        )
        readyQueue.add(id)
        currentThreadId = id
        return id
    }

    private fun allocateThreadId(): Int = nextThreadId++

    fun createThread(
        name: String,
        entryPoint: Address,
        priority: Int,
        stackSize: Int,
        attr: Int = 0,
    ): Int {
        val id = allocateThreadId()

        val saved = CpuState()
        saved.pc = entryPoint
        saved.setGpr(29, 0x08800000 + 0x100000 - stackSize)

        val thread = KThread(
            id = id,
            name = name,
            priority = priority,
            status = ThreadStatus.Dormant,
            entryPoint = entryPoint,
            savedState = saved,
            stackBase = 0x08800000 + 0x100000 - stackSize,
            stackSize = stackSize,
            attr = attr,
            exitStatus = 0,
        )
        threads[id] = thread
        return id
    }

    fun startThread(threadId: Int): Int {
        val thread = threads[threadId] ?: return -1
        if (thread.status != ThreadStatus.Dormant) return -1

        thread.status = ThreadStatus.Ready
        readyQueue.add(threadId)
        reschedule()
        return 0
    }

    fun exitThread(status: Int): Int {
        val thread = threads[currentThreadId] ?: return -1
        thread.status = ThreadStatus.Dormant
        thread.exitStatus = status
        reschedule()
        return status
    }

    fun deleteThread(threadId: Int): Int {
        val thread = threads.remove(threadId) ?: return -1
        readyQueue.remove(threadId)
        return 0
    }

    fun sleepThread(): Int {
        val thread = threads[currentThreadId] ?: return -1
        thread.status = ThreadStatus.Waiting("sleep")
        reschedule()
        return 0
    }

    fun wakeupThread(threadId: Int): Int {
        val thread = threads[threadId] ?: return -1
        if (thread.status is ThreadStatus.Waiting) {
            thread.status = ThreadStatus.Ready
            readyQueue.add(threadId)
            reschedule()
            return 0
        }
        return -1
    }

    fun delayCurrentThread(micros: Long): Int {
        val thread = threads[currentThreadId] ?: return -1
        thread.status = ThreadStatus.Waiting("delay")
        val wakeupTick = timer.nowMicros() + micros
        waitingThreads.add(Pair(wakeupTick, thread.id))
        waitingThreads.sortBy { it.first }
        reschedule()
        return 0
    }

    fun yield(): Int {
        reschedule()
        return 0
    }

    fun changePriority(threadId: Int, newPriority: Int): Int {
        val thread = threads[threadId] ?: return -1
        thread.priority = newPriority and 0xFF
        return 0
    }

    fun referThreadStatus(threadId: Int, statusPtr: Address, memory: IMemoryBus) {
        val thread = threads[threadId] ?: return
        memory.write32(statusPtr, thread.status.hashCode())
        memory.write32(statusPtr + 4, thread.priority)
    }

    fun createCallback(namePtr: Int, funcPtr: Int, arg: Int): Int {
        val id = nextCallbackId++
        callbacks[id] = KCallback(id = id, funcPtr = funcPtr, arg = arg)
        return id
    }

    fun checkCallbacks(): Boolean {
        processTimers()
        if (currentThreadId < 0) return false
        val thread = threads[currentThreadId] ?: return false
        var called = false
        for (cb in callbacks.values) {
            if (cb.active) {
                val savedPc = cpu.state.pc
                cpu.state.pc = Address(cb.funcPtr.toUInt())
                called = true
            }
        }
        return called
    }

    fun reschedule() {
        processTimers()
        val current = threads[currentThreadId]

        if (current != null && current.status == ThreadStatus.Running) {
            current.status = ThreadStatus.Ready
            if (current.id != idleThreadId) {
                saveState(current)
                readyQueue.add(current.id)
            }
        }

        if (readyQueue.isEmpty()) {
            currentThreadId = idleThreadId
            return
        }

        readyQueue.sortBy { threads[it]?.priority ?: 0xFF }
        val nextId = readyQueue.removeFirst()
        val next = threads[nextId] ?: return

        next.status = ThreadStatus.Running
        currentThreadId = nextId

        if (next.id != idleThreadId) {
            restoreState(next)
        }
    }

    private fun saveState(thread: KThread) {
        thread.savedState.pc = cpu.state.pc
        thread.savedState.nextPc = cpu.state.nextPc
        thread.savedState.inDelaySlot = cpu.state.inDelaySlot
        cpu.state.gpr.copyInto(thread.savedState.gpr)
        thread.savedState.hi = cpu.state.hi
        thread.savedState.lo = cpu.state.lo
    }

    private fun restoreState(thread: KThread) {
        cpu.state.pc = thread.savedState.pc
        cpu.state.nextPc = thread.savedState.nextPc
        cpu.state.inDelaySlot = thread.savedState.inDelaySlot
        thread.savedState.gpr.copyInto(cpu.state.gpr)
        cpu.state.hi = thread.savedState.hi
        cpu.state.lo = thread.savedState.lo
    }

    private fun processTimers() {
        val now = timer.nowMicros()
        while (waitingThreads.isNotEmpty() && waitingThreads.first().first <= now) {
            val (_, threadId) = waitingThreads.removeFirst()
            val thread = threads[threadId]
            if (thread != null && thread.status is ThreadStatus.Waiting) {
                thread.status = ThreadStatus.Ready
                readyQueue.add(threadId)
            }
        }
        readyQueue.sortBy { threads[it]?.priority ?: 0xFF }
    }

    val threadCount: Int get() = threads.size

    fun getThread(id: Int): KThread? = threads[id]
}
