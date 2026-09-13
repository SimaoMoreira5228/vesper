package vesper.core.loader

import io.kotest.core.spec.style.StringSpec

class DebugRelocTest : StringSpec({

    fun resource(name: String): ByteArray {
        val path = "pspautotests/tests/$name"
        val url =
            DebugRelocTest::class.java.classLoader.getResource(path)
                ?: throw RuntimeException("Resource not found: $path")
        return url.readBytes()
    }

    fun read32(
        bytes: ByteArray,
        offset: Int,
    ): UInt {
        return (
            (bytes[offset].toInt() and 0xFF).toUInt() or
                ((bytes[offset + 1].toInt() and 0xFF).toUInt() shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF).toUInt() shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF).toUInt() shl 24)
        )
    }

    fun read16(
        bytes: ByteArray,
        offset: Int,
    ): Int {
        return ((bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8))
    }

    "check HI16 LO16 pairing" {
        val prx = resource("cpu/cpu_alu/cpu_alu.prx")

        val loader = ElfLoader()
        val image = loader.load(prx).getOrThrow()
        val seg = image.segments[0]

        // Print all HI16/LO16 offsets by group and their symIndex
        for ((gi, g) in image.relocGroups.withIndex()) {
            var pendingHi16: RelocEntry? = null
            for (r in g.relocs) {
                if (r.type == ElfConstants.R_MIPS_HI16) {
                    if (pendingHi16 != null) {
                        println("Group $gi: UNPAIRED HI16 at 0x${pendingHi16.offset.toString(16)} sym=${pendingHi16.symIndex}")
                    }
                    pendingHi16 = r
                } else if (r.type == ElfConstants.R_MIPS_LO16) {
                    if (pendingHi16 != null) {
                        println("Group $gi: PAIR HI16=0x${pendingHi16.offset.toString(16)} LO16=0x${r.offset.toString(16)} sym=${pendingHi16.symIndex}")
                        pendingHi16 = null
                    } else {
                        println("Group $gi: ORPHAN LO16 at 0x${r.offset.toString(16)} sym=${r.symIndex}")
                    }
                }
            }
            if (pendingHi16 != null) {
                println("Group $gi: UNPAIRED HI16 at 0x${pendingHi16.offset.toString(16)} sym=${pendingHi16.symIndex}")
            }
        }

        // Now check what info we have at r_offset 0x1c540 and 0x1c544
        val phoff = read32(prx, 28).toInt()
        val phentsize = read16(prx, 42)
        val phnum = read16(prx, 44)

        // Find PT_LOAD offset
        var loadOffset = 0u
        for (i in 0 until phnum) {
            val off = phoff + i * phentsize
            val ptype = read32(prx, off).toInt()
            val poffset = read32(prx, off + 4)
            if (ptype == 1) {
                loadOffset = poffset
                break
            }
        }

        fun fileAddr(vaddr: Int): Int = vaddr - 0 + loadOffset.toInt()

        fun vaddrOf(fileOff: Int): Int = fileOff - loadOffset.toInt() + 0

        // Instructions at vaddr 0x1c540 and 0x1c544
        val insnHi = read32(prx, fileAddr(0x1c540))
        val insnLo = read32(prx, fileAddr(0x1c544))
        println("\nInstructions at vaddr 0x1c540: 0x${insnHi.toString(16).padStart(8, '0')}")
        println("Instructions at vaddr 0x1c544: 0x${insnLo.toString(16).padStart(8, '0')}")

        // Check what's in the reloc section that covers these addresses
        val shoff = read32(prx, 32).toInt()
        val shentsize = read16(prx, 46)
        val shnum = read16(prx, 48)
        for (i in 0 until shnum) {
            val soff = shoff + i * shentsize
            val stype = read32(prx, soff + 4).toInt()
            val sadd = read32(prx, soff + 12)
            val soffset = read32(prx, soff + 16)
            val ssize = read32(prx, soff + 20)
            val endOffset = soffset.toInt() + ssize.toInt()
            if ((stype.toUInt() == 9u || stype.toUInt() == 0x700000A0u)) {
                // Does section cover addr 0x1c540?
                val relocBase = soffset.toInt()
                val relocCount = ssize.toInt() / 8
                for (j in 0 until relocCount) {
                    val eoff = relocBase + j * 8
                    val rOffset = read32(prx, eoff)
                    if (rOffset.toInt() == 0x1c540 || rOffset.toInt() == 0x1c544) {
                        val rInfo = read32(prx, eoff + 4)
                        val rType = rInfo.toInt() and 0xFF
                        val rSym = (rInfo.toInt() ushr 8) and 0xFFFFFF
                        println("  Found reloc for vaddr 0x${rOffset.toString(16)}: type=$rType sym=$rSym at section $i")
                    }
                }
            }
        }

        // Check the data at the address the LW points to
        // Address computed: 0x08810000 + (-9092) = 0x0880DC7C
        // But file: vaddr 0xDC7C → file offset 0xDC7C + loadOffset = 0xDCDC
        val fileOffAtLoad = 0xDC7C.toUInt() + loadOffset
        val wordAtLoadAddr = read32(prx, fileOffAtLoad.toInt())
        println("Word at file offset 0x${fileOffAtLoad.toString(16)} (LW target): 0x${wordAtLoadAddr.toString(16).padStart(8, '0')}")
        println("vaddr of file offset 0x${fileOffAtLoad.toString(16)} = 0x${(fileOffAtLoad - loadOffset).toString(16)}")
    }
})
