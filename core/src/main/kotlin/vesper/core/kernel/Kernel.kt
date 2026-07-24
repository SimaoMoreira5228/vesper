package vesper.core.kernel

import vesper.common.Loggable
import vesper.common.info
import vesper.common.warn
import vesper.core.ICpu
import vesper.core.IKernel
import vesper.core.IMemoryBus
import vesper.core.cpu.Cpu
import vesper.core.gpu.GeState
import vesper.core.memory.Address

class Kernel(
    val memory: IMemoryBus,
    val cpu: Cpu,
    val timer: EmulatedTimer = EmulatedTimer(),
    val syscallTable: SyscallTable = SyscallTable(),
    val scheduler: Scheduler = Scheduler(cpu, timer),
    val synchPrimitives: SynchPrimitives = SynchPrimitives(scheduler),
    val fileIo: FileIo = FileIo(),
    val memoryManager: MemoryManager = MemoryManager(),
    val geState: GeState = GeState(),
    val controller: ControllerStub = ControllerStub(),
) : IKernel, Loggable {

    override val tag: String get() = "Kernel"

    private var exitRequested: Boolean = false

    fun init() {
        registerAllSyscalls()
        info { "Kernel initialized" }
        timer.reset()
    }

    override fun handleSyscall(id: Int, cpu: ICpu): Int {
        return syscallTable.dispatch(id, this, cpu as Cpu)
    }

    fun dispatchSyscallFromCpu(nid: Int, cpu: Cpu): Int {
        return syscallTable.dispatch(nid, this, cpu)
    }

    override fun isExitRequested(): Boolean = exitRequested

    override fun checkCallbacks(): Boolean {
        return scheduler.checkCallbacks()
    }

    private fun registerAllSyscalls() {
        syscallTable.register(Nids.EXIT_GAME, "sceKernelExitGame") { _, _ ->
            exitRequested = true
            cpu.halted = true
            info { "sceKernelExitGame" }
            0
        }

        syscallTable.register(Nids.EXIT_GAME_WITH_STATUS, "sceKernelExitGameWithStatus") { _, _ ->
            exitRequested = true
            cpu.halted = true
            info { "sceKernelExitGameWithStatus" }
            0
        }

        syscallTable.register(Nids.THREAD_CREATE, "sceKernelCreateThread") { kernel, cpu ->
            val entry = cpu.state.gpr(5)
            val priority = cpu.state.gpr(6)
            val stackSize = cpu.state.gpr(7)
            kernel.scheduler.createThread(
                name = "thread_0x${entry.toString(16)}",
                entryPoint = Address(entry.toUInt()),
                priority = priority and 0xFF,
                stackSize = stackSize,
                attr = 0,
            )
        }

        syscallTable.register(Nids.THREAD_START, "sceKernelStartThread") { kernel, cpu ->
            val threadId = cpu.state.gpr(4)
            kernel.scheduler.startThread(threadId)
        }

        syscallTable.register(Nids.THREAD_EXIT, "sceKernelExitThread") { kernel, cpu ->
            val status = cpu.state.gpr(4)
            kernel.scheduler.exitThread(status)
            0
        }

        syscallTable.register(Nids.THREAD_DELETE, "sceKernelDeleteThread") { kernel, cpu ->
            val threadId = cpu.state.gpr(4)
            kernel.scheduler.deleteThread(threadId)
        }

        syscallTable.register(Nids.THREAD_SLEEP, "sceKernelSleepThread") { kernel, _ ->
            kernel.scheduler.sleepThread()
            0
        }

        syscallTable.register(Nids.THREAD_SLEEP_CB, "sceKernelSleepThreadCB") { kernel, _ ->
            kernel.scheduler.sleepThread()
            kernel.checkCallbacks()
            0
        }

        syscallTable.register(Nids.THREAD_WAKEUP, "sceKernelWakeupThread") { kernel, cpu ->
            val threadId = cpu.state.gpr(4)
            kernel.scheduler.wakeupThread(threadId)
        }

        syscallTable.register(Nids.THREAD_GET_ID, "sceKernelGetThreadId") { kernel, _ ->
            kernel.scheduler.currentThreadId
        }

        syscallTable.register(Nids.DELAY_THREAD, "sceKernelDelayThread") { kernel, cpu ->
            val micros = cpu.state.gpr(4).toUInt().toLong()
            kernel.scheduler.delayCurrentThread(micros)
            0
        }

        syscallTable.register(Nids.DELAY_THREAD_CB, "sceKernelDelayThreadCB") { kernel, cpu ->
            val micros = cpu.state.gpr(4).toUInt().toLong()
            kernel.scheduler.delayCurrentThread(micros)
            kernel.checkCallbacks()
            0
        }

        syscallTable.register(Nids.CREATE_CALLBACK, "sceKernelCreateCallback") { kernel, cpu ->
            val funcPtr = cpu.state.gpr(5)
            val arg = cpu.state.gpr(6)
            kernel.scheduler.createCallback(0, funcPtr, arg)
        }

        syscallTable.register(Nids.CHECK_CALLBACK, "sceKernelCheckCallback") { kernel, _ ->
            if (kernel.checkCallbacks()) 1 else 0
        }

        syscallTable.register(Nids.CHANGE_THREAD_PRIORITY, "sceKernelChangeThreadPriority") { kernel, cpu ->
            val threadId = cpu.state.gpr(4)
            val newPriority = cpu.state.gpr(5)
            kernel.scheduler.changePriority(threadId, newPriority)
        }

        syscallTable.register(Nids.REFER_THREAD_STATUS, "sceKernelReferThreadStatus") { kernel, cpu ->
            val threadId = cpu.state.gpr(4)
            val statusPtr = Address(cpu.state.gpr(5).toUInt())
            kernel.scheduler.referThreadStatus(threadId, statusPtr, kernel.memory)
            0
        }

        syscallTable.register(Nids.IO_OPEN, "sceIoOpen") { kernel, cpu ->
            val path = readStringFromMemory(kernel.memory, Address(cpu.state.gpr(4).toUInt()))
            val flags = cpu.state.gpr(5)
            val mode = cpu.state.gpr(6)
            kernel.fileIo.open(path, flags, mode)
        }

        syscallTable.register(Nids.IO_CLOSE, "sceIoClose") { kernel, cpu ->
            val fd = cpu.state.gpr(4)
            kernel.fileIo.close(fd)
        }

        syscallTable.register(Nids.IO_READ, "sceIoRead") { kernel, cpu ->
            val fd = cpu.state.gpr(4)
            val bufPtr = Address(cpu.state.gpr(5).toUInt())
            val count = cpu.state.gpr(6)
            kernel.fileIo.read(fd, bufPtr, count, kernel)
        }

        syscallTable.register(Nids.IO_WRITE, "sceIoWrite") { kernel, cpu ->
            val fd = cpu.state.gpr(4)
            val bufPtr = Address(cpu.state.gpr(5).toUInt())
            val count = cpu.state.gpr(6)
            kernel.fileIo.write(fd, bufPtr, count, kernel)
        }

        syscallTable.register(Nids.IO_SEEK, "sceIoLseek") { kernel, cpu ->
            val fd = cpu.state.gpr(4)
            val offset = cpu.state.gpr(5)
            val whence = cpu.state.gpr(6)
            kernel.fileIo.seek(fd, offset, whence).toInt()
        }

        syscallTable.register(Nids.DISPLAY_SET_MODE, "sceDisplaySetMode") { _, _ -> 0 }
        syscallTable.register(Nids.DISPLAY_GET_MODE, "sceDisplayGetMode") { _, _ -> 0 }

        syscallTable.register(Nids.DISPLAY_SET_FRAMEBUF, "sceDisplaySetFrameBuf") { kernel, cpu ->
            val addr = Address(cpu.state.gpr(4).toUInt())
            val stride = cpu.state.gpr(5)
            val format = cpu.state.gpr(6)
            kernel.geState.setDisplayBuf(addr, stride, format)
            0
        }

        syscallTable.register(Nids.DISPLAY_WAIT_VBLANK_START, "sceDisplayWaitVblankStart") { _, _ -> 0 }

        syscallTable.register(Nids.DISPLAY_WAIT_VBLANK_START_CB, "sceDisplayWaitVblankStartCB") { kernel, _ ->
            kernel.checkCallbacks()
            0
        }

        syscallTable.register(Nids.CTRL_SET_SAMPLING_CYCLE, "sceCtrlSetSamplingCycle") { _, _ -> 0 }
        syscallTable.register(Nids.CTRL_SET_SAMPLING_MODE, "sceCtrlSetSamplingMode") { _, _ -> 0 }

        syscallTable.register(Nids.CTRL_PEEK_BUFFER_POSITIVE, "sceCtrlPeekBufferPositive") { kernel, cpu ->
            val bufPtr = Address(cpu.state.gpr(4).toUInt())
            kernel.controller.readBuffer(bufPtr, kernel)
            0
        }

        syscallTable.register(Nids.CTRL_READ_BUFFER_POSITIVE, "sceCtrlReadBufferPositive") { kernel, cpu ->
            val bufPtr = Address(cpu.state.gpr(4).toUInt())
            kernel.controller.readBuffer(bufPtr, kernel)
            0
        }

        syscallTable.register(Nids.KERNEL_DCACHE_WRITEBACK_RANGE, "sceKernelDcacheWritebackRange") { _, _ -> 0 }
        syscallTable.register(Nids.KERNEL_DCACHE_WRITEBACK_INV_RANGE, "sceKernelDcacheWritebackInvalidateRange") { _, _ -> 0 }
        syscallTable.register(Nids.KERNEL_ICACHE_INVALIDATE_RANGE, "sceKernelIcacheInvalidateRange") { _, _ -> 0 }
        syscallTable.register(Nids.KERNEL_LIBC_CLOCK, "sceKernelLibcClock") { _, _ -> 0 }
        syscallTable.register(Nids.KERNEL_LIBC_GETTIMEOFDAY, "sceKernelLibcGettimeofday") { _, _ -> 0 }

        syscallTable.register(Nids.KERNEL_GET_SYSTEM_TIME_WIDE, "sceKernelGetSystemTimeWide") { kernel, _ ->
            kernel.timer.nowMicros().toInt()
        }

        syscallTable.register(Nids.KERNEL_GET_SYSTEM_TIME, "sceKernelGetSystemTime") { kernel, cpu ->
            val timePtr = Address(cpu.state.gpr(4).toUInt())
            val time = SystemTime.now(kernel.timer)
            SystemTime.writeToMemory(kernel.memory, timePtr, time)
            0
        }

        syscallTable.register(Nids.POWER_SET_CLOCK_FREQUENCY, "scePowerSetClockFrequency") { _, _ -> 0 }
        syscallTable.register(Nids.POWER_GET_CPU_CLOCK_FREQUENCY, "scePowerGetCpuClockFrequency") { _, _ -> 333 }
        syscallTable.register(Nids.POWER_GET_BUS_CLOCK_FREQUENCY, "scePowerGetBusClockFrequency") { _, _ -> 166 }

        syscallTable.register(Nids.RTC_GET_CURRENT_TICK, "sceRtcGetCurrentTick") { kernel, cpu ->
            val tickPtr = Address(cpu.state.gpr(4).toUInt())
            val tick = kernel.timer.nowMicros()
            kernel.memory.write32(tickPtr, (tick and 0xFFFFFFFFL).toInt())
            kernel.memory.write32(tickPtr + 4, (tick shr 32).toInt())
            0
        }

        syscallTable.register(Nids.RTC_GET_TICK_RESOLUTION, "sceRtcGetTickResolution") { _, _ -> 1_000_000 }

        syscallTable.register(Nids.UTILITY_LOAD_MODULE, "sceUtilityLoadModule") { _, _ -> 0 }
        syscallTable.register(Nids.UTILITY_UNLOAD_MODULE, "sceUtilityUnloadModule") { _, _ -> 0 }
        syscallTable.register(Nids.IO_REMOVE, "sceIoRemove") { _, _ -> 0 }
        syscallTable.register(Nids.IO_MKDIR, "sceIoMkdir") { _, _ -> 0 }
        syscallTable.register(Nids.IO_RMDIR, "sceIoRmdir") { _, _ -> 0 }
        syscallTable.register(Nids.IO_IOCTL, "sceIoIoctl") { _, _ -> 0 }

        syscallTable.register(Nids.CREATE_SEMA, "sceKernelCreateSema") { kernel, cpu ->
            val initCount = cpu.state.gpr(5)
            val maxCount = cpu.state.gpr(6)
            kernel.synchPrimitives.createSemaphore(initCount, maxCount)
        }

        syscallTable.register(Nids.DELETE_SEMA, "sceKernelDeleteSema") { kernel, cpu ->
            val semaId = cpu.state.gpr(4)
            kernel.synchPrimitives.deleteSemaphore(semaId)
        }

        syscallTable.register(Nids.SIGNAL_SEMA, "sceKernelSignalSema") { kernel, cpu ->
            val semaId = cpu.state.gpr(4)
            val signal = cpu.state.gpr(5)
            kernel.synchPrimitives.signalSemaphore(semaId, signal)
        }

        syscallTable.register(Nids.WAIT_SEMA, "sceKernelWaitSema") { kernel, cpu ->
            val semaId = cpu.state.gpr(4)
            val need = cpu.state.gpr(5)
            kernel.synchPrimitives.waitSemaphore(semaId, need, false)
        }

        syscallTable.register(Nids.WAIT_SEMA_CB, "sceKernelWaitSemaCB") { kernel, cpu ->
            val semaId = cpu.state.gpr(4)
            val need = cpu.state.gpr(5)
            val result = kernel.synchPrimitives.waitSemaphore(semaId, need, false)
            kernel.checkCallbacks()
            result
        }

        syscallTable.register(Nids.POLL_SEMA, "sceKernelPollSema") { kernel, cpu ->
            val semaId = cpu.state.gpr(4)
            kernel.synchPrimitives.pollSemaphore(semaId)
        }
    }

    private fun readStringFromMemory(memory: IMemoryBus, addr: Address): String {
        val sb = StringBuilder()
        var current = addr
        while (true) {
            val byte = memory.read8(current) and 0xFF
            if (byte == 0) break
            sb.append(byte.toChar())
            current += 1
        }
        return sb.toString()
    }
}
