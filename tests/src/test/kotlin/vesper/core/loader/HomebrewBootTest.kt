package vesper.core.loader

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import vesper.core.cpu.Cpu
import vesper.core.kernel.Kernel
import vesper.core.kernel.Nids
import vesper.core.memory.Address
import vesper.core.memory.MemoryBus

class HomebrewBootTest : StringSpec({

    fun buildHomebrewPrx(): ByteArray {
        val nidExitGame = Nids.EXIT_GAME
        val codeVaddr = 0x08800000u
        val stubVaddr = 0x08801000u

        val code =
            byteArrayOf(
                0x57, 0x05, 0x02, 0x3C,
                0x5F, 0x2A, 0x42, 0x34,
                0x0C, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
            )

        val strtab =
            byteArrayOf(
                0,
                '.'.code.toByte(), 's'.code.toByte(), 'c'.code.toByte(), 'e'.code.toByte(),
                'S'.code.toByte(), 't'.code.toByte(), 'u'.code.toByte(), 'b'.code.toByte(),
                '.'.code.toByte(), 't'.code.toByte(), 'e'.code.toByte(), 'x'.code.toByte(), 't'.code.toByte(), 0,
                '.'.code.toByte(), 't'.code.toByte(), 'e'.code.toByte(), 'x'.code.toByte(), 't'.code.toByte(), 0,
                '.'.code.toByte(), 's'.code.toByte(), 'h'.code.toByte(), 's'.code.toByte(), 't'.code.toByte(),
                'r'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte(), 'b'.code.toByte(), 0,
            )

        val ehdrSize = 0x34
        val phdrSize = 32
        val shdrSize = 40
        val phnum = 1
        val shnum = 4
        val phdrsEnd = ehdrSize + phnum * phdrSize
        val shdrsEnd = phdrsEnd + shnum * shdrSize
        val codeFileOff = shdrsEnd
        val stubFileOff = codeFileOff + code.size
        val strtabFileOff = stubFileOff + 8
        val elfSize = strtabFileOff + strtab.size

        val pspHeaderSize = PspHeaderConstants.PSP_HEADER_SIZE
        val totalSize = pspHeaderSize + elfSize
        val bytes = ByteArray(totalSize) { 0 }
        val e = pspHeaderSize

        fun w(
            off: Int,
            v: UInt,
        ) {
            bytes[off] = (v and 0xFFu).toByte()
            bytes[off + 1] = ((v shr 8) and 0xFFu).toByte()
            bytes[off + 2] = ((v shr 16) and 0xFFu).toByte()
            bytes[off + 3] = ((v shr 24) and 0xFFu).toByte()
        }

        fun ew(
            off: Int,
            v: UInt,
        ) = w(e + off, v)

        bytes[e] = 0x7F
        bytes[e + 1] = 'E'.code.toByte()
        bytes[e + 2] = 'L'.code.toByte()
        bytes[e + 3] = 'F'.code.toByte()
        bytes[e + 4] = 1
        bytes[e + 5] = 1
        ew(16, 0xFFA0u)
        ew(18, 8u)
        ew(24, 0u)
        ew(28, ehdrSize.toUInt())
        ew(32, phdrsEnd.toUInt())
        bytes[e + 40] = ehdrSize.toByte()
        bytes[e + 42] = phdrSize.toByte()
        bytes[e + 44] = phnum.toByte()
        bytes[e + 46] = shdrSize.toByte()
        bytes[e + 48] = shnum.toByte()
        ew(50, 3u)

        ew(ehdrSize, 1u)
        ew(ehdrSize + 4, codeFileOff.toUInt())
        ew(ehdrSize + 8, codeVaddr)
        ew(ehdrSize + 12, codeVaddr)
        ew(ehdrSize + 16, code.size.toUInt())
        ew(ehdrSize + 20, code.size.toUInt())
        ew(ehdrSize + 24, 5u)
        ew(ehdrSize + 28, 0x1000u)

        fun sw(
            si: Int,
            off: Int,
            v: UInt,
        ) = ew(phdrsEnd + si * shdrSize + off, v)

        sw(0, 0, 0u)
        sw(1, 0, 15u)
        sw(1, 4, ElfConstants.SHT_PROGBITS.toUInt())
        sw(1, 8, (ElfConstants.SHF_ALLOC or ElfConstants.SHF_EXECINSTR).toUInt())
        sw(1, 12, codeVaddr)
        sw(1, 16, codeFileOff.toUInt())
        sw(1, 20, code.size.toUInt())
        code.copyInto(bytes, e + codeFileOff)

        sw(2, 0, 1u)
        sw(2, 4, ElfConstants.SHT_PROGBITS.toUInt())
        sw(2, 8, ElfConstants.SHF_ALLOC.toUInt())
        sw(2, 12, stubVaddr)
        sw(2, 16, stubFileOff.toUInt())
        sw(2, 20, 8u)
        w(e + stubFileOff, 0u)
        w(e + stubFileOff + 4, nidExitGame.toUInt())

        sw(3, 0, 23u)
        sw(3, 4, ElfConstants.SHT_STRTAB.toUInt())
        sw(3, 16, strtabFileOff.toUInt())
        sw(3, 20, strtab.size.toUInt())
        strtab.copyInto(bytes, e + strtabFileOff)

        bytes[0] = '~'.code.toByte()
        bytes[1] = 'P'.code.toByte()
        bytes[2] = 'S'.code.toByte()
        bytes[3] = 'P'.code.toByte()
        w(0x04, 0x1000u)
        w(0x06, PspHeaderConstants.COMPRESSION_PLAIN.toUInt())
        "homebrew".encodeToByteArray().copyInto(bytes, 0x0A)
        bytes[0x12] = 0
        bytes[0x26] = 1
        bytes[0x27] = 1
        w(0x28, elfSize.toUInt())
        w(0x2C, elfSize.toUInt())
        w(0x30, 0u)
        w(0x34, 0x10000u)
        w(0x38, 0u)
        w(0x44, codeVaddr)
        w(0x54, code.size.toUInt())
        w(0x78, 0x06050010u)
        w(0x7C, 0u)

        return bytes
    }

    "minimal program exits via direct syscall" {
        val mem = MemoryBus()
        val cpu = Cpu(mem)
        val kernel = Kernel(mem, cpu)
        kernel.init()
        cpu.kernel = kernel

        val base = 0x08800000u
        mem.write32(Address(base), 0x3C020557)
        mem.write32(Address(base + 4u), 0x34422A5F)
        mem.write32(Address(base + 8u), 0x0000000C)
        mem.write32(Address(base + 12u), 0x00000000)

        cpu.state.pc = Address(base)
        cpu.state.setGpr(29, 0x08800000 + 0x10000)

        var steps = 0
        while (!kernel.isExitRequested() && steps < 10) {
            cpu.step()
            steps++
        }

        kernel.isExitRequested() shouldBe true
    }

    "module loader with PRX works end-to-end" {
        val bytes = buildHomebrewPrx()
        val mem = MemoryBus()
        val cpu = Cpu(mem)
        val kernel = Kernel(mem, cpu)
        kernel.init()
        cpu.kernel = kernel

        val moduleLoader = ModuleLoader()
        val result = moduleLoader.loadAndResolve(bytes, mem, kernel)
        if (!result.isSuccess) {
            val error = result.exceptionOrNull()
            throw RuntimeException("PRX load failed: $error", error)
        }

        val image = result.getOrThrow()
        image.isPrx shouldBe true
        image.imports shouldHaveSize 1
        image.imports[0].nid shouldBe Nids.EXIT_GAME

        val firstWord = mem.read32(image.entryPoint)
        firstWord shouldBe 0x3C020557
        cpu.state.pc shouldBe image.entryPoint

        var steps = 0
        while (!cpu.halted && !kernel.isExitRequested() && steps < 10) {
            cpu.step()
            steps++
        }

        if (!kernel.isExitRequested()) {
            val pc = cpu.state.pc
            val insn = mem.read32(pc)
            throw AssertionError(
                "CPU halted=${cpu.halted} exitRequested=${kernel.isExitRequested()} pc=$pc insn=0x${insn.toString(
                    16,
                )} v0=0x${cpu.state.gpr(2).toUInt().toString(16)}",
            )
        }

        kernel.isExitRequested() shouldBe true
    }
})
