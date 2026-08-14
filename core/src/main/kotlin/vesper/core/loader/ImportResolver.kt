package vesper.core.loader

object ImportResolver {

    private fun ByteArray.cstr(offset: Int): String {
        val sb = StringBuilder()
        var i = offset
        while (i < size && this[i].toInt() != 0) { sb.append(this[i].toInt().toChar()); i++ }
        return sb.toString()
    }

    private fun ByteArray.u32(offset: Int): UInt {
        return ((this[offset].toInt() and 0xFF).toUInt() or
                ((this[offset + 1].toInt() and 0xFF).toUInt() shl 8) or
                ((this[offset + 2].toInt() and 0xFF).toUInt() shl 16) or
                ((this[offset + 3].toInt() and 0xFF).toUInt() shl 24))
    }

    fun parseImports(bytes: ByteArray, shdrs: List<Elf32Shdr>, shStrTab: ByteArray?): Pair<List<ImportEntry>, List<ImportModule>> {
        val sceStubTextSec = findSection(shdrs, shStrTab, ".sceStub.text")
        val libStubSec = findSection(shdrs, shStrTab, ".lib.stub")
        val rodataNidSec = findSection(shdrs, shStrTab, ".rodata.sceNid")

        val modules = parseLibStub(bytes, libStubSec)
        val imports = parseSceStubText(bytes, sceStubTextSec, rodataNidSec, modules)

        return Pair(imports, modules)
    }

    private fun findSection(shdrs: List<Elf32Shdr>, strTab: ByteArray?, name: String): Elf32Shdr? {
        return shdrs.find { shdr ->
            if (strTab == null) false
            else sectionName(strTab, shdr.name) == name
        }
    }

    private fun sectionName(strTab: ByteArray, nameOff: Int): String {
        val sb = StringBuilder()
        var i = nameOff
        while (i < strTab.size && strTab[i].toInt() != 0) { sb.append(strTab[i].toInt().toChar()); i++ }
        return sb.toString()
    }

    private fun parseLibStub(bytes: ByteArray, sec: Elf32Shdr?): List<ImportModule> {
        val modules = mutableListOf<ImportModule>()
        if (sec == null) return modules

        val base = sec.offset.toInt()
        val sz = sec.size.toInt()
        if (base + sz > bytes.size) return modules

        var pos = base
        val end = base + sz

        while (pos + 20 <= end) {
            val namePtr = bytes.u32(pos).toInt()
            val flagsVer = bytes.u32(pos + 4).toInt()
            val count = bytes.u32(pos + 8).toInt()
            val flags = flagsVer ushr 16
            val version = flagsVer and 0xFFFF

            val name = if (namePtr > 0 && namePtr < bytes.size) bytes.cstr(namePtr) else ""

            if (name.isNotEmpty()) {
                modules.add(ImportModule(name = name, flagsVer = flagsVer, nidCount = 0))
            }
            pos += 20
        }
        return modules
    }

    private fun parseSceStubText(
        bytes: ByteArray,
        sec: Elf32Shdr?,
        nidSec: Elf32Shdr?,
        modules: List<ImportModule>,
    ): List<ImportEntry> {
        val imports = mutableListOf<ImportEntry>()
        if (sec == null) return imports

        val stubBase = sec.offset.toInt()
        val stubSz = sec.size.toInt()
        if (stubBase + stubSz > bytes.size) return imports

        val stubCount = stubSz / 8

        if (nidSec != null) {
            val nidBase = nidSec.offset.toInt()
            val nidSz = nidSec.size.toInt()
            if (nidBase + nidSz <= bytes.size) {
                val nidCount = nidSz / 4
                for (i in 0 until nidCount.coerceAtMost(stubCount)) {
                    val nid = bytes.u32(nidBase + i * 4).toInt()
                    if (nid == 0) continue
                    val stubOff = i * 8
                    val stubAddr = sec.addr.toInt() + stubOff
                    imports.add(ImportEntry(stubAddr = stubAddr.toUInt(), nid = nid))
                }
                return imports
            }
        }

        for (i in 0 until stubCount) {
            val pos = stubBase + i * 8
            if (pos + 8 > bytes.size) break
            val modPtr = bytes.u32(pos)
            val nid = bytes.u32(pos + 4)
            val stubAddr = sec.addr.toInt() + i * 8
            if (modPtr != 0u || nid == 0u) continue
            imports.add(ImportEntry(stubAddr = stubAddr.toUInt(), nid = nid.toInt()))
        }
        return imports
    }

    fun parseRelocationTables(bytes: ByteArray, phdrs: List<Elf32Phdr>, segments: List<Segment>, shdrs: List<Elf32Shdr>): List<SegmentRelocs> {
        val groups = mutableListOf<SegmentRelocs>()

        for (phdr in phdrs) {
            if (phdr.type != ElfConstants.PT_SCE_PSPREL && phdr.type != ElfConstants.PT_SCE_PSPREL2) continue
            val base = phdr.vaddr.toInt()
            val sz = phdr.filesz.toInt()
            if (base + sz > bytes.size || sz < 4) continue
            var segIdx = -1
            for ((i, seg) in segments.withIndex()) {
                if (seg.vaddr == (phdr.paddr and 0x1FFFFFFFu)) { segIdx = i; break }
            }
            if (segIdx < 0) segIdx = 0
            if (phdr.type == ElfConstants.PT_SCE_PSPREL) {
                groups.add(SegmentRelocs(segIdx, parsePspRel(bytes, base, sz)))
            }
        }

        for (shdr in shdrs) {
            val shType = shdr.type.toUInt()
            if (shType != 9u && shType != ElfConstants.PT_SCE_PSPREL.toUInt()) continue
            val base = shdr.offset.toInt()
            val sz = shdr.size.toInt()
            if (base + sz > bytes.size || sz < 8) continue
            if (segments.isNotEmpty()) {
                groups.add(SegmentRelocs(0, parseElfRel(bytes, base, sz)))
            }
        }

        return groups
    }

    private fun parseElfRel(bytes: ByteArray, base: Int, sz: Int): List<RelocEntry> {
        val relocs = mutableListOf<RelocEntry>()
        val count = sz / 8
        for (i in 0 until count) {
            val off = base + i * 8
            if (off + 8 > bytes.size) break
            val rOffset = bytes.u32(off).toInt()
            val rInfo = bytes.u32(off + 4).toInt()
            val rType = rInfo and 0xFF
            val rSym = rInfo ushr 8
            relocs.add(RelocEntry(
                offset = rOffset.toUInt(),
                type = rType,
                symIndex = rSym,
                addend = 0,
            ))
        }
        return relocs
    }

    private fun parsePspRel(bytes: ByteArray, base: Int, sz: Int): List<RelocEntry> {
        val relocs = mutableListOf<RelocEntry>()
        val entrySize = ElfConstants.PSPREL_ENTRY_SIZE
        val count = sz / entrySize
        for (i in 0 until count) {
            val off = base + i * entrySize
            if (off + entrySize > bytes.size) break
            relocs.add(RelocEntry(
                offset = bytes.u32(off),
                type = bytes.u32(off + 4).toInt(),
                symIndex = bytes.u32(off + 8).toInt(),
                addend = bytes.u32(off + 12).toInt(),
            ))
        }
        return relocs
    }
}
