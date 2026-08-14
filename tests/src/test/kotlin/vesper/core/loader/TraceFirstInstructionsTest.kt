package vesper.core.loader

import io.kotest.core.spec.style.StringSpec

class TraceFirstInstructionsTest : StringSpec({

    fun resource(name: String): ByteArray {
        val path = "pspautotests/tests/$name"
        val url = TraceFirstInstructionsTest::class.java.classLoader.getResource(path)
            ?: throw RuntimeException("Resource not found: $path")
        return url.readBytes()
    }

    "trace first 200000 steps of cpu_alu" {
        val prx = resource("cpu/cpu_alu/cpu_alu.prx")
        val runner = PspAutotestRunner(traceFirst = 200000, traceInstructions = true)
        try {
            runner.run(prx)
        } catch (e: AssertionError) {
            println("Expected: ${e.message}")
        }
    }
})
