package vesper.core.kernel

import vesper.common.Loggable
import vesper.common.warn
import vesper.core.IMemoryBus
import vesper.core.memory.Address
import java.io.File
import java.io.RandomAccessFile

data class FileDescriptor(
    val id: Int,
    val path: String,
    val flags: Int,
    val hostFile: RandomAccessFile?,
)

class FileIo : Loggable {

    override val tag: String get() = "FileIo"

    private var nextFd: Int = 1
    private val fds = mutableMapOf<Int, FileDescriptor>()

    private val mountPoints = mutableMapOf<String, File>()

    init {
        val cwd = File(".").absoluteFile
        mountPoints["host0:"] = cwd
        mountPoints["disc0:"] = cwd
        mountPoints["ms0:"] = cwd
        mountPoints["umd0:"] = cwd
    }

    fun mount(device: String, path: File) {
        mountPoints[device] = path
    }

    fun resolvePath(pspPath: String): File? {
        for ((device, root) in mountPoints) {
            if (pspPath.startsWith(device, ignoreCase = true)) {
                val relative = pspPath.removePrefix(device).trimStart('/').trimEnd('/')
                return File(root, relative)
            }
        }
        val cleaned = pspPath.replace('\\', '/').trimEnd('/')
        if (cleaned.startsWith("/")) {
            return File(cleaned)
        }
        return File(mountPoints["host0:"] ?: File("."), cleaned)
    }

    fun open(path: String, flags: Int, mode: Int): Int {
        val resolved = resolvePath(path) ?: return -1
        val fd = nextFd++

        if (!resolved.exists()) {
            if (flags and 0x200 == 0) return -1
            resolved.parentFile?.mkdirs()
            resolved.createNewFile()
        }

        try {
            val raf = RandomAccessFile(resolved, "rw")
            fds[fd] = FileDescriptor(id = fd, path = path, flags = flags, hostFile = raf)
            return fd
        } catch (e: Exception) {
            warn { "Failed to open $path: ${e.message}" }
            return -1
        }
    }

    fun close(fd: Int): Int {
        val file = fds.remove(fd) ?: return -1
        try {
            file.hostFile?.close()
        } catch (_: Exception) {}
        return 0
    }

    fun read(fd: Int, bufPtr: Address, count: Int, kernel: Kernel): Int {
        val file = fds[fd] ?: return -1
        val raf = file.hostFile ?: return -1
        return try {
            val bytes = ByteArray(count)
            val readCount = raf.read(bytes)
            if (readCount > 0) {
                kernel.memory.writeBytes(bufPtr, bytes.sliceArray(0 until readCount))
            }
            readCount.coerceAtLeast(0)
        } catch (e: Exception) {
            warn { "Failed to read fd $fd: ${e.message}" }
            -1
        }
    }

    fun write(fd: Int, bufPtr: Address, count: Int, kernel: Kernel): Int {
        val file = fds[fd] ?: return -1
        val raf = file.hostFile ?: return -1
        return try {
            val bytes = kernel.memory.readBytes(bufPtr, count)
            raf.write(bytes)
            count
        } catch (e: Exception) {
            warn { "Failed to write fd $fd: ${e.message}" }
            -1
        }
    }

    fun seek(fd: Int, offset: Int, whence: Int): Long {
        val file = fds[fd] ?: return -1L
        val raf = file.hostFile ?: return -1L
        return try {
            val pos = when (whence) {
                0 -> offset.toLong()
                1 -> raf.filePointer + offset
                2 -> raf.length() + offset
                else -> return -1L
            }
            raf.seek(pos)
            raf.filePointer
        } catch (e: Exception) {
            warn { "Failed to seek fd $fd: ${e.message}" }
            -1L
        }
    }

    fun devctl(name: String, cmd: Int, inData: ByteArray?, inLen: Int, outData: ByteArray?): Int {
        return -1
    }

    fun listOpenFiles(): List<FileDescriptor> = fds.values.toList()
}
