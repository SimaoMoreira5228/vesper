package vesper.core.cpu

import vesper.core.memory.Address
import kotlin.math.absoluteValue

object VectorUnit {
    private const val sourcePrefix = 0
    private const val targetPrefix = 1
    private const val destinationPrefix = 2
    private const val passthroughPrefix = 0xE4

    fun executeCop2(cpu: Cpu, insn: Int) {
        cpu.raiseException(CpuException.ReservedInstruction)
    }

    fun executeMemory(cpu: Cpu, insn: Int) {
        val opcode = insn ushr 26
        val base = cpu.state.gpr(instructionRs(insn)).toUInt()
        val offset = (insn and 0xFFFC).toShort().toInt()
        val address = Address(base + offset.toUInt())
        val register = instructionRt(insn) or ((insn and 3) shl 5)

        when (opcode) {
            Opcode.LV_S -> writeScalarBits(cpu.state, register, cpu.memory.read32(address))
            Opcode.SV_S -> cpu.memory.write32(address, readScalarBits(cpu.state, register))
            Opcode.LV_Q -> {
                val values = FloatArray(4) { lane ->
                    Float.fromBits(cpu.memory.read32(address + lane * 4))
                }
                writeVector(cpu.state, register, 4, values, applyDestinationPrefix = false)
            }
            Opcode.SV_Q -> {
                val values = readVector(cpu.state, register, 4, prefixIndex = null)
                for (lane in 0 until 4) {
                    cpu.memory.write32(address + lane * 4, values[lane].toRawBits())
                }
            }
            else -> cpu.raiseException(CpuException.ReservedInstruction)
        }
    }

    fun executeVfpu5(cpu: Cpu, insn: Int) {
        val type = (insn ushr 23) and 7
        when (type) {
            0, 1 -> cpu.state.vfpuCtrl[sourcePrefix] = insn and 0xFFFFF
            2, 3 -> cpu.state.vfpuCtrl[targetPrefix] = insn and 0xFFFFF
            4, 5 -> cpu.state.vfpuCtrl[destinationPrefix] = insn and 0xFFF
            6 -> {
                writeVector(cpu.state, instructionVt(insn), 1, floatArrayOf((insn and 0xFFFF).toShort().toFloat()))
                consumePrefixes(cpu.state)
            }
            7 -> {
                writeVector(cpu.state, instructionVt(insn), 1, floatArrayOf(halfToFloat(insn and 0xFFFF)))
                consumePrefixes(cpu.state)
            }
        }
    }

    fun executeVfpu4(cpu: Cpu, insn: Int) {
        val operation = instructionRt(insn)
        if (operation != 0) {
            cpu.raiseException(CpuException.ReservedInstruction)
            return
        }
        val size = vectorSize(insn)
        val values = readVector(cpu.state, instructionVs(insn), size, sourcePrefix)
        writeVector(cpu.state, instructionVd(insn), size, values)
        consumePrefixes(cpu.state)
    }

    fun executeArithmetic(cpu: Cpu, insn: Int) {
        val opcode = insn ushr 26
        val operation = (insn ushr 23) and 7
        val size = vectorSize(insn)
        val state = cpu.state
        val source = readVector(state, instructionVs(insn), size, sourcePrefix)
        val result = FloatArray(size)

        when {
            opcode == Opcode.VFPU0 && operation <= 1 -> {
                val target = readVector(state, instructionVt(insn), size, targetPrefix)
                for (lane in 0 until size) {
                    result[lane] = if (operation == 0) source[lane] + target[lane] else source[lane] - target[lane]
                }
            }
            opcode == Opcode.VFPU1 && operation == 0 -> {
                val target = readVector(state, instructionVt(insn), size, targetPrefix)
                for (lane in 0 until size) result[lane] = source[lane] * target[lane]
            }
            opcode == Opcode.VFPU1 && operation == 1 -> {
                val target = readVector(state, instructionVt(insn), size, targetPrefix)
                var dot = 0f
                for (lane in 0 until size) dot += source[lane] * target[lane]
                result[0] = dot
                writeVector(state, instructionVd(insn), 1, result)
                consumePrefixes(state)
                return
            }
            opcode == Opcode.VFPU1 && operation == 2 -> {
                val scalar = readVector(state, instructionVt(insn), 1, targetPrefix)[0]
                for (lane in 0 until size) result[lane] = source[lane] * scalar
            }
            else -> {
                cpu.raiseException(CpuException.ReservedInstruction)
                return
            }
        }

        writeVector(state, instructionVd(insn), size, result)
        consumePrefixes(state)
    }

    fun vectorRegisters(register: Int, size: Int): IntArray {
        val matrix = (register ushr 2) and 7
        val column = register and 3
        var transpose = (register ushr 5) and 1
        val row = when (size) {
            1 -> {
                transpose = 0
                (register ushr 5) and 3
            }
            2 -> (register ushr 5) and 2
            3 -> (register ushr 6) and 1
            4 -> (register ushr 5) and 2
            else -> error("Invalid VFPU vector size: $size")
        }
        return IntArray(size) { lane ->
            if (transpose != 0) {
                matrix * 4 + ((row + lane) and 3) + column * 32
            } else {
                matrix * 4 + column + ((row + lane) and 3) * 32
            }
        }
    }

    private fun readVector(state: CpuState, register: Int, size: Int, prefixIndex: Int?): FloatArray {
        val registers = vectorRegisters(register, size)
        val raw = FloatArray(size) { state.vpr[registers[it]] }
        if (prefixIndex == null) return raw
        val prefix = state.vfpuCtrl[prefixIndex]
        return FloatArray(size) { lane ->
            val swizzle = (prefix ushr (lane * 2)) and 3
            val absolute = (prefix ushr (8 + lane)) and 1 != 0
            val constant = (prefix ushr (12 + lane)) and 1 != 0
            val negate = (prefix ushr (16 + lane)) and 1 != 0
            var value = if (constant) prefixConstant(swizzle, absolute) else raw.getOrElse(swizzle) { 0f }
            if (!constant && absolute) value = value.absoluteValue
            if (negate) -value else value
        }
    }

    private fun writeVector(
        state: CpuState,
        register: Int,
        size: Int,
        values: FloatArray,
        applyDestinationPrefix: Boolean = true,
    ) {
        val registers = vectorRegisters(register, size)
        val prefix = if (applyDestinationPrefix) state.vfpuCtrl[destinationPrefix] else 0
        for (lane in 0 until size) {
            if (applyDestinationPrefix && (prefix and (1 shl (8 + lane))) != 0) continue
            val saturation = if (applyDestinationPrefix) (prefix ushr (lane * 2)) and 3 else 0
            state.vpr[registers[lane]] = when (saturation) {
                1 -> values[lane].coerceIn(0f, 1f)
                3 -> values[lane].coerceIn(-1f, 1f)
                else -> values[lane]
            }
        }
    }

    private fun readScalarBits(state: CpuState, register: Int): Int =
        state.vpr[vectorRegisters(register, 1)[0]].toRawBits()

    private fun writeScalarBits(state: CpuState, register: Int, bits: Int) {
        state.vpr[vectorRegisters(register, 1)[0]] = Float.fromBits(bits)
    }

    private fun consumePrefixes(state: CpuState) {
        state.vfpuCtrl[sourcePrefix] = passthroughPrefix
        state.vfpuCtrl[targetPrefix] = passthroughPrefix
        state.vfpuCtrl[destinationPrefix] = 0
    }

    private fun prefixConstant(swizzle: Int, alternate: Boolean): Float = when (swizzle) {
        0 -> if (alternate) 3f else 0f
        1 -> if (alternate) 1f / 3f else 1f
        2 -> if (alternate) 1f / 4f else 2f
        else -> if (alternate) 1f / 6f else 1f / 2f
    }

    private fun vectorSize(insn: Int): Int =
        1 + ((insn ushr 7) and 1) + (((insn ushr 15) and 1) shl 1)

    private fun instructionVd(insn: Int): Int = insn and 0x7F
    private fun instructionVs(insn: Int): Int = (insn ushr 8) and 0x7F
    private fun instructionVt(insn: Int): Int = (insn ushr 16) and 0x7F

    private fun halfToFloat(value: Int): Float {
        val sign = (value ushr 15) and 1
        var exponent = (value ushr 10) and 0x1F
        var fraction = value and 0x3FF
        if (exponent == 0x1F) {
            return Float.fromBits((sign shl 31) or (0xFF shl 23) or fraction)
        }
        if (exponent == 0 && fraction == 0) return if (sign != 0) -0.0f else 0.0f
        if (exponent == 0) {
            while (fraction and 0x400 == 0) {
                fraction = fraction shl 1
                exponent--
            }
            fraction = fraction and 0x3FF
        }
        return Float.fromBits((sign shl 31) or ((exponent + 112) shl 23) or (fraction shl 13))
    }
}
