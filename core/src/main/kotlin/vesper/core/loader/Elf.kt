package vesper.core.loader

object ElfConstants {
    const val ELFMAG0: Byte = 0x7F
    const val ELFMAG1: Byte = 'E'.code.toByte()
    const val ELFMAG2: Byte = 'L'.code.toByte()
    const val ELFMAG3: Byte = 'F'.code.toByte()
    val ELF_MAGIC = byteArrayOf(ELFMAG0, ELFMAG1, ELFMAG2, ELFMAG3)

    const val ELFCLASS32 = 1
    const val ELFDATA2LSB = 1
    const val EM_MIPS = 8
    const val ET_EXEC = 2
    const val ET_SCE_RELEXEC = 0xFFA0

    const val PT_LOAD = 1
    const val PT_SCE_PSPREL = 0x700000A0
    const val PT_SCE_PSPREL2 = 0x700000A1

    const val SHT_NULL = 0
    const val SHT_PROGBITS = 1
    const val SHT_SYMTAB = 2
    const val SHT_STRTAB = 3
    const val SHT_NOBITS = 8

    const val PF_X = 1
    const val PF_W = 2
    const val PF_R = 4

    const val SHF_EXECINSTR = 4
    const val SHF_ALLOC = 2
    const val SHF_WRITE = 1

    const val R_MIPS_NONE = 0
    const val R_MIPS_32 = 2
    const val R_MIPS_26 = 4
    const val R_MIPS_HI16 = 5
    const val R_MIPS_LO16 = 6
    const val R_MIPS_GPREL = 7
    const val R_MIPS_LITERAL = 8
    const val R_MIPS_GOT16 = 9
    const val R_MIPS_CALL16 = 10
    const val R_MIPS_GPREL32 = 12
    const val R_MIPS_SHIFT5 = 16
    const val R_MIPS_SHIFT6 = 17

    const val PSPREL_ENTRY_SIZE = 16
}

data class Elf32Ehdr(
    val ident: ByteArray,
    val type: Int,
    val machine: Int,
    val version: Int,
    val entry: UInt,
    val phoff: UInt,
    val shoff: UInt,
    val flags: Int,
    val ehsize: Int,
    val phentsize: Int,
    val phnum: Int,
    val shentsize: Int,
    val shnum: Int,
    val shstrndx: Int,
)

data class Elf32Phdr(
    val type: Int,
    val offset: UInt,
    val vaddr: UInt,
    val paddr: UInt,
    val filesz: UInt,
    val memsz: UInt,
    val flags: Int,
    val align: UInt,
)

data class Elf32Shdr(
    val name: Int,
    val type: Int,
    val flags: Int,
    val addr: UInt,
    val offset: UInt,
    val size: UInt,
    val link: Int,
    val info: Int,
    val addralign: Int,
    val entsize: UInt,
)

data class Segment(
    val vaddr: UInt,
    val size: UInt,
    val data: ByteArray,
    val flags: Int,
    val requestedVaddr: UInt = vaddr,
)

data class ImportEntry(
    val stubAddr: UInt,
    val nid: Int,
    val moduleName: String = "",
)

data class ImportModule(
    val name: String,
    val flagsVer: Int,
    val nidCount: Int,
)

data class RelocEntry(
    val offset: UInt,
    val type: Int,
    val symIndex: Int,
    val addend: Int,
)

data class SegmentRelocs(
    val segIndex: Int,
    val relocs: List<RelocEntry>,
)
