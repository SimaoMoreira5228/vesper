package vesper.core.cpu

import kotlin.math.absoluteValue
import kotlin.math.pow

internal object VectorConversions {
    fun executeUnary(
        cpu: Cpu,
        insn: Int,
    ) {
        val operation = instructionRt(insn)
        val size = VectorUnit.vectorSize(insn)
        val state = cpu.state
        when (operation) {
            0, 1, 2, 4, 5, 16, 17, 18, 19, 20, 21, 22, 23, 24, 26, 28 -> {
                val source = VectorUnit.readVector(state, VectorUnit.instructionVs(insn), size, VectorUnit.SOURCE_PREFIX)
                val result = FloatArray(size) { lane -> unary(operation, source[lane]) }
                VectorUnit.writeVector(state, VectorUnit.instructionVd(insn), size, result)
            }
            3 -> VectorUnit.writeIdentity(state, VectorUnit.instructionVd(insn))
            6 -> VectorUnit.writeVector(state, VectorUnit.instructionVd(insn), size, FloatArray(size) { 0f })
            7 -> VectorUnit.writeVector(state, VectorUnit.instructionVd(insn), size, FloatArray(size) { 1f })
            else -> {
                cpu.raiseException(CpuException.ReservedInstruction)
                return
            }
        }
        VectorUnit.consumePrefixes(state)
    }

    fun executeVfpu7(
        cpu: Cpu,
        insn: Int,
    ) {
        val state = cpu.state
        when (val index = instructionRt(insn)) {
            18 -> convertFloatToHalf(state, insn)
            19 -> convertHalfToFloat(state, insn)
            in 24..27 -> convertColorToInt(state, insn, index - 24)
            in 28..31 -> convertIntToColor(state, insn, index - 28)
            else -> cpu.raiseException(CpuException.ReservedInstruction)
        }
        VectorUnit.consumePrefixes(state)
    }

    fun executeVfpu9(
        cpu: Cpu,
        insn: Int,
    ) {
        val state = cpu.state
        val size = VectorUnit.vectorSize(insn)
        when (instructionRt(insn)) {
            6, 7 -> {
                val source = VectorUnit.readVector(state, VectorUnit.instructionVs(insn), size, VectorUnit.SOURCE_PREFIX)
                var sum = 0f
                for (lane in 0 until size) sum += source[lane]
                val value = if (instructionRt(insn) == 7) sum / size else sum
                VectorUnit.writeVector(state, VectorUnit.instructionVd(insn), 1, floatArrayOf(value))
            }
            else -> {
                cpu.raiseException(CpuException.ReservedInstruction)
                return
            }
        }
        VectorUnit.consumePrefixes(state)
    }

    fun executeVf2i(
        cpu: Cpu,
        insn: Int,
        mode: Int,
    ) {
        val state = cpu.state
        val size = VectorUnit.vectorSize(insn)
        val source = VectorUnit.readVector(state, VectorUnit.instructionVs(insn), size, VectorUnit.SOURCE_PREFIX)
        val scale = (1L shl ((insn ushr 16) and 0x1F)).toFloat()
        val result =
            FloatArray(size) { lane ->
                val value = source[lane]
                val bits =
                    if (value.isNaN()) {
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
        VectorUnit.writeVector(state, VectorUnit.instructionVd(insn), size, result)
        VectorUnit.consumePrefixes(state)
    }

    fun executeVi2f(
        cpu: Cpu,
        insn: Int,
    ) {
        val state = cpu.state
        val size = VectorUnit.vectorSize(insn)
        val source = VectorUnit.readVector(state, VectorUnit.instructionVs(insn), size, VectorUnit.SOURCE_PREFIX)
        val scale = 1f / (1L shl ((insn ushr 16) and 0x1F)).toFloat()
        val result = FloatArray(size) { lane -> source[lane].toRawBits().toFloat() * scale }
        VectorUnit.writeVector(state, VectorUnit.instructionVd(insn), size, result)
        VectorUnit.consumePrefixes(state)
    }

    fun executeVcmov(
        cpu: Cpu,
        insn: Int,
    ) {
        val state = cpu.state
        val size = VectorUnit.vectorSize(insn)
        val conditional = (insn ushr 19) and 1
        val index = (insn ushr 16) and 7
        val source = VectorUnit.readVector(state, VectorUnit.instructionVs(insn), size, VectorUnit.SOURCE_PREFIX)
        val destination = VectorUnit.readVector(state, VectorUnit.instructionVd(insn), size, VectorUnit.TARGET_PREFIX)
        val condition = state.vfpuCtrl[VectorUnit.CC_REGISTER]
        when {
            index < 6 ->
                if (((condition ushr index) and 1) == (1 - conditional)) {
                    for (lane in 0 until size) destination[lane] = source[lane]
                }
            index == 6 -> for (lane in 0 until size) {
                if (((condition ushr lane) and 1) == (1 - conditional)) destination[lane] = source[lane]
            }
        }
        VectorUnit.writeVector(state, VectorUnit.instructionVd(insn), size, destination)
        VectorUnit.consumePrefixes(state)
    }

    private fun convertColorToInt(
        state: CpuState,
        insn: Int,
        mode: Int,
    ) {
        val size = VectorUnit.vectorSize(insn)
        val source = VectorUnit.readVector(state, VectorUnit.instructionVs(insn), size, VectorUnit.SOURCE_PREFIX)
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
            VectorUnit.writeVector(state, VectorUnit.instructionVd(insn), 4, result)
        } else {
            val elements = if (size == 1) 1 else 2
            val result = FloatArray(4)
            for (i in 0 until elements) {
                val value = source[i].toRawBits()
                result[i * 2] =
                    if (mode == 3) {
                        Float.fromBits((value and 0xFFFF) shl 16)
                    } else {
                        Float.fromBits((value and 0xFFFF) shl 15)
                    }
                result[i * 2 + 1] =
                    if (mode == 3) {
                        Float.fromBits(value and 0xFFFF0000.toInt())
                    } else {
                        Float.fromBits((value and 0xFFFF0000.toInt()) ushr 1)
                    }
            }
            VectorUnit.writeVector(state, VectorUnit.instructionVd(insn), 4, result)
        }
    }

    private fun convertIntToColor(
        state: CpuState,
        insn: Int,
        mode: Int,
    ) {
        val size = VectorUnit.vectorSize(insn)
        val source = VectorUnit.readVector(state, VectorUnit.instructionVs(insn), 4, null)
        if (mode <= 1) {
            var packed = 0
            for (i in 0 until 4) {
                val value = source[i].toRawBits()
                val component = if (mode == 1) value ushr 24 else (if (value < 0) 0 else value ushr 23) and 0xFF
                packed = packed or ((component and 0xFF) shl (i * 8))
            }
            VectorUnit.writeVector(state, VectorUnit.instructionVd(insn), 4, floatArrayOf(Float.fromBits(packed), 0f, 0f, 0f))
        } else {
            val elements = (size + 1) / 2
            val result = FloatArray(4)
            for (i in 0 until elements) {
                val low = source[i * 2].toRawBits()
                val high = source[i * 2 + 1].toRawBits()
                val packed =
                    if (mode == 3) {
                        (low ushr 16) or ((high ushr 16) shl 16)
                    } else {
                        ((if (low < 0) 0 else low ushr 15) and 0xFFFF) or
                            (((if (high < 0) 0 else high ushr 15) and 0xFFFF) shl 16)
                    }
                result[i] = Float.fromBits(packed)
            }
            VectorUnit.writeVector(state, VectorUnit.instructionVd(insn), 4, result)
        }
    }

    private fun convertFloatToHalf(
        state: CpuState,
        insn: Int,
    ) {
        val size = VectorUnit.vectorSize(insn)
        val source = VectorUnit.readVector(state, VectorUnit.instructionVs(insn), size, VectorUnit.SOURCE_PREFIX)
        if (size <= 2) {
            val packed = (floatToHalf(source[0]) and 0xFFFF) or ((floatToHalf(source.getOrElse(1) { 0f }) and 0xFFFF) shl 16)
            VectorUnit.writeVector(state, VectorUnit.instructionVd(insn), 1, floatArrayOf(Float.fromBits(packed)))
        } else {
            val low = (floatToHalf(source[0]) and 0xFFFF) or ((floatToHalf(source[1]) and 0xFFFF) shl 16)
            val high = (floatToHalf(source[2]) and 0xFFFF) or ((floatToHalf(source[3]) and 0xFFFF) shl 16)
            VectorUnit.writeVector(state, VectorUnit.instructionVd(insn), 2, floatArrayOf(Float.fromBits(low), Float.fromBits(high)))
        }
    }

    private fun convertHalfToFloat(
        state: CpuState,
        insn: Int,
    ) {
        val size = VectorUnit.vectorSize(insn)
        val source = VectorUnit.readVector(state, VectorUnit.instructionVs(insn), size, VectorUnit.SOURCE_PREFIX)
        if (size == 1) {
            val packed = source[0].toRawBits()
            VectorUnit.writeVector(
                state,
                VectorUnit.instructionVd(insn),
                2,
                floatArrayOf(
                    Float.fromBits(VectorUnit.halfToFloat(packed and 0xFFFF).toRawBits()),
                    Float.fromBits(VectorUnit.halfToFloat(packed ushr 16).toRawBits()),
                ),
            )
        } else {
            val low = source[0].toRawBits()
            val high = source[1].toRawBits()
            VectorUnit.writeVector(
                state,
                VectorUnit.instructionVd(insn),
                4,
                floatArrayOf(
                    Float.fromBits(VectorUnit.halfToFloat(low and 0xFFFF).toRawBits()),
                    Float.fromBits(VectorUnit.halfToFloat(low ushr 16).toRawBits()),
                    Float.fromBits(VectorUnit.halfToFloat(high and 0xFFFF).toRawBits()),
                    Float.fromBits(VectorUnit.halfToFloat(high ushr 16).toRawBits()),
                ),
            )
        }
    }

    private fun unary(
        operation: Int,
        value: Float,
    ): Float =
        when (operation) {
            0, 1, 2 -> value
            4 ->
                if (value <= 0f) {
                    0f
                } else if (value > 1f) {
                    1f
                } else {
                    value
                }
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
}
