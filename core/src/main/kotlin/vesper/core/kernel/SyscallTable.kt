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

    fun register(nid: Int, name: String, handler: SyscallHandler) {
        byNid[nid] = Entry(nid, name, handler)
    }

    fun dispatch(nid: Int, kernel: Kernel, cpu: Cpu): Int {
        val entry = byNid[nid]
        if (entry != null) {
            return entry.handler.invoke(kernel, cpu)
        }
        Logger.warn("SyscallTable", msg = { "Unimplemented syscall 0x${nid.toString(16).padStart(8, '0')}" })
        return 0
    }

    fun isRegistered(nid: Int): Boolean = nid in byNid
}
