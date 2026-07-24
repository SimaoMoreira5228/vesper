package vesper.common

fun Int.bit(offset: Int): Int = (this shr offset) and 1

fun Int.bits(hi: Int, lo: Int): Int {
    val width = hi - lo + 1
    return if (width >= 32) this else (this shr lo) and ((1 shl width) - 1)
}

fun Int.signExtend(bit: Int): Int {
    val shift = 31 - bit
    return (this shl shift) shr shift
}

fun Int.sextByte(): Int = this.toByte().toInt()

fun Int.sextHalf(): Int = this.toShort().toInt()

fun UInt.bit(offset: Int): UInt = (this shr offset) and 1u

fun UInt.bits(hi: Int, lo: Int): UInt {
    val width = hi - lo + 1
    return if (width >= 32) this else (this shr lo) and ((1u shl width) - 1u)
}

fun UInt.signExtend(bit: Int): UInt {
    val shift = 31 - bit
    return (this shl shift) shr shift
}

fun Int.rotR(amount: Int): Int {
    if (amount == 0) return this
    return (this ushr amount) or (this shl (32 - amount))
}
