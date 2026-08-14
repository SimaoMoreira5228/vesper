package vesper.core.cpu

fun interface InstructionHandler {
    fun execute(cpu: Cpu, instruction: Int)
}

object OpcodeTable {

    private val handlers = arrayOfNulls<InstructionHandler>(64)
    private val specialHandlers = arrayOfNulls<InstructionHandler>(64)
    private val regimmHandlers = arrayOfNulls<InstructionHandler>(32)
    private val special2Handlers = arrayOfNulls<InstructionHandler>(64)
    private val special3Handlers = arrayOfNulls<InstructionHandler>(64)

    init {
        handlers[Opcode.SPECIAL] = InstructionHandler { cpu, insn ->
            val funct = instructionFunct(insn)
            specialHandlers[funct]?.execute(cpu, insn)
                ?: cpu.raiseException(CpuException.ReservedInstruction)
        }

        handlers[Opcode.REGIMM] = InstructionHandler { cpu, insn ->
            val rt = instructionRt(insn)
            regimmHandlers[rt]?.execute(cpu, insn)
                ?: cpu.raiseException(CpuException.ReservedInstruction)
        }

        handlers[Opcode.J] = InstructionHandler(ControlFlow::executeJ)
        handlers[Opcode.JAL] = InstructionHandler(ControlFlow::executeJal)
        handlers[Opcode.BEQ] = InstructionHandler(ControlFlow::executeBeq)
        handlers[Opcode.BNE] = InstructionHandler(ControlFlow::executeBne)
        handlers[Opcode.BLEZ] = InstructionHandler(ControlFlow::executeBlez)
        handlers[Opcode.BGTZ] = InstructionHandler(ControlFlow::executeBgtz)
        handlers[Opcode.BEQL] = InstructionHandler(ControlFlow::executeBeql)
        handlers[Opcode.BNEL] = InstructionHandler(ControlFlow::executeBnel)
        handlers[Opcode.BLEZL] = InstructionHandler(ControlFlow::executeBlezl)
        handlers[Opcode.BGTZL] = InstructionHandler(ControlFlow::executeBgtzl)
        handlers[Opcode.ADDI] = InstructionHandler(Arithmetic::executeAddi)
        handlers[Opcode.ADDIU] = InstructionHandler(Arithmetic::executeAddiu)
        handlers[Opcode.SLTI] = InstructionHandler(Arithmetic::executeSlti)
        handlers[Opcode.SLTIU] = InstructionHandler(Arithmetic::executeSltiu)
        handlers[Opcode.ANDI] = InstructionHandler(Arithmetic::executeAndi)
        handlers[Opcode.ORI] = InstructionHandler(Arithmetic::executeOri)
        handlers[Opcode.XORI] = InstructionHandler(Arithmetic::executeXori)
        handlers[Opcode.LUI] = InstructionHandler(Arithmetic::executeLui)
        handlers[Opcode.LB] = InstructionHandler(MemoryAccess::executeLb)
        handlers[Opcode.LH] = InstructionHandler(MemoryAccess::executeLh)
        handlers[Opcode.LWL] = InstructionHandler(MemoryAccess::executeLwl)
        handlers[Opcode.LW] = InstructionHandler(MemoryAccess::executeLw)
        handlers[Opcode.LBU] = InstructionHandler(MemoryAccess::executeLbu)
        handlers[Opcode.LHU] = InstructionHandler(MemoryAccess::executeLhu)
        handlers[Opcode.LWR] = InstructionHandler(MemoryAccess::executeLwr)
        handlers[Opcode.SB] = InstructionHandler(MemoryAccess::executeSb)
        handlers[Opcode.SH] = InstructionHandler(MemoryAccess::executeSh)
        handlers[Opcode.SWL] = InstructionHandler(MemoryAccess::executeSwl)
        handlers[Opcode.SW] = InstructionHandler(MemoryAccess::executeSw)
        handlers[Opcode.SWR] = InstructionHandler(MemoryAccess::executeSwr)
        handlers[Opcode.COP2] = InstructionHandler { _, _ -> }
        handlers[Opcode.COP1X] = InstructionHandler { _, _ -> }

        specialHandlers[Funct.SLL] = InstructionHandler(Arithmetic::executeSll)
        specialHandlers[Funct.SRL] = InstructionHandler(Arithmetic::executeSrl)
        specialHandlers[Funct.SRA] = InstructionHandler(Arithmetic::executeSra)
        specialHandlers[Funct.SLLV] = InstructionHandler(Arithmetic::executeSllv)
        specialHandlers[Funct.SRLV] = InstructionHandler(Arithmetic::executeSrlv)
        specialHandlers[Funct.SRAV] = InstructionHandler(Arithmetic::executeSrav)
        specialHandlers[Funct.JR] = InstructionHandler(ControlFlow::executeJr)
        specialHandlers[Funct.JALR] = InstructionHandler(ControlFlow::executeJalr)
        specialHandlers[Funct.MOVZ] = InstructionHandler(Arithmetic::executeMovz)
        specialHandlers[Funct.MOVN] = InstructionHandler(Arithmetic::executeMovn)
        specialHandlers[Funct.SYSCALL] = InstructionHandler(MultiplyDivide::executeSyscall)
        specialHandlers[Funct.BREAK] = InstructionHandler(MultiplyDivide::executeBreak)
        specialHandlers[Funct.MFHI] = InstructionHandler(MultiplyDivide::executeMfhi)
        specialHandlers[Funct.MTHI] = InstructionHandler(MultiplyDivide::executeMthi)
        specialHandlers[Funct.MFLO] = InstructionHandler(MultiplyDivide::executeMflo)
        specialHandlers[Funct.MTLO] = InstructionHandler(MultiplyDivide::executeMtlo)
        specialHandlers[Funct.MULT] = InstructionHandler(MultiplyDivide::executeMult)
        specialHandlers[Funct.MULTU] = InstructionHandler(MultiplyDivide::executeMultu)
        specialHandlers[Funct.DIV] = InstructionHandler(MultiplyDivide::executeDiv)
        specialHandlers[Funct.DIVU] = InstructionHandler(MultiplyDivide::executeDivu)
        specialHandlers[0x1C] = InstructionHandler(MultiplyDivide::executeMadd)
        specialHandlers[0x1D] = InstructionHandler(MultiplyDivide::executeMaddu)
        specialHandlers[Funct.MUL] = InstructionHandler(MultiplyDivide::executeMul)
        specialHandlers[Funct.ADD] = InstructionHandler(Arithmetic::executeAdd)
        specialHandlers[Funct.ADDU] = InstructionHandler(Arithmetic::executeAddu)
        specialHandlers[Funct.SUB] = InstructionHandler(Arithmetic::executeSub)
        specialHandlers[Funct.SUBU] = InstructionHandler(Arithmetic::executeSubu)
        specialHandlers[Funct.AND] = InstructionHandler(Arithmetic::executeAnd)
        specialHandlers[Funct.OR] = InstructionHandler(Arithmetic::executeOr)
        specialHandlers[Funct.XOR] = InstructionHandler(Arithmetic::executeXor)
        specialHandlers[Funct.NOR] = InstructionHandler(Arithmetic::executeNor)
        specialHandlers[Funct.SLT] = InstructionHandler(Arithmetic::executeSlt)
        specialHandlers[Funct.SLTU] = InstructionHandler(Arithmetic::executeSltu)
        specialHandlers[0x2D] = InstructionHandler(Arithmetic::executeMin)
        specialHandlers[0x2E] = InstructionHandler(Arithmetic::executeMax)

        regimmHandlers[RegImm.BLTZ] = InstructionHandler(ControlFlow::executeBltz)
        regimmHandlers[RegImm.BGEZ] = InstructionHandler(ControlFlow::executeBgez)
        regimmHandlers[RegImm.BLTZAL] = InstructionHandler(ControlFlow::executeBltzal)
        regimmHandlers[RegImm.BGEZAL] = InstructionHandler(ControlFlow::executeBgezal)
        regimmHandlers[RegImm.BLTZL] = InstructionHandler(ControlFlow::executeBltzl)
        regimmHandlers[RegImm.BGEZL] = InstructionHandler(ControlFlow::executeBgezl)
        regimmHandlers[RegImm.BLTZALL] = InstructionHandler(ControlFlow::executeBltzall)
        regimmHandlers[RegImm.BGEZALL] = InstructionHandler(ControlFlow::executeBgezall)

        handlers[Opcode.SPECIAL2] = InstructionHandler { cpu, insn ->
            val funct = instructionFunct(insn)
            special2Handlers[funct]?.execute(cpu, insn)
        }

        handlers[Opcode.SPECIAL3] = InstructionHandler { cpu, insn ->
            val funct = instructionFunct(insn)
            special3Handlers[funct]?.execute(cpu, insn)
                ?: cpu.raiseException(CpuException.ReservedInstruction)
        }

        special3Handlers[0x00] = InstructionHandler { cpu, insn ->
            if (instructionShamt(insn) == 16 && instructionRd(insn) == 0)
                Arithmetic.executeWsbh(cpu, insn)
            else
                Arithmetic.executeExt(cpu, insn)
        }
        special3Handlers[0x04] = InstructionHandler(Arithmetic::executeIns)
        special3Handlers[0x20] = InstructionHandler(Arithmetic::executeSeb)
        special3Handlers[0x21] = InstructionHandler(Arithmetic::executeSeh)

        special2Handlers[0x20] = InstructionHandler(Arithmetic::executeClz)
        special2Handlers[0x21] = InstructionHandler(Arithmetic::executeClo)
    }

    fun dispatch(cpu: Cpu, instruction: Int) {
        val opcode = instructionOpcode(instruction)
        handlers[opcode]?.execute(cpu, instruction)
            ?: cpu.raiseException(CpuException.ReservedInstruction)
    }
}
