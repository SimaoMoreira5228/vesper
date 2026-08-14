package vesper.core.kernel

import vesper.common.Loggable
import vesper.common.info
import vesper.common.warn
import vesper.core.IMemoryBus
import vesper.core.memory.Address

class KemulatorDevice(
    var onOutput: ((String) -> Unit)? = null,
) : Loggable {

    override val tag: String get() = "Kemulator"

    companion object {
        const val DEVCTL_IS_EMULATOR = 0x00000003
        const val DEVCTL_SEND_OUTPUT = 0x00000002
        const val DEVCTL_GET_HAS_DISPLAY = 0x00000001
        const val DEVCTL_EMIT_SCREENSHOT = 0x00000020
        const val DEVCTL_SEND_CTRLDATA = 0x00000010
        private const val maxCapturedWrite = 1024 * 1024
    }

    private val capturedOutput = StringBuilder()
    private enum class OutputSource { DEVCTL, WRITE, DIRECT }
    private var lastOutputSource: OutputSource? = null
    private var lastOutputText: String? = null
    private val sourceWrites = IntArray(OutputSource.entries.size)
    private val sourceChars = IntArray(OutputSource.entries.size)

    val output: String get() = capturedOutput.toString()

    fun handleDevctl(
        deviceName: String,
        cmd: Int,
        inData: ByteArray?,
        inLen: Int,
        outData: ByteArray?,
    ): Int {
        val lower = deviceName.lowercase()
        return when {
            lower.startsWith("kemulator:") -> handleKemulator(cmd, inData, outData)
            lower.startsWith("emulator:") -> handleEmulator(cmd, inData, outData)
            else -> -1
        }
    }

    private fun handleKemulator(cmd: Int, inData: ByteArray?, outData: ByteArray?): Int {
        when (cmd) {
            DEVCTL_IS_EMULATOR -> {
                if (outData != null && outData.size >= 4) {
                    outData[0] = 1; outData[1] = 0; outData[2] = 0; outData[3] = 0
                }
                return 0
            }
            DEVCTL_GET_HAS_DISPLAY -> {
                if (outData != null && outData.size >= 4) {
                    outData[0] = 0; outData[1] = 0; outData[2] = 0; outData[3] = 0
                }
                return 0
            }
            DEVCTL_EMIT_SCREENSHOT -> return 0
            DEVCTL_SEND_CTRLDATA -> return -1
            else -> return -1
        }
    }

    private fun handleEmulator(cmd: Int, inData: ByteArray?, outData: ByteArray?): Int {
        when (cmd) {
            DEVCTL_SEND_OUTPUT -> {
                if (inData != null) {
                    val text = try {
                        inData.decodeToString().trimEnd('\u0000')
                    } catch (_: Exception) {
                        inData.takeWhile { it.toInt() in 0x20..0x7e || it.toInt() == 0x0a }.toByteArray().decodeToString()
                    }
                    if (text.isNotEmpty()) appendOutput(text, OutputSource.DEVCTL)
                }
                return 0
            }
            else -> return -1
        }
    }

    fun outputCount(): Int = capturedOutput.length

    fun outputStats(): String = OutputSource.entries.joinToString(", ") { source ->
        "${source.name.lowercase()}=${sourceWrites[source.ordinal]}/${sourceChars[source.ordinal]}"
    }

    fun capture(text: String) = appendOutput(text, OutputSource.DIRECT)

    fun captureWrite(fd: Int, bufPtr: Address, count: Int, memory: IMemoryBus) {
        if (count <= 0 || count > maxCapturedWrite) return
        val data = memory.readBytes(bufPtr, count)
        // Strip null bytes (replace with spaces) to make printf output readable
        val sanitized = data.map { b ->
            val c = b.toInt() and 0xFF
            when {
                c == 0x00 -> ' '.code.toByte()
                c in 0x20..0x7e || c == 0x0a -> b
                c == 0x0d -> b
                else -> '?'.code.toByte()
            }
        }.toByteArray()
        val text = try {
            sanitized.decodeToString()
        } catch (_: Exception) {
            sanitized.map { b -> val c = b.toInt() and 0xFF; if (c in 0x20..0x7e || c == 0x0a) c.toChar() else '?' }.joinToString("")
        }
        appendOutput(text, OutputSource.WRITE)
    }

    fun captureDevctl(cmd: Int, inData: ByteArray?, inLen: Int, memory: IMemoryBus) {
        if (cmd != 2 || inData == null) return
        val text = try {
            inData.decodeToString().trimEnd('\u0000')
        } catch (_: Exception) {
            inData.takeWhile { it.toInt() in 0x20..0x7e || it.toInt() == 0x0a }.toByteArray().decodeToString()
        }
        if (text.isNotEmpty()) appendOutput(text, OutputSource.DEVCTL)
    }

    fun clear() {
        capturedOutput.clear()
        lastOutputSource = null
        lastOutputText = null
        sourceWrites.fill(0)
        sourceChars.fill(0)
    }

    private fun appendOutput(text: String, source: OutputSource) {
        if (text == lastOutputText && source != lastOutputSource) return
        capturedOutput.append(text)
        sourceWrites[source.ordinal]++
        sourceChars[source.ordinal] += text.length
        lastOutputText = text
        lastOutputSource = source
        onOutput?.invoke(text)
    }
}
