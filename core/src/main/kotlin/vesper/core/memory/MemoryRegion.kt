package vesper.core.memory

enum class MemoryRegion(
    val base: Address,
    val size: UInt,
    val label: String,
) {
    SCRATCHPAD(Address(0x00010000u), 0x4000u, "Scratchpad"),
    VRAM(Address(0x04000000u), 0x400000u, "VRAM"),
    RAM_LOW(Address(0x08000000u), 0x2000000u, "Main RAM (32MB)"),
    RAM_HIGH(Address(0x88000000u), 0x2000000u, "Kernel RAM (32MB)"),
    UNCACHED_RAM_LOW(Address(0x40000000u), 0x2000000u, "Uncached Main RAM"),
    UNCACHED_RAM_HIGH(Address(0xC0000000u), 0x2000000u, "Uncached Kernel RAM"),
    IO(Address(0xBC000000u), 0x4000000u, "I/O Registers"),
    IO_ALT(Address(0x9C000000u), 0x4000000u, "I/O Registers (Alt)"),
    SCRATCHPAD_K1(Address(0xA0010000u), 0x4000u, "Scratchpad (K1)"),
    VRAM_K0(Address(0x84000000u), 0x400000u, "VRAM (K0)"),
    ;

    val end: Address get() = Address(base.value + size)

    fun contains(address: Address): Boolean = address >= base && address < end

    fun offset(address: Address): UInt = address.value - base.value

    companion object {
        private val regionsSorted: List<MemoryRegion> = entries.sortedBy { it.base.value }

        fun resolve(address: Address): MemoryRegion? {
            for (region in regionsSorted) {
                if (region.contains(address)) return region
            }
            return null
        }
    }
}
