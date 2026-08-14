package vesper.core.kernel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import vesper.core.memory.Address
import vesper.core.memory.MemoryBus

class KemulatorDeviceTest : StringSpec({
    "captures buffered stdout writes larger than eight KiB" {
        val memory = MemoryBus()
        val address = Address(0x08810000u)
        val text = "x".repeat(12 * 1024)
        memory.writeBytes(address, text.encodeToByteArray())
        val device = KemulatorDevice()

        device.captureWrite(1, address, text.length, memory)

        device.output shouldBe text
    }
})
