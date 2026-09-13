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
    val kemulator: KemulatorDevice = KemulatorDevice(),
) : IKernel, Loggable {

    override val tag: String get() = "Kernel"

    private var exitRequested: Boolean = false
    val importMap = mutableMapOf<Address, Int>()
    val bootThreadExit: Address = Address(Scheduler.THREAD_EXIT_TRAMPOLINE)

    fun registerImport(stubAddr: Address, nid: Int) {
        importMap[stubAddr] = nid
    }

    override fun resolveImport(pc: Address): Int? = importMap[pc]

    fun init() {
        writeExitTrampoline()
        syscallTable.registerAllKpspemuStubs()
        registerAllSyscalls()
        info { "Kernel initialized" }
        timer.reset()
    }

    private fun writeExitTrampoline() {
        val addr = Address(Scheduler.THREAD_EXIT_TRAMPOLINE)
        val nid = Nids.THREAD_EXIT
        memory.write32(addr, 0x3C020000 or (nid ushr 16))
        memory.write32(addr + 4, 0x34420000 or (nid and 0xFFFF))
        memory.write32(addr + 8, 0x24040000)
        memory.write32(addr + 12, 0x0000000C)
        memory.write32(addr + 16, 0x00000000)
    }

    override fun handleSyscall(id: Int, cpu: ICpu): Int {
        val caller = scheduler.currentThread()
        val result = syscallTable.dispatch(id, this, cpu as Cpu)
        if (scheduler.currentThreadId == caller?.id) {
            cpu.state.setGpr(2, result)
        } else {
            caller?.savedState?.setGpr(2, result)
        }
        return result
    }

    override fun hasRunnableThread(): Boolean = scheduler.hasRunnableThread()

    override fun advanceIdle() {
        timer.advance(1000)
        scheduler.tick()
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
            info { "sceKernelExitGame" }
            0
        }

        syscallTable.register(Nids.EXIT_GAME_WITH_STATUS, "sceKernelExitGameWithStatus") { _, _ ->
            exitRequested = true
            info { "sceKernelExitGameWithStatus" }
            0
        }

        syscallTable.register(Nids.THREAD_CREATE, "sceKernelCreateThread") { kernel, cpu ->
            val name = readStringFromMemory(kernel.memory, Address(cpu.state.gpr(4).toUInt()))
            val entry = cpu.state.gpr(5)
            val priority = cpu.state.gpr(6)
            val stackSize = cpu.state.gpr(7)
            val attr = cpu.state.gpr(8)
            kernel.scheduler.createThread(
                name = name,
                entryPoint = Address(entry.toUInt()),
                priority = priority and 0xFF,
                stackSize = stackSize,
                attr = attr,
            )
        }

        syscallTable.register(Nids.THREAD_START, "sceKernelStartThread") { kernel, cpu ->
            val threadId = cpu.state.gpr(4)
            kernel.scheduler.startThread(
                threadId = threadId,
                userDataLength = cpu.state.gpr(5),
                userDataPtr = cpu.state.gpr(6),
                gp = cpu.state.gpr(28),
            )
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

        syscallTable.register(Nids.THREAD_WAIT_END, "sceKernelWaitThreadEnd") { kernel, cpu ->
            kernel.scheduler.waitThreadEnd(cpu.state.gpr(4))
        }

        syscallTable.register(Nids.THREAD_WAIT_END_CB, "sceKernelWaitThreadEndCB") { kernel, cpu ->
            val result = kernel.scheduler.waitThreadEnd(cpu.state.gpr(4))
            kernel.checkCallbacks()
            result
        }

        syscallTable.register(Nids.THREAD_TERMINATE, "sceKernelTerminateThread") { kernel, cpu ->
            kernel.scheduler.terminateThread(cpu.state.gpr(4))
        }

        syscallTable.register(Nids.THREAD_EXIT_DELETE, "sceKernelExitDeleteThread") { kernel, cpu ->
            val threadId = kernel.scheduler.currentThreadId
            kernel.scheduler.exitThread(cpu.state.gpr(4))
            kernel.scheduler.deleteThread(threadId)
            0
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
            val fd = kernel.fileIo.open(path, flags, mode)
            fd
        }

        syscallTable.register(Nids.IO_CLOSE, "sceIoClose") { kernel, cpu ->
            val fd = cpu.state.gpr(4)
            if (fd <= 2) 0 else kernel.fileIo.close(fd)
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
            if (fd == 1 || fd == 2) {
                kernel.kemulator.captureWrite(fd, bufPtr, count, kernel.memory)
                count
            } else {
                kernel.fileIo.write(fd, bufPtr, count, kernel)
            }
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

        syscallTable.register(Nids.SYS_MEM_ALLOC_PARTITION, "sceKernelAllocPartitionMemory") { kernel, cpu ->
            val partition = cpu.state.gpr(4)
            val namePtr = Address(cpu.state.gpr(5).toUInt())
            val type = cpu.state.gpr(6)
            val size = cpu.state.gpr(7)
            val name = readStringFromMemory(kernel.memory, namePtr)
            val result = kernel.memoryManager.allocPartitionMemory(name, type, size)
            result
        }

        syscallTable.register(Nids.SYS_MEM_GET_BLOCK_HEAD, "sceKernelGetBlockHeadAddr") { kernel, cpu ->
            val blockId = cpu.state.gpr(4)
            kernel.memoryManager.getBlockAddress(blockId)
        }

        syscallTable.register(Nids.SYS_MEM_MAX_FREE, "sceKernelMaxFreeMemSize") { kernel, _ ->
            kernel.memoryManager.maxFreeMemSize()
        }

        syscallTable.register(Nids.SYS_MEM_TOTAL_FREE, "sceKernelTotalFreeMemSize") { kernel, _ ->
            kernel.memoryManager.totalFreeMemSize()
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

        syscallTable.register(Nids.SYS_MEM_SET_COMPILED_SDK, "sceKernelSetCompiledSdkVersion") { _, _ -> 0 }
        syscallTable.register(Nids.SYS_MEM_SET_COMPILER, "sceKernelSetCompilerVersion") { _, _ -> 0 }

        syscallTable.register(0x172D316E, "sceKernelStdin") { _, _ -> 0 }
        syscallTable.register(0xA6BAB2E9.toInt(), "sceKernelStdout") { _, _ -> 1 }
        syscallTable.register(0xF78BA90A.toInt(), "sceKernelStderr") { _, _ -> 2 }
        syscallTable.register(0x13A5ABEF.toInt(), "sceKernelPrintf") { kernel, cpu ->
            val fmtPtr = Address(cpu.state.gpr(4).toUInt())
            if (fmtPtr.value >= 0x08800000u) {
                var len = 0
                while (len < 4096 && kernel.memory.read8(fmtPtr + len) != 0) len++
                if (len > 0) {
                    val raw = kernel.memory.readBytes(fmtPtr, len).decodeToString()
                    kernel.kemulator.capture(raw)
                }
            }
            0
        }

        syscallTable.register(0xD675EBB8.toInt(), "sceKernelSelfStopUnloadModule") { _, _ ->
            exitRequested = true
            cpu.halted = true
            info { "sceKernelSelfStopUnloadModule" }
            0
        }

        syscallTable.register(Nids.IO_REMOVE, "sceIoRemove") { _, _ -> 0 }
        syscallTable.register(Nids.IO_MKDIR, "sceIoMkdir") { _, _ -> 0 }
        syscallTable.register(Nids.IO_RMDIR, "sceIoRmdir") { _, _ -> 0 }

        val ioDevctlHandler = fun(kernel: Kernel, cpu: Cpu): Int {
            val namePtr = Address(cpu.state.gpr(4).toUInt())
            val cmd = cpu.state.gpr(5)
            val inPtr = Address(cpu.state.gpr(6).toUInt())
            val inLen = cpu.state.gpr(7)

            val name = readStringFromMemory(kernel.memory, namePtr)
            val inData = if (inPtr != Address.ZERO && inLen > 0) kernel.memory.readBytes(inPtr, inLen) else null

            val sp = cpu.state.gpr(29).toUInt()
            val outPtr = Address(cpu.memory.read32(Address(sp + 16u)).toUInt())
            val outLen = cpu.memory.read32(Address(sp + 20u)).toUInt()
            val outData = if (outPtr != Address.ZERO && outLen > 0u && outLen < 1024u && outPtr.value >= 0x08800000u && outPtr.value < 0x08A00000u)
                ByteArray(outLen.toInt()) else null

            val kemResult = kernel.kemulator.handleDevctl(name, cmd, inData, inLen, outData)
            if (cmd == 2 && inData != null) {
                val end = inData.indexOf(0).let { if (it < 0) inData.size else it }
                if (end > 0) {
                    kernel.kemulator.capture(inData.sliceArray(0 until end).decodeToString())
                }
            }
            if (kemResult >= 0) {
                if (outData != null && outPtr != Address.ZERO) {
                    kernel.memory.writeBytes(outPtr, outData)
                }
                return kemResult
            }

            return kernel.fileIo.devctl(name, cmd, inData, inLen, outData)
        }

        syscallTable.register(Nids.IO_IOCTL, "sceIoIoctl", SyscallHandler(ioDevctlHandler))
        syscallTable.register(Nids.IO_DEVCtl, "sceIoDevctl", SyscallHandler(ioDevctlHandler))

        syscallTable.register(Nids.CREATE_SEMA, "sceKernelCreateSema") { kernel, cpu ->
            val name = readStringFromMemory(kernel.memory, Address(cpu.state.gpr(4).toUInt()))
            val attr = cpu.state.gpr(5)
            val initCount = cpu.state.gpr(6)
            val maxCount = cpu.state.gpr(7)
            kernel.synchPrimitives.createSemaphore(name, attr, initCount, maxCount)
        }

        syscallTable.register(Nids.REFER_SEMA_STATUS, "sceKernelReferSemaStatus") { kernel, cpu ->
            val semaId = cpu.state.gpr(4)
            val infoPtr = Address(cpu.state.gpr(5).toUInt())
            kernel.synchPrimitives.referSemaphoreStatus(semaId, infoPtr, kernel.memory)
            0
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
