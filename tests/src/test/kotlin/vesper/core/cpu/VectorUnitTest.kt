package vesper.core.cpu

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import vesper.core.memory.MemoryBus

class VectorUnitTest : StringSpec({
    "vector register addressing covers rows columns and transposes" {
        VectorUnit.vectorRegisters(0x00, 4).toList() shouldBe listOf(0, 32, 64, 96)
        VectorUnit.vectorRegisters(0x20, 4).toList() shouldBe listOf(0, 1, 2, 3)
        VectorUnit.vectorRegisters(0x7F, 1).toList() shouldBe listOf(127)
    }

    "viim writes a signed scalar immediate" {
        val cpu = Cpu(MemoryBus())
        val instruction = (Opcode.VFPU5 shl 26) or (6 shl 23) or (5 shl 16) or 0xFFFF

        VectorUnit.executeVfpu5(cpu, instruction)

        cpu.state.vpr[VectorUnit.vectorRegisters(5, 1)[0]] shouldBe -1f
    }

    "vadd applies source swizzles and consumes prefixes" {
        val cpu = Cpu(MemoryBus())
        val source = VectorUnit.vectorRegisters(0, 4)
        val target = VectorUnit.vectorRegisters(1, 4)
        source.forEachIndexed { lane, register -> cpu.state.vpr[register] = (lane + 1).toFloat() }
        target.forEachIndexed { lane, register -> cpu.state.vpr[register] = (lane + 10).toFloat() }
        cpu.state.vfpuCtrl[0] = 0
        val instruction = (Opcode.VFPU0 shl 26) or (1 shl 15) or (1 shl 7) or (1 shl 16) or 2

        VectorUnit.executeArithmetic(cpu, instruction)

        VectorUnit.vectorRegisters(2, 4).map { cpu.state.vpr[it] } shouldBe listOf(11f, 11f, 11f, 11f)
        cpu.state.vfpuCtrl[0] shouldBe 0xE4
    }

    "two-axis swizzle matches the vregs fixture" {
        val cpu = Cpu(MemoryBus())
        cpu.reset()
        fun vreg(matrix: Int, row: Int, column: Int) = (row shl 5) or (matrix shl 2) or column
        fun vadd(register: Int): Int =
            (Opcode.VFPU0 shl 26) or (1 shl 15) or (1 shl 7) or
                (register shl 16) or (register shl 8) or register

        for (register in 0 until 128) {
            val instruction = (Opcode.VFPU5 shl 26) or (6 shl 23) or (register shl 16) or register
            VectorUnit.executeVfpu5(cpu, instruction)
        }
        for (matrix in 0 until 8) for (row in 0 until 4) {
            VectorUnit.executeArithmetic(cpu, vadd(vreg(matrix, 0, row)))
        }
        for (matrix in 0 until 8) {
            for (row in 0 until 4) {
                cpu.state.vfpuCtrl[0] = 0x1B
                VectorUnit.executeArithmetic(cpu, vadd(vreg(matrix, 0, row)))
            }
            for (column in 0 until 4) {
                cpu.state.vfpuCtrl[0] = 0x1B
                VectorUnit.executeArithmetic(cpu, vadd(vreg(matrix, 1, column)))
            }
        }

        for (row in 0 until 4) {
            VectorUnit.vectorRegisters(vreg(0, 0, row), 4).map { cpu.state.vpr[it] } shouldBe
                listOf(396f, 396f, 396f, 396f)
        }
    }
})
