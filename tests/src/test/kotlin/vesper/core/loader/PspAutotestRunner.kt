package vesper.core.loader

import vesper.core.cpu.Cpu
import vesper.core.kernel.Kernel
import vesper.core.memory.MemoryBus

class PspAutotestRunner(
    val traceFirst: Int = 0,
    val traceInstructions: Boolean = false,
) {
    private val maxInstructions = 100_000_000

    fun run(prxBytes: ByteArray): String {
        val memory = MemoryBus()
        val cpu = Cpu(memory)
        val kernel = Kernel(memory, cpu)
        kernel.init()
        cpu.kernel = kernel

        if (traceFirst > 0) {
            vesper.common.Logger.setMinLevel(vesper.common.LogLevel.TRACE)
            vesper.common.LogSinks.add(StdoutLogSink())
            kernel.syscallTable.trace = true
            cpu.traceInstructions = traceInstructions
        }

        val result = ModuleLoader().loadAndResolve(prxBytes, memory, kernel)
        require(result.isSuccess) { "Failed to load PRX: ${result.exceptionOrNull()}" }

        var steps = 0
        while (!cpu.halted && steps < maxInstructions) {
            if (steps == traceFirst && traceFirst > 0) {
                kernel.syscallTable.trace = false
                cpu.traceInstructions = false
            }
            cpu.step()
            steps++
        }

        if (steps >= maxInstructions) {
            throw AssertionError("Reached max instructions ($maxInstructions)")
        }
        return kernel.kemulator.output
    }
}
