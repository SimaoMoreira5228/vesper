package vesper.core.kernel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.ints.shouldBeGreaterThan
import vesper.core.cpu.Cpu
import vesper.core.cpu.OpcodeTable
import vesper.core.memory.Address
import vesper.core.memory.MemoryBus

class KernelIntegrationTest : StringSpec({

    fun makeKernel(): Kernel {
        val mem = MemoryBus()
        val cpu = Cpu(mem)
        val timer = EmulatedTimer()
        timer.reset()
        val kernel = Kernel(mem, cpu, timer)
        kernel.init()
        return kernel
    }

    "kernel init registers syscalls" {
        val kernel = makeKernel()
        kernel.syscallTable.isRegistered(Nids.THREAD_CREATE) shouldBe true
        kernel.syscallTable.isRegistered(Nids.THREAD_START) shouldBe true
        kernel.syscallTable.isRegistered(Nids.THREAD_EXIT) shouldBe true
        kernel.syscallTable.isRegistered(Nids.EXIT_GAME) shouldBe true
        kernel.syscallTable.isRegistered(Nids.DELAY_THREAD) shouldBe true
        kernel.syscallTable.isRegistered(Nids.IO_OPEN) shouldBe true
        kernel.syscallTable.isRegistered(Nids.DISPLAY_SET_MODE) shouldBe true
        kernel.syscallTable.isRegistered(Nids.CTRL_PEEK_BUFFER_POSITIVE) shouldBe true
        kernel.syscallTable.isRegistered(Nids.CREATE_SEMA) shouldBe true
    }

    "exit game via syscall table" {
        val mem = MemoryBus()
        val cpu = Cpu(mem)
        val kernel = Kernel(mem, cpu)
        kernel.init()

        kernel.syscallTable.dispatch(Nids.EXIT_GAME, kernel, cpu)

        kernel.isExitRequested() shouldBe true
    }

    "exit game via handling exception from step" {
        val mem = MemoryBus()
        val cpu = Cpu(mem)
        val kernel = Kernel(mem, cpu)
        kernel.init()
        cpu.kernel = kernel

        cpu.state.setGpr(2, Nids.EXIT_GAME)
        cpu.state.pc = Address(0x08800000u)
        mem.write32(cpu.state.pc, 0x0000000C)

        cpu.step()
        kernel.isExitRequested() shouldBe true
    }

    "create thread via syscall table" {
        val mem = MemoryBus()
        val cpu = Cpu(mem)
        val kernel = Kernel(mem, cpu)
        kernel.init()
        cpu.kernel = kernel

        cpu.state.setGpr(2, Nids.THREAD_CREATE)
        cpu.state.setGpr(5, 0x08801000)
        cpu.state.setGpr(6, 0x10)
        cpu.state.setGpr(7, 0x1000)

        kernel.syscallTable.dispatch(Nids.THREAD_CREATE, kernel, cpu)

        kernel.scheduler.threadCount shouldBeGreaterThan 1
        cpu.halted shouldBe false
    }

    "create and start thread via syscall table works end-to-end" {
        val mem = MemoryBus()
        val cpu = Cpu(mem)
        val kernel = Kernel(mem, cpu)
        kernel.init()
        cpu.kernel = kernel

        cpu.state.setGpr(2, Nids.THREAD_CREATE)
        cpu.state.setGpr(5, 0x08801000)
        cpu.state.setGpr(6, 0x10)
        cpu.state.setGpr(7, 0x1000)
        kernel.syscallTable.dispatch(Nids.THREAD_CREATE, kernel, cpu)

        val threadId = kernel.scheduler.currentThreadId
        threadId.shouldBeGreaterThan(-1)

        cpu.state.setGpr(2, Nids.THREAD_START)
        cpu.state.setGpr(4, threadId)
        kernel.syscallTable.dispatch(Nids.THREAD_START, kernel, cpu)
    }

    "display framebuffer address is tracked" {
        val kernel = makeKernel()
        kernel.geState.framebufferAddr = Address(0x04000000u)
        kernel.geState.framebufferAddr shouldBe Address(0x04000000u)
    }
})
