package vesper.core.cpu

private val singleOperationNames =
    mapOf(
        0x00 to "ADD.S",
        0x01 to "SUB.S",
        0x02 to "MUL.S",
        0x03 to "DIV.S",
        0x04 to "SQRT.S",
        0x05 to "ABS.S",
        0x06 to "MOV.S",
        0x07 to "NEG.S",
        0x0C to "ROUND.W.S",
        0x0D to "TRUNC.W.S",
        0x0E to "CEIL.W.S",
        0x0F to "FLOOR.W.S",
        0x24 to "CVT.W.S",
    )

private val compareNames =
    listOf(
        "C.F.S", "C.UN.S", "C.EQ.S", "C.UEQ.S",
        "C.OLT.S", "C.ULT.S", "C.OLE.S", "C.ULE.S",
        "C.SF.S", "C.NGLE.S", "C.SEQ.S", "C.NGL.S",
        "C.LT.S", "C.NGE.S", "C.LE.S", "C.NGT.S",
    )

fun disassembleCop1(
    pc: Int,
    insn: Int,
): String {
    val rs = instructionRs(insn)
    val rt = instructionRt(insn)
    val fs = instructionRd(insn)
    val fd = instructionShamt(insn)
    val funct = instructionFunct(insn)
    return when (rs) {
        0 -> "MFC1 ${regName(rt)}, \$f$fs"
        2 -> "CFC1 ${regName(rt)}, \$f$fs"
        4 -> "MTC1 ${regName(rt)}, \$f$fs"
        6 -> "CTC1 ${regName(rt)}, \$f$fs"
        8 -> {
            val name =
                when (rt) {
                    0 -> "BC1F"
                    1 -> "BC1T"
                    2 -> "BC1FL"
                    3 -> "BC1TL"
                    else -> "BC1/0x${rt.toString(16)}"
                }
            "$name ${pc + 4 + (instructionImmediateSigned(insn) shl 2)}"
        }
        16 ->
            when {
                funct in 0x30..0x3F -> "${compareNames[funct and 0xF]} \$f$fs, \$f$rt"
                funct in 0x00..0x03 -> "${singleOperationNames[funct]} \$f$fd, \$f$fs, \$f$rt"
                funct in singleOperationNames -> "${singleOperationNames[funct]} \$f$fd, \$f$fs"
                else -> "COP1.S/0x${funct.toString(16)} \$f$fd, \$f$fs, \$f$rt"
            }
        20 ->
            if (funct == 0x20) {
                "CVT.S.W \$f$fd, \$f$fs"
            } else {
                "COP1.W/0x${funct.toString(16)} \$f$fd, \$f$fs"
            }
        else -> "COP1/0x${rs.toString(16)} ${regName(rt)}, \$f$fs"
    }
}
