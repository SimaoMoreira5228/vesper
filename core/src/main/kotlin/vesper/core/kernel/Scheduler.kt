package vesper.core.kernel

import vesper.common.Loggable
import vesper.core.IMemoryBus
import vesper.core.cpu.Cpu
import vesper.core.cpu.CpuState
import vesper.core.memory.Address

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

    private val readyQueue = ArrayDeque<Int>()
    private val sleeping = mutableListOf<TimerEvent>()
    private var nextStackTop: Int = MAIN_STACK_TOP - STACK_GAP

    init {
        createMainThread()
    }

    private fun createMainThread(): Int {
        val id = nextThreadId++
        val saved = CpuState()
        saved.reset(Address.ZERO)
        threads[id] = KThread(
            id = id,
            name = "main",
            priority = MAIN_PRIORITY,
            status = ThreadStatus.Running,
            entryPoint = Address.ZERO,
            savedState = saved,
            stackBase = MAIN_STACK_TOP,
            stackSize = STACK_GAP,
            attr = 0,
            exitStatus = 0,
        )
        currentThreadId = id
        return id
    }

    fun createThread(
        name: String,
        entryPoint: Address,
        priority: Int,
        stackSize: Int,
        attr: Int = 0,
    ): Int {
        val id = nextThreadId++

        val saved = CpuState()
        saved.reset(entryPoint)
        saved.setGpr(31, THREAD_EXIT_TRAMPOLINE.toInt())
        val stackTop = nextStackTop
        nextStackTop -= (stackSize + 0xFFF) and 0xFFFFF000.toInt()
        saved.setGpr(29, stackTop - 16)

        threads[id] = KThread(
            id = id,
            name = name,
            priority = priority,
            status = ThreadStatus.Dormant,
            entryPoint = entryPoint,
            savedState = saved,
            stackBase = nextStackTop,
            stackSize = stackSize,
            attr = attr,
            exitStatus = 0,
        )
        return id
    }

    fun startThread(threadId: Int, userDataLength: Int = 0, userDataPtr: Int = 0, gp: Int = 0): Int {
        val thread = threads[threadId] ?: return -1
        if (thread.status != ThreadStatus.Dormant) return -1

        thread.savedState.setGpr(4, userDataLength)
        thread.savedState.setGpr(5, userDataPtr)
        thread.savedState.setGpr(28, gp)
        makeReady(threadId)
        reschedule()
        return 0
    }

    fun exitThread(status: Int): Int {
        val thread = threads[currentThreadId] ?: return -1
        thread.status = ThreadStatus.Dormant
        thread.exitStatus = status
        wakeWaiters(thread)
        reschedule()
        return status
    }

    fun terminateThread(threadId: Int): Int {
        val thread = threads[threadId] ?: return -1
        if (thread.status == ThreadStatus.Dormant) return -1
        thread.status = ThreadStatus.Dormant
        wakeWaiters(thread)
        if (threadId == currentThreadId) reschedule()
        return 0
    }

    fun deleteThread(threadId: Int): Int {
        val thread = threads[threadId] ?: return -1
        wakeWaiters(thread)
        threads.remove(threadId)
        readyQueue.remove(threadId)
        sleeping.removeAll { it.threadId == threadId }
        if (threadId == currentThreadId) {
            currentThreadId = -1
            reschedule()
        }
        return 0
    }

    fun waitThreadEnd(threadId: Int): Int {
        val thread = threads[threadId] ?: return -1
        if (thread.status == ThreadStatus.Dormant) return 0
        thread.waitQueue.add(currentThreadId)
        threads[currentThreadId]?.status = ThreadStatus.Waiting("threadEnd")
        reschedule()
        return 0
    }

    fun sleepThread(): Int {
        threads[currentThreadId]?.status = ThreadStatus.Waiting("sleep")
        reschedule()
        return 0
    }

    fun wakeupThread(threadId: Int): Int {
        val thread = threads[threadId] ?: return -1
        if (thread.status is ThreadStatus.Waiting) {
            makeReady(threadId)
            reschedule()
            return 0
        }
        return -1
    }

    fun delayCurrentThread(micros: Long): Int {
        val thread = threads[currentThreadId] ?: return -1
        thread.status = ThreadStatus.Waiting("delay")
        sleeping.add(TimerEvent(timer.nowMicros() + micros, thread.id))
        sleeping.sortBy { it.wakeupTick }
        reschedule()
        return 0
    }

    fun makeReady(threadId: Int) {
        val thread = threads[threadId] ?: return
        if (thread.status == ThreadStatus.Running) return
        thread.status = ThreadStatus.Ready
        if (thread.id !in readyQueue) readyQueue.add(thread.id)
    }

    fun reschedule() {
        processTimers()
        val current = threads[currentThreadId]
        if (current != null) {
            saveState(current)
            if (current.status == ThreadStatus.Running) {
                current.status = ThreadStatus.Ready
                if (current.id !in readyQueue) readyQueue.add(current.id)
            }
        }
        dispatchNext()
    }

    fun tick() {
        processTimers()
        dispatchNext()
    }

    fun hasRunnableThread(): Boolean =
        threads[currentThreadId]?.status == ThreadStatus.Running || readyQueue.isNotEmpty()

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

    private fun dispatchNext() {
        if (readyQueue.isEmpty()) return
        readyQueue.sortBy { threads[it]?.priority ?: 0xFF }
        val nextId = readyQueue.removeFirst()
        val next = threads[nextId] ?: return
        val previous = currentThreadId
        next.status = ThreadStatus.Running
        currentThreadId = nextId
        if (nextId != previous) restoreState(next)
    }

    private fun processTimers() {
        val now = timer.nowMicros()
        while (sleeping.isNotEmpty() && sleeping.first().wakeupTick <= now) {
            makeReady(sleeping.removeFirst().threadId)
        }
    }

    private fun wakeWaiters(thread: KThread) {
        for (waiterId in thread.waitQueue) makeReady(waiterId)
        thread.waitQueue.clear()
    }

    private fun saveState(thread: KThread) {
        thread.savedState.copyFrom(cpu.state)
    }

    private fun restoreState(thread: KThread) {
        cpu.state.copyFrom(thread.savedState)
    }

    fun createCallback(namePtr: Int, funcPtr: Int, arg: Int): Int {
        val id = nextCallbackId++
        callbacks[id] = KCallback(id = id, funcPtr = funcPtr, arg = arg)
        return id
    }

    fun checkCallbacks(): Boolean {
        val thread = threads[currentThreadId] ?: return false
        var called = false
        for (cb in callbacks.values) {
            if (cb.active) {
                thread.savedState.pc = Address(cb.funcPtr.toUInt())
                called = true
            }
        }
        return called
    }

    fun currentThread(): KThread? = threads[currentThreadId]

    val threadCount: Int get() = threads.size

    fun getThread(id: Int): KThread? = threads[id]

    companion object {
        const val MAIN_PRIORITY = 0x20
        const val MAIN_STACK_TOP = 0x09F00000
        const val STACK_GAP = 0x00100000
        const val THREAD_EXIT_TRAMPOLINE = 0x09FF0000u
    }
}
