package vesper.core.kernel

import vesper.common.Loggable
import vesper.core.IMemoryBus
import vesper.core.memory.Address
import java.security.MessageDigest

class Digest : Loggable {
    override val tag: String get() = "Digest"

    private val contexts = mutableMapOf<Int, MessageDigest>()

    fun start(
        contextPtr: Int,
        algorithm: String,
    ) {
        contexts[contextPtr] = MessageDigest.getInstance(algorithm)
    }

    fun update(
        contextPtr: Int,
        memory: IMemoryBus,
        dataPtr: Int,
        length: Int,
    ) {
        val digest = contexts[contextPtr] ?: return
        if (length > 0) digest.update(memory.readBytes(Address(dataPtr.toUInt()), length))
    }

    fun finish(
        contextPtr: Int,
        memory: IMemoryBus,
        resultPtr: Int,
    ) {
        val digest = contexts.remove(contextPtr) ?: return
        writeWords(memory, resultPtr, digest.digest())
    }

    fun once(
        algorithm: String,
        memory: IMemoryBus,
        dataPtr: Int,
        length: Int,
        resultPtr: Int,
    ) {
        val digest = MessageDigest.getInstance(algorithm)
        if (length > 0) digest.update(memory.readBytes(Address(dataPtr.toUInt()), length))
        writeWords(memory, resultPtr, digest.digest())
    }

    private fun writeWords(
        memory: IMemoryBus,
        resultPtr: Int,
        bytes: ByteArray,
    ) {
        memory.writeBytes(Address(resultPtr.toUInt()), bytes)
    }
}

fun SyscallTable.registerDigestSyscalls(digest: Digest) {
    register(Nids.MD5_BLOCK_INIT, "sceKernelUtilsMd5BlockInit") { _, cpu ->
        digest.start(cpu.state.gpr(4), "MD5")
        0
    }
    register(Nids.MD5_BLOCK_UPDATE, "sceKernelUtilsMd5BlockUpdate") { kernel, cpu ->
        digest.update(cpu.state.gpr(4), kernel.memory, cpu.state.gpr(5), cpu.state.gpr(6))
        0
    }
    register(Nids.MD5_BLOCK_RESULT, "sceKernelUtilsMd5BlockResult") { kernel, cpu ->
        digest.finish(cpu.state.gpr(4), kernel.memory, cpu.state.gpr(5))
        0
    }
    register(Nids.MD5_DIGEST, "sceKernelUtilsMd5Digest") { kernel, cpu ->
        digest.once("MD5", kernel.memory, cpu.state.gpr(4), cpu.state.gpr(5), cpu.state.gpr(6))
        0
    }
    register(Nids.SHA1_BLOCK_INIT, "sceKernelUtilsSha1BlockInit") { _, cpu ->
        digest.start(cpu.state.gpr(4), "SHA-1")
        0
    }
    register(Nids.SHA1_BLOCK_UPDATE, "sceKernelUtilsSha1BlockUpdate") { kernel, cpu ->
        digest.update(cpu.state.gpr(4), kernel.memory, cpu.state.gpr(5), cpu.state.gpr(6))
        0
    }
    register(Nids.SHA1_BLOCK_RESULT, "sceKernelUtilsSha1BlockResult") { kernel, cpu ->
        digest.finish(cpu.state.gpr(4), kernel.memory, cpu.state.gpr(5))
        0
    }
    register(Nids.SHA1_DIGEST, "sceKernelUtilsSha1Digest") { kernel, cpu ->
        digest.once("SHA-1", kernel.memory, cpu.state.gpr(4), cpu.state.gpr(5), cpu.state.gpr(6))
        0
    }
}
