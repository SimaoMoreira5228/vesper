package vesper.core.loader

import vesper.common.Loggable
import vesper.common.info
import vesper.common.warn
import vesper.core.IMemoryBus
import vesper.core.memory.Address

object Relocation : Loggable {

    override val tag: String get() = "Relocation"

    fun applyAll(
        memory: IMemoryBus,
        segments: List<Segment>,
        relocGroups: List<SegmentRelocs>,
    ) {
        for (group in relocGroups) {
            val segIdx = group.segIndex
            if (segIdx < 0 || segIdx >= segments.size) continue
            val seg = segments[segIdx]
            val requestedBase = seg.requestedVaddr
            val actualBase = seg.vaddr
            val delta = actualBase.toLong() - requestedBase.toLong()

            if (delta == 0L && group.relocs.none { it.type == ElfConstants.R_MIPS_HI16 || it.type == ElfConstants.R_MIPS_LO16 }) {
                continue
            }

            info { "Applying ${group.relocs.size} relocations to segment $segIdx (delta=$delta)" }

            val pendingHi16 = mutableListOf<RelocEntry>()
            for (reloc in group.relocs) {
                if (reloc.type == ElfConstants.R_MIPS_HI16) {
                    pendingHi16 += reloc
                    continue
                }
                if (reloc.type == ElfConstants.R_MIPS_LO16) {
                    if (pendingHi16.isNotEmpty()) {
                        // PSP PRX relocation streams preserve HI16/LO16 order
                        // but may not preserve ELF symbol indices.
                        for (hi16 in pendingHi16) {
                            applyHi16Lo16Pair(memory, seg, hi16, reloc, delta)
                        }
                        pendingHi16.clear()
                    } else {
                        applySingle(memory, seg, reloc, delta)
                    }
                    continue
                }
                applySingle(memory, seg, reloc, delta)
            }

            for (leftover in pendingHi16) {
                applySingle(memory, seg, leftover, delta)
            }
        }
    }

    private fun applySingle(memory: IMemoryBus, seg: Segment, reloc: RelocEntry, delta: Long) {
        val addr = Address(seg.vaddr + reloc.offset)
        val word = memory.read32(addr)

        val patched = when (reloc.type) {
            ElfConstants.R_MIPS_NONE -> word

            ElfConstants.R_MIPS_32 -> {
                (word.toLong() + delta + reloc.addend.toLong()).toInt()
            }

            ElfConstants.R_MIPS_26 -> {
                val target = (word and 0x03FFFFFF).toLong() shl 2
                val fullAddr = (target + delta.toLong() + reloc.addend.toLong())
                val newField = ((fullAddr shr 2) and 0x3FFFFFF).toInt()
                (word and 0xFC000000.toInt()) or newField
            }

            ElfConstants.R_MIPS_HI16 -> {
                val val16 = (word.toUInt() and 0xFFFFu).toInt()
                val extended = (val16 shl 16)
                val result = (extended.toLong() + delta + reloc.addend.toLong()).toInt()
                val hi = ((result + 0x8000) ushr 16) and 0xFFFF
                (word and 0xFFFF0000.toInt()) or hi
            }

            ElfConstants.R_MIPS_LO16 -> {
                val val16 = (word.toUInt() and 0xFFFFu).toInt()
                val signExtended = if (val16 and 0x8000 != 0) val16 or (-1 shl 16) else val16
                val result = (signExtended.toLong() + delta + reloc.addend.toLong()).toInt()
                (word and 0xFFFF0000.toInt()) or (result and 0xFFFF)
            }

            ElfConstants.R_MIPS_GPREL -> {
                word
            }

            ElfConstants.R_MIPS_LITERAL -> {
                (word.toLong() + delta + reloc.addend.toLong()).toInt()
            }

            ElfConstants.R_MIPS_GOT16 -> {
                val val16 = (word.toUInt() and 0xFFFFu).toInt()
                val extended = (val16 shl 16)
                val result = (extended.toLong() + delta + reloc.addend.toLong()).toInt()
                val hi = (result ushr 16) and 0xFFFF
                (word and 0xFFFF0000.toInt()) or hi
            }

            ElfConstants.R_MIPS_CALL16 -> {
                val val16 = (word.toUInt() and 0xFFFFu).toInt()
                val extended = (val16 shl 16)
                val result = (extended.toLong() + delta + reloc.addend.toLong()).toInt()
                val hi = (result ushr 16) and 0xFFFF
                (word and 0xFFFF0000.toInt()) or hi
            }

            ElfConstants.R_MIPS_GPREL32 -> {
                word
            }

            ElfConstants.R_MIPS_SHIFT5 -> {
                val value = reloc.addend and 0x1F
                val mask = 0xFC1F83FF.toInt()
                (word and mask) or ((value and 0x1F) shl 11) or ((value shr 5) shl 16)
            }

            ElfConstants.R_MIPS_SHIFT6 -> {
                val value = reloc.addend and 0x3F
                val mask = 0xFC1F83FF.toInt()
                (word and mask) or ((value and 0x1F) shl 11) or ((value shr 5) shl 16)
            }

            else -> {
                warn { "Unsupported relocation type ${reloc.type} at $addr" }
                word
            }
        }

        if (patched != word) {
            memory.write32(addr, patched)
        }
    }

    private fun applyHi16Lo16Pair(
        memory: IMemoryBus,
        seg: Segment,
        hi16: RelocEntry,
        lo16: RelocEntry,
        delta: Long,
    ) {
        val addrHi = Address(seg.vaddr + hi16.offset)
        val addrLo = Address(seg.vaddr + lo16.offset)
        val wordHi = memory.read32(addrHi)
        val wordLo = memory.read32(addrLo)

        val hiPart = (wordHi.toUInt() and 0xFFFFu).toInt()
        val loPart = (wordLo.toUInt() and 0xFFFFu).toInt()
        val loSignExtended = if (loPart and 0x8000 != 0) loPart or (-1 shl 16) else loPart

        val fullAddr = ((hiPart shl 16) + loSignExtended)
        val adjusted = (fullAddr.toLong() + delta + hi16.addend).toInt()

        val newHi = (((adjusted + 0x8000) ushr 16) and 0xFFFF)
        val patchedHi = (wordHi and 0xFFFF0000.toInt()) or newHi

        val newLo = (adjusted and 0xFFFF)
        val patchedLo = (wordLo and 0xFFFF0000.toInt()) or newLo

        if (patchedHi != wordHi) memory.write32(addrHi, patchedHi)
        if (patchedLo != wordLo) memory.write32(addrLo, patchedLo)
    }
}
