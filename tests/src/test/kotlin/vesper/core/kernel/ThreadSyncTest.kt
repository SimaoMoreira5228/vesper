package vesper.core.kernel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThan as longShouldBeGreaterThan
import vesper.core.cpu.Cpu
import vesper.core.memory.MemoryBus

class ThreadSyncTest : StringSpec({

    fun makeKernel(): Kernel {
        val mem = MemoryBus()
        val cpu = Cpu(mem)
        val timer = EmulatedTimer()
        timer.reset()
        return Kernel(mem, cpu, timer)
    }

    "create semaphore with initial count" {
        val kernel = makeKernel()
        val semaId = kernel.synchPrimitives.createSemaphore("s", 0, 5, 10)
        semaId.shouldBeGreaterThan(0)
    }

    "signal semaphore increments count" {
        val kernel = makeKernel()
        val semaId = kernel.synchPrimitives.createSemaphore("s", 0, 1, 10)
        kernel.synchPrimitives.signalSemaphore(semaId, 1) shouldBe 0
    }

    "wait semaphore decrements count when available" {
        val kernel = makeKernel()
        val semaId = kernel.synchPrimitives.createSemaphore("s", 0, 3, 10)
        kernel.synchPrimitives.waitSemaphore(semaId, 2, false) shouldBe 0
    }

    "poll semaphore returns success when available" {
        val kernel = makeKernel()
        val semaId = kernel.synchPrimitives.createSemaphore("s", 0, 1, 10)

        kernel.synchPrimitives.pollSemaphore(semaId) shouldBe 0
        kernel.synchPrimitives.pollSemaphore(semaId) shouldBe -1
    }

    "delete semaphore cleans up" {
        val kernel = makeKernel()
        val semaId = kernel.synchPrimitives.createSemaphore("s", 0, 1, 10)
        kernel.synchPrimitives.deleteSemaphore(semaId) shouldBe 0
    }

    "create and lock mutex" {
        val kernel = makeKernel()
        val mutexId = kernel.synchPrimitives.createMutex()
        mutexId.shouldBeGreaterThan(0)

        kernel.synchPrimitives.lockMutex(mutexId) shouldBe 0
        kernel.synchPrimitives.unlockMutex(mutexId) shouldBe 0
    }

    "recursive mutex lock" {
        val kernel = makeKernel()
        val mutexId = kernel.synchPrimitives.createMutex(true)

        kernel.synchPrimitives.lockMutex(mutexId) shouldBe 0
        kernel.synchPrimitives.lockMutex(mutexId) shouldBe 0
        kernel.synchPrimitives.unlockMutex(mutexId) shouldBe 0
        kernel.synchPrimitives.unlockMutex(mutexId) shouldBe 0
    }

    "create and set event flag" {
        val kernel = makeKernel()
        val flagId = kernel.synchPrimitives.createEventFlag(0)
        flagId.shouldBeGreaterThan(0)

        kernel.synchPrimitives.setEventFlag(flagId, 0xFF) shouldBe 0
    }

    "event flag wait when already set" {
        val kernel = makeKernel()
        val flagId = kernel.synchPrimitives.createEventFlag(0xFF)

        kernel.synchPrimitives.waitEventFlag(flagId, 0x0F, 0, 0, 0) shouldBe 0
    }

    "clear event flag bits" {
        val kernel = makeKernel()
        val flagId = kernel.synchPrimitives.createEventFlag(0xFF)
        kernel.synchPrimitives.clearEventFlag(flagId, 0x0F) shouldBe 0
    }
})
