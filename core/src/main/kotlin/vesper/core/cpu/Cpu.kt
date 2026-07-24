package vesper.core.cpu

import vesper.common.Loggable
import vesper.common.Logger
import vesper.common.info
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

    private var halted: Boolean = false

    override fun reset() {
        state.reset(Address(0x08900000u))
        halted = false
        info { "CPU reset to entry point $pc" }
    }

    override fun step() {
        if (halted) return

        val insn = memory.read32(state.pc)

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

    private fun handleException(exception: CpuException) {
        when (exception) {
            is CpuException.Syscall -> {
                kernel?.handleSyscall(0, this)
            }
            is CpuException.Breakpoint -> {
                warn { "Breakpoint hit at $pc" }
                halted = true
            }
            is CpuException.ReservedInstruction -> {
                warn { "Reserved instruction at $pc" }
                halted = true
            }
            is CpuException.AddressError -> {
                val op = if (exception.isStore) "write" else "read"
                warn { "Address error on $op at ${exception.address}" }
                halted = true
            }
            is CpuException.ArithmeticOverflow -> {
                warn { "Arithmetic overflow" }
                halted = true
            }
            is CpuException.UnimplementedInstruction -> {
                warn { "Unimplemented instruction opcode=0x${exception.opcode.toString(16)} funct=0x${exception.funct.toString(16)}" }
                halted = true
            }
        }
    }

    fun run(instructions: Int = Int.MAX_VALUE) {
        var count = 0
        while (!halted && count < instructions) {
            step()
            count++
        }
    }

    override fun toString(): String = state.toString()

    companion object {
        val logTag: String = "CPU"
    }
}
