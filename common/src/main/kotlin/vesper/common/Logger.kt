package vesper.common

enum class LogLevel { TRACE, DEBUG, INFO, WARN, ERROR }

fun interface LogSink {
    fun log(level: LogLevel, tag: String, message: String)
}

object LogSinks {
    private val sinks = mutableListOf<LogSink>()

    fun add(sink: LogSink) { sinks.add(sink) }

    fun remove(sink: LogSink) { sinks.remove(sink) }

    fun emit(level: LogLevel, tag: String, message: String) {
        for (sink in sinks) sink.log(level, tag, message)
    }
}

object Logger {
    private var minLevel = LogLevel.DEBUG

    fun setMinLevel(level: LogLevel) { minLevel = level }

    fun trace(tag: String, msg: () -> String) { if (minLevel <= LogLevel.TRACE) LogSinks.emit(LogLevel.TRACE, tag, msg()) }
    fun debug(tag: String, msg: () -> String) { if (minLevel <= LogLevel.DEBUG) LogSinks.emit(LogLevel.DEBUG, tag, msg()) }
    fun info(tag: String, msg: () -> String) { if (minLevel <= LogLevel.INFO) LogSinks.emit(LogLevel.INFO, tag, msg()) }
    fun warn(tag: String, msg: () -> String) { if (minLevel <= LogLevel.WARN) LogSinks.emit(LogLevel.WARN, tag, msg()) }
    fun error(tag: String, msg: () -> String) { if (minLevel <= LogLevel.ERROR) LogSinks.emit(LogLevel.ERROR, tag, msg()) }
}

interface Loggable {
    val tag: String
}

fun Loggable.trace(msg: () -> String) = Logger.trace(tag, msg)
fun Loggable.debug(msg: () -> String) = Logger.debug(tag, msg)
fun Loggable.info(msg: () -> String) = Logger.info(tag, msg)
fun Loggable.warn(msg: () -> String) = Logger.warn(tag, msg)
fun Loggable.error(msg: () -> String) = Logger.error(tag, msg)
