package vesper.core.loader

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import vesper.core.cpu.Cpu
import vesper.core.kernel.Kernel
import vesper.core.kernel.Nids
import vesper.core.memory.Address
import vesper.core.memory.MemoryBus

class ElfImportTest : StringSpec({

    fun write32(bytes: ByteArray, offset: Int, value: UInt) {
        bytes[offset] = (value and 0xFFu).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xFFu).toByte()
        bytes[offset + 2] = ((value shr 16) and 0xFFu).toByte()
        bytes[offset + 3] = ((value shr 24) and 0xFFu).toByte()
    }

    fun buildPrxWithImport(
        nid: Int,
        code: ByteArray = byteArrayOf(0x00, 0x00, 0x00, 0x0C.toByte()),
        stubBase: UInt = 0x08801000u,
    ): ByteArray {
        val stubSize = 8
        val codeVaddr = 0x08800000u
        val stubVaddr = stubBase

        val shstrtabBytes = listOf(
            0,
            '.'.code, 's'.code, 'c'.code, 'e'.code, 'S'.code, 't'.code, 'u'.code, 'b'.code,
            '.'.code, 't'.code, 'e'.code, 'x'.code, 't'.code, 0,
            '.'.code, 't'.code, 'e'.code, 'x'.code, 't'.code, 0,
            '.'.code, 's'.code, 'h'.code, 's'.code, 't'.code, 'r'.code, 't'.code, 'a'.code, 'b'.code, 0,
        )
        val shstrtabContent = ByteArray(shstrtabBytes.size) { shstrtabBytes[it].toByte() }
        val shstrtabSize = shstrtabContent.size

        val shoff = 0x34
        val shdrSize = 40
        val sectionHeadersEnd = shdrSize * 4

        val elfRawSize = shoff + sectionHeadersEnd + code.size + stubSize + shstrtabSize
        val elfBuf = ByteArray(elfRawSize) { 0 }

        fun write32elf(off: Int, v: UInt) = write32(elfBuf, off, v)

        elfBuf[0] = 0x7F; elfBuf[1] = 'E'.code.toByte(); elfBuf[2] = 'L'.code.toByte(); elfBuf[3] = 'F'.code.toByte()
        elfBuf[4] = 1; elfBuf[5] = 1
        write32elf(16, 2u); write32elf(18, 8u)
        write32elf(24, codeVaddr); write32elf(28, 0u); write32elf(32, shoff.toUInt())
        elfBuf[40] = 0x34; elfBuf[42] = 32; elfBuf[44] = 1
        elfBuf[46] = shdrSize.toByte(); elfBuf[48] = 4; write32elf(50, 3u)

        val textFileOff = shoff + sectionHeadersEnd
        write32elf(shoff + shdrSize * 1 + 4, ElfConstants.SHT_PROGBITS.toUInt())
        write32elf(shoff + shdrSize * 1 + 8, (ElfConstants.SHF_ALLOC or ElfConstants.SHF_EXECINSTR).toUInt())
        write32elf(shoff + shdrSize * 1 + 12, codeVaddr)
        write32elf(shoff + shdrSize * 1 + 16, textFileOff.toUInt())
        write32elf(shoff + shdrSize * 1 + 20, code.size.toUInt())
        code.copyInto(elfBuf, textFileOff)

        val stubFileOff = textFileOff + code.size
        write32elf(shoff + shdrSize * 2, 1u)
        write32elf(shoff + shdrSize * 2 + 4, ElfConstants.SHT_PROGBITS.toUInt())
        write32elf(shoff + shdrSize * 2 + 8, ElfConstants.SHF_ALLOC.toUInt())
        write32elf(shoff + shdrSize * 2 + 12, stubVaddr)
        write32elf(shoff + shdrSize * 2 + 16, stubFileOff.toUInt())
        write32elf(shoff + shdrSize * 2 + 20, stubSize.toUInt())
        write32elf(stubFileOff, 0u); write32elf(stubFileOff + 4, nid.toUInt())

        val strTabFileOff = stubFileOff + stubSize
        write32elf(shoff + shdrSize * 3, 21u)
        write32elf(shoff + shdrSize * 3 + 4, ElfConstants.SHT_STRTAB.toUInt())
        write32elf(shoff + shdrSize * 3 + 8, 0u); write32elf(shoff + shdrSize * 3 + 12, 0u)
        write32elf(shoff + shdrSize * 3 + 16, strTabFileOff.toUInt())
        write32elf(shoff + shdrSize * 3 + 20, shstrtabSize.toUInt())
        shstrtabContent.copyInto(elfBuf, strTabFileOff)

        val pspHeaderSize = PspHeaderConstants.PSP_HEADER_SIZE
        val totalSize = pspHeaderSize + elfRawSize
        val bytes = ByteArray(totalSize) { 0 }

        bytes[0] = '~'.code.toByte(); bytes[1] = 'P'.code.toByte()
        bytes[2] = 'S'.code.toByte(); bytes[3] = 'P'.code.toByte()

        write32(bytes, 0x04, 0x1000u)
        write32(bytes, 0x06, PspHeaderConstants.COMPRESSION_PLAIN.toUInt())
        bytes[0x0A] = 't'.code.toByte(); bytes[0x0B] = 'e'.code.toByte()
        bytes[0x0C] = 's'.code.toByte(); bytes[0x0D] = 't'.code.toByte()
        bytes[0x0E] = 0
        bytes[0x26] = 1; bytes[0x27] = 1

        write32(bytes, 0x28, elfRawSize.toUInt())
        write32(bytes, 0x2C, elfRawSize.toUInt())
        write32(bytes, 0x30, 0u)
        write32(bytes, 0x34, 0x10000u)
        write32(bytes, 0x38, 0u)
        write32(bytes, 0x44, codeVaddr)
        write32(bytes, 0x48, 0u); write32(bytes, 0x4C, 0u); write32(bytes, 0x50, 0u)
        write32(bytes, 0x54, code.size.toUInt())
        write32(bytes, 0x58, 0u); write32(bytes, 0x5C, 0u); write32(bytes, 0x60, 0u)
        write32(bytes, 0x78, 0x06050010u)
        write32(bytes, 0x7C, 0u)

        elfBuf.copyInto(bytes, pspHeaderSize)

        return bytes
    }

    "parse import from PRX with known NID" {
        val prxBytes = buildPrxWithImport(nid = 0x05572A5F)
        val loader = ElfLoader()
        val result = loader.load(prxBytes)

        result.isSuccess shouldBe true
        val image = result.getOrThrow()
        image.isPrx shouldBe true
        image.pspHeader shouldNotBe null
        image.imports shouldHaveSize 1
        image.imports[0].nid shouldBe 0x05572A5F
    }

    "PRX module name from PSP header" {
        val prxBytes = buildPrxWithImport(nid = 0x446D8DE6)
        val loader = ElfLoader()
        val image = loader.load(prxBytes).getOrThrow()

        image.moduleName shouldBe "test"
        image.pspHeader!!.moduleName shouldBe "test"
    }

    "PRX entry point from PSP header" {
        val prxBytes = buildPrxWithImport(nid = 0x109F50BC)
        val loader = ElfLoader()
        val image = loader.load(prxBytes).getOrThrow()

        image.entryPoint shouldBe Address(0x08800000u)
    }

    "PRX sdk version parsed" {
        val prxBytes = buildPrxWithImport(nid = 0x05572A5F)
        val loader = ElfLoader()
        val image = loader.load(prxBytes).getOrThrow()

        image.sdkVersion shouldBe 0x06050010
    }

    "bare ELF still loads without PRX header" {
        val mem = MemoryBus()
        val cpu = Cpu(mem)
        val kernel = Kernel(mem, cpu)
        kernel.init()
        cpu.kernel = kernel

        val stubAddr = Address(0x08801000u)
        kernel.registerImport(stubAddr, Nids.EXIT_GAME)
        cpu.state.pc = stubAddr
        mem.write32(stubAddr, 0x0000000C)
        cpu.step()

        kernel.isExitRequested() shouldBe true
    }

    "PRX import works through module loader" {
        val prxBytes = buildPrxWithImport(nid = Nids.EXIT_GAME)
        val mem = MemoryBus()
        val cpu = Cpu(mem)
        val kernel = Kernel(mem, cpu)
        kernel.init()
        cpu.kernel = kernel

        val loader = ModuleLoader()
        val result = loader.loadAndResolve(prxBytes, mem, kernel)
        result.isSuccess shouldBe true
        val image = result.getOrThrow()

        image.isPrx shouldBe true
        image.imports shouldHaveSize 1
    }
})
