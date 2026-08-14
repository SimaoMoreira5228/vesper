package vesper.core.cpu

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import vesper.core.memory.Address
import vesper.core.memory.MemoryBus

class ControlFlowTest : StringSpec({

    fun cpu(init: Cpu.() -> Unit = {}): Cpu {
        val mem = MemoryBus()
        val c = Cpu(mem)
        c.state.pc = Address(0x08800000u)
        c.init()
        return c
    }

    fun rType(rs: Int, rt: Int, rd: Int, funct: Int): Int {
        return (rs shl 21) or (rt shl 16) or (rd shl 11) or funct
    }

    fun iType(opcode: Int, rs: Int, rt: Int, imm: Int): Int {
        return (opcode shl 26) or (rs shl 21) or (rt shl 16) or (imm and 0xFFFF)
    }

    fun jType(opcode: Int, target: Int): Int {
        return (opcode shl 26) or (target and 0x3FFFFFF)
    }

    fun writeMem(mem: MemoryBus, addr: UInt, value: Int) {
        mem.write32(Address(addr), value)
    }

    "BEQ branches when equal" {
        val c = cpu {
            state.setGpr(1, 5)
            state.setGpr(2, 5)
        }
        val startPc = c.state.pc
        OpcodeTable.dispatch(c, iType(Opcode.BEQ, 1, 2, 4))

        c.state.inDelaySlot shouldBe true
        c.state.nextPc shouldBe Address(startPc.value + 20u)
    }

    "BEQ falls through when not equal" {
        val c = cpu {
            state.setGpr(1, 5)
            state.setGpr(2, 6)
        }
        OpcodeTable.dispatch(c, iType(Opcode.BEQ, 1, 2, 4))
        c.state.inDelaySlot shouldBe false
    }

    "BNE branches when not equal" {
        val c = cpu {
            state.setGpr(1, 5)
            state.setGpr(2, 6)
        }
        val startPc = c.state.pc
        OpcodeTable.dispatch(c, iType(Opcode.BNE, 1, 2, 4))

        c.state.inDelaySlot shouldBe true
        c.state.nextPc shouldBe Address(startPc.value + 20u)
    }

    "BEQL skips its delay slot when not taken" {
        val c = cpu {
            state.setGpr(1, 1)
            state.setGpr(2, 2)
        }
        writeMem(c.memory as MemoryBus, 0x08800000u, iType(Opcode.BEQL, 1, 2, 4))
        writeMem(c.memory as MemoryBus, 0x08800004u, iType(Opcode.ADDIU, 0, 3, 1))

        c.step()

        c.state.pc shouldBe Address(0x08800008u)
        c.state.gpr(3) shouldBe 0
        c.state.inDelaySlot shouldBe false
    }

    "BLEZ branches when <= 0" {
        val c = cpu { state.setGpr(1, -1) }
        OpcodeTable.dispatch(c, iType(Opcode.BLEZ, 1, 0, 4))
        c.state.inDelaySlot shouldBe true
    }

    "BLEZ does not branch when > 0" {
        val c = cpu { state.setGpr(1, 1) }
        OpcodeTable.dispatch(c, iType(Opcode.BLEZ, 1, 0, 4))
        c.state.inDelaySlot shouldBe false
    }

    "BGTZ branches when > 0" {
        val c = cpu { state.setGpr(1, 1) }
        OpcodeTable.dispatch(c, iType(Opcode.BGTZ, 1, 0, 4))
        c.state.inDelaySlot shouldBe true
    }

    "BLTZ branches when < 0" {
        val c = cpu { state.setGpr(1, -1) }
        OpcodeTable.dispatch(c, iType(Opcode.REGIMM, 1, 0x00, 4))
        c.state.inDelaySlot shouldBe true
    }

    "BGEZ branches when >= 0" {
        val c = cpu { state.setGpr(1, 0) }
        OpcodeTable.dispatch(c, iType(Opcode.REGIMM, 1, 0x01, 4))
        c.state.inDelaySlot shouldBe true
    }

    "BLTZAL saves return address" {
        val c = cpu { state.setGpr(1, -1) }
        val startPc = c.state.pc
        OpcodeTable.dispatch(c, iType(Opcode.REGIMM, 1, 0x10, 4))
        c.state.gpr(31) shouldBe (startPc.value + 8u).toInt()
        c.state.inDelaySlot shouldBe true
    }

    "BGEZAL saves return address" {
        val c = cpu { state.setGpr(1, 0) }
        val startPc = c.state.pc
        OpcodeTable.dispatch(c, iType(Opcode.REGIMM, 1, 0x11, 4))
        c.state.gpr(31) shouldBe (startPc.value + 8u).toInt()
        c.state.inDelaySlot shouldBe true
    }

    "J jumps to target" {
        val c = cpu()
        val startPc = c.state.pc
        OpcodeTable.dispatch(c, jType(Opcode.J, 0x0220000))
        val expected = (startPc.value and 0xF0000000u) or (0x0220000u * 4u)
        c.state.inDelaySlot shouldBe true
        c.state.nextPc shouldBe Address(expected)
    }

    "JAL saves return address and jumps" {
        val c = cpu()
        val startPc = c.state.pc
        OpcodeTable.dispatch(c, jType(Opcode.JAL, 0x0220000))
        c.state.gpr(31) shouldBe (startPc.value + 8u).toInt()
        c.state.inDelaySlot shouldBe true
    }

    "JR jumps to register" {
        val c = cpu { state.setGpr(1, 0x08801000) }
        OpcodeTable.dispatch(c, rType(1, 0, 0, Funct.JR))
        c.state.inDelaySlot shouldBe true
        c.state.nextPc shouldBe Address(0x08801000u)
    }

    "JALR saves return address" {
        val c = cpu { state.setGpr(1, 0x08801000) }
        val startPc = c.state.pc
        OpcodeTable.dispatch(c, rType(1, 0, 31, Funct.JALR))
        c.state.gpr(31) shouldBe (startPc.value + 8u).toInt()
        c.state.inDelaySlot shouldBe true
        c.state.nextPc shouldBe Address(0x08801000u)
    }

    "Delay slot executes branch then commits" {
        val c = cpu {
            state.setGpr(1, 1)
            state.setGpr(2, 1)
        }
        writeMem(c.memory as MemoryBus, 0x08800000u, iType(Opcode.BEQ, 1, 2, 8))
        writeMem(c.memory as MemoryBus, 0x08800004u, 0)

        c.state.pc = Address(0x08800000u)
        c.step()
        c.state.pc shouldBe Address(0x08800004u)
        c.state.inDelaySlot shouldBe true
        c.state.nextPc shouldBe Address(0x08800024u)

        c.step()
        c.state.pc shouldBe Address(0x08800024u)
        c.state.inDelaySlot shouldBe false
    }

    "Back-to-back branch and branch" {
        val c = cpu {
            state.setGpr(1, 1)
            state.setGpr(2, 1)
        }
        c.state.pc = Address(0x08800000u)
        writeMem(c.memory as MemoryBus, 0x08800000u, iType(Opcode.BEQ, 1, 2, 8))
        writeMem(c.memory as MemoryBus, 0x08800004u, 0)

        c.step()
        c.state.pc shouldBe Address(0x08800004u)
        c.state.inDelaySlot shouldBe true
        c.step()
        c.state.pc shouldBe Address(0x08800024u)
    }
})
