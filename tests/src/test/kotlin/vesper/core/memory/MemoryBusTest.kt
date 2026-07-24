package vesper.core.memory

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class MemoryBusTest : StringSpec({

    "write8 and read8 round-trip in RAM" {
        val bus = MemoryBus()
        val addr = Address(0x08800100u)
        bus.write8(addr, 0xAB)
        bus.read8(addr) shouldBe 0xAB
    }

    "write16 and read16 round-trip" {
        val bus = MemoryBus()
        val addr = Address(0x08800100u)
        bus.write16(addr, 0xABCD)
        bus.read16(addr) shouldBe 0xABCD
    }

    "write32 and read32 round-trip" {
        val bus = MemoryBus()
        val addr = Address(0x08800100u)
        bus.write32(addr, 0xDEADBEEF.toInt())
        bus.read32(addr) shouldBe 0xDEADBEEF.toInt()
    }

    "write32 little-endian byte order" {
        val bus = MemoryBus()
        val addr = Address(0x08800100u)
        bus.write32(addr, 0x12345678)

        bus.read8(addr) shouldBe 0x78
        bus.read8(addr + 1) shouldBe 0x56
        bus.read8(addr + 2) shouldBe 0x34
        bus.read8(addr + 3) shouldBe 0x12
    }

    "readBytes returns correct sequence" {
        val bus = MemoryBus()
        val addr = Address(0x08800100u)
        bus.write32(addr, 0xDEADBEEF.toInt())
        bus.write32(addr + 4, 0xCAFEBABE.toInt())

        val bytes = bus.readBytes(addr, 8)
        bytes.size shouldBe 8
        (bytes[0].toInt() and 0xFF) shouldBe 0xEF
        (bytes[1].toInt() and 0xFF) shouldBe 0xBE
        (bytes[4].toInt() and 0xFF) shouldBe 0xBE
        (bytes[5].toInt() and 0xFF) shouldBe 0xBA
    }

    "writeBytes stores correctly" {
        val bus = MemoryBus()
        val data = byteArrayOf(0x10, 0x20, 0x30, 0x40)
        bus.writeBytes(Address(0x08800100u), data)

        bus.read8(Address(0x08800100u)) shouldBe 0x10
        bus.read8(Address(0x08800103u)) shouldBe 0x40
    }

    "scratchpad region is accessible" {
        val bus = MemoryBus()
        val addr = Address(0x00010000u)
        bus.write8(addr, 0xFF)
        bus.read8(addr) shouldBe 0xFF
    }

    "VRAM region is accessible" {
        val bus = MemoryBus()
        val addr = Address(0x04000000u)
        bus.write32(addr, 0xA5A5A5A5.toInt())
        bus.read32(addr) shouldBe 0xA5A5A5A5.toInt()
    }

    "RAM is separate from scratchpad" {
        val bus = MemoryBus()
        bus.write8(Address(0x00010000u), 0xAA)
        bus.write8(Address(0x08800000u), 0xBB)
        bus.read8(Address(0x00010000u)) shouldBe 0xAA
        bus.read8(Address(0x08800000u)) shouldBe 0xBB
    }

    "read32 at unaligned address still works (byte-by-byte)" {
        val bus = MemoryBus()
        val addr = Address(0x08800101u)
        bus.write8(addr, 0x01)
        bus.write8(addr + 1, 0x02)
        bus.write8(addr + 2, 0x03)
        bus.write8(addr + 3, 0x04)

        bus.read32(addr) shouldBe 0x04030201
    }

    "loadSegment writes data at base" {
        val bus = MemoryBus()
        val data = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        bus.loadSegment(Address(0x08810000u), data)
        bus.read32(Address(0x08810000u)) shouldBe 0x44332211
    }

    "region resolve works for RAM" {
        MemoryRegion.resolve(Address(0x08800000u)) shouldBe MemoryRegion.RAM_LOW
    }

    "region resolve returns null for unmapped" {
        MemoryRegion.resolve(Address(0xFF000000u)) shouldBe null
    }

    "contains returns true for mapped addresses" {
        val bus = MemoryBus()
        bus.contains(Address(0x08800000u)) shouldBe true
        bus.contains(Address(0x00010000u)) shouldBe true
        bus.contains(Address(0x04000000u)) shouldBe true
    }

    "contains returns false for unmapped addresses" {
        val bus = MemoryBus()
        bus.contains(Address(0xFF000000u)) shouldBe false
    }
})
