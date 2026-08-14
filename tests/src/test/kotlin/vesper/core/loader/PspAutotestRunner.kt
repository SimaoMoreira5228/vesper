package vesper.core.loader

import vesper.core.cpu.Cpu
import vesper.core.kernel.Kernel
import vesper.core.memory.MemoryBus

class PspAutotestRunner(
    val traceFirst: Int = 0,
    val traceInstructions: Boolean = false,
    val maxInstructions: Int = 100_000_000,
) {
    var stepsExecuted: Int = 0
        private set
    var stopReason: StopReason = StopReason.NOT_STARTED
        private set
    var finalPc: String = "n/a"
        private set
    var recentTrace: List<String> = emptyList()
        private set
    var outputStats: String = "n/a"
        private set

    enum class StopReason { NOT_STARTED, KERNEL_EXIT, CPU_HALTED, INSTRUCTION_LIMIT }

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

        stepsExecuted = steps
        finalPc = cpu.pc.toString()
        recentTrace = cpu.lastTrace.takeLast(3)
        outputStats = kernel.kemulator.outputStats()
        if (steps >= maxInstructions) {
            stopReason = StopReason.INSTRUCTION_LIMIT
            throw AssertionError("Reached max instructions ($maxInstructions) at PC ${cpu.pc}; recent=${recentTrace.joinToString(" | ")}")
        }
        stopReason = if (kernel.isExitRequested()) StopReason.KERNEL_EXIT else StopReason.CPU_HALTED
        return kernel.kemulator.output
    }
}
