package vesper.core.loader

import vesper.common.Logger
import vesper.common.warn
import vesper.core.memory.Address

class ElfLoader {

    companion object {
        private val logTag = "ElfLoader"
    }

    sealed class LoadError(message: String? = null) : Exception(message) {
        class BadMagic(val found: ByteArray) : LoadError()
        class BadClass(val cls: Int) : LoadError()
        class BadEndian(val endian: Int) : LoadError()
        class BadMachine(val machine: Short) : LoadError()
        class UnsupportedType(val type: Short) : LoadError()
        class ParseError(message: String) : LoadError(message)
    }

    fun load(bytes: ByteArray): Result<ElfImage> {
        return runCatching {
            if (bytes.size < 52) throw LoadError.ParseError("File too small for ELF header")

            val magic = bytes.sliceArray(0 until 4)
            if (!magic.contentEquals(ElfConstants.ELF_MAGIC))
                throw LoadError.BadMagic(magic)

            if (bytes[4].toInt() != ElfConstants.ELFCLASS32)
                throw LoadError.BadClass(bytes[4].toInt())

            if (bytes[5].toInt() != ElfConstants.ELFDATA2LSB)
                throw LoadError.BadEndian(bytes[5].toInt())

            val ehdr = readEhdr(bytes)

            if (ehdr.machine.toInt() != ElfConstants.EM_MIPS)
                throw LoadError.BadMachine(ehdr.machine)

            if (ehdr.type.toInt() != ElfConstants.ET_EXEC && ehdr.type.toInt() != ElfConstants.ET_SCE_RELEXEC)
                throw LoadError.UnsupportedType(ehdr.type)

            val phdrs = readPhdrs(bytes, ehdr)

            val segments = mutableListOf<Segment>()
            var moduleName = ""

            for (phdr in phdrs) {
                when (phdr.type) {
                    ElfConstants.PT_LOAD -> {
                        val data = if (phdr.filesz > 0u)
                            bytes.sliceArray(phdr.offset.toInt() until (phdr.offset + phdr.filesz).toInt())
                        else
                            ByteArray(0)

                        segments.add(Segment(
                            vaddr = phdr.vaddr,
                            size = phdr.memsz,
                            data = data,
                            flags = phdr.flags,
                        ))
                    }
                }
            }

            moduleName = tryFindModuleName(bytes, phdrs)

            ElfImage(
                entryPoint = Address(ehdr.entry),
                segments = segments,
                moduleName = moduleName,
            )
        }
    }

    private fun readEhdr(bytes: ByteArray): Elf32Ehdr {
        return Elf32Ehdr(
            ident = bytes.sliceArray(0 until 16),
            type = bytes.read16(16),
            machine = bytes.read16(18),
            version = bytes.read32(20).toInt(),
            entry = bytes.read32(24),
            phoff = bytes.read32(28),
            shoff = bytes.read32(32),
            flags = bytes.read32(36).toInt(),
            ehsize = bytes.read16(40),
            phentsize = bytes.read16(42),
            phnum = bytes.read16(44),
            shentsize = bytes.read16(46),
            shnum = bytes.read16(48),
            shstrndx = bytes.read16(50),
        )
    }

    private fun readPhdrs(bytes: ByteArray, ehdr: Elf32Ehdr): List<Elf32Phdr> {
        val phdrs = mutableListOf<Elf32Phdr>()
        val phoff = ehdr.phoff.toInt()
        val phentsize = ehdr.phentsize.toInt()
        for (i in 0 until ehdr.phnum.toInt()) {
            val offset = phoff + i * phentsize
            if (offset + 32 > bytes.size) break
            phdrs.add(Elf32Phdr(
                type = bytes.read32(offset).toInt(),
                offset = bytes.read32(offset + 4),
                vaddr = bytes.read32(offset + 8),
                paddr = bytes.read32(offset + 12),
                filesz = bytes.read32(offset + 16),
                memsz = bytes.read32(offset + 20),
                flags = bytes.read32(offset + 24).toInt(),
                align = bytes.read32(offset + 28),
            ))
        }
        return phdrs
    }

    private fun tryFindModuleName(bytes: ByteArray, phdrs: List<Elf32Phdr>): String {
        val shdrs = readShdrs(bytes)
        for (shdr in shdrs) {
            if (shdr.type == ElfConstants.SHT_STRTAB) {
                val modInfo = findModuleInfoInSection(bytes, shdrs)
                if (modInfo != null) return modInfo.name
            }
        }
        return ""
    }

    private fun readShdrs(bytes: ByteArray): List<Elf32Shdr> {
        if (bytes.size < 52) return emptyList()
        val shoff = bytes.read32(32).toInt()
        val shentsize = bytes.read16(46).toInt()
        val shnum = bytes.read16(48).toInt()
        if (shoff <= 0 || shentsize < 40 || shnum <= 0) return emptyList()
        val shdrs = mutableListOf<Elf32Shdr>()
        for (i in 0 until shnum) {
            val offset = shoff + i * shentsize
            if (offset + 40 > bytes.size) break
            shdrs.add(Elf32Shdr(
                name = bytes.read32(offset).toInt(),
                type = bytes.read32(offset + 4).toInt(),
                flags = bytes.read32(offset + 8).toInt(),
                addr = bytes.read32(offset + 12),
                offset = bytes.read32(offset + 16),
                size = bytes.read32(offset + 20),
                link = bytes.read32(offset + 24).toInt(),
                info = bytes.read32(offset + 28).toInt(),
                addralign = bytes.read32(offset + 32).toInt(),
                entsize = bytes.read32(offset + 36),
            ))
        }
        return shdrs
    }

    private fun findModuleInfoInSection(bytes: ByteArray, shdrs: List<Elf32Shdr>): ModuleInfo? {
        for (shdr in shdrs) {
            if (shdr.type == ElfConstants.SHT_PROGBITS && shdr.size >= ModuleInfo.SIZE.toUInt()) {
                val offset = shdr.offset.toInt()
                if (offset + ModuleInfo.SIZE <= bytes.size) {
                    val sig = (bytes[offset].toInt() and 0xFF) or
                            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
                    if (sig == 0) {
                        val sectionData = bytes.sliceArray(offset until (offset + shdr.size.toInt()))
                        val modInfo = ModuleInfo.read(sectionData, 0)
                        return modInfo
                    }
                }
            }
        }
        return null
    }

    private fun ByteArray.read16(offset: Int): Short {
        return (((this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8))).toShort()
    }

    private fun ByteArray.read32(offset: Int): UInt {
        return ((this[offset].toInt() and 0xFF).toUInt() or
                ((this[offset + 1].toInt() and 0xFF).toUInt() shl 8) or
                ((this[offset + 2].toInt() and 0xFF).toUInt() shl 16) or
                ((this[offset + 3].toInt() and 0xFF).toUInt() shl 24))
    }
}
