package vesper.core.kernel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import vesper.core.cpu.Cpu
import vesper.core.memory.Address
import vesper.core.memory.MemoryBus

class SchedulerTest : StringSpec({

    fun makeKernel(): Kernel {
        val mem = MemoryBus()
        val cpu = Cpu(mem)
        val timer = EmulatedTimer()
        timer.reset()
        return Kernel(mem, cpu, timer)
    }

    "create thread returns valid id" {
        val kernel = makeKernel()
        val id = kernel.scheduler.createThread("test", Address(0x08801000u), 0x10, 0x1000)
        id.shouldBeGreaterThan(0)
    }

    "start thread makes active" {
        val kernel = makeKernel()
        val id = kernel.scheduler.createThread("test", Address(0x08801000u), 0x10, 0x1000)
        kernel.scheduler.startThread(id) shouldBe 0

        val thread = kernel.scheduler.getThread(id)
        thread shouldNotBe null
        (thread!!.status !is ThreadStatus.Dormant) shouldBe true
    }

    "exit thread makes it dormant" {
        val kernel = makeKernel()
        val id = kernel.scheduler.createThread("test", Address(0x08801000u), 0x10, 0x1000)
        kernel.scheduler.startThread(id)

        kernel.scheduler.exitThread(0)
        val thread = kernel.scheduler.getThread(id)
        thread!!.status::class shouldBe ThreadStatus.Dormant::class
    }

    "delete thread removes it" {
        val kernel = makeKernel()
        val id = kernel.scheduler.createThread("test", Address(0x08801000u), 0x10, 0x1000)
        kernel.scheduler.deleteThread(id) shouldBe 0
        kernel.scheduler.getThread(id) shouldBe null
    }

    "create multiple threads" {
        val kernel = makeKernel()
        val id1 = kernel.scheduler.createThread("t1", Address(0x08801000u), 0x10, 0x1000)
        val id2 = kernel.scheduler.createThread("t2", Address(0x08802000u), 0x20, 0x2000)

        id1 shouldNotBe id2
        kernel.scheduler.threadCount shouldBe 3
    }

    "reschedule switches to higher priority thread" {
        val kernel = makeKernel()
        val id = kernel.scheduler.createThread("high", Address(0x08801000u), 0x08, 0x1000)
        kernel.scheduler.startThread(id)

        kernel.scheduler.reschedule()
        kernel.scheduler.getThread(id)!!.status::class shouldBe (ThreadStatus.Running::class)
    }

    "change thread priority" {
        val kernel = makeKernel()
        val id = kernel.scheduler.createThread("test", Address(0x08801000u), 0x20, 0x1000)

        kernel.scheduler.changePriority(id, 0x10) shouldBe 0
        kernel.scheduler.getThread(id)!!.priority shouldBe 0x10
    }

    "create callback returns valid id" {
        val kernel = makeKernel()
        val cbid = kernel.scheduler.createCallback(0, 0x08801000, 0)
        cbid.shouldBeGreaterThan(0)
    }
})
