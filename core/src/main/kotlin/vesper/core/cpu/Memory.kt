package vesper.core.cpu

import vesper.common.Logger
import vesper.common.sextByte
import vesper.common.sextHalf
import vesper.common.warn
import vesper.core.memory.Address
import vesper.core.memory.isAligned32
import vesper.core.cpu.Cpu.Companion.logTag

object MemoryAccess {

    private fun loadAddress(cpu: Cpu, insn: Int): Address {
        val rs = cpu.state.gpr(instructionRs(insn))
        val imm = instructionImmediateSigned(insn)
        return Address((rs.toUInt() + imm.toUInt()))
    }

    fun executeLb(cpu: Cpu, insn: Int) {
        val addr = loadAddress(cpu, insn)
        if (!cpu.memory.contains(addr)) {
            cpu.raiseException(CpuException.AddressError(addr, false))
            return
        }
        cpu.state.setGpr(instructionRt(insn), cpu.memory.read8(addr).sextByte())
    }

    fun executeLbu(cpu: Cpu, insn: Int) {
        val addr = loadAddress(cpu, insn)
        if (!cpu.memory.contains(addr)) {
            cpu.raiseException(CpuException.AddressError(addr, false))
            return
        }
        cpu.state.setGpr(instructionRt(insn), cpu.memory.read8(addr) and 0xFF)
    }

    fun executeLh(cpu: Cpu, insn: Int) {
        val addr = loadAddress(cpu, insn)
        if (!cpu.memory.contains(addr)) {
            cpu.raiseException(CpuException.AddressError(addr, false))
            return
        }
        cpu.state.setGpr(instructionRt(insn), cpu.memory.read16(addr).sextHalf())
    }

    fun executeLhu(cpu: Cpu, insn: Int) {
        val addr = loadAddress(cpu, insn)
        if (!cpu.memory.contains(addr)) {
            cpu.raiseException(CpuException.AddressError(addr, false))
            return
        }
        cpu.state.setGpr(instructionRt(insn), cpu.memory.read16(addr) and 0xFFFF)
    }

    fun executeLw(cpu: Cpu, insn: Int) {
        val addr = loadAddress(cpu, insn)
        if (!cpu.memory.contains(addr)) {
            cpu.raiseException(CpuException.AddressError(addr, false))
            return
        }
        if (!addr.isAligned32()) {
            cpu.raiseException(CpuException.AddressError(addr, false))
            return
        }
        cpu.state.setGpr(instructionRt(insn), cpu.memory.read32(addr))
    }

    fun executeSb(cpu: Cpu, insn: Int) {
        val addr = loadAddress(cpu, insn)
        if (!cpu.memory.contains(addr)) {
            cpu.raiseException(CpuException.AddressError(addr, true))
            return
        }
        val rt = cpu.state.gpr(instructionRt(insn))
        cpu.memory.write8(addr, rt and 0xFF)
    }

    fun executeSh(cpu: Cpu, insn: Int) {
        val addr = loadAddress(cpu, insn)
        if (!cpu.memory.contains(addr)) {
            cpu.raiseException(CpuException.AddressError(addr, true))
            return
        }
        val rt = cpu.state.gpr(instructionRt(insn))
        cpu.memory.write16(addr, rt and 0xFFFF)
    }

    fun executeSw(cpu: Cpu, insn: Int) {
        val addr = loadAddress(cpu, insn)
        if (!cpu.memory.contains(addr)) {
            cpu.raiseException(CpuException.AddressError(addr, true))
            return
        }
        if (!addr.isAligned32()) {
            cpu.raiseException(CpuException.AddressError(addr, true))
            return
        }
        val rt = cpu.state.gpr(instructionRt(insn))
        cpu.memory.write32(addr, rt)
    }

    fun executeLwl(cpu: Cpu, insn: Int) {
        val addr = loadAddress(cpu, insn)
        if (!cpu.memory.contains(addr)) {
            cpu.raiseException(CpuException.AddressError(addr, false))
            return
        }
        val rt = instructionRt(insn)
        val alignedAddr = Address(addr.value and 0xFFFFFFFCu)
        val offset = (addr.value and 3u).toInt()
        val shift = offset * 8
        val mem = cpu.memory.read32(alignedAddr)
        val current = cpu.state.gpr(rt)
        val memMask = (-1 shl shift)
        val currentMask = memMask.inv()
        val newVal = (mem and memMask) or (current and currentMask)
        cpu.state.setGpr(rt, newVal)
    }

    fun executeLwr(cpu: Cpu, insn: Int) {
        val addr = loadAddress(cpu, insn)
        if (!cpu.memory.contains(addr)) {
            cpu.raiseException(CpuException.AddressError(addr, false))
            return
        }
        val rt = instructionRt(insn)
        val alignedAddr = Address(addr.value and 0xFFFFFFFCu)
        val offset = (addr.value and 3u).toInt()
        val shift = offset * 8
        val mem = cpu.memory.read32(alignedAddr)
        val current = cpu.state.gpr(rt)
        val memMask = (1 shl shift) - 1
        val currentMask = memMask.inv()
        val newVal = (mem and memMask) or (current and currentMask)
        cpu.state.setGpr(rt, newVal)
    }

    fun executeSwl(cpu: Cpu, insn: Int) {
        val addr = loadAddress(cpu, insn)
        if (!cpu.memory.contains(addr)) {
            cpu.raiseException(CpuException.AddressError(addr, true))
            return
        }
        val alignedAddr = Address(addr.value and 0xFFFFFFFCu)
        val offset = (addr.value and 3u).toInt()
        val shift = offset * 8
        val rt = cpu.state.gpr(instructionRt(insn))
        val mem = cpu.memory.read32(alignedAddr)
        val regMask = (-1 shl shift)
        val memMask = regMask.inv()
        val newVal = (rt and regMask) or (mem and memMask)
        cpu.memory.write32(alignedAddr, newVal)
    }

    fun executeSwr(cpu: Cpu, insn: Int) {
        val addr = loadAddress(cpu, insn)
        if (!cpu.memory.contains(addr)) {
            cpu.raiseException(CpuException.AddressError(addr, true))
            return
        }
        val alignedAddr = Address(addr.value and 0xFFFFFFFCu)
        val offset = (addr.value and 3u).toInt()
        val shift = offset * 8
        val rt = cpu.state.gpr(instructionRt(insn))
        val mem = cpu.memory.read32(alignedAddr)
        val regMask = (1 shl shift) - 1
        val memMask = regMask.inv()
        val newVal = (rt and regMask) or (mem and memMask)
        cpu.memory.write32(alignedAddr, newVal)
    }
}
