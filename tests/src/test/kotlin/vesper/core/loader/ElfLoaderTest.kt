package vesper.core.loader

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.booleans.shouldBeTrue
import vesper.core.memory.Address

private fun writeU32(bytes: ByteArray, offset: Int, value: UInt) {
    bytes[offset] = (value and 0xFFu).toByte()
    bytes[offset + 1] = ((value shr 8) and 0xFFu).toByte()
    bytes[offset + 2] = ((value shr 16) and 0xFFu).toByte()
    bytes[offset + 3] = ((value shr 24) and 0xFFu).toByte()
}

class ElfLoaderTest : StringSpec({

    fun minimalElf(
        entry: UInt = 0x08900000u,
        vaddr: UInt = 0x08900000u,
        filesz: UInt = 256u,
        memsz: UInt = 512u,
        segmentData: ByteArray? = null,
    ): ByteArray {
        val segCount = 1
        val phdrOffset = 0x34u
        val data = segmentData ?: ByteArray(filesz.toInt()) { i -> (i and 0xFF).toByte() }
        val ehdrSize = 0x34
        val phdrSize = 0x20
        val totalSize = ehdrSize + phdrSize * segCount + data.size
        val bytes = ByteArray(totalSize) { 0 }

        bytes[0] = 0x7F
        bytes[1] = 'E'.code.toByte()
        bytes[2] = 'L'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        bytes[4] = 1
        bytes[5] = 1
        bytes[6] = 1
        bytes[7] = 0

        val eType = ElfConstants.ET_EXEC
        bytes[16] = (eType and 0xFF).toByte()
        bytes[17] = (eType shr 8).toByte()

        val machine = ElfConstants.EM_MIPS
        bytes[18] = (machine and 0xFF).toByte()
        bytes[19] = (machine shr 8).toByte()

        bytes[20] = 1; bytes[21] = 0; bytes[22] = 0; bytes[23] = 0

        writeU32(bytes, 24, entry)
        writeU32(bytes, 28, phdrOffset)
        writeU32(bytes, 32, 0u)
        bytes[40] = ehdrSize.toByte()
        bytes[42] = phdrSize.toByte()
        bytes[44] = segCount.toByte()
        bytes[48] = 0
        bytes[50] = 0

        val phdrBase = phdrOffset.toInt()
        writeU32(bytes, phdrBase, ElfConstants.PT_LOAD.toUInt())
        writeU32(bytes, phdrBase + 4, 0u)
        writeU32(bytes, phdrBase + 8, vaddr)
        writeU32(bytes, phdrBase + 12, vaddr)
        writeU32(bytes, phdrBase + 16, filesz)
        writeU32(bytes, phdrBase + 20, memsz)
        writeU32(bytes, phdrBase + 24, (ElfConstants.PF_R or ElfConstants.PF_W or ElfConstants.PF_X).toUInt())
        writeU32(bytes, phdrBase + 28, 0x1000u)

        data.copyInto(bytes, totalSize - data.size)
        return bytes
    }

    "load minimal ELF succeeds" {
        val elf = minimalElf()
        val loader = ElfLoader()
        val result = loader.load(elf)

        result.isSuccess shouldBe true
        val image = result.getOrThrow()
        image.entryPoint shouldBe Address(0x08900000u)
        image.segments.size shouldBe 1
    }

    "segments have correct vaddr and size" {
        val elf = minimalElf(vaddr = 0x08810000u, filesz = 256u, memsz = 512u)
        val loader = ElfLoader()
        val image = loader.load(elf).getOrThrow()

        val seg = image.segments[0]
        seg.vaddr shouldBe 0x08810000u
        seg.size shouldBe 512u
        seg.data.size shouldBe 256
    }

    "rejects invalid magic" {
        val bytes = ByteArray(52) { 0 }
        val loader = ElfLoader()
        val result = loader.load(bytes)
        result.isFailure shouldBe true
    }

    "rejects non-MIPS machine" {
        val elf = minimalElf()
        elf[18] = 3
        elf[19] = 0

        val loader = ElfLoader()
        val result = loader.load(elf)
        result.isFailure shouldBe true
    }

    "handles zero-file-size segment" {
        val elf = minimalElf(filesz = 0u, memsz = 64u)
        val loader = ElfLoader()
        val result = loader.load(elf)
        result.isSuccess shouldBe true
        val image = result.getOrThrow()
        image.segments[0].data.size shouldBe 0
    }

    "loadSegment into MemoryBus" {
        val elf = minimalElf(vaddr = 0x08810000u)
        val loader = ElfLoader()
        val image = loader.load(elf).getOrThrow()

        val mem = vesper.core.memory.MemoryBus()
        for (seg in image.segments) {
            mem.loadSegment(Address(seg.vaddr), seg.data)
        }

        mem.read32(Address(0x08810000u)) shouldNotBe 0
        mem.contains(Address(0x08810000u)) shouldBe true
    }
})
