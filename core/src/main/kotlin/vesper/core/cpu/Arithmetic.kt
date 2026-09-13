package vesper.core.cpu

object Arithmetic {
    fun executeAdd(
        cpu: Cpu,
        insn: Int,
    ) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val rt = cpu.state.gpr(instructionRt(insn))
        val rd = instructionRd(insn)
        val result = rs.toLong() + rt.toLong()
        if (result != (result.toInt()).toLong()) {
            cpu.raiseException(CpuException.ArithmeticOverflow)
            return
        }
        cpu.state.setGpr(rd, result.toInt())
    }

    fun executeAddu(
        cpu: Cpu,
        insn: Int,
    ) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val rt = cpu.state.gpr(instructionRt(insn))
        cpu.state.setGpr(instructionRd(insn), rs + rt)
    }

    fun executeSub(
        cpu: Cpu,
        insn: Int,
    ) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val rt = cpu.state.gpr(instructionRt(insn))
        val result = rs.toLong() - rt.toLong()
        if (result != (result.toInt()).toLong()) {
            cpu.raiseException(CpuException.ArithmeticOverflow)
            return
        }
        cpu.state.setGpr(instructionRd(insn), result.toInt())
    }

    fun executeSubu(
        cpu: Cpu,
        insn: Int,
    ) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val rt = cpu.state.gpr(instructionRt(insn))
        cpu.state.setGpr(instructionRd(insn), rs - rt)
    }

    fun executeAddi(
        cpu: Cpu,
        insn: Int,
    ) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val imm = instructionImmediateSigned(insn)
        val result = rs.toLong() + imm.toLong()
        if (result != (result.toInt()).toLong()) {
            cpu.raiseException(CpuException.ArithmeticOverflow)
            return
        }
        cpu.state.setGpr(instructionRt(insn), result.toInt())
    }

    fun executeAddiu(
        cpu: Cpu,
        insn: Int,
    ) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val imm = instructionImmediateSigned(insn)
        cpu.state.setGpr(instructionRt(insn), rs + imm)
    }

    fun executeSlt(
        cpu: Cpu,
        insn: Int,
    ) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val rt = cpu.state.gpr(instructionRt(insn))
        cpu.state.setGpr(instructionRd(insn), if (rs < rt) 1 else 0)
    }

    fun executeSltu(
        cpu: Cpu,
        insn: Int,
    ) {
        val rs = cpu.state.gpr(instructionRs(insn)).toUInt()
        val rt = cpu.state.gpr(instructionRt(insn)).toUInt()
        cpu.state.setGpr(instructionRd(insn), if (rs < rt) 1 else 0)
    }

    fun executeSlti(
        cpu: Cpu,
        insn: Int,
    ) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val imm = instructionImmediateSigned(insn)
        cpu.state.setGpr(instructionRt(insn), if (rs < imm) 1 else 0)
    }

    fun executeSltiu(
        cpu: Cpu,
        insn: Int,
    ) {
        val rs = cpu.state.gpr(instructionRs(insn)).toUInt()
        val imm = instructionImmediateSigned(insn).toUInt()
        cpu.state.setGpr(instructionRt(insn), if (rs < imm) 1 else 0)
    }

    fun executeAnd(
        cpu: Cpu,
        insn: Int,
    ) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val rt = cpu.state.gpr(instructionRt(insn))
        cpu.state.setGpr(instructionRd(insn), rs and rt)
    }

    fun executeOr(
        cpu: Cpu,
        insn: Int,
    ) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val rt = cpu.state.gpr(instructionRt(insn))
        cpu.state.setGpr(instructionRd(insn), rs or rt)
    }

    fun executeXor(
        cpu: Cpu,
        insn: Int,
    ) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val rt = cpu.state.gpr(instructionRt(insn))
        cpu.state.setGpr(instructionRd(insn), rs xor rt)
    }

    fun executeNor(
        cpu: Cpu,
        insn: Int,
    ) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val rt = cpu.state.gpr(instructionRt(insn))
        cpu.state.setGpr(instructionRd(insn), (rs or rt).inv())
    }

    fun executeAndi(
        cpu: Cpu,
        insn: Int,
    ) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val imm = instructionImmediate(insn)
        cpu.state.setGpr(instructionRt(insn), rs and imm)
    }

    fun executeOri(
        cpu: Cpu,
        insn: Int,
    ) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val imm = instructionImmediate(insn)
        cpu.state.setGpr(instructionRt(insn), rs or imm)
    }

    fun executeXori(
        cpu: Cpu,
        insn: Int,
    ) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val imm = instructionImmediate(insn)
        cpu.state.setGpr(instructionRt(insn), rs xor imm)
    }

    fun executeLui(
        cpu: Cpu,
        insn: Int,
    ) {
        val imm = instructionImmediate(insn)
        cpu.state.setGpr(instructionRt(insn), imm shl 16)
    }

    fun executeMovz(
        cpu: Cpu,
        insn: Int,
    ) {
        val rt = cpu.state.gpr(instructionRt(insn))
        if (rt == 0) {
            val rs = cpu.state.gpr(instructionRs(insn))
            cpu.state.setGpr(instructionRd(insn), rs)
        }
    }

    fun executeMovn(
        cpu: Cpu,
        insn: Int,
    ) {
        val rt = cpu.state.gpr(instructionRt(insn))
        if (rt != 0) {
            val rs = cpu.state.gpr(instructionRs(insn))
            cpu.state.setGpr(instructionRd(insn), rs)
        }
    }

    fun executeSll(
        cpu: Cpu,
        insn: Int,
    ) {
        val rt = cpu.state.gpr(instructionRt(insn))
        val shamt = instructionShamt(insn)
        cpu.state.setGpr(instructionRd(insn), rt shl shamt)
    }

    fun executeSrl(
        cpu: Cpu,
        insn: Int,
    ) {
        val rt = cpu.state.gpr(instructionRt(insn))
        val shamt = instructionShamt(insn)
        cpu.state.setGpr(instructionRd(insn), rt ushr shamt)
    }

    fun executeSra(
        cpu: Cpu,
        insn: Int,
    ) {
        val rt = cpu.state.gpr(instructionRt(insn))
        val shamt = instructionShamt(insn)
        cpu.state.setGpr(instructionRd(insn), rt shr shamt)
    }

    fun executeSllv(
        cpu: Cpu,
        insn: Int,
    ) {
        val rt = cpu.state.gpr(instructionRt(insn))
        val rs = cpu.state.gpr(instructionRs(insn))
        cpu.state.setGpr(instructionRd(insn), rt shl (rs and 0x1F))
    }

    fun executeSrlv(
        cpu: Cpu,
        insn: Int,
    ) {
        val rt = cpu.state.gpr(instructionRt(insn))
        val rs = cpu.state.gpr(instructionRs(insn))
        cpu.state.setGpr(instructionRd(insn), rt ushr (rs and 0x1F))
    }

    fun executeSrav(
        cpu: Cpu,
        insn: Int,
    ) {
        val rt = cpu.state.gpr(instructionRt(insn))
        val rs = cpu.state.gpr(instructionRs(insn))
        cpu.state.setGpr(instructionRd(insn), rt shr (rs and 0x1F))
    }

    fun executeRor(
        cpu: Cpu,
        insn: Int,
    ) {
        val rt = cpu.state.gpr(instructionRt(insn))
        val shift = instructionShamt(insn)
        cpu.state.setGpr(instructionRd(insn), if (shift == 0) rt else (rt ushr shift) or (rt shl (32 - shift)))
    }

    fun executeRorv(
        cpu: Cpu,
        insn: Int,
    ) {
        val rt = cpu.state.gpr(instructionRt(insn))
        val shift = cpu.state.gpr(instructionRs(insn)) and 0x1F
        cpu.state.setGpr(instructionRd(insn), if (shift == 0) rt else (rt ushr shift) or (rt shl (32 - shift)))
    }

    fun executeSeb(
        cpu: Cpu,
        insn: Int,
    ) {
        val rt = cpu.state.gpr(instructionRt(insn))
        cpu.state.setGpr(instructionRd(insn), rt.toByte().toInt())
    }

    fun executeSeh(
        cpu: Cpu,
        insn: Int,
    ) {
        val rt = cpu.state.gpr(instructionRt(insn))
        cpu.state.setGpr(instructionRd(insn), rt.toShort().toInt())
    }

    fun executeWsbh(
        cpu: Cpu,
        insn: Int,
    ) {
        val rt = cpu.state.gpr(instructionRt(insn))
        val result = ((rt ushr 8) and 0x00FF00FF) or ((rt shl 8) and 0xFF00FF00.toInt())
        cpu.state.setGpr(instructionRd(insn), result)
    }

    fun executeWsbw(
        cpu: Cpu,
        insn: Int,
    ) {
        val rt = cpu.state.gpr(instructionRt(insn))
        val result =
            ((rt ushr 24) and 0x000000FF) or
                ((rt ushr 8) and 0x0000FF00) or
                ((rt shl 8) and 0x00FF0000) or
                ((rt shl 24) and 0xFF000000.toInt())
        cpu.state.setGpr(instructionRd(insn), result)
    }

    fun executeBitrev(
        cpu: Cpu,
        insn: Int,
    ) {
        val value = cpu.state.gpr(instructionRt(insn))
        cpu.state.setGpr(instructionRd(insn), Integer.reverse(value))
    }

    fun executeMin(
        cpu: Cpu,
        insn: Int,
    ) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val rt = cpu.state.gpr(instructionRt(insn))
        cpu.state.setGpr(instructionRd(insn), if (rs < rt) rs else rt)
    }

    fun executeMax(
        cpu: Cpu,
        insn: Int,
    ) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val rt = cpu.state.gpr(instructionRt(insn))
        cpu.state.setGpr(instructionRd(insn), if (rs > rt) rs else rt)
    }

    fun executeClz(
        cpu: Cpu,
        insn: Int,
    ) {
        val rs = cpu.state.gpr(instructionRs(insn))
        if (rs == 0) {
            cpu.state.setGpr(instructionRd(insn), 32)
            return
        }
        cpu.state.setGpr(instructionRd(insn), rs.countLeadingZeroBits())
    }

    fun executeClo(
        cpu: Cpu,
        insn: Int,
    ) {
        val rs = cpu.state.gpr(instructionRs(insn))
        if (rs == -1) {
            cpu.state.setGpr(instructionRd(insn), 32)
            return
        }
        cpu.state.setGpr(instructionRd(insn), rs.inv().countLeadingZeroBits())
    }

    fun executeExt(
        cpu: Cpu,
        insn: Int,
    ) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val rt = instructionRt(insn)
        val pos = instructionShamt(insn)
        val size = (instructionRd(insn) and 0x1F) + 1
        cpu.state.setGpr(rt, (rs ushr pos) and ((1 shl size) - 1))
    }

    fun executeIns(
        cpu: Cpu,
        insn: Int,
    ) {
        val rs = cpu.state.gpr(instructionRs(insn))
        val rt = instructionRt(insn)
        val pos = instructionShamt(insn)
        val msbd = instructionRd(insn) and 0x1F
        val size = msbd - pos + 1
        if (size <= 0) return
        val mask = ((1 shl size) - 1) shl pos
        val current = cpu.state.gpr(rt)
        cpu.state.setGpr(rt, (current and mask.inv()) or ((rs shl pos) and mask))
    }
}
