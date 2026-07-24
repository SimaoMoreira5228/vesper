package vesper.core.memory

@JvmInline
value class Address(val value: UInt) {
    companion object {
        val ZERO = Address(0u)
        val MAX = Address(0xFFFFFFFFu)
        val INVALID = Address(0xDEADBEEFu)
    }

    operator fun plus(other: Address): Address = Address(value + other.value)
    operator fun plus(offset: UInt): Address = Address(value + offset)
    operator fun plus(offset: Int): Address = Address(value + offset.toUInt())
    operator fun minus(other: Address): Address = Address(value - other.value)
    operator fun minus(offset: UInt): Address = Address(value - offset)
    operator fun minus(offset: Int): Address = Address(value - offset.toUInt())
    operator fun compareTo(other: Address): Int = value.compareTo(other.value)
    operator fun inc(): Address = Address(value + 1u)
    operator fun dec(): Address = Address(value - 1u)

    fun alignedTo(alignment: Int): Boolean = value and (alignment.toUInt() - 1u) == 0u
    fun pageAligned(): Boolean = alignedTo(0x1000)

    override fun toString(): String = "0x${value.toString(16).padStart(8, '0').uppercase()}"
}

fun Address.isAligned16(): Boolean = alignedTo(2)
fun Address.isAligned32(): Boolean = alignedTo(4)
