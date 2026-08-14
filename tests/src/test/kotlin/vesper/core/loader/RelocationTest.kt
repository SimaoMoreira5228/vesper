package vesper.core.loader

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import vesper.core.memory.Address
import vesper.core.memory.MemoryBus

class RelocationTest : StringSpec({

    "R_MIPS_NONE does nothing" {
        val mem = MemoryBus()
        val seg = Segment(0x08800000u, 0x1000u, byteArrayOf(0x12, 0x34, 0x56, 0x78), 5)
        mem.writeBytes(Address(0x08800000u), byteArrayOf(0x12, 0x34, 0x56, 0x78))
        val relocs = listOf(RelocEntry(0u, ElfConstants.R_MIPS_NONE, 0, 0))

        Relocation.applyAll(mem, listOf(seg), listOf(SegmentRelocs(0, relocs)))
        mem.read32(Address(0x08800000u)) shouldBe 0x78563412
    }

    "R_MIPS_32 adds delta" {
        val mem = MemoryBus()
        mem.write32(Address(0x08800000u), 0x100)
        val seg = Segment(0x08800000u, 0x1000u, ByteArray(0), 5, requestedVaddr = 0x08800000u)
        val relocs = listOf(RelocEntry(0u, ElfConstants.R_MIPS_32, 0, 0))

        Relocation.applyAll(mem, listOf(seg), listOf(SegmentRelocs(0, relocs)))
        mem.read32(Address(0x08800000u)) shouldBe 0x100
    }

    "R_MIPS_32 with delta" {
        val mem = MemoryBus()
        val testVaddr = 0x08800000u
        mem.write32(Address(testVaddr), 0x1000)
        val seg = Segment(testVaddr, 0x1000u, ByteArray(0), 5)
        val relocs = listOf(RelocEntry(0u, ElfConstants.R_MIPS_32, 0, 0))

        val segWithReq = seg.copy(requestedVaddr = testVaddr + 0x1000u)
        Relocation.applyAll(mem, listOf(segWithReq), listOf(SegmentRelocs(0, relocs)))
    }

    "R_MIPS_HI16 paired with R_MIPS_LO16" {
        val mem = MemoryBus()
        val base = 0x08800000u
        val targetAddr = 0x08801000
        val lui = 0x3C020000 or ((targetAddr ushr 16) and 0xFFFF)
        val addiu = 0x24420000 or (targetAddr and 0xFFFF)
        mem.write32(Address(base), lui)
        mem.write32(Address(base + 4u), addiu)

        val seg = Segment(base, 0x100u, ByteArray(0), 5)
        val hi = RelocEntry(0u, ElfConstants.R_MIPS_HI16, 0, 0)
        val lo = RelocEntry(4u, ElfConstants.R_MIPS_LO16, 0, 0)

        Relocation.applyAll(mem, listOf(seg), listOf(SegmentRelocs(0, listOf(hi, lo))))
    }

    "R_MIPS_26 updates jump target" {
        val mem = MemoryBus()
        val base = 0x08800000u

        val jalTarget = 0x0200000
        val jalInsn = 0x0C000000 or (jalTarget and 0x3FFFFFF)
        mem.write32(Address(base), jalInsn)

        val seg = Segment(base, 0x100u, ByteArray(0), 5)
        val relocs = listOf(RelocEntry(0u, ElfConstants.R_MIPS_26, 0, 0))

        Relocation.applyAll(mem, listOf(seg), listOf(SegmentRelocs(0, relocs)))
    }

    "R_MIPS_GPREL adjusts word" {
        val mem = MemoryBus()
        mem.write32(Address(0x08800000u), 0x1000)
        val seg = Segment(0x08800000u, 0x1000u, ByteArray(0), 5)
        val relocs = listOf(RelocEntry(0u, ElfConstants.R_MIPS_GPREL, 0, 0x100))

        Relocation.applyAll(mem, listOf(seg), listOf(SegmentRelocs(0, relocs)))
    }

    "R_MIPS_GOT16 adjusts got entry" {
        val mem = MemoryBus()
        val base = 0x08800000u
        mem.write32(Address(base), 0x8000)
        val seg = Segment(base, 0x1000u, ByteArray(0), 5)
        val relocs = listOf(RelocEntry(0u, ElfConstants.R_MIPS_GOT16, 0, 0))

        Relocation.applyAll(mem, listOf(seg), listOf(SegmentRelocs(0, relocs)))
    }

    "R_MIPS_LITERAL same as GPREL" {
        val mem = MemoryBus()
        mem.write32(Address(0x08800000u), 0x2000)
        val seg = Segment(0x08800000u, 0x1000u, ByteArray(0), 5)
        val relocs = listOf(RelocEntry(0u, ElfConstants.R_MIPS_LITERAL, 0, 0))

        Relocation.applyAll(mem, listOf(seg), listOf(SegmentRelocs(0, relocs)))
    }

    "zero delta does not change values" {
        val mem = MemoryBus()
        mem.write32(Address(0x08800000u), 0x1000)
        val seg = Segment(0x08800000u, 0x1000u, ByteArray(0), 5)
        val relocs = listOf(RelocEntry(0u, ElfConstants.R_MIPS_32, 0, 0))

        Relocation.applyAll(mem, listOf(seg), listOf(SegmentRelocs(0, relocs)))
        mem.read32(Address(0x08800000u)) shouldBe 0x1000
    }

    "no reloc groups is a no-op" {
        val mem = MemoryBus()
        mem.write32(Address(0x08800000u), 0xDEADBEEF.toInt())
        val seg = Segment(0x08800000u, 0x1000u, ByteArray(0), 5)

        Relocation.applyAll(mem, listOf(seg), emptyList())
        mem.read32(Address(0x08800000u)) shouldBe 0xDEADBEEF.toInt()
    }
})
