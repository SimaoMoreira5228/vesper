package vesper.core.cpu

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import vesper.core.memory.Address
import vesper.core.memory.MemoryBus

class ArithmeticTest : StringSpec({

    fun cpu(init: Cpu.() -> Unit = {}): Cpu {
        val mem = MemoryBus()
        val c = Cpu(mem)
        c.state.pc = Address(0x08800000u)
        c.init()
        return c
    }

    fun insn(
        opcode: Int,
        rs: Int,
        rt: Int,
        rd: Int,
        shamt: Int = 0,
        funct: Int = 0,
    ): Int {
        return (opcode shl 26) or (rs shl 21) or (rt shl 16) or (rd shl 11) or (shamt shl 6) or funct
    }

    fun iInsn(
        opcode: Int,
        rs: Int,
        rt: Int,
        imm: Int,
    ): Int {
        return (opcode shl 26) or (rs shl 21) or (rt shl 16) or (imm and 0xFFFF)
    }

    "ADDI adds immediate to register" {
        val c = cpu { state.setGpr(1, 5) }
        OpcodeTable.dispatch(c, iInsn(Opcode.ADDI, 1, 2, 10))
        c.state.gpr(2) shouldBe 15
    }

    "ADDI with negative immediate" {
        val c = cpu { state.setGpr(1, 5) }
        OpcodeTable.dispatch(c, iInsn(Opcode.ADDI, 1, 2, -3))
        c.state.gpr(2) shouldBe 2
    }

    "ADDIU does not trap on overflow" {
        val c = cpu { state.setGpr(1, 0x7FFFFFFF) }
        OpcodeTable.dispatch(c, iInsn(Opcode.ADDIU, 1, 2, 1))
        c.state.gpr(2) shouldBe 0x80000000.toInt()
    }

    "ADDI traps on signed overflow" {
        val c = cpu { state.setGpr(1, 0x7FFFFFFF) }
        OpcodeTable.dispatch(c, iInsn(Opcode.ADDI, 1, 2, 1))
        c.state.gpr(2) shouldBe 0
    }

    "ADDU adds without overflow check" {
        val c =
            cpu {
                state.setGpr(1, 10)
                state.setGpr(2, 20)
            }
        OpcodeTable.dispatch(c, insn(0, 1, 2, 3, funct = Funct.ADDU))
        c.state.gpr(3) shouldBe 30
    }

    "ADDU zero register is preserved" {
        val c = cpu()
        OpcodeTable.dispatch(c, iInsn(Opcode.ADDIU, 0, 1, 42))
        c.state.gpr(0) shouldBe 0
        c.state.gpr(1) shouldBe 42
    }

    "SUB subtracts correctly" {
        val c =
            cpu {
                state.setGpr(1, 20)
                state.setGpr(2, 8)
            }
        OpcodeTable.dispatch(c, insn(0, 1, 2, 3, funct = Funct.SUBU))
        c.state.gpr(3) shouldBe 12
    }

    "ANDI bitwise and" {
        val c = cpu { state.setGpr(1, 0xFF) }
        OpcodeTable.dispatch(c, iInsn(Opcode.ANDI, 1, 2, 0x0F))
        c.state.gpr(2) shouldBe 0x0F
    }

    "ORI bitwise or" {
        val c = cpu { state.setGpr(1, 0xF0) }
        OpcodeTable.dispatch(c, iInsn(Opcode.ORI, 1, 2, 0x0F))
        c.state.gpr(2) shouldBe 0xFF
    }

    "XORI bitwise xor" {
        val c = cpu { state.setGpr(1, 0xFF) }
        OpcodeTable.dispatch(c, iInsn(Opcode.XORI, 1, 2, 0xFF))
        c.state.gpr(2) shouldBe 0
    }

    "LUI loads upper immediate" {
        val c = cpu()
        OpcodeTable.dispatch(c, iInsn(Opcode.LUI, 0, 1, 0xA5A5))
        c.state.gpr(1) shouldBe 0xA5A50000.toInt()
    }

    "SLT sets less than signed" {
        val c =
            cpu {
                state.setGpr(1, 5)
                state.setGpr(2, 10)
            }
        OpcodeTable.dispatch(c, insn(0, 1, 2, 3, funct = Funct.SLT))
        c.state.gpr(3) shouldBe 1
    }

    "SLT with negative comparison" {
        val c =
            cpu {
                state.setGpr(1, -1)
                state.setGpr(2, 1)
            }
        OpcodeTable.dispatch(c, insn(0, 1, 2, 3, funct = Funct.SLT))
        c.state.gpr(3) shouldBe 1
    }

    "SLTU unsigned comparison" {
        val c =
            cpu {
                state.setGpr(1, -1)
                state.setGpr(2, 1)
            }
        OpcodeTable.dispatch(c, insn(0, 1, 2, 3, funct = Funct.SLTU))
        c.state.gpr(3) shouldBe 0
    }

    "AND register" {
        val c =
            cpu {
                state.setGpr(1, 0xFF)
                state.setGpr(2, 0x0F)
            }
        OpcodeTable.dispatch(c, insn(0, 1, 2, 3, funct = Funct.AND))
        c.state.gpr(3) shouldBe 0x0F
    }

    "OR register" {
        val c =
            cpu {
                state.setGpr(1, 0xF0)
                state.setGpr(2, 0x0F)
            }
        OpcodeTable.dispatch(c, insn(0, 1, 2, 3, funct = Funct.OR))
        c.state.gpr(3) shouldBe 0xFF
    }

    "XOR register" {
        val c =
            cpu {
                state.setGpr(1, 0xFF)
                state.setGpr(2, 0xFF)
            }
        OpcodeTable.dispatch(c, insn(0, 1, 2, 3, funct = Funct.XOR))
        c.state.gpr(3) shouldBe 0
    }

    "NOR register" {
        val c =
            cpu {
                state.setGpr(1, 0)
                state.setGpr(2, 0)
            }
        OpcodeTable.dispatch(c, insn(0, 1, 2, 3, funct = Funct.NOR))
        c.state.gpr(3) shouldBe -1
    }

    "SLL shifts left logical" {
        val c = cpu { state.setGpr(2, 1) }
        OpcodeTable.dispatch(c, insn(0, 0, 2, 3, shamt = 5, funct = Funct.SLL))
        c.state.gpr(3) shouldBe 32
    }

    "SRL shifts right logical" {
        val c = cpu { state.setGpr(2, 0x80000000.toInt()) }
        OpcodeTable.dispatch(c, insn(0, 0, 2, 3, shamt = 1, funct = Funct.SRL))
        c.state.gpr(3) shouldBe 0x40000000
    }

    "SRA shifts right arithmetic" {
        val c = cpu { state.setGpr(2, 0x80000000.toInt()) }
        OpcodeTable.dispatch(c, insn(0, 0, 2, 3, shamt = 1, funct = Funct.SRA))
        c.state.gpr(3) shouldBe 0xC0000000.toInt()
    }

    "SLLV variable shift left" {
        val c =
            cpu {
                state.setGpr(1, 5)
                state.setGpr(2, 1)
            }
        OpcodeTable.dispatch(c, insn(0, 1, 2, 3, funct = Funct.SLLV))
        c.state.gpr(3) shouldBe 32
    }

    "MOVZ conditional move when zero" {
        val c =
            cpu {
                state.setGpr(1, 42)
                state.setGpr(2, 0)
            }
        OpcodeTable.dispatch(c, insn(0, 1, 2, 3, funct = Funct.MOVZ))
        c.state.gpr(3) shouldBe 42
    }

    "MOVZ no move when non-zero" {
        val c =
            cpu {
                state.setGpr(1, 42)
                state.setGpr(2, 1)
                state.setGpr(3, 99)
            }
        OpcodeTable.dispatch(c, insn(0, 1, 2, 3, funct = Funct.MOVZ))
        c.state.gpr(3) shouldBe 99
    }

    "MOVN conditional move when non-zero" {
        val c =
            cpu {
                state.setGpr(1, 42)
                state.setGpr(2, 1)
            }
        OpcodeTable.dispatch(c, insn(0, 1, 2, 3, funct = Funct.MOVN))
        c.state.gpr(3) shouldBe 42
    }
})
