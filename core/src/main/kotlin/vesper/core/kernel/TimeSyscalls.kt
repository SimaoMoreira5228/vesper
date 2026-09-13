package vesper.core.kernel

import vesper.core.IMemoryBus
import vesper.core.memory.Address
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun SyscallTable.registerTimeConversionSyscalls() {
    register(Nids.USEC2SYS_CLOCK, "sceKernelUSec2SysClock") { kernel, cpu ->
        val usec = cpu.state.gpr(4).toUInt().toULong()
        write64(kernel.memory, Address(cpu.state.gpr(5).toUInt()), usec)
        0
    }
    register(Nids.USEC2SYS_CLOCK_WIDE, "sceKernelUSec2SysClockWide") { _, cpu ->
        val usec = cpu.state.gpr(4).toUInt().toULong()
        cpu.state.setGpr(3, (usec shr 32).toInt())
        usec.toInt()
    }
    register(Nids.SYS_CLOCK2USEC, "sceKernelSysClock2USec") { kernel, cpu ->
        val clock = read64(kernel.memory, Address(cpu.state.gpr(4).toUInt()))
        writeOptional(kernel.memory, cpu.state.gpr(5), (clock / 1_000_000uL).toInt())
        writeOptional(kernel.memory, cpu.state.gpr(6), (clock % 1_000_000uL).toInt())
        0
    }
    register(Nids.SYS_CLOCK2USEC_WIDE, "sceKernelSysClock2USecWide") { kernel, cpu ->
        val clock = cpu.state.gpr(4).toUInt().toULong() or (cpu.state.gpr(5).toUInt().toULong() shl 32)
        writeOptional(kernel.memory, cpu.state.gpr(6), (clock / 1_000_000uL).toInt())
        writeOptional(kernel.memory, cpu.state.gpr(7), (clock % 1_000_000uL).toInt())
        0
    }
    register(Nids.RTC_GET_CURRENT_CLOCK, "sceRtcGetCurrentClock") { kernel, cpu ->
        writeClock(kernel.memory, Address(cpu.state.gpr(4).toUInt()), cpu.state.gpr(5))
        0
    }
    register(Nids.RTC_GET_CURRENT_CLOCK_LOCAL, "sceRtcGetCurrentClockLocalTime") { kernel, cpu ->
        writeClock(kernel.memory, Address(cpu.state.gpr(4).toUInt()), 0)
        0
    }
}

private fun writeClock(
    memory: IMemoryBus,
    timePtr: Address,
    timezoneMinutes: Int,
) {
    val now = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(timezoneMinutes.toLong())
    memory.write16(timePtr, now.year)
    memory.write16(timePtr + 2, now.monthValue)
    memory.write16(timePtr + 4, now.dayOfMonth)
    memory.write16(timePtr + 6, now.hour)
    memory.write16(timePtr + 8, now.minute)
    memory.write16(timePtr + 10, now.second)
    memory.write32(timePtr + 12, now.nano / 1000)
}

private fun read64(
    memory: IMemoryBus,
    address: Address,
): ULong {
    val low = memory.read32(address).toUInt().toULong()
    val high = memory.read32(address + 4).toUInt().toULong()
    return low or (high shl 32)
}

private fun write64(
    memory: IMemoryBus,
    address: Address,
    value: ULong,
) {
    memory.write32(address, value.toInt())
    memory.write32(address + 4, (value shr 32).toInt())
}

private fun writeOptional(
    memory: IMemoryBus,
    address: Int,
    value: Int,
) {
    if (address != 0) memory.write32(Address(address.toUInt()), value)
}
