package vesper.core.kernel

import vesper.core.memory.Address

class EmulatedTimer {
    private var tickBase: Long = System.nanoTime()
    private var emulatedTickOffset: Long = 0L
    private var pauseBase: Long = 0L
    private var paused: Boolean = false

    fun now(): Long {
        if (paused) return pauseBase + emulatedTickOffset
        val elapsed = (System.nanoTime() - tickBase) / 1000L
        return elapsed + emulatedTickOffset
    }

    fun nowMicros(): Long = now()

    fun pause() {
        if (!paused) {
            pauseBase = now()
            paused = true
        }
    }

    fun resume() {
        if (paused) {
            emulatedTickOffset = pauseBase
            tickBase = System.nanoTime()
            paused = false
        }
    }

    fun advance(micros: Long) {
        emulatedTickOffset += micros
    }

    fun reset() {
        tickBase = System.nanoTime()
        emulatedTickOffset = 0L
        pauseBase = 0L
        paused = false
    }
}

data class TimerEvent(
    val wakeupTick: Long,
    val threadId: Int,
)

data class SystemTime(
    val year: Short,
    val month: Short,
    val day: Short,
    val hour: Short,
    val minute: Short,
    val second: Short,
    val microsecond: Int,
) {
    companion object {
        const val STRUCT_SIZE = 16

        fun now(timer: EmulatedTimer): SystemTime {
            val totalMicros = timer.nowMicros()
            val totalSeconds = totalMicros / 1_000_000L
            val micros = (totalMicros % 1_000_000L).toInt()
            val days = totalSeconds / 86400L
            val timeOfDay = (totalSeconds % 86400L).toInt()
            val hours = timeOfDay / 3600
            val minutes = (timeOfDay % 3600) / 60
            val seconds = timeOfDay % 60

            val year2000 = (days / 365).toInt()
            return SystemTime(
                year = (2000 + year2000).toShort(),
                month = 1.toShort(),
                day = ((days % 365L) + 1).toShort(),
                hour = hours.toShort(),
                minute = minutes.toShort(),
                second = seconds.toShort(),
                microsecond = micros,
            )
        }

        fun writeToMemory(
            memory: vesper.core.IMemoryBus,
            addr: Address,
            time: SystemTime,
        ) {
            memory.write16(addr, time.year.toInt())
            memory.write16(addr + 2, time.month.toInt())
            memory.write16(addr + 4, time.day.toInt())
            memory.write16(addr + 6, time.hour.toInt())
            memory.write16(addr + 8, time.minute.toInt())
            memory.write16(addr + 10, time.second.toInt())
            memory.write32(addr + 12, time.microsecond)
        }
    }
}
