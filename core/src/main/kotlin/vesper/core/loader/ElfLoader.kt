package vesper.core.loader

import vesper.core.memory.Address

class ElfLoader {
    companion object {
        private val logTag = "ElfLoader"
    }

    sealed class LoadError(message: String? = null) : Exception(message) {
        class BadMagic(val found: ByteArray) : LoadError()

        class BadClass(val cls: Int) : LoadError()

        class BadEndian(val endian: Int) : LoadError()

        class BadMachine(val machine: Int) : LoadError()

        class UnsupportedType(val type: Int) : LoadError()

        class BadPspHeader(message: String) : LoadError(message)

        class ParseError(message: String) : LoadError(message)
    }

    fun load(bytes: ByteArray): Result<ElfImage> {
        return runCatching {
            if (bytes.size < 52) throw LoadError.ParseError("File too small")
            if (isPspPrx(bytes)) return@runCatching loadPrx(bytes)
            if (isElf(bytes)) return@runCatching parseElf(bytes, 0u)
            throw LoadError.BadMagic(bytes.sliceArray(0 until 4))
        }
    }

    private fun isPspPrx(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == PspHeaderConstants.PSP_MAGIC[0] && bytes[1] == PspHeaderConstants.PSP_MAGIC[1] &&
            bytes[2] == PspHeaderConstants.PSP_MAGIC[2] && bytes[3] == PspHeaderConstants.PSP_MAGIC[3]

    private fun isElf(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == ElfConstants.ELFMAG0 && bytes[1] == ElfConstants.ELFMAG1 &&
            bytes[2] == ElfConstants.ELFMAG2 && bytes[3] == ElfConstants.ELFMAG3

    private fun loadPrx(bytes: ByteArray): ElfImage {
        val header = PspHeader.parse(bytes) ?: throw LoadError.BadPspHeader("Failed to parse PSP header")
        if (header.compressionType != PspHeaderConstants.COMPRESSION_PLAIN) {
            throw LoadError.BadPspHeader("Compressed PRX not supported (type=0x${header.compressionType.toString(16)})")
        }
        if (header.encryptType != 0) {
            throw LoadError.BadPspHeader("Encrypted PRX not supported (type=${header.encryptType})")
        }

        val elfOffset = header.elfOffset.toInt()
        val elfBytes = bytes.sliceArray(elfOffset until bytes.size)
        if (!isElf(elfBytes)) throw LoadError.BadPspHeader("No ELF data at PRX offset $elfOffset")

        val image = parseElf(elfBytes, elfOffset.toUInt())

        val prxSegments = mutableListOf<Segment>()
        for (i in 0 until header.numSegments.coerceAtMost(4)) {
            val addr = header.segmentAddresses[i]
            val size = header.segmentSizes[i]
            if (addr == 0u || size == 0u) continue
            val segVaddr = addr and 0x1FFFFFFFu
            val elfSeg = image.segments.find { it.vaddr == segVaddr }
            if (elfSeg != null) prxSegments.add(elfSeg.copy(requestedVaddr = segVaddr))
        }

        val allSegments = if (prxSegments.isNotEmpty()) prxSegments else image.segments
        val entryPoint =
            if (header.entryPoint > 0u) {
                header.entryPoint
            } else {
                header.segmentAddresses.getOrElse(0) { 0u }
            }

        return image.copy(
            entryPoint = Address(entryPoint),
            segments = allSegments,
            moduleName = header.moduleName.ifEmpty { image.moduleName },
            sdkVersion = header.sdkVersion,
            isPrx = true,
            pspHeader = header,
            moduleAttr = header.moduleAttr,
            numSegments = header.numSegments,
            bssSize = header.bssSize,
        )
    }

    private fun parseElf(
        bytes: ByteArray,
        fileBase: UInt,
    ): ElfImage {
        val ehdr = readEhdr(bytes)
        if (ehdr.machine != ElfConstants.EM_MIPS) throw LoadError.BadMachine(ehdr.machine)
        if (ehdr.type != ElfConstants.ET_EXEC && ehdr.type != ElfConstants.ET_SCE_RELEXEC) {
            throw LoadError.UnsupportedType(ehdr.type)
        }

        val phdrs = readPhdrs(bytes, ehdr)
        val segments = mutableListOf<Segment>()
        for (phdr in phdrs) {
            if (phdr.type == ElfConstants.PT_LOAD) {
                val segData =
                    if (phdr.filesz > 0u) {
                        bytes.sliceArray(phdr.offset.toInt() until (phdr.offset + phdr.filesz).toInt())
                    } else {
                        ByteArray(0)
                    }
                segments.add(Segment(phdr.vaddr, phdr.memsz, segData, phdr.flags, requestedVaddr = phdr.vaddr))
            }
        }

        val shdrs = readShdrs(bytes, ehdr)
        val shStrTab = readSectionNames(bytes, shdrs, ehdr)

        val (imports, importModules) = ImportResolver.parseImports(bytes, shdrs, shStrTab)
        val relocGroups = ImportResolver.parseRelocationTables(bytes, phdrs, segments, shdrs)
        val moduleInfo = tryFindModuleInfo(bytes, shdrs, shStrTab)

        return ElfImage(
            entryPoint = Address(ehdr.entry),
            segments = segments,
            moduleName = moduleInfo?.name ?: "",
            globalPointer = moduleInfo?.gp?.toUInt() ?: 0u,
            imports = imports,
            importModules = importModules,
            relocGroups = relocGroups,
        )
    }

    private fun readEhdr(bytes: ByteArray): Elf32Ehdr =
        Elf32Ehdr(
            ident = bytes.sliceArray(0 until 16),
            type = bytes.readU16(16), machine = bytes.readU16(18),
            version = bytes.read32(20).toInt(), entry = bytes.read32(24),
            phoff = bytes.read32(28), shoff = bytes.read32(32),
            flags = bytes.read32(36).toInt(),
            ehsize = bytes.read16(40), phentsize = bytes.read16(42),
            phnum = bytes.read16(44), shentsize = bytes.read16(46),
            shnum = bytes.read16(48), shstrndx = bytes.read16(50),
        )

    private fun readPhdrs(
        bytes: ByteArray,
        ehdr: Elf32Ehdr,
    ): List<Elf32Phdr> {
        val base = ehdr.phoff.toInt()
        val esize = ehdr.phentsize
        val list = mutableListOf<Elf32Phdr>()
        for (i in 0 until ehdr.phnum) {
            val off = base + i * esize
            if (off + 32 > bytes.size) break
            list.add(
                Elf32Phdr(
                    type = bytes.read32(off).toInt(),
                    offset = bytes.read32(off + 4),
                    vaddr = bytes.read32(off + 8),
                    paddr = bytes.read32(off + 12),
                    filesz = bytes.read32(off + 16),
                    memsz = bytes.read32(off + 20),
                    flags = bytes.read32(off + 24).toInt(),
                    align = bytes.read32(off + 28),
                ),
            )
        }
        return list
    }

    private fun readShdrs(
        bytes: ByteArray,
        ehdr: Elf32Ehdr,
    ): List<Elf32Shdr> {
        if (bytes.size < 52) return emptyList()
        val base = ehdr.shoff.toInt()
        val esize = ehdr.shentsize
        val count = ehdr.shnum
        if (base <= 0 || esize < 40 || count <= 0) return emptyList()
        val list = mutableListOf<Elf32Shdr>()
        for (i in 0 until count) {
            val off = base + i * esize
            if (off + 40 > bytes.size) break
            list.add(
                Elf32Shdr(
                    name = bytes.read32(off).toInt(),
                    type = bytes.read32(off + 4).toInt(),
                    flags = bytes.read32(off + 8).toInt(),
                    addr = bytes.read32(off + 12),
                    offset = bytes.read32(off + 16),
                    size = bytes.read32(off + 20),
                    link = bytes.read32(off + 24).toInt(),
                    info = bytes.read32(off + 28).toInt(),
                    addralign = bytes.read32(off + 32).toInt(),
                    entsize = bytes.read32(off + 36),
                ),
            )
        }
        return list
    }

    private fun readSectionNames(
        bytes: ByteArray,
        shdrs: List<Elf32Shdr>,
        ehdr: Elf32Ehdr,
    ): ByteArray? {
        val idx = ehdr.shstrndx
        if (idx < 0 || idx >= shdrs.size) return null
        if (idx >= shdrs.size) return null
        val sec = shdrs[idx]
        val off = sec.offset.toInt()
        val sz = sec.size.toInt()
        if (off + sz > bytes.size) return null
        return bytes.sliceArray(off until off + sz)
    }

    private fun tryFindModuleInfo(
        bytes: ByteArray,
        shdrs: List<Elf32Shdr>,
        names: ByteArray?,
    ): ModuleInfo? {
        val section =
            shdrs.firstOrNull { shdr ->
                names != null && sectionName(names, shdr.name) == ".rodata.sceModuleInfo"
            } ?: return null
        val off = section.offset.toInt()
        if (off < 0 || off + ModuleInfo.SIZE > bytes.size) return null
        return ModuleInfo.read(bytes, off)
    }

    private fun sectionName(
        names: ByteArray,
        offset: Int,
    ): String {
        if (offset !in names.indices) return ""
        var end = offset
        while (end < names.size && names[end].toInt() != 0) end++
        return String(names, offset, end - offset, Charsets.US_ASCII)
    }

    private fun ByteArray.read16(offset: Int): Int {
        return ((this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8))
    }

    private fun ByteArray.readU16(offset: Int): Int {
        return ((this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8))
    }

    private fun ByteArray.read32(offset: Int): UInt {
        return (
            (this[offset].toInt() and 0xFF).toUInt() or
                ((this[offset + 1].toInt() and 0xFF).toUInt() shl 8) or
                ((this[offset + 2].toInt() and 0xFF).toUInt() shl 16) or
                ((this[offset + 3].toInt() and 0xFF).toUInt() shl 24)
        )
    }
}
