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

    const val SHT_SYMTAB = 2
    const val SHT_STRTAB = 3
    const val SHT_PROGBITS = 1

    const val PF_X = 1
    const val PF_W = 2
    const val PF_R = 4
}

data class Elf32Ehdr(
    val ident: ByteArray,
    val type: Short,
    val machine: Short,
    val version: Int,
    val entry: UInt,
    val phoff: UInt,
    val shoff: UInt,
    val flags: Int,
    val ehsize: Short,
    val phentsize: Short,
    val phnum: Short,
    val shentsize: Short,
    val shnum: Short,
    val shstrndx: Short,
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
)
