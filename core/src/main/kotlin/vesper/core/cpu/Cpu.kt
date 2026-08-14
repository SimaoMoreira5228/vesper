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

    private val crashTrace: MutableList<String> = mutableListOf()
    val lastTrace: List<String> get() = crashTrace.toList()

    fun recordCrashTrace() {
        try {
            java.io.StringWriter().use { sw ->
                sw.write("[CPU Crash] --- Last ${crashTrace.size} instructions ---\n")
                if (crashTrace.isEmpty()) {
                    sw.write("  (empty - size is ${crashTrace.size})\n")
                }
                var idx = 0
                for (line in crashTrace) {
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

        lastInsnPc = state.pc
        val insn = memory.read32(state.pc)

        if (state.pc.value < 0x04000000u && state.pc.value >= 0x1000u && traceStepCount > 10) {
            warn { "Jumped to unmapped address 0x${state.pc.value.toString(16)} from 0x${lastInsnPc.value.toString(16)} " +
                "ra=0x${state.gpr(31).toUInt().toString(16)}" }
        }

        traceStepCount++
        val traceLine = "0x${state.pc.value.toString(16).padStart(8, '0')}: 0x${insn.toUInt().toString(16).padStart(8, '0')}  ${disassemble(state.pc.value.toInt(), insn)}  " +
            "a0=${state.gpr(4)} a1=${state.gpr(5)} a2=${state.gpr(6)} a3=${state.gpr(7)} " +
            "v0=${state.gpr(2)} v1=${state.gpr(3)} ra=${state.gpr(31).toUInt().toString(16)} sp=${state.gpr(29).toUInt().toString(16)}"
        if (traceInstructions) {
            trace { traceLine }
        }
        crashTrace.add(traceLine)
        if (crashTrace.size > crashTraceSize) crashTrace.removeAt(0)

        state.pc = if (state.inDelaySlot) state.nextPc else state.pc + Address(4u)
        state.inDelaySlot = false

        OpcodeTable.dispatch(this, insn)

        val exception = state.exceptionPending
        if (exception != null) {
            state.exceptionPending = null
            handleException(exception)
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
