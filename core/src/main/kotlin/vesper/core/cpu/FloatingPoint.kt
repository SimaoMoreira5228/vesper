package vesper.core.cpu

import vesper.core.memory.Address
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

object FloatingPoint {
    private const val CONDITION_BIT = 1 shl 23
    private const val FLUSH_TO_ZERO_BIT = 1 shl 24

    fun executeLoad(cpu: Cpu, insn: Int) {
        val address = effectiveAddress(cpu, insn)
        cpu.state.fpr[instructionRt(insn)] = Float.fromBits(cpu.memory.read32(address))
    }

    fun executeStore(cpu: Cpu, insn: Int) {
        val address = effectiveAddress(cpu, insn)
        cpu.memory.write32(address, cpu.state.fpr[instructionRt(insn)].toRawBits())
    }

    fun executeCop1(cpu: Cpu, insn: Int) {
        when (instructionRs(insn)) {
            0 -> cpu.state.setGpr(instructionRt(insn), cpu.state.fpr[instructionRd(insn)].toRawBits())
            2 -> cpu.state.setGpr(instructionRt(insn), cpu.state.fcr[instructionRd(insn)])
            4 -> cpu.state.fpr[instructionRd(insn)] = Float.fromBits(cpu.state.gpr(instructionRt(insn)))
            6 -> {
                val register = instructionRd(insn)
                if (register != 0) cpu.state.fcr[register] = cpu.state.gpr(instructionRt(insn))
            }
            8 -> executeBranch(cpu, insn)
            16 -> executeSingle(cpu, insn)
            else -> cpu.raiseException(CpuException.ReservedInstruction)
        }
    }

    private fun executeSingle(cpu: Cpu, insn: Int) {
        val funct = instructionFunct(insn)
        val fs = cpu.state.fpr[instructionRd(insn)]
        val ft = cpu.state.fpr[instructionRt(insn)]
        val fd = instructionShamt(insn)
        if (funct in 0x30..0x3F) {
            setCondition(cpu, compare(funct - 0x30, fs, ft))
            return
        }
        val result = when (funct) {
            0x00 -> fs + ft
            0x01 -> fs - ft
            0x02 -> fs * ft
            0x03 -> fs / ft
            0x04 -> sqrt(fs)
            0x05 -> kotlin.math.abs(fs)
            0x06 -> fs
            0x07 -> -fs
            0x0C -> Float.fromBits(roundedWord(cpu, fs, Rounding.NEAREST))
            0x0D -> Float.fromBits(roundedWord(cpu, fs, Rounding.TRUNCATE))
            0x0E -> Float.fromBits(roundedWord(cpu, fs, Rounding.CEIL))
            0x0F -> Float.fromBits(roundedWord(cpu, fs, Rounding.FLOOR))
            0x20 -> fs
            0x24 -> Float.fromBits(roundedWord(cpu, fs, currentRounding(cpu)))
            else -> {
                cpu.raiseException(CpuException.ReservedInstruction)
                return
            }
        }
        cpu.state.fpr[fd] = flush(result, cpu.state.fcr[31])
    }

    private fun executeBranch(cpu: Cpu, insn: Int) {
        val condition = (cpu.state.fcr[31] and CONDITION_BIT) != 0
        val branchType = instructionRt(insn)
        val likely = (branchType and 2) != 0
        val take = if ((branchType and 1) != 0) condition else !condition
        if (take) {
            cpu.state.nextPc = cpu.state.pc + (instructionImmediateSigned(insn) shl 2)
            cpu.state.inDelaySlot = true
        } else if (likely) {
            cpu.state.pc += Address(4u)
        }
    }

    private fun effectiveAddress(cpu: Cpu, insn: Int) =
        Address((cpu.state.gpr(instructionRs(insn)) + instructionImmediateSigned(insn)).toUInt())

    private fun setCondition(cpu: Cpu, value: Boolean) {
        cpu.state.fcr[31] = if (value) cpu.state.fcr[31] or CONDITION_BIT
        else cpu.state.fcr[31] and CONDITION_BIT.inv()
    }

    private fun compare(code: Int, left: Float, right: Float): Boolean {
        val unordered = left.isNaN() || right.isNaN()
        return when (code) {
            0, 8 -> false
            1, 9 -> unordered
            2, 10 -> !unordered && left == right
            3, 11 -> unordered || (!unordered && left == right)
            4, 12 -> !unordered && left < right
            5, 13 -> unordered || (!unordered && left < right)
            6, 14 -> !unordered && left <= right
            7, 15 -> unordered || (!unordered && left <= right)
            else -> false
        }
    }

    private enum class Rounding { NEAREST, TRUNCATE, CEIL, FLOOR }

    private fun currentRounding(cpu: Cpu) = when (cpu.state.fcr[31] and 3) {
        1 -> Rounding.TRUNCATE
        2 -> Rounding.CEIL
        3 -> Rounding.FLOOR
        else -> Rounding.NEAREST
    }

    private fun roundedWord(cpu: Cpu, value: Float, mode: Rounding): Int {
        if (value.isNaN()) return Int.MAX_VALUE
        if (value == Float.POSITIVE_INFINITY) return Int.MAX_VALUE
        if (value == Float.NEGATIVE_INFINITY) return Int.MIN_VALUE
        val rounded = when (mode) {
            Rounding.NEAREST -> Math.rint(value.toDouble())
            Rounding.TRUNCATE -> value.toDouble().toInt().toDouble()
            Rounding.CEIL -> ceil(value.toDouble())
            Rounding.FLOOR -> floor(value.toDouble())
        }
        return rounded.coerceIn(Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble()).toInt()
    }

    private fun flush(value: Float, fcr31: Int): Float =
        if (fcr31 and FLUSH_TO_ZERO_BIT != 0 && value != 0f && abs(value) < 1.17549435E-38f)
            Math.copySign(0f, value)
        else value
}
