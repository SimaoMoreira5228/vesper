package vesper.core.kernel

import vesper.common.Logger
import vesper.core.cpu.Cpu

fun interface SyscallHandler {
    fun invoke(kernel: Kernel, cpu: Cpu): Int
}

class SyscallTable {
    private data class Entry(val nid: Int, val name: String, val handler: SyscallHandler)

    private val byNid = mutableMapOf<Int, Entry>()
    private val logTag = "SyscallTable"

    var trace: Boolean = false

    fun register(nid: Int, name: String, handler: SyscallHandler) {
        byNid[nid] = Entry(nid, name, handler)
    }

    fun dispatch(nid: Int, kernel: Kernel, cpu: Cpu): Int {
        val entry = byNid[nid]
        if (entry != null) {
            if (trace) {
                val a0 = cpu.state.gpr(4)
                val a1 = cpu.state.gpr(5)
                val a2 = cpu.state.gpr(6)
                val a3 = cpu.state.gpr(7)
                println("[SYSCALL] ${entry.name}(a0=0x${a0.toUInt().toString(16)}, a1=0x${a1.toUInt().toString(16)}, a2=0x${a2.toUInt().toString(16)}, a3=0x${a3.toUInt().toString(16)})")
            }
            return entry.handler.invoke(kernel, cpu)
        }
        return 0
    }

    fun isRegistered(nid: Int): Boolean = nid in byNid
}
