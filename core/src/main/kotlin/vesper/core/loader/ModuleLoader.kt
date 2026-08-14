package vesper.core.loader

import vesper.common.Loggable
import vesper.common.info
import vesper.core.IMemoryBus
import vesper.core.cpu.Cpu
import vesper.core.kernel.Kernel
import vesper.core.memory.Address

class ModuleLoader : Loggable {

    override val tag: String get() = "ModuleLoader"
    private val defaultBase = 0x08804000u

    var stdoutAddr: Address? = null
        private set

    fun load(bytes: ByteArray, memory: IMemoryBus): Result<ElfImage> {
        val loader = ElfLoader()
        return loader.load(bytes).map { image ->
            val relocated = relocateImage(image)
            loadImageIntoMemory(relocated, memory)
            relocated
        }
    }

    fun loadAndResolve(
        bytes: ByteArray,
        memory: IMemoryBus,
        kernel: Kernel,
    ): Result<ElfImage> {
        val loader = ElfLoader()
        return loader.load(bytes).map { image ->
            val relocated = relocateImage(image)
            loadImageIntoMemory(relocated, memory)
            Relocation.applyAll(memory, relocated.segments, relocated.relocGroups)
            resolveImports(relocated, memory, kernel)
            startImage(relocated, kernel.cpu)
            relocated
        }
    }

    private fun relocateImage(image: ElfImage): ElfImage {
        val needsReloc = image.segments.any { it.vaddr == 0u }
        if (!needsReloc) return image

        val delta = defaultBase
        info { "Relocating by +0x${delta.toString(16)}" }

        val relocated = image.segments.map { seg ->
            seg.copy(vaddr = seg.vaddr + delta, requestedVaddr = seg.vaddr)
        }
        val newEntry = if (image.entryPoint.value < 0x100000u)
            Address(delta + image.entryPoint.value) else image.entryPoint

        val relocatedImports = image.imports.map { imp ->
            imp.copy(stubAddr = imp.stubAddr + delta)
        }

        val relocatedRelocGroups = image.relocGroups.map { group ->
            group.copy(relocs = group.relocs.map { reloc ->
                reloc.copy(offset = reloc.offset)
            })
        }

        return image.copy(
            segments = relocated,
            entryPoint = newEntry,
            imports = relocatedImports,
            relocGroups = relocatedRelocGroups,
            globalPointer = if (image.globalPointer < 0x100000u) image.globalPointer + delta else image.globalPointer,
        )
    }

    fun loadImageIntoMemory(image: ElfImage, memory: IMemoryBus) {
        for (seg in image.segments) {
            val addr = Address(seg.vaddr)
            memory.writeBytes(addr, seg.data)
            info { "Loaded segment at $addr (${seg.data.size} bytes)" }
        }

        for (seg in image.segments) {
            val implicitBss = seg.size.toInt() - seg.data.size
            if (implicitBss > 0) {
                val bssAddr = Address(seg.vaddr + seg.data.size.toUInt())
                memory.writeBytes(bssAddr, ByteArray(implicitBss))
                info { "Implicit BSS at $bssAddr ($implicitBss bytes)" }
            }
        }

        if (image.bssSize > 0u && image.segments.isNotEmpty()) {
            val lastSeg = image.segments.last()
            val bssAddr = Address(lastSeg.vaddr + lastSeg.size)
            memory.writeBytes(bssAddr, ByteArray(image.bssSize.toInt()))
            info { "BSS at $bssAddr (${image.bssSize} bytes)" }
        }
    }

    fun resolveImports(image: ElfImage, memory: IMemoryBus, kernel: Kernel) {
        for (entry in image.imports) {
            val addr = Address(entry.stubAddr)
            val syscallAddr = addr + 4
            memory.write32(syscallAddr, 0x0000000C)
            kernel.registerImport(syscallAddr, entry.nid)
            info { "Resolved import at $syscallAddr -> 0x${entry.nid.toString(16).padStart(8, '0')}" }
        }
    }

    fun startImage(image: ElfImage, cpu: Cpu) {
        cpu.reset()
        cpu.state.pc = image.entryPoint
        if (image.globalPointer != 0u) cpu.state.setGpr(28, image.globalPointer.toInt())
        val base = defaultBase.toInt()
        val segEnd = image.segments.maxOfOrNull { (it.vaddr + it.size).toInt() } ?: base
        val spAddr = 0x09F00000
        cpu.state.setGpr(29, spAddr)
        val argStrAddr = segEnd + 0x1000
        val memory = cpu.memory
        val argBytes = "pspautotest\u0000".encodeToByteArray()
        cpu.state.setGpr(4, argBytes.size)
        cpu.state.setGpr(5, argStrAddr)
        for ((i, b) in argBytes.withIndex()) {
            memory.write8(Address((argStrAddr + i).toUInt()), b.toInt())
        }
        info { "Starting module at ${image.entryPoint}, SP=0x${spAddr.toString(16)}, segEnd=0x${segEnd.toString(16)}" }
    }

}
