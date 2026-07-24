package vesper.core.gpu

import vesper.core.IMemoryBus
import vesper.core.memory.Address

object Framebuffer {

    fun readRaw(memory: IMemoryBus, state: GeState): ByteArray? {
        val fbAddr = state.framebufferAddr
        if (fbAddr == Address.ZERO) return null

        val width = state.framebufferWidth
        val height = state.framebufferHeight
        val bpp = state.pixelDataSize()
        val stride = state.framebufferStride

        if (stride <= 0 || width <= 0 || height <= 0) return null

        val rowBytes = width * bpp
        val totalSize = height * rowBytes

        val data = ByteArray(totalSize)
        for (y in 0 until height) {
            val rowStart = fbAddr + Address((y * stride * bpp).toUInt())
            for (x in 0 until width) {
                for (b in 0 until bpp) {
                    val srcAddr = rowStart + Address((x * bpp + b).toUInt())
                    data[y * rowBytes + x * bpp + b] = memory.read8(srcAddr).toByte()
                }
            }
        }
        return data
    }

    fun readRawContiguous(memory: IMemoryBus, state: GeState): ByteArray? {
        val fbAddr = state.framebufferAddr
        if (fbAddr == Address.ZERO) return null

        val width = state.framebufferWidth
        val height = state.framebufferHeight
        val bpp = state.pixelDataSize()
        val totalSize = width * height * bpp

        if (!memory.contains(fbAddr)) return null
        return memory.readBytes(fbAddr, totalSize)
    }
}
