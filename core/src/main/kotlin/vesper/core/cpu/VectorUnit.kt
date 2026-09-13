package vesper.core.cpu

import vesper.core.memory.Address
import kotlin.math.absoluteValue
import kotlin.math.pow

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
        val register = instructionRt(insn) or ((insn and 1) shl 5)

        when (opcode) {
            Opcode.LV_S -> writeScalarBits(cpu.state, register, cpu.memory.read32(address))
            Opcode.SV_S -> cpu.memory.write32(address, readScalarBits(cpu.state, register))
            Opcode.LVL_Q -> {
                val lanes = vectorRegisters(register, 4)
                val laneOffset = (address.value.toInt() ushr 2) and 3
                if (insn and 2 == 0) {
                    for (i in 0..laneOffset) cpu.state.vpr[lanes[3 - i]] = Float.fromBits(cpu.memory.read32(address - i * 4))
                } else {
                    for (i in 0..(3 - laneOffset)) cpu.state.vpr[lanes[i]] = Float.fromBits(cpu.memory.read32(address + i * 4))
                }
            }
            Opcode.SVL_Q -> {
                val lanes = vectorRegisters(register, 4)
                val laneOffset = (address.value.toInt() ushr 2) and 3
                if (insn and 2 == 0) {
                    for (i in 0..laneOffset) cpu.memory.write32(address - i * 4, cpu.state.vpr[lanes[3 - i]].toRawBits())
                } else {
                    for (i in 0..(3 - laneOffset)) cpu.memory.write32(address + i * 4, cpu.state.vpr[lanes[i]].toRawBits())
                }
            }
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
        when (val selector = (insn ushr 21) and 0x1F) {
            0 -> executeVfpu4Unary(cpu, insn)
            1 -> executeVfpu7(cpu, insn)
            in 16..19 -> executeVf2i(cpu, insn, selector)
            20 -> executeVi2f(cpu, insn)
            else -> cpu.raiseException(CpuException.ReservedInstruction)
        }
    }

    private fun executeVfpu7(cpu: Cpu, insn: Int) {
        val state = cpu.state
        when (val index = instructionRt(insn)) {
            18 -> convertFloatToHalf(state, insn)
            19 -> convertHalfToFloat(state, insn)
            in 24..27 -> convertColorToInt(state, insn, index - 24)
            in 28..31 -> convertIntToColor(state, insn, index - 28)
            else -> cpu.raiseException(CpuException.ReservedInstruction)
        }
        consumePrefixes(state)
    }

    private fun convertColorToInt(state: CpuState, insn: Int, mode: Int) {
        val size = vectorSize(insn)
        val source = readVector(state, instructionVs(insn), size, sourcePrefix)
        if (mode <= 1) {
            val value = source[0].toRawBits()
            val result = FloatArray(4)
            if (mode == 1) {
                result[0] = Float.fromBits((value and 0xFF) shl 24)
                result[1] = Float.fromBits((value and 0xFF00) shl 16)
                result[2] = Float.fromBits((value and 0xFF0000) shl 8)
                result[3] = Float.fromBits(value and 0xFF000000.toInt())
            } else {
                var shifted = value
                for (lane in 0 until 4) {
                    result[lane] = Float.fromBits(((shifted and 0xFF) * 0x01010101) ushr 1)
                    shifted = shifted ushr 8
                }
            }
            writeVector(state, instructionVd(insn), 4, result)
        } else {
            val elements = if (size == 1) 1 else 2
            val result = FloatArray(4)
            for (i in 0 until elements) {
                val value = source[i].toRawBits()
                result[i * 2] = if (mode == 3) Float.fromBits((value and 0xFFFF) shl 16)
                else Float.fromBits((value and 0xFFFF) shl 15)
                result[i * 2 + 1] = if (mode == 3) Float.fromBits(value and 0xFFFF0000.toInt())
                else Float.fromBits((value and 0xFFFF0000.toInt()) ushr 1)
            }
            writeVector(state, instructionVd(insn), 4, result)
        }
    }

    private fun convertIntToColor(state: CpuState, insn: Int, mode: Int) {
        val size = vectorSize(insn)
        val source = readVector(state, instructionVs(insn), 4, null)
        if (mode <= 1) {
            var packed = 0
            for (i in 0 until 4) {
                val value = source[i].toRawBits()
                val component = if (mode == 1) value ushr 24 else (if (value < 0) 0 else value ushr 23) and 0xFF
                packed = packed or ((component and 0xFF) shl (i * 8))
            }
            writeVector(state, instructionVd(insn), 4, floatArrayOf(Float.fromBits(packed), 0f, 0f, 0f))
        } else {
            val elements = (size + 1) / 2
            val result = FloatArray(4)
            for (i in 0 until elements) {
                val low = source[i * 2].toRawBits()
                val high = source[i * 2 + 1].toRawBits()
                val packed = if (mode == 3) {
                    (low ushr 16) or ((high ushr 16) shl 16)
                } else {
                    ((if (low < 0) 0 else low ushr 15) and 0xFFFF) or
                        (((if (high < 0) 0 else high ushr 15) and 0xFFFF) shl 16)
                }
                result[i] = Float.fromBits(packed)
            }
            writeVector(state, instructionVd(insn), 4, result)
        }
    }

    private fun convertFloatToHalf(state: CpuState, insn: Int) {
        val size = vectorSize(insn)
        val source = readVector(state, instructionVs(insn), size, sourcePrefix)
        if (size <= 2) {
            val packed = (floatToHalf(source[0]) and 0xFFFF) or ((floatToHalf(source.getOrElse(1) { 0f }) and 0xFFFF) shl 16)
            writeVector(state, instructionVd(insn), 1, floatArrayOf(Float.fromBits(packed)))
        } else {
            val low = (floatToHalf(source[0]) and 0xFFFF) or ((floatToHalf(source[1]) and 0xFFFF) shl 16)
            val high = (floatToHalf(source[2]) and 0xFFFF) or ((floatToHalf(source[3]) and 0xFFFF) shl 16)
            writeVector(state, instructionVd(insn), 2, floatArrayOf(Float.fromBits(low), Float.fromBits(high)))
        }
    }

    private fun convertHalfToFloat(state: CpuState, insn: Int) {
        val size = vectorSize(insn)
        val source = readVector(state, instructionVs(insn), size, sourcePrefix)
        if (size == 1) {
            val packed = source[0].toRawBits()
            writeVector(
                state,
                instructionVd(insn),
                2,
                floatArrayOf(Float.fromBits(halfToFloat(packed and 0xFFFF).toRawBits()), Float.fromBits(halfToFloat(packed ushr 16).toRawBits())),
            )
        } else {
            val low = source[0].toRawBits()
            val high = source[1].toRawBits()
            writeVector(
                state,
                instructionVd(insn),
                4,
                floatArrayOf(
                    Float.fromBits(halfToFloat(low and 0xFFFF).toRawBits()),
                    Float.fromBits(halfToFloat(low ushr 16).toRawBits()),
                    Float.fromBits(halfToFloat(high and 0xFFFF).toRawBits()),
                    Float.fromBits(halfToFloat(high ushr 16).toRawBits()),
                ),
            )
        }
    }

    private fun floatToHalf(value: Float): Int {
        val bits = value.toRawBits()
        val sign = (bits ushr 16) and 0x8000
        val exponent = (bits ushr 23) and 0xFF
        var mantissa = bits and 0x7FFFFF
        val halfExponent = exponent - 127 + 15
        return when {
            exponent == 0xFF && mantissa == 0 -> sign or 0x7C00
            exponent == 0xFF -> sign or 0x7E00 or (mantissa and 0x3FF)
            halfExponent >= 0x1F -> sign or 0x7C00
            halfExponent <= 0 -> {
                if (halfExponent < -10) {
                    sign
                } else {
                    mantissa = mantissa or 0x800000
                    sign or (mantissa ushr (14 - halfExponent))
                }
            }
            else -> sign or (halfExponent shl 10) or (mantissa ushr 13)
        }
    }

    private fun executeVfpu4Unary(cpu: Cpu, insn: Int) {
        val operation = instructionRt(insn)
        val size = vectorSize(insn)
        val state = cpu.state
        when (operation) {
            0, 1, 2, 4, 5, 16, 17, 18, 19, 20, 21, 22, 23, 24, 26, 28 -> {
                val source = readVector(state, instructionVs(insn), size, sourcePrefix)
                val result = FloatArray(size) { lane -> unary(operation, source[lane]) }
                writeVector(state, instructionVd(insn), size, result)
            }
            3 -> writeIdentity(state, instructionVd(insn))
            6 -> writeVector(state, instructionVd(insn), size, FloatArray(size) { 0f })
            7 -> writeVector(state, instructionVd(insn), size, FloatArray(size) { 1f })
            else -> {
                cpu.raiseException(CpuException.ReservedInstruction)
                return
            }
        }
        consumePrefixes(state)
    }

    private fun executeVf2i(cpu: Cpu, insn: Int, mode: Int) {
        val state = cpu.state
        val size = vectorSize(insn)
        val source = readVector(state, instructionVs(insn), size, sourcePrefix)
        val scale = (1L shl ((insn ushr 16) and 0x1F)).toFloat()
        val result = FloatArray(size) { lane ->
            val value = source[lane]
            val bits = if (value.isNaN()) {
                Int.MAX_VALUE
            } else {
                val scaled = value.toDouble() * scale
                when {
                    scaled > Int.MAX_VALUE.toDouble() -> Int.MAX_VALUE
                    scaled <= Int.MIN_VALUE.toDouble() -> Int.MIN_VALUE
                    mode == 16 -> kotlin.math.round(scaled).toInt()
                    mode == 17 -> if (value >= 0) kotlin.math.floor(scaled).toInt() else kotlin.math.ceil(scaled).toInt()
                    mode == 18 -> kotlin.math.ceil(scaled).toInt()
                    else -> kotlin.math.floor(scaled).toInt()
                }
            }
            Float.fromBits(bits)
        }
        writeVector(state, instructionVd(insn), size, result)
        consumePrefixes(state)
    }

    private fun executeVi2f(cpu: Cpu, insn: Int) {
        val state = cpu.state
        val size = vectorSize(insn)
        val source = readVector(state, instructionVs(insn), size, sourcePrefix)
        val scale = 1f / (1L shl ((insn ushr 16) and 0x1F)).toFloat()
        val result = FloatArray(size) { lane -> source[lane].toRawBits().toFloat() * scale }
        writeVector(state, instructionVd(insn), size, result)
        consumePrefixes(state)
    }

    private fun unary(operation: Int, value: Float): Float = when (operation) {
        0, 1, 2 -> value
        4 -> value.coerceIn(0f, 1f)
        5 -> value.coerceIn(-1f, 1f)
        16 -> 1f / value
        17 -> 1f / kotlin.math.sqrt(value)
        18 -> kotlin.math.sin(value * (kotlin.math.PI.toFloat() / 180f))
        19 -> kotlin.math.cos(value * (kotlin.math.PI.toFloat() / 180f))
        20 -> 2f.pow(value)
        21 -> kotlin.math.log2(value)
        22 -> kotlin.math.sqrt(value.absoluteValue)
        23 -> kotlin.math.asin(value) * (180f / kotlin.math.PI.toFloat())
        24 -> -1f / value
        26 -> -kotlin.math.sin(value * (kotlin.math.PI.toFloat() / 180f))
        28 -> 2f.pow(-value)
        else -> value
    }

    private fun writeIdentity(state: CpuState, register: Int) {
        val matrix = (register ushr 2) and 7
        val base = matrix * 4
        for (row in 0 until 4) {
            for (column in 0 until 4) {
                state.vpr[base + column + row * 32] = if (row == column) 1f else 0f
            }
        }
    }

    fun executeVfpu6(cpu: Cpu, insn: Int) {
        val operation = instructionRt(insn)
        val side = vectorSize(insn)
        val state = cpu.state
        when (operation) {
            in 0..3 -> {
                val source = readMatrix(state, instructionVs(insn))
                val target = readMatrix(state, instructionVt(insn))
                val result = FloatArray(16)
                for (row in 0 until side) {
                    for (column in 0 until side) {
                        val lanes = if (row == side - 1 && column == side - 1) 4 else side
                        var sum = 0f
                        for (c in 0 until lanes) sum += source[column * 4 + c] * target[row * 4 + c]
                        result[row * 4 + column] = sum
                    }
                }
                writeMatrix(state, instructionVd(insn), result)
            }
            in 16..19 -> {
                val source = readMatrix(state, instructionVs(insn))
                val scale = readVector(state, instructionVt(insn), 1, targetPrefix)
                val result = FloatArray(16)
                for (row in 0 until side) {
                    for (column in 0 until side) {
                        val factor = if (row == side - 1) scale[column] else scale[0]
                        result[row * 4 + column] = source[row * 4 + column] * factor
                    }
                }
                writeMatrix(state, instructionVd(insn), result)
            }
            in 4..15 -> {
                val dimension = (insn ushr 23) and 3
                val transform = readMatrix(state, instructionVs(insn))
                val vector = readVector(state, instructionVt(insn), dimension + 1, targetPrefix)
                val result = FloatArray(dimension + 1)
                for (row in 0..dimension) {
                    var sum = 0f
                    for (k in 0..dimension) sum += transform[row * 4 + k] * vector[k]
                    result[row] = sum
                }
                writeVector(state, instructionVd(insn), dimension + 1, result)
            }
            else -> {
                cpu.raiseException(CpuException.ReservedInstruction)
                return
            }
        }
        consumePrefixes(state)
    }

    private fun readMatrix(state: CpuState, register: Int): FloatArray {
        val base = ((register ushr 2) and 7) * 4
        return FloatArray(16) { index ->
            state.vpr[base + (index % 4) + (index / 4) * 32]
        }
    }

    private fun writeMatrix(state: CpuState, register: Int, values: FloatArray) {
        val base = ((register ushr 2) and 7) * 4
        for (index in 0 until 16) {
            state.vpr[base + (index % 4) + (index / 4) * 32] = values[index]
        }
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
            opcode == Opcode.VFPU0 && operation == 7 -> {
                val target = readVector(state, instructionVt(insn), size, targetPrefix)
                for (lane in 0 until size) result[lane] = source[lane] / target[lane]
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
