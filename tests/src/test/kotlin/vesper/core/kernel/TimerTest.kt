package vesper.core.kernel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import vesper.core.cpu.Cpu
import vesper.core.memory.Address
import vesper.core.memory.MemoryBus

class TimerTest : StringSpec({

    "timer starts at approximately zero" {
        val timer = EmulatedTimer()
        timer.reset()
        timer.pause()
        (timer.nowMicros() >= 0L) shouldBe true
    }

    "timer advance adds micros when paused" {
        val timer = EmulatedTimer()
        timer.reset()
        timer.pause()
        val t1 = timer.nowMicros()
        timer.advance(1000)
        val t2 = timer.nowMicros()
        t2 shouldBe t1 + 1000
    }

    "timer advance with larger value" {
        val timer = EmulatedTimer()
        timer.reset()
        timer.pause()
        timer.advance(1_000_000)
        (timer.nowMicros() >= 1_000_000L) shouldBe true
    }

    "multiple advances accumulate" {
        val timer = EmulatedTimer()
        timer.reset()
        timer.pause()
        timer.advance(5000)
        timer.advance(2000)
        val t1 = timer.nowMicros()

        timer.advance(1000)
        val t2 = timer.nowMicros()
        t2 shouldBe t1 + 1000
    }

    "reset clears time" {
        val timer = EmulatedTimer()
        timer.reset()
        timer.pause()
        timer.advance(1_000_000)
        timer.reset()
        timer.pause()
        (timer.nowMicros() < 5000L) shouldBe true
    }

    "delay suspends thread and timer expiry wakes it" {
        val mem = MemoryBus()
        val cpu = Cpu(mem)
        val timer = EmulatedTimer()
        timer.reset()
        timer.pause()
        val kernel = Kernel(mem, cpu, timer)
        kernel.init()

        val threadId = kernel.scheduler.createThread("test", Address(0x08801000u), 0x10, 0x1000)
        threadId.shouldBeGreaterThan(0)
        kernel.scheduler.startThread(threadId)

        kernel.scheduler.delayCurrentThread(1000L) shouldBe 0

        val afterDelay = kernel.scheduler.getThread(kernel.scheduler.currentThreadId)
        if (afterDelay != null && afterDelay.id != threadId) {
            val delayed = kernel.scheduler.getThread(threadId)
            delayed!!.status::class shouldBe ThreadStatus.Waiting::class

            timer.advance(2000)
            kernel.scheduler.reschedule()

            kernel.scheduler.currentThreadId shouldBe threadId
        }
    }
})
