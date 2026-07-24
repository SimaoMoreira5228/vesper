package vesper.core.cpu

import vesper.core.cpu.Cpu.Companion.logTag
import vesper.common.warn

object Arithmetic {

    fun executeAdd(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val rt = cpu.state.gpr(instructionRt(insn))
        val rd = instructionRd(insn)
        val result = rs.toLong() + rt.toLong()
        if (result != (result.toInt()).toLong()) {
            cpu.raiseException(CpuException.ArithmeticOverflow)
            return
        }
        cpu.state.setGpr(rd, result.toInt())
    }

    fun executeAddu(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val rt = cpu.state.gpr(instructionRt(insn))
        cpu.state.setGpr(instructionRd(insn), rs + rt)
    }

    fun executeSub(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val rt = cpu.state.gpr(instructionRt(insn))
        val result = rs.toLong() - rt.toLong()
        if (result != (result.toInt()).toLong()) {
            cpu.raiseException(CpuException.ArithmeticOverflow)
            return
        }
        cpu.state.setGpr(instructionRd(insn), result.toInt())
    }

    fun executeSubu(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val rt = cpu.state.gpr(instructionRt(insn))
        cpu.state.setGpr(instructionRd(insn), rs - rt)
    }

    fun executeAddi(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val imm = instructionImmediateSigned(insn)
        val result = rs.toLong() + imm.toLong()
        if (result != (result.toInt()).toLong()) {
            cpu.raiseException(CpuException.ArithmeticOverflow)
            return
        }
        cpu.state.setGpr(instructionRt(insn), result.toInt())
    }

    fun executeAddiu(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val imm = instructionImmediateSigned(insn)
        cpu.state.setGpr(instructionRt(insn), rs + imm)
    }

    fun executeSlt(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val rt = cpu.state.gpr(instructionRt(insn))
        cpu.state.setGpr(instructionRd(insn), if (rs < rt) 1 else 0)
    }

    fun executeSltu(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn)).toUInt()
        val rt = cpu.state.gpr(instructionRt(insn)).toUInt()
        cpu.state.setGpr(instructionRd(insn), if (rs < rt) 1 else 0)
    }

    fun executeSlti(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val imm = instructionImmediateSigned(insn)
        cpu.state.setGpr(instructionRt(insn), if (rs < imm) 1 else 0)
    }

    fun executeSltiu(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn)).toUInt()
        val imm = instructionImmediateSigned(insn).toUInt()
        cpu.state.setGpr(instructionRt(insn), if (rs < imm) 1 else 0)
    }

    fun executeAnd(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val rt = cpu.state.gpr(instructionRt(insn))
        cpu.state.setGpr(instructionRd(insn), rs and rt)
    }

    fun executeOr(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val rt = cpu.state.gpr(instructionRt(insn))
        cpu.state.setGpr(instructionRd(insn), rs or rt)
    }

    fun executeXor(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val rt = cpu.state.gpr(instructionRt(insn))
        cpu.state.setGpr(instructionRd(insn), rs xor rt)
    }

    fun executeNor(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val rt = cpu.state.gpr(instructionRt(insn))
        cpu.state.setGpr(instructionRd(insn), (rs or rt).inv())
    }

    fun executeAndi(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val imm = instructionImmediate(insn)
        cpu.state.setGpr(instructionRt(insn), rs and imm)
    }

    fun executeOri(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val imm = instructionImmediate(insn)
        cpu.state.setGpr(instructionRt(insn), rs or imm)
    }

    fun executeXori(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val imm = instructionImmediate(insn)
        cpu.state.setGpr(instructionRt(insn), rs xor imm)
    }

    fun executeLui(cpu: Cpu, insn: Int) {
        val imm = instructionImmediate(insn)
        cpu.state.setGpr(instructionRt(insn), imm shl 16)
    }

    fun executeMovz(cpu: Cpu, insn: Int) {
        val rt = cpu.state.gpr(instructionRt(insn))
        if (rt == 0) {
            val rs = cpu.state.gpr(instructionRs(insn))
            cpu.state.setGpr(instructionRd(insn), rs)
        }
    }

    fun executeMovn(cpu: Cpu, insn: Int) {
        val rt = cpu.state.gpr(instructionRt(insn))
        if (rt != 0) {
            val rs = cpu.state.gpr(instructionRs(insn))
            cpu.state.setGpr(instructionRd(insn), rs)
        }
    }

    fun executeSll(cpu: Cpu, insn: Int) {
        val rt = cpu.state.gpr(instructionRt(insn))
        val shamt = instructionShamt(insn)
        cpu.state.setGpr(instructionRd(insn), rt shl shamt)
    }

    fun executeSrl(cpu: Cpu, insn: Int) {
        val rt = cpu.state.gpr(instructionRt(insn))
        val shamt = instructionShamt(insn)
        cpu.state.setGpr(instructionRd(insn), rt ushr shamt)
    }

    fun executeSra(cpu: Cpu, insn: Int) {
        val rt = cpu.state.gpr(instructionRt(insn))
        val shamt = instructionShamt(insn)
        cpu.state.setGpr(instructionRd(insn), rt shr shamt)
    }

    fun executeSllv(cpu: Cpu, insn: Int) {
        val rt = cpu.state.gpr(instructionRt(insn))
        val rs = cpu.state.gpr(instructionRs(insn))
        cpu.state.setGpr(instructionRd(insn), rt shl (rs and 0x1F))
    }

    fun executeSrlv(cpu: Cpu, insn: Int) {
        val rt = cpu.state.gpr(instructionRt(insn))
        val rs = cpu.state.gpr(instructionRs(insn))
        cpu.state.setGpr(instructionRd(insn), rt ushr (rs and 0x1F))
    }

    fun executeSrav(cpu: Cpu, insn: Int) {
        val rt = cpu.state.gpr(instructionRt(insn))
        val rs = cpu.state.gpr(instructionRs(insn))
        cpu.state.setGpr(instructionRd(insn), rt shr (rs and 0x1F))
    }
}
