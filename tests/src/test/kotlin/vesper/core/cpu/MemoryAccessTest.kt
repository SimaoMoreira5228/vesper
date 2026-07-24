package vesper.core.cpu

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import vesper.core.memory.Address
import vesper.core.memory.MemoryBus

class MemoryAccessTest : StringSpec({

    fun cpu(init: Cpu.() -> Unit = {}): Cpu {
        val mem = MemoryBus()
        val c = Cpu(mem)
        c.state.pc = Address(0x08800000u)
        c.init()
        return c
    }

    fun cpuWithMem(init: MemoryBus.() -> Unit = {}): Cpu {
        val mem = MemoryBus()
        mem.init()
        val c = Cpu(mem)
        c.state.pc = Address(0x08800000u)
        return c
    }

    fun iType(opcode: Int, rs: Int, rt: Int, imm: Int): Int {
        return (opcode shl 26) or (rs shl 21) or (rt shl 16) or (imm and 0xFFFF)
    }

    "LW loads word from memory" {
        val c = cpuWithMem { write32(Address(0x08800100u), 0xDEADBEEF.toInt()) }
        c.state.setGpr(1, 0x08800100)
        OpcodeTable.dispatch(c, iType(Opcode.LW, 1, 2, 0))
        c.state.gpr(2) shouldBe 0xDEADBEEF.toInt()
    }

    "LW with immediate offset" {
        val c = cpuWithMem { write32(Address(0x08800100u), 0xCAFEBABE.toInt()) }
        c.state.setGpr(1, 0x088000FC)
        OpcodeTable.dispatch(c, iType(Opcode.LW, 1, 2, 4))
        c.state.gpr(2) shouldBe 0xCAFEBABE.toInt()
    }

    "SW stores word to memory" {
        val c = cpu()
        c.state.setGpr(1, 0x08800100)
        c.state.setGpr(2, 0xA5A5A5A5.toInt())
        OpcodeTable.dispatch(c, iType(Opcode.SW, 1, 2, 0))
        c.memory.read32(Address(0x08800100u)) shouldBe 0xA5A5A5A5.toInt()
    }

    "LB loads byte with sign extension" {
        val c = cpuWithMem { write8(Address(0x08800100u), 0xFA) }
        c.state.setGpr(1, 0x08800100)
        OpcodeTable.dispatch(c, iType(Opcode.LB, 1, 2, 0))
        c.state.gpr(2) shouldBe 0xFFFFFFFA.toInt()
    }

    "LBU loads byte without sign extension" {
        val c = cpuWithMem { write8(Address(0x08800100u), 0xFA) }
        c.state.setGpr(1, 0x08800100)
        OpcodeTable.dispatch(c, iType(Opcode.LBU, 1, 2, 0))
        c.state.gpr(2) shouldBe 0xFA
    }

    "LH loads half with sign extension" {
        val c = cpuWithMem { write16(Address(0x08800100u), 0xFAFA) }
        c.state.setGpr(1, 0x08800100)
        OpcodeTable.dispatch(c, iType(Opcode.LH, 1, 2, 0))
        c.state.gpr(2) shouldBe 0xFFFFFAFA.toInt()
    }

    "LHU loads half without sign extension" {
        val c = cpuWithMem { write16(Address(0x08800100u), 0xFAFA) }
        c.state.setGpr(1, 0x08800100)
        OpcodeTable.dispatch(c, iType(Opcode.LHU, 1, 2, 0))
        c.state.gpr(2) shouldBe 0xFAFA
    }

    "SB stores byte" {
        val c = cpu()
        c.state.setGpr(1, 0x08800100)
        c.state.setGpr(2, 0xA5)
        OpcodeTable.dispatch(c, iType(Opcode.SB, 1, 2, 0))
        c.memory.read8(Address(0x08800100u)) shouldBe 0xA5
    }

    "SH stores halfword" {
        val c = cpu()
        c.state.setGpr(1, 0x08800100)
        c.state.setGpr(2, 0xA5A5)
        OpcodeTable.dispatch(c, iType(Opcode.SH, 1, 2, 0))
        c.memory.read16(Address(0x08800100u)) shouldBe 0xA5A5
    }

    "LWL loads word left (unaligned)" {
        val c = cpuWithMem { write32(Address(0x08800100u), 0xAABBCCDD.toInt()) }
        c.state.setGpr(1, 0x08800102)
        c.state.setGpr(2, 0)

        OpcodeTable.dispatch(c, iType(Opcode.LWL, 1, 2, 0))
        c.state.gpr(2) shouldBe 0xAABB0000.toInt()
    }

    "LWR loads word right (unaligned)" {
        val c = cpuWithMem { write32(Address(0x08800100u), 0xAABBCCDD.toInt()) }
        c.state.setGpr(1, 0x08800102)
        c.state.setGpr(2, 0)

        OpcodeTable.dispatch(c, iType(Opcode.LWR, 1, 2, 0))
        c.state.gpr(2) shouldBe 0x0000CCDD
    }

    "SWL + SWR stores unaligned word" {
        val c = cpu()
        c.state.setGpr(1, 0x08800102)
        c.state.setGpr(2, 0xAABBCCDD.toInt())

        OpcodeTable.dispatch(c, iType(Opcode.SWL, 1, 2, 0))
        OpcodeTable.dispatch(c, iType(Opcode.SWR, 1, 2, 0))

        c.memory.read32(Address(0x08800100u)) shouldBe 0xAABBCCDD.toInt()
    }
})
