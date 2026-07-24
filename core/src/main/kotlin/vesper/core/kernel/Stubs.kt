package vesper.core.kernel

import vesper.core.IMemoryBus
import vesper.core.memory.Address

class DisplayStub {
    var framebufferAddr: Address = Address.ZERO
    var framebufferStride: Int = 512
    var framebufferPixelFormat: Int = 3
    var mode: Int = 0
    var width: Int = 480
    var height: Int = 272
}

class ControllerStub {
    data class CtrlData(
        val timestamp: Int = 0,
        val buttons: Int = 0,
        val lx: Byte = 128.toByte(),
        val ly: Byte = 128.toByte(),
        val rx: Byte = 128.toByte(),
        val ry: Byte = 128.toByte(),
        val reserved: Int = 0,
    )

    private var samplingCycle: Int = 0
    private var samplingMode: Int = 0

    fun readBuffer(ptr: Address, kernel: Kernel) {
        val data = CtrlData()
        kernel.memory.write32(ptr, data.buttons)
        kernel.memory.write8(ptr + 4, data.lx.toInt() and 0xFF)
        kernel.memory.write8(ptr + 5, data.ly.toInt() and 0xFF)
        kernel.memory.write8(ptr + 6, data.rx.toInt() and 0xFF)
        kernel.memory.write8(ptr + 7, data.ry.toInt() and 0xFF)
        kernel.memory.write32(ptr + 8, data.timestamp)
    }
}
