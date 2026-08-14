package vesper.core.loader

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.ints.shouldBeGreaterThan

class SyscallTraceTest : StringSpec({

    fun resource(name: String): ByteArray {
        val path = "pspautotests/tests/$name"
        val url = SyscallTraceTest::class.java.classLoader.getResource(path)
            ?: throw RuntimeException("Resource not found: $path")
        return url.readBytes()
    }

    "cpu_alu imports and relocs are parsed" {
        val prx = resource("cpu/cpu_alu/cpu_alu.prx")
        val loader = ElfLoader()
        val result = loader.load(prx)
        result.isSuccess shouldBe true
        val image = result.getOrThrow()
        image.imports.size shouldBeGreaterThan 60
        image.relocGroups.size shouldBeGreaterThan 0
        println("Import count: ${image.imports.size}, Reloc groups: ${image.relocGroups.size}")
        for ((i, g) in image.relocGroups.withIndex()) {
            println("  Group $i: seg=${g.segIndex} relocs=${g.relocs.size}")
            if (i == 0) {
                for (r in g.relocs.take(5)) {
                    println("    offset=0x${r.offset.toString(16)} type=${r.type}")
                }
            }
        }
    }
})
