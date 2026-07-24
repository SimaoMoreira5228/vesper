package vesper.core.cpu

object Opcode {
    const val SPECIAL   = 0x00
    const val REGIMM    = 0x01
    const val J         = 0x02
    const val JAL       = 0x03
    const val BEQ       = 0x04
    const val BNE       = 0x05
    const val BLEZ      = 0x06
    const val BGTZ      = 0x07
    const val ADDI      = 0x08
    const val ADDIU     = 0x09
    const val SLTI      = 0x0A
    const val SLTIU     = 0x0B
    const val ANDI      = 0x0C
    const val ORI       = 0x0D
    const val XORI      = 0x0E
    const val LUI       = 0x0F
    const val COP0      = 0x10
    const val COP1      = 0x11
    const val COP2      = 0x12
    const val LB        = 0x20
    const val LH        = 0x21
    const val LWL       = 0x22
    const val LW        = 0x23
    const val LBU       = 0x24
    const val LHU       = 0x25
    const val LWR       = 0x26
    const val SB        = 0x28
    const val SH        = 0x29
    const val SWL       = 0x2A
    const val SW        = 0x2B
    const val SWR       = 0x2E
    const val LWC1      = 0x31
    const val SWC1      = 0x39
}

object Funct {
    const val SLL       = 0x00
    const val MOVCI     = 0x01
    const val SRL       = 0x02
    const val SRA       = 0x03
    const val SLLV      = 0x04
    const val SRLV      = 0x06
    const val SRAV      = 0x07
    const val JR        = 0x08
    const val JALR      = 0x09
    const val MOVZ      = 0x0A
    const val MOVN      = 0x0B
    const val SYSCALL   = 0x0C
    const val BREAK     = 0x0D
    const val MFHI      = 0x10
    const val MTHI      = 0x11
    const val MFLO      = 0x12
    const val MTLO      = 0x13
    const val MULT      = 0x18
    const val MULTU     = 0x19
    const val DIV       = 0x1A
    const val DIVU      = 0x1B
    const val ADD       = 0x20
    const val ADDU      = 0x21
    const val SUB       = 0x22
    const val SUBU      = 0x23
    const val AND       = 0x24
    const val OR        = 0x25
    const val XOR       = 0x26
    const val NOR       = 0x27
    const val SLT       = 0x2A
    const val SLTU      = 0x2B
}

object RegImm {
    const val BLTZ      = 0x00
    const val BGEZ      = 0x01
    const val BLTZL     = 0x02
    const val BGEZL     = 0x03
    const val BLTZAL    = 0x10
    const val BGEZAL    = 0x11
    const val BLTZALL   = 0x12
    const val BGEZALL   = 0x13
}

fun instructionOpcode(insn: Int): Int = (insn ushr 26) and 0x3F
fun instructionRs(insn: Int): Int = (insn ushr 21) and 0x1F
fun instructionRt(insn: Int): Int = (insn ushr 16) and 0x1F
fun instructionRd(insn: Int): Int = (insn ushr 11) and 0x1F
fun instructionShamt(insn: Int): Int = (insn ushr 6) and 0x1F
fun instructionFunct(insn: Int): Int = insn and 0x3F
fun instructionImmediate(insn: Int): Int = insn and 0xFFFF
fun instructionImmediateSigned(insn: Int): Int = (insn.toShort()).toInt()
fun instructionTarget(insn: Int): Int = insn and 0x3FFFFFF
