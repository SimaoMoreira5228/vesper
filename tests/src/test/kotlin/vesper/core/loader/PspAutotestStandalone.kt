package vesper.core.loader

fun main(args: Array<String>) {
    val prxPath = args.firstOrNull() ?: error("Usage: runPspAutotest <prx_path> [expected_path]")
    val prxBytes = java.io.File(prxPath).readBytes()
    val trace = args.lastOrNull()?.toIntOrNull() ?: 0
    val runner = PspAutotestRunner(traceFirst = trace, traceInstructions = trace > 0)
    val output = runner.run(prxBytes)
    println("=== Output (${output.length} chars) ===")
    println(output)
    println("=== End ===")

    if (args.size >= 2) {
        val expected = java.io.File(args[1]).readText().replace("\r\n", "\n").trimEnd()
        val match = output.trimEnd() == expected
        println("Match: $match")
        if (!match) {
            val outLen = output.length
            val expLen = expected.length
            println("Output len: $outLen, Expected len: $expLen")
            for (i in 0 until minOf(outLen, expLen, 200)) {
                if (output.getOrElse(i) { ' ' } != expected.getOrElse(i) { ' ' }) {
                    println("  First diff at pos $i: actual=0x${output[i].code.toString(16)} expected=0x${expected[i].code.toString(16)}")
                    break
                }
            }
        }
    }
}
