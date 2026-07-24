package vesper.core.gpu

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import vesper.core.memory.Address
import vesper.core.memory.MemoryBus

class FramebufferTest : StringSpec({

    "readRaw returns null when no framebuffer set" {
        val mem = MemoryBus()
        val state = GeState()
        Framebuffer.readRaw(mem, state) shouldBe null
        Framebuffer.readRawContiguous(mem, state) shouldBe null
    }

    "readRawContiguous returns correct size for RGB565" {
        val mem = MemoryBus()
        val state = GeState()
        state.setDisplayBuf(Address(0x04000000u), 512, GeState.PIXEL_FORMAT_RGB565)

        val data = Framebuffer.readRawContiguous(mem, state)
        data shouldNotBe null
        data!!.size shouldBe 480 * 272 * 2
    }

    "readRawContiguous returns correct size for RGBA8888" {
        val mem = MemoryBus()
        val state = GeState()
        state.setDisplayBuf(Address(0x04000000u), 512, GeState.PIXEL_FORMAT_RGBA8888)

        val data = Framebuffer.readRawContiguous(mem, state)
        data shouldNotBe null
        data!!.size shouldBe 480 * 272 * 4
    }

    "readRaw reads pixel data from VRAM" {
        val mem = MemoryBus()
        val state = GeState()
        state.setDisplayBuf(Address(0x04000000u), 512, GeState.PIXEL_FORMAT_RGB565)

        mem.write16(Address(0x04000000u), 0xF800)
        mem.write16(Address(0x04000002u), 0x07E0)
        mem.write16(Address(0x04000004u), 0x001F)

        val data = Framebuffer.readRaw(mem, state)
        data shouldNotBe null
        data!![0].toInt() shouldBe 0x00
        data[1].toInt() shouldBe 0xF8.toByte().toInt()
    }
})
