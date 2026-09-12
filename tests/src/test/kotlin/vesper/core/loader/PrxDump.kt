package vesper.core.loader

import vesper.core.cpu.Cpu
import vesper.core.cpu.disassemble
import vesper.core.kernel.Kernel
import vesper.core.memory.Address
import vesper.core.memory.MemoryBus

fun main(args: Array<String>) {
    val bytes = java.io.File(args[0]).readBytes()
    val start = args[1].removePrefix("0x").toUInt(16)
    val end = args[2].removePrefix("0x").toUInt(16)

    val memory = MemoryBus()
    val cpu = Cpu(memory)
    val kernel = Kernel(memory, cpu)
    kernel.init()
    cpu.kernel = kernel
    val result = ModuleLoader().loadAndResolve(bytes, memory, kernel)
    require(result.isSuccess) { "Failed to load PRX: ${result.exceptionOrNull()}" }

    var pc = start
    while (pc < end) {
        val insn = memory.read32(Address(pc))
        println("0x${pc.toString(16).padStart(8, '0')}: ${insn.toUInt().toString(16).padStart(8, '0')}  ${disassemble(pc.toInt(), insn)}")
        pc += 4u
    }
}
