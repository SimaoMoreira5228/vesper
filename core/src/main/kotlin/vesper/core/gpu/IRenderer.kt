package vesper.core.gpu

interface IRenderer {
    fun init(
        windowHandle: Long,
        width: Int,
        height: Int,
    )

    fun presentFrame(
        data: ByteArray,
        width: Int,
        height: Int,
        format: Int,
    )

    fun shutdown()

    val instanceHandle: Long
    val surfaceHandle: Long
}
