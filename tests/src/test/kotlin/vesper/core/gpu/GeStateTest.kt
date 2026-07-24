package vesper.core.gpu

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import vesper.core.memory.Address

class GeStateTest : StringSpec({

    "default state is zeroed" {
        val state = GeState()
        state.framebufferAddr shouldBe Address.ZERO
        state.pixelFormat shouldBe 0
        state.framebufferStride shouldBe 0
    }

    "set register" {
        val state = GeState()
        state.setRegister(GeState.REG_FRAMEBUFFER_ADDR, 0x04000000u)
        state.framebufferAddr shouldBe Address(0x04000000u)
    }

    "set display buf" {
        val state = GeState()
        state.setDisplayBuf(Address(0x04000000u), 512, GeState.PIXEL_FORMAT_RGB565)

        state.framebufferAddr shouldBe Address(0x04000000u)
        state.framebufferStride shouldBe 512
        state.pixelFormat shouldBe GeState.PIXEL_FORMAT_RGB565
        state.pixelDataSize() shouldBe 2
    }

    "pixel data size for RGBA8888" {
        val state = GeState()
        state.pixelFormat = GeState.PIXEL_FORMAT_RGBA8888
        state.pixelDataSize() shouldBe 4
    }

    "pixel data size for 16-bit formats" {
        val state = GeState()
        state.pixelFormat = GeState.PIXEL_FORMAT_RGB565
        state.pixelDataSize() shouldBe 2

        state.pixelFormat = GeState.PIXEL_FORMAT_RGBA5551
        state.pixelDataSize() shouldBe 2

        state.pixelFormat = GeState.PIXEL_FORMAT_RGBA4444
        state.pixelDataSize() shouldBe 2
    }

    "reset clears all registers" {
        val state = GeState()
        state.setRegister(GeState.REG_FRAMEBUFFER_ADDR, 0x04000000u)
        state.setRegister(GeState.REG_TEXTURE_ADDR, 0x05000000u)

        state.reset()

        state.framebufferAddr shouldBe Address.ZERO
        state.getRegister(GeState.REG_TEXTURE_ADDR) shouldBe 0u
    }
})
