package vesper.core.loader

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty

class PspAutotestSuiteTest : StringSpec({
    data class Case(val path: String, val maxInstructions: Int = 100_000_000)

    val cases = listOf(
        Case("cpu/cpu_alu/cpu_alu"),
        Case("cpu/cpu_alu/cpu_branch"),
        Case("cpu/cpu_alu/cpu_branch2"),
        Case("cpu/fpu/fcr"),
        Case("cpu/fpu/fpu", maxInstructions = 300_000_000),
        Case("cpu/lsu/lsu"),
        Case("cpu/icache/icache"),
        Case("cpu/vfpu/convert"),
        Case("cpu/vfpu/vavg"),
        Case("display/display"),
        Case("hash/hash"),
        Case("loader/bss/bss"),
        Case("misc/testgp"),
        Case("misc/timeconv"),
        Case("rtc/rtc"),
        Case("string/string"),
        Case("threads/threads/threads"),
        Case("threads/semaphores/semaphores"),
    )

    fun resource(path: String): ByteArray {
        val url = PspAutotestSuiteTest::class.java.classLoader.getResource(path)
            ?: throw RuntimeException("Resource not found: $path")
        return url.readBytes()
    }

    fun expected(path: String): String {
        val url = PspAutotestSuiteTest::class.java.classLoader.getResource(path)
            ?: throw RuntimeException("Resource not found: $path")
        return url.readText().replace("\r\n", "\n").trimEnd()
    }

    for (case in cases) {
        case.path {
            val output = PspAutotestRunner(maxInstructions = case.maxInstructions)
                .run(resource("pspautotests/tests/${case.path}.prx"))

            output.shouldNotBeEmpty()
            output.trimEnd() shouldBe expected("pspautotests/tests/${case.path}.expected")
        }
    }
})
