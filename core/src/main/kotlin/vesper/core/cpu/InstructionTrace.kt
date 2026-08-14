package vesper.core.cpu

internal class InstructionTrace(private val capacity: Int) {
    private val pcs = IntArray(capacity)
    private val instructions = IntArray(capacity)
    private val registers = Array(8) { IntArray(capacity) }
    private var next = 0
    private var size = 0

    fun record(pc: UInt, instruction: Int, state: CpuState) {
        pcs[next] = pc.toInt()
        instructions[next] = instruction
        registers[0][next] = state.gpr(4)
        registers[1][next] = state.gpr(5)
        registers[2][next] = state.gpr(6)
        registers[3][next] = state.gpr(7)
        registers[4][next] = state.gpr(2)
        registers[5][next] = state.gpr(3)
        registers[6][next] = state.gpr(31)
        registers[7][next] = state.gpr(29)
        next = (next + 1) % capacity
        if (size < capacity) size++
    }

    fun lines(): List<String> = List(size) { offset ->
        val index = (next - size + offset + capacity) % capacity
        formatInstructionTrace(
            pc = pcs[index].toUInt(),
            instruction = instructions[index],
            a0 = registers[0][index],
            a1 = registers[1][index],
            a2 = registers[2][index],
            a3 = registers[3][index],
            v0 = registers[4][index],
            v1 = registers[5][index],
            ra = registers[6][index],
            sp = registers[7][index],
        )
    }
}

internal fun formatInstructionTrace(pc: UInt, instruction: Int, state: CpuState): String =
    formatInstructionTrace(
        pc = pc,
        instruction = instruction,
        a0 = state.gpr(4),
        a1 = state.gpr(5),
        a2 = state.gpr(6),
        a3 = state.gpr(7),
        v0 = state.gpr(2),
        v1 = state.gpr(3),
        ra = state.gpr(31),
        sp = state.gpr(29),
    )

private fun formatInstructionTrace(
    pc: UInt,
    instruction: Int,
    a0: Int,
    a1: Int,
    a2: Int,
    a3: Int,
    v0: Int,
    v1: Int,
    ra: Int,
    sp: Int,
): String =
    "0x${pc.toString(16).padStart(8, '0')}: 0x${instruction.toUInt().toString(16).padStart(8, '0')}  " +
        "${disassemble(pc.toInt(), instruction)}  a0=$a0 a1=$a1 a2=$a2 a3=$a3 " +
        "v0=$v0 v1=$v1 ra=${ra.toUInt().toString(16)} sp=${sp.toUInt().toString(16)}"
