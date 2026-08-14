package vesper.core.loader

import vesper.common.LogLevel
import vesper.common.LogSink

class StdoutLogSink(private val minLevel: LogLevel = LogLevel.TRACE) : LogSink {
    override fun log(level: LogLevel, tag: String, message: String) {
        if (level.ordinal < minLevel.ordinal) return
        println("[${level.name.padStart(5)}] [$tag] $message")
    }
}
