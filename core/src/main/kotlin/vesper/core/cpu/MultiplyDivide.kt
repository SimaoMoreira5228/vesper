package vesper.core.cpu

import vesper.common.Logger
import vesper.common.warn
import vesper.core.cpu.Cpu.Companion.logTag

object MultiplyDivide {

    fun executeMult(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn)).toLong()
        val rt = cpu.state.gpr(instructionRt(insn)).toLong()
        val result = rs * rt
        cpu.state.lo = result.toInt()
        cpu.state.hi = (result shr 32).toInt()
    }

    fun executeMultu(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn)).toUInt().toLong()
        val rt = cpu.state.gpr(instructionRt(insn)).toUInt().toLong()
        val result = rs * rt
        cpu.state.lo = result.toInt()
        cpu.state.hi = (result shr 32).toInt()
    }

    fun executeDiv(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val rt = cpu.state.gpr(instructionRt(insn))
        if (rt == 0) {
            cpu.state.lo = if (rs >= 0) -1 else 1
            cpu.state.hi = rs
        } else if (rs == Int.MIN_VALUE && rt == -1) {
            cpu.state.lo = Int.MIN_VALUE
            cpu.state.hi = 0
        } else {
            cpu.state.lo = rs / rt
            cpu.state.hi = rs % rt
        }
    }

    fun executeDivu(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn)).toUInt()
        val rt = cpu.state.gpr(instructionRt(insn)).toUInt()
        if (rt == 0u) {
            cpu.state.lo = (-1).toInt()
            cpu.state.hi = rs.toInt()
        } else {
            cpu.state.lo = (rs / rt).toInt()
            cpu.state.hi = (rs % rt).toInt()
        }
    }

    fun executeMfhi(cpu: Cpu, insn: Int) {
        cpu.state.setGpr(instructionRd(insn), cpu.state.hi)
    }

    fun executeMflo(cpu: Cpu, insn: Int) {
        cpu.state.setGpr(instructionRd(insn), cpu.state.lo)
    }

    fun executeMthi(cpu: Cpu, insn: Int) {
        cpu.state.hi = cpu.state.gpr(instructionRs(insn))
    }

    fun executeMtlo(cpu: Cpu, insn: Int) {
        cpu.state.lo = cpu.state.gpr(instructionRs(insn))
    }

    fun executeSyscall(cpu: Cpu, insn: Int) {
        cpu.raiseException(CpuException.Syscall)
    }

    fun executeBreak(cpu: Cpu, insn: Int) {
        cpu.raiseException(CpuException.Breakpoint)
    }

    fun executeMul(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn)).toLong()
        val rt = cpu.state.gpr(instructionRt(insn)).toLong()
        cpu.state.setGpr(instructionRd(insn), (rs * rt).toInt())
    }

    fun executeMadd(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn)).toLong()
        val rt = cpu.state.gpr(instructionRt(insn)).toLong()
        val acc = (cpu.state.hi.toUInt().toLong() shl 32) or (cpu.state.lo.toUInt().toLong())
        val result = acc + rs * rt
        cpu.state.lo = result.toInt()
        cpu.state.hi = (result shr 32).toInt()
    }

    fun executeMaddu(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn)).toUInt().toLong()
        val rt = cpu.state.gpr(instructionRt(insn)).toUInt().toLong()
        val acc = (cpu.state.hi.toUInt().toLong() shl 32) or (cpu.state.lo.toUInt().toLong())
        val result = acc + rs * rt
        cpu.state.lo = result.toInt()
        cpu.state.hi = (result shr 32).toInt()
    }
}
