@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package vesper.core.gpu

import vesper.core.memory.Address

class GeState {
    private val cmdmem = UIntArray(256)

    companion object {
        const val REG_FRAMEBUFFER_ADDR = 0x47 and 0xFF
        const val REG_FRAMEBUFFER_STRIDE = 0x48 and 0xFF
        const val REG_FRAMEBUFFER_FORMAT = 0x49 and 0xFF
        const val REG_DISPLAY_ADDR = 0x4A and 0xFF
        const val REG_VERTEXTYPE = 0x04 and 0xFF
        const val REG_PRIMITIVE_TYPE = 0x08 and 0xFF
        const val REG_SCISSOR1 = 0x10 and 0xFF
        const val REG_SCISSOR2 = 0x11 and 0xFF
        const val REG_VIEWPORT1 = 0x14 and 0xFF
        const val REG_VIEWPORT2 = 0x15 and 0xFF
        const val REG_OFFSET1 = 0x16 and 0xFF
        const val REG_OFFSET2 = 0x17 and 0xFF
        const val REG_TEXTURE_ADDR = 0x2C and 0xFF
        const val REG_TEXTURE_WIDTH = 0x2D and 0xFF
        const val REG_TEXTURE_HEIGHT = 0x2E and 0xFF
        const val REG_TEXTURE_FORMAT = 0x2F and 0xFF
        const val REG_CLUT_ADDR = 0x30 and 0xFF
        const val REG_CLUT_FORMAT = 0x31 and 0xFF

        const val PIXEL_FORMAT_RGB565 = 0
        const val PIXEL_FORMAT_RGBA5551 = 1
        const val PIXEL_FORMAT_RGBA4444 = 2
        const val PIXEL_FORMAT_RGBA8888 = 3
        const val PIXEL_FORMAT_INDEX4 = 4
        const val PIXEL_FORMAT_INDEX8 = 5
        const val PIXEL_FORMAT_INDEX16 = 6
        const val PIXEL_FORMAT_INDEX32 = 7
        const val PIXEL_FORMAT_DXT1 = 8
        const val PIXEL_FORMAT_DXT3 = 9
        const val PIXEL_FORMAT_DXT5 = 10

        const val FRAMEBUFFER_STRIDE_DEFAULT = 512
    }

    fun setRegister(index: Int, value: UInt) {
        if (index in cmdmem.indices) cmdmem[index] = value
    }

    fun getRegister(index: Int): UInt {
        return if (index in cmdmem.indices) cmdmem[index] else 0u
    }

    var framebufferAddr: Address
        get() {
            val addr = cmdmem[REG_FRAMEBUFFER_ADDR].toLong()
            val low = cmdmem[(REG_FRAMEBUFFER_ADDR + 1) and 0xFF].toLong()
            return Address((addr or (low shl 32)).toUInt())
        }
        set(value) {
            cmdmem[REG_FRAMEBUFFER_ADDR] = value.value
        }

    var displayAddr: Address
        get() {
            val addr = cmdmem[REG_DISPLAY_ADDR].toLong()
            return Address(addr.toUInt())
        }
        set(value) {
            cmdmem[REG_DISPLAY_ADDR] = value.value
        }

    var framebufferStride: Int
        get() = cmdmem[REG_FRAMEBUFFER_STRIDE].toInt()
        set(value) { cmdmem[REG_FRAMEBUFFER_STRIDE] = value.toUInt() }

    var pixelFormat: Int
        get() = cmdmem[REG_FRAMEBUFFER_FORMAT].toInt()
        set(value) { cmdmem[REG_FRAMEBUFFER_FORMAT] = value.toUInt() }

    val framebufferWidth: Int get() = 480
    val framebufferHeight: Int get() = 272

    fun pixelDataSize(): Int {
        return when (pixelFormat) {
            PIXEL_FORMAT_RGBA8888 -> 4
            else -> 2
        }
    }

    fun setDisplayBuf(addr: Address, stride: Int, format: Int) {
        framebufferAddr = addr
        displayAddr = addr
        framebufferStride = if (stride > 0) stride else FRAMEBUFFER_STRIDE_DEFAULT
        pixelFormat = format
    }

    fun reset() {
        cmdmem.fill(0u)
    }
}
