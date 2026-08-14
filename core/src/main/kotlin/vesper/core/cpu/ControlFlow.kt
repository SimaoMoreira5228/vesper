package vesper.core.cpu

import vesper.core.memory.Address

object ControlFlow {

    private fun branchTarget(cpu: Cpu, insn: Int): Address {
        val offset = instructionImmediateSigned(insn) shl 2
        return cpu.state.pc + offset
    }

    private fun takeBranch(cpu: Cpu, target: Address) {
        cpu.state.nextPc = target
        cpu.state.inDelaySlot = true
    }

    private fun branchLikely(cpu: Cpu, condition: Boolean, insn: Int) {
        if (condition) {
            takeBranch(cpu, branchTarget(cpu, insn))
        } else {
            cpu.state.pc += Address(4u)
        }
    }

    fun executeBeq(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val rt = cpu.state.gpr(instructionRt(insn))
        if (rs == rt) takeBranch(cpu, branchTarget(cpu, insn))
    }

    fun executeBne(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val rt = cpu.state.gpr(instructionRt(insn))
        if (rs != rt) takeBranch(cpu, branchTarget(cpu, insn))
    }

    fun executeBlez(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        if (rs <= 0) takeBranch(cpu, branchTarget(cpu, insn))
    }

    fun executeBgtz(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        if (rs > 0) takeBranch(cpu, branchTarget(cpu, insn))
    }

    fun executeBeql(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val rt = cpu.state.gpr(instructionRt(insn))
        branchLikely(cpu, rs == rt, insn)
    }

    fun executeBnel(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val rt = cpu.state.gpr(instructionRt(insn))
        branchLikely(cpu, rs != rt, insn)
    }

    fun executeBlezl(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        branchLikely(cpu, rs <= 0, insn)
    }

    fun executeBgtzl(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        branchLikely(cpu, rs > 0, insn)
    }

    fun executeBltz(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        if (rs < 0) takeBranch(cpu, branchTarget(cpu, insn))
    }

    fun executeBgez(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        if (rs >= 0) takeBranch(cpu, branchTarget(cpu, insn))
    }

    fun executeBltzl(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        branchLikely(cpu, rs < 0, insn)
    }

    fun executeBgezl(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        branchLikely(cpu, rs >= 0, insn)
    }

    fun executeBltzal(cpu: Cpu, insn: Int) {
        cpu.state.setGpr(31, (cpu.state.pc.value + 4u).toInt())
        val rs = cpu.state.gpr(instructionRs(insn))
        if (rs < 0) takeBranch(cpu, branchTarget(cpu, insn))
    }

    fun executeBgezal(cpu: Cpu, insn: Int) {
        cpu.state.setGpr(31, (cpu.state.pc.value + 4u).toInt())
        val rs = cpu.state.gpr(instructionRs(insn))
        if (rs >= 0) takeBranch(cpu, branchTarget(cpu, insn))
    }

    fun executeBltzall(cpu: Cpu, insn: Int) {
        cpu.state.setGpr(31, (cpu.state.pc.value + 4u).toInt())
        val rs = cpu.state.gpr(instructionRs(insn))
        branchLikely(cpu, rs < 0, insn)
    }

    fun executeBgezall(cpu: Cpu, insn: Int) {
        cpu.state.setGpr(31, (cpu.state.pc.value + 4u).toInt())
        val rs = cpu.state.gpr(instructionRs(insn))
        branchLikely(cpu, rs >= 0, insn)
    }

    fun executeJ(cpu: Cpu, insn: Int) {
        val target = instructionTarget(insn)
        val newPc = (cpu.state.pc.value and 0xF0000000u) or (target.toUInt() shl 2)
        takeBranch(cpu, Address(newPc))
    }

    fun executeJal(cpu: Cpu, insn: Int) {
        cpu.state.setGpr(31, (cpu.state.pc.value + 4u).toInt())
        val target = instructionTarget(insn)
        val newPc = (cpu.state.pc.value and 0xF0000000u) or (target.toUInt() shl 2)
        takeBranch(cpu, Address(newPc))
    }

    fun executeJr(cpu: Cpu, insn: Int) {
        val rs = cpu.state.gpr(instructionRs(insn))
        takeBranch(cpu, Address(rs.toUInt()))
    }

    fun executeJalr(cpu: Cpu, insn: Int) {
        val rd = instructionRd(insn)
        val rs = cpu.state.gpr(instructionRs(insn))
        cpu.state.setGpr(rd, (cpu.state.pc.value + 4u).toInt())
        takeBranch(cpu, Address(rs.toUInt()))
    }
}
