package vesper.core.cpu

import vesper.common.Loggable
import vesper.common.info
import vesper.common.trace
import vesper.common.warn
import vesper.core.ICpu
import vesper.core.IMemoryBus
import vesper.core.IKernel
import vesper.core.memory.Address

class Cpu(
    val memory: IMemoryBus,
    var kernel: IKernel? = null,
) : ICpu, Loggable {

    override val tag: String get() = "CPU"

    val state: CpuState = CpuState()

    override val pc: Address get() = state.pc

    var halted: Boolean = false
        internal set

    private var lastInsnPc: Address = Address.ZERO
    var traceInstructions: Boolean = false
    var traceStepCount: Int = 0
        private set

    private val crashTrace = InstructionTrace(crashTraceSize)
    val lastTrace: List<String> get() = crashTrace.lines()

    fun recordCrashTrace() {
        try {
            java.io.StringWriter().use { sw ->
                val traceLines = crashTrace.lines()
                sw.write("[CPU Crash] --- Last ${traceLines.size} instructions ---\n")
                if (traceLines.isEmpty()) {
                    sw.write("  (empty - size is ${traceLines.size})\n")
                }
                var idx = 0
                for (line in traceLines) {
                    val first80 = line.take(80)
                    sw.write("  [$idx] $first80\n")
                    idx++
                }
                sw.write("[CPU Crash] --- End trace ---\n")
                val msg = sw.toString()
                System.err.print(msg)
                System.err.flush()
                System.out.print(msg)
                System.out.flush()
            }
        } catch (e: Exception) {
            System.err.println("[CPU Crash] Error: ${e.message}")
            System.err.flush()
        }
    }

    companion object {
        private const val crashTraceSize = 20
        val logTag: String = "CPU"
    }

    override fun reset() {
        state.reset(Address(0x08900000u))
        halted = false
        kernel?.let { info { "CPU reset with kernel" } }
    }

    override fun step() {
        if (halted) return
        if (kernel?.isExitRequested() == true) {
            halted = true
            return
        }

        val instructionPc = state.pc
        val pendingBranchTarget = if (state.inDelaySlot) state.nextPc else null
        state.inDelaySlot = false
        lastInsnPc = instructionPc
        val insn = memory.read32(state.pc)

        if (state.pc.value < 0x04000000u && state.pc.value >= 0x1000u && traceStepCount > 10) {
            warn { "Jumped to unmapped address 0x${state.pc.value.toString(16)} from 0x${lastInsnPc.value.toString(16)} " +
                "ra=0x${state.gpr(31).toUInt().toString(16)}" }
        }

        traceStepCount++
        if (traceInstructions) {
            trace { formatInstructionTrace(instructionPc.value, insn, state) }
        }
        crashTrace.record(instructionPc.value, insn, state)

        OpcodeTable.dispatch(this, insn)

        val exception = state.exceptionPending
        if (exception != null) {
            state.exceptionPending = null
            handleException(exception)
        }

        when {
            state.inDelaySlot -> state.pc = instructionPc + Address(4u)
            pendingBranchTarget != null -> state.pc = pendingBranchTarget
            state.pc == instructionPc -> state.pc = instructionPc + Address(4u)
        }
    }

    fun raiseException(exception: CpuException) {
        state.exceptionPending = exception
    }

    fun getLastInsnAddress(): Address = lastInsnPc

    private fun handleException(exception: CpuException) {
        when (exception) {
            is CpuException.Syscall -> {
                val importNid = kernel?.resolveImport(lastInsnPc)
                val nid = importNid ?: state.gpr(2)
                val result = kernel?.handleSyscall(nid, this) ?: 0
                state.setGpr(2, result)
            }
            is CpuException.Breakpoint -> {
            }
            is CpuException.ReservedInstruction -> {
                recordCrashTrace()
                warn { "Reserved instruction at $lastInsnPc" }
                halted = true
            }
            is CpuException.AddressError -> {
                recordCrashTrace()
                val op = if (exception.isStore) "write" else "read"
                warn { "Address error on $op at ${exception.address}" }
                halted = true
            }
            is CpuException.ArithmeticOverflow -> {
                recordCrashTrace()
                warn { "Arithmetic overflow at $lastInsnPc" }
                halted = true
            }
            is CpuException.UnimplementedInstruction -> {
                recordCrashTrace()
                warn { "Unimplemented opcode=0x${exception.opcode.toString(16)} funct=0x${exception.funct.toString(16)} at $lastInsnPc" }
                halted = true
            }
        }
    }

    fun run(instructions: Int = Int.MAX_VALUE) {
        var count = 0
        while (!halted && count < instructions) {
            step()
            count++
            kernel?.checkCallbacks()
        }
    }

    override fun toString(): String = state.toString()
}
