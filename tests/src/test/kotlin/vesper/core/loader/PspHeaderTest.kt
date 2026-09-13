package vesper.core.loader

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class PspHeaderTest : StringSpec({

    fun buildMinimalPspHeader(
        name: String = "test",
        compType: Int = PspHeaderConstants.COMPRESSION_PLAIN,
        segAddr: UInt = 0x08800000u,
        segSize: UInt = 0x1000u,
        entryOff: UInt = 0u,
    ): ByteArray {
        val headerSize = PspHeaderConstants.PSP_HEADER_SIZE
        val bytes = ByteArray(headerSize + 64) { 0 }

        fun w32(
            offset: Int,
            value: UInt,
        ) {
            bytes[offset] = (value and 0xFFu).toByte()
            bytes[offset + 1] = ((value shr 8) and 0xFFu).toByte()
            bytes[offset + 2] = ((value shr 16) and 0xFFu).toByte()
            bytes[offset + 3] = ((value shr 24) and 0xFFu).toByte()
        }

        bytes[0] = '~'.code.toByte()
        bytes[1] = 'P'.code.toByte()
        bytes[2] = 'S'.code.toByte()
        bytes[3] = 'P'.code.toByte()

        w32(0x04, 0x1000u)
        w32(0x06, compType.toUInt())

        val nameBytes = name.encodeToByteArray()
        nameBytes.copyInto(bytes, 0x0A)
        bytes[0x0A + nameBytes.size] = 0

        bytes[0x26] = 1
        bytes[0x27] = 1

        w32(0x28, 64u)
        w32(0x2C, 64u)
        w32(0x30, entryOff)
        w32(0x34, 0x10000u)
        w32(0x38, 0x200u)
        w32(0x44, segAddr)
        w32(0x54, segSize)
        w32(0x78, 0x06050010u)
        w32(0x7C, 0u)

        return bytes
    }

    "detect PSP magic" {
        val bytes = buildMinimalPspHeader()
        val header = PspHeader.parse(bytes)
        header shouldNotBe null
        header!!.moduleName shouldBe "test"
    }

    "reject non-PSP data" {
        val bytes = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())
        val header = PspHeader.parse(bytes)
        header shouldBe null
    }

    "reject too-short data" {
        val bytes = ByteArray(10) { 0 }
        val header = PspHeader.parse(bytes)
        header shouldBe null
    }

    "parse module name" {
        val bytes = buildMinimalPspHeader(name = "my_module")
        val header = PspHeader.parse(bytes)
        header!!.moduleName shouldBe "my_module"
    }

    "parse entry point from segment + entry" {
        val bytes = buildMinimalPspHeader(segAddr = 0x08800000u, entryOff = 0x100u)
        val header = PspHeader.parse(bytes)
        header!!.entryPoint shouldBe 0x08800100u
    }

    "parse header fields" {
        val bytes = buildMinimalPspHeader()
        val header = PspHeader.parse(bytes)!!

        header.moduleAttr shouldBe 0x1000
        header.compressionType shouldBe PspHeaderConstants.COMPRESSION_PLAIN
        header.numSegments shouldBe 1
        header.dataSize shouldBe 64u
        header.totalSize shouldBe 64u
        header.bssSize shouldBe 0x200u
        header.sdkVersion shouldBe 0x06050010
        header.encryptType shouldBe 0
        header.isUserModule shouldBe true
        header.isKernelModule shouldBe false
    }

    "parse all four segment addresses" {
        val bytes = buildMinimalPspHeader()
        val header = PspHeader.parse(bytes)!!

        header.segmentAddresses.size shouldBe 4
        header.segmentAddresses[0] shouldBe 0x08800000u
        header.segmentAddresses.drop(1).all { it == 0u } shouldBe true
    }

    "kern module detection" {
        val bytes = buildMinimalPspHeader()
        var header = PspHeader.parse(bytes)!!
        header.isUserModule shouldBe true

        val bytes2 = buildMinimalPspHeader()
        val w32 = { off: Int, v: UInt ->
            bytes2[off] = (v and 0xFFu).toByte()
            bytes2[off + 1] = ((v shr 8) and 0xFFu).toByte()
            bytes2[off + 2] = ((v shr 16) and 0xFFu).toByte()
            bytes2[off + 3] = ((v shr 24) and 0xFFu).toByte()
        }
        w32(0x34, 0xFFFF8000u)
        header = PspHeader.parse(bytes2)!!
        header.isKernelModule shouldBe true
        header.isUserModule shouldBe false
    }

    "compressed PRX detected" {
        val bytes = buildMinimalPspHeader(compType = PspHeaderConstants.COMPRESSION_GZIP)
        val header = PspHeader.parse(bytes)!!
        header.compressionType shouldBe PspHeaderConstants.COMPRESSION_GZIP
    }

    "plain PRX elf offset is 0x150" {
        val bytes = buildMinimalPspHeader()
        val header = PspHeader.parse(bytes)!!
        header.elfOffset shouldBe 0x150u
    }
})
