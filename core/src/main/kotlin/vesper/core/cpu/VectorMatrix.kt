package vesper.core.cpu

internal object VectorMatrix {

    fun execute(cpu: Cpu, insn: Int) {
        val family = (insn ushr 21) and 0x1F
        val side = VectorUnit.vectorSize(insn)
        val state = cpu.state
        when (family) {
            in 0..3 -> {
                val source = readMatrix(state, VectorUnit.instructionVs(insn))
                val target = readMatrix(state, VectorUnit.instructionVt(insn))
                val result = FloatArray(16)
                for (row in 0 until side) {
                    for (column in 0 until side) {
                        val lanes = if (row == side - 1 && column == side - 1) 4 else side
                        var sum = 0f
                        for (c in 0 until lanes) sum += source[column * 4 + c] * target[row * 4 + c]
                        result[row * 4 + column] = sum
                    }
                }
                writeMatrix(state, VectorUnit.instructionVd(insn), result)
            }
            in 16..19 -> {
                val source = readMatrix(state, VectorUnit.instructionVs(insn))
                val scale = VectorUnit.readVector(state, VectorUnit.instructionVt(insn), 1, VectorUnit.targetPrefix)
                val result = FloatArray(16)
                for (row in 0 until side) {
                    for (column in 0 until side) {
                        result[row * 4 + column] = source[row * 4 + column] * scale[0]
                    }
                }
                writeMatrix(state, VectorUnit.instructionVd(insn), result)
            }
            in 4..15 -> {
                val dimension = (insn ushr 23) and 3
                val transform = readMatrix(state, VectorUnit.instructionVs(insn))
                val vector = VectorUnit.readVector(state, VectorUnit.instructionVt(insn), dimension + 1, VectorUnit.targetPrefix)
                val result = FloatArray(dimension + 1)
                for (row in 0..dimension) {
                    var sum = 0f
                    for (k in 0..dimension) sum += transform[row * 4 + k] * vector[k]
                    result[row] = sum
                }
                VectorUnit.writeVector(state, VectorUnit.instructionVd(insn), dimension + 1, result)
            }
            28 -> executeMatrix1(cpu, insn)
            else -> {
                cpu.raiseException(CpuException.ReservedInstruction)
                return
            }
        }
        VectorUnit.consumePrefixes(state)
    }

    private fun executeMatrix1(cpu: Cpu, insn: Int) {
        val state = cpu.state
        when ((insn ushr 16) and 0xF) {
            0 -> writeMatrix(state, VectorUnit.instructionVd(insn), readMatrix(state, VectorUnit.instructionVs(insn)))
            3 -> VectorUnit.writeIdentity(state, VectorUnit.instructionVd(insn))
            6 -> writeMatrix(state, VectorUnit.instructionVd(insn), FloatArray(16))
            7 -> writeMatrix(state, VectorUnit.instructionVd(insn), FloatArray(16) { 1f })
            else -> {
                cpu.raiseException(CpuException.ReservedInstruction)
                return
            }
        }
        VectorUnit.consumePrefixes(state)
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
}
