package vesper.core.cpu

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import vesper.core.memory.Address
import vesper.core.memory.MemoryBus

class FloatingPointTest : StringSpec({

    fun single(
        ft: Int,
        fs: Int,
        fd: Int,
        funct: Int,
    ): Int = (Opcode.COP1 shl 26) or (16 shl 21) or (ft shl 16) or (fs shl 11) or (fd shl 6) or funct

    fun cop1(
        rs: Int,
        rt: Int = 0,
        rd: Int = 0,
        fd: Int = 0,
        funct: Int = 0,
    ): Int = (Opcode.COP1 shl 26) or (rs shl 21) or (rt shl 16) or (rd shl 11) or (fd shl 6) or funct

    fun multiply(roundingMode: Int): Float {
        val cpu = Cpu(MemoryBus())
        cpu.state.fcr[31] = roundingMode
        cpu.state.fpr[1] = 0.2965576648712158203125f
        cpu.state.fpr[2] = 62.0f

        OpcodeTable.dispatch(cpu, single(ft = 2, fs = 1, fd = 3, funct = 0x02))

        return cpu.state.fpr[3]
    }

    "single precision multiply follows FCR31 rounding mode" {
        val nearest = multiply(0)
        val towardZero = multiply(1)
        val towardPositive = multiply(2)
        val towardNegative = multiply(3)

        towardZero shouldBe Math.nextDown(nearest)
        towardPositive shouldBe nearest
        towardNegative shouldBe towardZero
    }

    "exact arithmetic does not set the inexact flag" {
        val cpu = Cpu(MemoryBus())
        cpu.state.fpr[1] = 1.0f
        cpu.state.fpr[2] = 2.0f

        OpcodeTable.dispatch(cpu, single(ft = 2, fs = 1, fd = 3, funct = 0x02))

        cpu.state.fpr[3] shouldBe 2.0f
        cpu.state.fcr[31] shouldBe 0
    }

    "MTC1 and MFC1 preserve raw register bits" {
        val cpu = Cpu(MemoryBus())
        val bits = 0xFFC12345u.toInt()
        cpu.state.setGpr(4, bits)

        OpcodeTable.dispatch(cpu, cop1(rs = 4, rt = 4, rd = 7))
        OpcodeTable.dispatch(cpu, cop1(rs = 0, rt = 5, rd = 7))

        cpu.state.fpr[7].toRawBits() shouldBe bits
        cpu.state.gpr(5) shouldBe bits
    }

    "only defined FCR registers can be read or written" {
        val cpu = Cpu(MemoryBus())
        cpu.state.setGpr(4, -1)

        OpcodeTable.dispatch(cpu, cop1(rs = 6, rt = 4, rd = 25))
        OpcodeTable.dispatch(cpu, cop1(rs = 2, rt = 5, rd = 25))
        OpcodeTable.dispatch(cpu, cop1(rs = 6, rt = 4, rd = 31))
        OpcodeTable.dispatch(cpu, cop1(rs = 2, rt = 6, rd = 31))

        cpu.state.gpr(5) shouldBe 0
        cpu.state.gpr(6) shouldBe 0x0181FFFF
    }

    "word conversion opcodes store integer bits in the FPR" {
        val cpu = Cpu(MemoryBus())
        cpu.state.fpr[1] = 1.5f

        OpcodeTable.dispatch(cpu, single(ft = 0, fs = 1, fd = 2, funct = 0x0C))
        OpcodeTable.dispatch(cpu, single(ft = 0, fs = 1, fd = 3, funct = 0x0D))
        OpcodeTable.dispatch(cpu, single(ft = 0, fs = 1, fd = 4, funct = 0x0E))
        OpcodeTable.dispatch(cpu, single(ft = 0, fs = 1, fd = 5, funct = 0x0F))

        cpu.state.fpr[2].toRawBits() shouldBe 2
        cpu.state.fpr[3].toRawBits() shouldBe 1
        cpu.state.fpr[4].toRawBits() shouldBe 2
        cpu.state.fpr[5].toRawBits() shouldBe 1
    }

    "CVT S W obeys directed rounding for large integers" {
        val towardPositive = Cpu(MemoryBus())
        towardPositive.state.fcr[31] = 2
        towardPositive.state.fpr[1] = Float.fromBits(16_777_217)
        OpcodeTable.dispatch(towardPositive, cop1(rs = 20, rd = 1, fd = 2, funct = 0x20))

        val towardZero = Cpu(MemoryBus())
        towardZero.state.fcr[31] = 1
        towardZero.state.fpr[1] = Float.fromBits(16_777_217)
        OpcodeTable.dispatch(towardZero, cop1(rs = 20, rd = 1, fd = 2, funct = 0x20))

        towardPositive.state.fpr[2] shouldBe 16_777_218f
        towardZero.state.fpr[2] shouldBe 16_777_216f
    }

    "unary operations ignore the unused ft register" {
        val cpu = Cpu(MemoryBus())
        cpu.state.fpr[0] = Float.NaN
        cpu.state.fpr[1] = -2.0f

        OpcodeTable.dispatch(cpu, single(ft = 0, fs = 1, fd = 2, funct = 0x05))

        cpu.state.fpr[2] shouldBe 2.0f
        cpu.state.fcr[31] shouldBe 0
    }

    "word conversions report inexact and invalid operations" {
        val cpu = Cpu(MemoryBus())
        cpu.state.fpr[1] = 1.5f
        OpcodeTable.dispatch(cpu, single(ft = 0, fs = 1, fd = 2, funct = 0x0D))
        cpu.state.fcr[31] and 0x00001004 shouldBe 0x00001004

        cpu.state.fcr[31] = 0
        cpu.state.fpr[1] = Float.NaN
        OpcodeTable.dispatch(cpu, single(ft = 0, fs = 1, fd = 2, funct = 0x24))
        cpu.state.fcr[31] and 0x00010040 shouldBe 0x00010040
    }

    "COP1 compare drives BC1T and executes its delay slot" {
        val cpu = Cpu(MemoryBus())
        cpu.state.pc = Address(0x08800000u)
        cpu.state.fpr[1] = 3.0f
        cpu.state.fpr[2] = 3.0f

        OpcodeTable.dispatch(cpu, single(ft = 2, fs = 1, fd = 0, funct = 0x32))
        OpcodeTable.dispatch(cpu, cop1(rs = 8, rt = 1) or 2)

        cpu.state.fcr[31] and (1 shl 23) shouldBe (1 shl 23)
        cpu.state.inDelaySlot shouldBe true
        cpu.state.nextPc shouldBe Address(0x0880000Cu)
    }

    "scalar COP1 instructions have useful disassembly" {
        val mul = single(ft = 2, fs = 1, fd = 3, funct = 0x02)
        val compare = single(ft = 4, fs = 3, fd = 0, funct = 0x3E)
        val convert = cop1(rs = 20, rd = 5, fd = 6, funct = 0x20)

        disassemble(0x08800000, mul) shouldBe "MUL.S \$f3, \$f1, \$f2"
        disassemble(0x08800000, compare) shouldBe "C.LE.S \$f3, \$f4"
        disassemble(0x08800000, convert) shouldBe "CVT.S.W \$f6, \$f5"
    }
})
