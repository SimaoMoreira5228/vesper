package vesper.core.loader

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty

class PspAutotestCPUTest : StringSpec({

    fun resource(name: String): ByteArray {
        val path = "pspautotests/tests/$name"
        val url = PspAutotestCPUTest::class.java.classLoader.getResource(path)
            ?: throw RuntimeException("Resource not found: $path")
        return url.readBytes()
    }

    fun expected(name: String): String {
        val path = "pspautotests/tests/$name"
        val url = PspAutotestCPUTest::class.java.classLoader.getResource(path)
            ?: throw RuntimeException("Resource not found: $path")
        return url.readText().replace("\r\n", "\n").trimEnd()
    }

    val runner = PspAutotestRunner()

    "cpu_alu" {
        val prx = resource("cpu/cpu_alu/cpu_alu.prx")
        val output = runner.run(prx)

        output.shouldNotBeEmpty()
        output.trimEnd() shouldBe expected("cpu/cpu_alu/cpu_alu.expected")
    }

    "cpu_branch" {
        val prx = resource("cpu/cpu_alu/cpu_branch.prx")
        val output = runner.run(prx)

        output.shouldNotBeEmpty()
        output.trimEnd() shouldBe expected("cpu/cpu_alu/cpu_branch.expected")
    }

    "cpu_branch2" {
        val prx = resource("cpu/cpu_alu/cpu_branch2.prx")
        val output = runner.run(prx)

        output.shouldNotBeEmpty()
        output.trimEnd() shouldBe expected("cpu/cpu_alu/cpu_branch2.expected")
    }

    "fpu_fcr" {
        val prx = resource("cpu/fpu/fcr.prx")
        val output = runner.run(prx)

        output.shouldNotBeEmpty()
        output.trimEnd() shouldBe expected("cpu/fpu/fcr.expected")
    }
})
