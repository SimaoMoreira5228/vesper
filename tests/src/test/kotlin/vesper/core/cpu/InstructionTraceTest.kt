package vesper.core.cpu

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import vesper.core.memory.Address
import vesper.core.memory.MemoryBus

class InstructionTraceTest : StringSpec({
    "crash trace preserves the latest instructions in execution order" {
        val memory = MemoryBus()
        val cpu = Cpu(memory)
        cpu.state.pc = Address(0x08800000u)

        repeat(25) { cpu.step() }

        val trace = cpu.lastTrace
        trace shouldHaveSize 20
        trace.first().startsWith("0x08800014:") shouldBe true
        trace.last().startsWith("0x08800060:") shouldBe true
    }

    "trace captures registers before instruction execution" {
        val cpu = Cpu(MemoryBus())
        cpu.state.setGpr(4, 42)

        cpu.step()

        cpu.lastTrace.single().contains("a0=42") shouldBe true
    }
})
