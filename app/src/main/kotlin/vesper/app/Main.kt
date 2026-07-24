package vesper.app

import vesper.common.Logger
import vesper.common.LogLevel
import vesper.common.LogSink
import vesper.platform.Platform

fun main() {
    Logger.setMinLevel(LogLevel.DEBUG)

    Platform.init()

    println("Vesper v0.1.0-SNAPSHOT")
    println("Platform: ${Platform.name}")
    println("PSP Emulator - M0 Scaffolding Complete")
    println()
    println("Modules loaded:")
    println("  :common  - Bit manipulation, logging")
    println("  :core    - CPU interpreter, memory bus, ELF loader")
    println("  :platform- Platform bindings (stub)")
    println("  :app     - Application entry")
}
