package vesper.core.kernel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import vesper.core.cpu.Cpu
import vesper.core.memory.Address
import vesper.core.memory.MemoryBus
import java.io.File

class FileIoTest : StringSpec({

    fun makeKernel(): Kernel {
        val mem = MemoryBus()
        val cpu = Cpu(mem)
        val timer = EmulatedTimer()
        timer.reset()
        val kernel = Kernel(mem, cpu, timer)
        kernel.init()
        return kernel
    }

    "resolve path with host0 device" {
        val kernel = makeKernel()
        val resolved = kernel.fileIo.resolvePath("host0:test.txt")
        resolved shouldNotBe null
        resolved!!.name shouldBe "test.txt"
    }

    "resolve path with ms0 device" {
        val kernel = makeKernel()
        val resolved = kernel.fileIo.resolvePath("ms0:PSP/GAME/test/test.prx")
        resolved shouldNotBe null
        resolved!!.path shouldContain "test.prx"
    }

    "open non-existent file returns -1" {
        val kernel = makeKernel()
        val fd = kernel.fileIo.open("host0:nonexistent_file_12345.txt", 0, 0)
        fd shouldBe -1
    }

    "open file with O_CREAT flag succeeds" {
        val kernel = makeKernel()
        val path = "host0:vesper_test_open.txt"
        val fd = kernel.fileIo.open(path, 0x200, 0x1FF)
        fd.shouldBeGreaterThan(0)

        kernel.fileIo.close(fd)
        File("vesper_test_open.txt").delete()
    }

    "write and read back" {
        val kernel = makeKernel()
        val path = "host0:vesper_test_rw.txt"
        val fd = kernel.fileIo.open(path, 0x200 or 0x1, 0x1FF)
        fd.shouldBeGreaterThan(0)

        val testData = "Hello Vesper!".toByteArray()
        kernel.memory.writeBytes(Address(0x08800000u), testData)

        val written = kernel.fileIo.write(fd, Address(0x08800000u), testData.size, kernel)
        written shouldBe testData.size

        kernel.fileIo.close(fd)

        val fd2 = kernel.fileIo.open(path, 0x1, 0x1FF)
        kernel.fileIo.seek(fd2, 0, 0)

        val readBuf = kernel.memory.readBytes(Address(0x08800000u), testData.size)
        String(readBuf) shouldBe "Hello Vesper!"

        kernel.fileIo.close(fd2)
        File("vesper_test_rw.txt").delete()
    }

    "readBytes from memory returns what was written" {
        val bus = MemoryBus()
        val data = byteArrayOf(0x41, 0x42, 0x43, 0x00, 0x44)
        bus.writeBytes(Address(0x08800000u), data)
        val read = bus.readBytes(Address(0x08800000u), 5)
        String(read) shouldBe "ABC\u0000D"
    }
})
