package vesper.core.loader

import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val prxPath = args.firstOrNull() ?: error("Usage: runPspAutotest <prx_path> [expected_path]")
    val prxBytes = java.io.File(prxPath).readBytes()
    val expectedPath = args.getOrNull(1)?.takeUnless { it.toIntOrNull() != null }
    val numericArgs = args.drop(1).mapNotNull { it.toIntOrNull() }
    val trace = numericArgs.getOrNull(0) ?: 0
    val maxInstructions = numericArgs.getOrNull(1) ?: 100_000_000
    val outputCheckpoint = System.getProperty("vesper.outputCheckpoint")?.toIntOrNull()
    val instructionsAfterCheckpoint = System.getProperty("vesper.instructionsAfterCheckpoint")?.toIntOrNull() ?: 0
    val tracePcStart = System.getProperty("vesper.tracePcStart")?.removePrefix("0x")?.toUIntOrNull(16)
    val tracePcEnd = System.getProperty("vesper.tracePcEnd")?.removePrefix("0x")?.toUIntOrNull(16)
    val runner =
        PspAutotestRunner(
            traceFirst = trace,
            traceInstructions = trace > 0,
            maxInstructions = maxInstructions,
            outputCheckpoint = outputCheckpoint,
            instructionsAfterCheckpoint = instructionsAfterCheckpoint,
            tracePcRange = if (tracePcStart != null && tracePcEnd != null) tracePcStart..tracePcEnd else null,
        )
    val output = runner.run(prxBytes)
    println("=== Output (${output.length} chars) ===")
    println(output)
    println("=== End ===")
    println("Steps: ${runner.stepsExecuted}")
    println("Stop: ${runner.stopReason}")
    println("Final PC: ${runner.finalPc}")
    println("Output writes: ${runner.outputStats}")
    println("Recent: ${runner.recentTrace.joinToString(" | ")}")

    if (expectedPath != null) {
        val expected = java.io.File(expectedPath).readText().replace("\r\n", "\n").trimEnd()
        val match = output.trimEnd() == expected
        println("Match: $match")
        if (!match) {
            val outLen = output.length
            val expLen = expected.length
            println("Output len: $outLen, Expected len: $expLen")
            println(PspAutotestDiff.render(expected, output.trimEnd()))
            exitProcess(1)
        }
    }
}
