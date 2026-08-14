package vesper.core.cpu

import vesper.core.memory.Address

class CpuState {
    val gpr: IntArray = IntArray(32)
    var pc: Address = Address.ZERO
    var nextPc: Address = Address.ZERO
    var hi: Int = 0
    var lo: Int = 0
    var inDelaySlot: Boolean = false
    var exceptionPending: CpuException? = null

    val fpr: FloatArray = FloatArray(32)
    val fcr: IntArray = IntArray(32)
    val vpr: FloatArray = FloatArray(128) { Float.NaN }
    val vfpuCtrl: IntArray = IntArray(16)

    fun reset(entryPoint: Address) {
        gpr.fill(0)
        hi = 0
        lo = 0
        fpr.fill(0f)
        fcr.fill(0)
        vpr.fill(Float.NaN)
        vfpuCtrl.fill(0)
        fcr[0] = 0x00003351
        fcr[31] = 0x00000E00
        vfpuCtrl[0] = 0xE4
        vfpuCtrl[1] = 0xE4
        inDelaySlot = false
        exceptionPending = null
        pc = entryPoint
        nextPc = Address.ZERO
    }

    fun gpr(index: Int): Int {
        if (index == 0) return 0
        return gpr[index]
    }

    fun setGpr(index: Int, value: Int) {
        if (index != 0) gpr[index] = value
    }

    fun copyFrom(other: CpuState) {
        other.gpr.copyInto(gpr)
        pc = other.pc
        nextPc = other.nextPc
        hi = other.hi
        lo = other.lo
        other.fpr.copyInto(fpr)
        other.fcr.copyInto(fcr)
        other.vpr.copyInto(vpr)
        other.vfpuCtrl.copyInto(vfpuCtrl)
        inDelaySlot = other.inDelaySlot
        exceptionPending = other.exceptionPending
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("PC: $pc  HI: 0x${hi.toUInt().toString(16).padStart(8, '0')}  LO: 0x${lo.toUInt().toString(16).padStart(8, '0')}")
        sb.appendLine("DelaySlot: $inDelaySlot  nextPc: $nextPc")
        for (i in 0 until 32 step 4) {
            sb.append("R${i.toString().padStart(2, ' ')}: ")
            for (j in 0 until 4) {
                val idx = i + j
                if (idx > 0) {
                    sb.append("0x${gpr[idx].toUInt().toString(16).padStart(8, '0')} ")
                } else {
                    sb.append("0x00000000 ")
                }
            }
            sb.appendLine()
        }
        return sb.toString()
    }
}
