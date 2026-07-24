package vesper.core.cpu

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import vesper.core.memory.Address
import vesper.core.memory.MemoryBus

class MultiplyDivideTest : StringSpec({

    fun cpu(): Cpu {
        val mem = MemoryBus()
        val c = Cpu(mem)
        c.state.pc = Address(0x08800000u)
        return c
    }

    fun rType(rs: Int, rt: Int, rd: Int, funct: Int): Int {
        return (rs shl 21) or (rt shl 16) or (rd shl 11) or funct
    }

    "MULT multiplies signed 32-bit" {
        val c = cpu()
        c.state.setGpr(1, 1000)
        c.state.setGpr(2, 2000)

        val insn = rType(1, 2, 0, Funct.MULT)
        OpcodeTable.dispatch(c, insn)

        c.state.lo shouldBe 2000000
        c.state.hi shouldBe 0
    }

    "MULT large result uses HI/LO" {
        val c = cpu()
        c.state.setGpr(1, 0x100000)
        c.state.setGpr(2, 0x100000)

        val insn = rType(1, 2, 0, Funct.MULT)
        OpcodeTable.dispatch(c, insn)

        c.state.lo shouldBe 0
        c.state.hi shouldBe 0x100
    }

    "MULTU unsigned multiply" {
        val c = cpu()
        c.state.setGpr(1, 0x80000000.toInt())
        c.state.setGpr(2, 2)

        val insn = rType(1, 2, 0, Funct.MULTU)
        OpcodeTable.dispatch(c, insn)

        c.state.lo shouldBe 0
        c.state.hi shouldBe 1
    }

    "DIV signed division" {
        val c = cpu()
        c.state.setGpr(1, 100)
        c.state.setGpr(2, 3)

        val insn = rType(1, 2, 0, Funct.DIV)
        OpcodeTable.dispatch(c, insn)

        c.state.lo shouldBe 33
        c.state.hi shouldBe 1
    }

    "DIV negative division" {
        val c = cpu()
        c.state.setGpr(1, -100)
        c.state.setGpr(2, 3)

        val insn = rType(1, 2, 0, Funct.DIV)
        OpcodeTable.dispatch(c, insn)

        c.state.lo shouldBe -33
        c.state.hi shouldBe -1
    }

    "DIV by zero" {
        val c = cpu()
        c.state.setGpr(1, 100)
        c.state.setGpr(2, 0)

        val insn = rType(1, 2, 0, Funct.DIV)
        OpcodeTable.dispatch(c, insn)

        c.state.lo shouldBe -1
        c.state.hi shouldBe 100
    }

    "DIVU unsigned division" {
        val c = cpu()
        c.state.setGpr(1, 0x80000000.toInt())
        c.state.setGpr(2, 2)

        val insn = rType(1, 2, 0, Funct.DIVU)
        OpcodeTable.dispatch(c, insn)

        c.state.lo shouldBe 0x40000000
        c.state.hi shouldBe 0
    }

    "MFHI moves HI to register" {
        val c = cpu()
        c.state.hi = 0xDEAD
        val insn = rType(0, 0, 1, Funct.MFHI)
        OpcodeTable.dispatch(c, insn)
        c.state.gpr(1) shouldBe 0xDEAD
    }

    "MFLO moves LO to register" {
        val c = cpu()
        c.state.lo = 0xBEEF
        val insn = rType(0, 0, 1, Funct.MFLO)
        OpcodeTable.dispatch(c, insn)
        c.state.gpr(1) shouldBe 0xBEEF
    }

    "MTHI moves register to HI" {
        val c = cpu()
        c.state.setGpr(1, 0xCAFE)
        val insn = rType(1, 0, 0, Funct.MTHI)
        OpcodeTable.dispatch(c, insn)
        c.state.hi shouldBe 0xCAFE
    }

    "MTLO moves register to LO" {
        val c = cpu()
        c.state.setGpr(1, 0xCAFE)
        val insn = rType(1, 0, 0, Funct.MTLO)
        OpcodeTable.dispatch(c, insn)
        c.state.lo shouldBe 0xCAFE
    }

    "SYSCALL raises syscall exception" {
        val c = cpu()
        val insn = rType(0, 0, 0, Funct.SYSCALL)
        OpcodeTable.dispatch(c, insn)
        c.state.exceptionPending shouldBe CpuException.Syscall
    }

    "BREAK raises breakpoint exception" {
        val c = cpu()
        val insn = rType(0, 0, 0, Funct.BREAK)
        OpcodeTable.dispatch(c, insn)
        c.state.exceptionPending shouldBe CpuException.Breakpoint
    }
})
