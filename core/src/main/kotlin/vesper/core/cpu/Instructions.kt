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
    const val SPECIAL2  = 0x1C
    const val SPECIAL3  = 0x1F
    const val COP0      = 0x10
    const val COP1      = 0x11
    const val COP2      = 0x12
    const val COP1X     = 0x13
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
    const val BEQL      = 0x14
    const val BNEL      = 0x15
    const val BLEZL     = 0x16
    const val BGTZL     = 0x17
    const val VFPU0     = 0x18
    const val VFPU1     = 0x19
    const val LV_S      = 0x32
    const val VFPU4     = 0x34
    const val LVL_Q     = 0x35
    const val LV_Q      = 0x36
    const val VFPU5     = 0x37
    const val SV_S      = 0x3A
    const val VFPU6     = 0x3C
    const val SVL_Q     = 0x3D
    const val SV_Q      = 0x3E
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
    const val MUL       = 0x2C
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

val opcodeNames = mapOf(
    Opcode.SPECIAL  to "SPECIAL",
    Opcode.REGIMM   to "REGIMM",
    Opcode.J        to "J",
    Opcode.JAL      to "JAL",
    Opcode.BEQ      to "BEQ",
    Opcode.BNE      to "BNE",
    Opcode.BLEZ     to "BLEZ",
    Opcode.BGTZ     to "BGTZ",
    Opcode.BEQL      to "BEQL",
    Opcode.BNEL      to "BNEL",
    Opcode.BLEZL     to "BLEZL",
    Opcode.BGTZL     to "BGTZL",
    Opcode.ADDI     to "ADDI",
    Opcode.ADDIU    to "ADDIU",
    Opcode.SLTI     to "SLTI",
    Opcode.SLTIU    to "SLTIU",
    Opcode.ANDI     to "ANDI",
    Opcode.ORI      to "ORI",
    Opcode.XORI     to "XORI",
    Opcode.LUI      to "LUI",
    Opcode.COP0     to "COP0",
    Opcode.COP1     to "COP1",
    Opcode.COP2     to "COP2",
    Opcode.LB       to "LB",
    Opcode.LH       to "LH",
    Opcode.LWL      to "LWL",
    Opcode.LW       to "LW",
    Opcode.LBU      to "LBU",
    Opcode.LHU      to "LHU",
    Opcode.LWR      to "LWR",
    Opcode.SB       to "SB",
    Opcode.SH       to "SH",
    Opcode.SWL      to "SWL",
    Opcode.SW       to "SW",
    Opcode.SWR      to "SWR",
    Opcode.LWC1     to "LWC1",
    Opcode.SWC1     to "SWC1",
)

val functNames = mapOf(
    Funct.SLL     to "SLL",
    Funct.MOVCI   to "MOVCI",
    Funct.SRL     to "SRL",
    Funct.SRA     to "SRA",
    Funct.SLLV    to "SLLV",
    Funct.SRLV    to "SRLV",
    Funct.SRAV    to "SRAV",
    Funct.JR      to "JR",
    Funct.JALR    to "JALR",
    Funct.MOVZ    to "MOVZ",
    Funct.MOVN    to "MOVN",
    Funct.SYSCALL to "SYSCALL",
    Funct.BREAK   to "BREAK",
    Funct.MFHI    to "MFHI",
    Funct.MTHI    to "MTHI",
    Funct.MFLO    to "MFLO",
    Funct.MTLO    to "MTLO",
    Funct.MULT    to "MULT",
    Funct.MULTU   to "MULTU",
    Funct.DIV     to "DIV",
    Funct.DIVU    to "DIVU",
    Funct.ADD     to "ADD",
    Funct.ADDU    to "ADDU",
    Funct.SUB     to "SUB",
    Funct.SUBU    to "SUBU",
    Funct.AND     to "AND",
    Funct.OR      to "OR",
    Funct.XOR     to "XOR",
    Funct.NOR     to "NOR",
    Funct.SLT     to "SLT",
    Funct.SLTU    to "SLTU",
)

val regimmNames = mapOf(
    RegImm.BLTZ    to "BLTZ",
    RegImm.BGEZ    to "BGEZ",
    RegImm.BLTZL   to "BLTZL",
    RegImm.BGEZL   to "BGEZL",
    RegImm.BLTZAL  to "BLTZAL",
    RegImm.BGEZAL  to "BGEZAL",
    RegImm.BLTZALL to "BLTZALL",
    RegImm.BGEZALL to "BGEZALL",
)

private val regNames = (0..31).map { i ->
    if (i == 0) "\$zero"
    else if (i == 1) "\$at"
    else if (i in 2..3) "\$v${i - 2}"
    else if (i in 4..7) "\$a${i - 4}"
    else if (i in 8..15) "\$t${i - 8}"
    else if (i in 16..23) "\$s${i - 16}"
    else if (i in 24..25) "\$t${i - 16}"
    else if (i == 26) "\$k0"
    else if (i == 27) "\$k1"
    else if (i == 28) "\$gp"
    else if (i == 29) "\$sp"
    else if (i == 30) "\$fp"
    else if (i == 31) "\$ra"
    else ""
}

fun regName(i: Int): String = regNames.getOrElse(i) { "?\$$i" }

fun disassemble(pc: Int, insn: Int): String {
    val op = instructionOpcode(insn)
    val rs = instructionRs(insn)
    val rt = instructionRt(insn)
    val rd = instructionRd(insn)
    val shamt = instructionShamt(insn)
    val funct = instructionFunct(insn)
    val imm = instructionImmediate(insn)
    val immSigned = instructionImmediateSigned(insn)
    val target = instructionTarget(insn)

    val opName = opcodeNames[op] ?: "0x${op.toString(16)}"

    return when (op) {
        Opcode.SPECIAL -> {
            when (funct) {
                Funct.SYSCALL -> "SYSCALL"
                Funct.BREAK -> "BREAK"
                Funct.JR -> "JR ${regName(rs)}"
                Funct.JALR -> "JALR ${regName(rs)}, ${regName(rd)}"
                Funct.MFHI -> "MFHI ${regName(rd)}"
                Funct.MTHI -> "MTHI ${regName(rs)}"
                Funct.MFLO -> "MFLO ${regName(rd)}"
                Funct.MTLO -> "MTLO ${regName(rs)}"
                Funct.SLL -> "SLL ${regName(rd)}, ${regName(rt)}, $shamt"
                Funct.SRL -> "SRL ${regName(rd)}, ${regName(rt)}, $shamt"
                Funct.SRA -> "SRA ${regName(rd)}, ${regName(rt)}, $shamt"
                Funct.SLLV -> "SLLV ${regName(rd)}, ${regName(rt)}, ${regName(rs)}"
                Funct.SRLV -> "SRLV ${regName(rd)}, ${regName(rt)}, ${regName(rs)}"
                Funct.SRAV -> "SRAV ${regName(rd)}, ${regName(rt)}, ${regName(rs)}"
                Funct.ADD -> "ADD ${regName(rd)}, ${regName(rs)}, ${regName(rt)}"
                Funct.ADDU -> "ADDU ${regName(rd)}, ${regName(rs)}, ${regName(rt)}"
                Funct.SUB -> "SUB ${regName(rd)}, ${regName(rs)}, ${regName(rt)}"
                Funct.SUBU -> "SUBU ${regName(rd)}, ${regName(rs)}, ${regName(rt)}"
                Funct.AND -> "AND ${regName(rd)}, ${regName(rs)}, ${regName(rt)}"
                Funct.OR -> "OR ${regName(rd)}, ${regName(rs)}, ${regName(rt)}"
                Funct.XOR -> "XOR ${regName(rd)}, ${regName(rs)}, ${regName(rt)}"
                Funct.NOR -> "NOR ${regName(rd)}, ${regName(rs)}, ${regName(rt)}"
                Funct.SLT -> "SLT ${regName(rd)}, ${regName(rs)}, ${regName(rt)}"
                Funct.SLTU -> "SLTU ${regName(rd)}, ${regName(rs)}, ${regName(rt)}"
                Funct.MULT -> "MULT ${regName(rs)}, ${regName(rt)}"
                Funct.MULTU -> "MULTU ${regName(rs)}, ${regName(rt)}"
                Funct.DIV -> "DIV ${regName(rs)}, ${regName(rt)}"
                Funct.DIVU -> "DIVU ${regName(rs)}, ${regName(rt)}"
                Funct.MOVZ -> "MOVZ ${regName(rd)}, ${regName(rs)}, ${regName(rt)}"
                Funct.MOVN -> "MOVN ${regName(rd)}, ${regName(rs)}, ${regName(rt)}"
                else -> "SPECIAL/0x${funct.toString(16)} ${regName(rs)} ${regName(rt)} ${regName(rd)}"
            }
        }

        Opcode.REGIMM -> {
            val rName = regimmNames[rt] ?: "REGIMM/0x${rt.toString(16)}"
            "$rName ${regName(rs)}, ${pc + 4 + (immSigned shl 2)}"
        }

        Opcode.J, Opcode.JAL -> {
            val name = if (op == Opcode.JAL) "JAL" else "J"
            "$name 0x${(((pc + 4) and -0x10000000) or (target shl 2)).toString(16)}"
        }

        Opcode.BEQ, Opcode.BNE, Opcode.BEQL, Opcode.BNEL -> {
            "$opName ${regName(rs)}, ${regName(rt)}, ${pc + 4 + (immSigned shl 2)}"
        }

        Opcode.BLEZ, Opcode.BGTZ, Opcode.BLEZL, Opcode.BGTZL -> {
            "$opName ${regName(rs)}, ${pc + 4 + (immSigned shl 2)}"
        }

        Opcode.ADDI, Opcode.ADDIU, Opcode.SLTI, Opcode.SLTIU,
        Opcode.ANDI, Opcode.ORI, Opcode.XORI -> {
            val name = opName
            val immVal = if (op == Opcode.ANDI || op == Opcode.ORI || op == Opcode.XORI) imm else immSigned
            "$name ${regName(rt)}, ${regName(rs)}, $immVal"
        }

        Opcode.LUI -> {
            "LUI ${regName(rt)}, 0x${imm.toString(16)}"
        }

        Opcode.LB, Opcode.LH, Opcode.LW, Opcode.LBU, Opcode.LHU, Opcode.LWL, Opcode.LWR,
        Opcode.SB, Opcode.SH, Opcode.SW, Opcode.SWL, Opcode.SWR -> {
            val name = opName
            "$name ${regName(rt)}, $immSigned(${regName(rs)})"
        }

        Opcode.COP0 -> "COP0 $rs $rt $rd"
        Opcode.COP1 -> disassembleCop1(pc, insn)
        Opcode.COP2 -> "COP2 $rs $rt $rd"
        Opcode.LWC1, Opcode.SWC1 -> "$opName ${regName(rt)}, $immSigned(${regName(rs)})"

        else -> "$opName ${regName(rs)}, ${regName(rt)}, ${regName(rd)}"
    }
}
