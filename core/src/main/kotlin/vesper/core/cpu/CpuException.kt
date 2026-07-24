package vesper.core.cpu

import vesper.core.memory.Address

sealed interface CpuException {
    data object ReservedInstruction : CpuException
    data object Syscall : CpuException
    data object Breakpoint : CpuException
    data class AddressError(val address: Address, val isStore: Boolean) : CpuException
    data object ArithmeticOverflow : CpuException
    data class UnimplementedInstruction(val opcode: Int, val funct: Int) : CpuException
}
