package vesper.core.kernel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import vesper.core.cpu.Cpu
import vesper.core.memory.MemoryBus

class SyscallTableTest : StringSpec({

    "register and dispatch known syscall" {
        val mem = MemoryBus()
        val cpu = Cpu(mem)
        val kernel = Kernel(mem, cpu)
        var called = false

        kernel.syscallTable.register(0xDEADBEEF.toInt(), "test_syscall") { _, _ ->
            called = true
            42
        }

        val result = kernel.syscallTable.dispatch(0xDEADBEEF.toInt(), kernel, cpu)
        called shouldBe true
        result shouldBe 42
    }

    "unknown syscall returns 0" {
        val mem = MemoryBus()
        val cpu = Cpu(mem)
        val kernel = Kernel(mem, cpu)

        val result = kernel.syscallTable.dispatch(0xFFFFFFFF.toInt(), kernel, cpu)
        result shouldBe 0
    }

    "register replaces previous handler for same NID" {
        val mem = MemoryBus()
        val cpu = Cpu(mem)
        val kernel = Kernel(mem, cpu)

        kernel.syscallTable.register(0x1234, "first") { _, _ -> 1 }
        kernel.syscallTable.register(0x1234, "second") { _, _ -> 2 }

        kernel.syscallTable.dispatch(0x1234, kernel, cpu) shouldBe 2
    }

    "system time returns non-zero" {
        val mem = MemoryBus()
        val cpu = Cpu(mem)
        val kernel = Kernel(mem, cpu)
        kernel.init()

        val time = kernel.syscallTable.dispatch(Nids.KERNEL_GET_SYSTEM_TIME_WIDE, kernel, cpu)
        time shouldNotBe 0
    }
})
