package vesper.platform

import org.lwjgl.system.Configuration
import vesper.common.Logger

object Platform {
    var window: Window? = null
        private set

    fun init() {
        System.setProperty("java.awt.headless", "true")
        Configuration.DEBUG.set(false)
        Configuration.DEBUG_FUNCTIONS.set(false)
        Configuration.DEBUG_LOADER.set(false)

        Logger.info("Platform") { "Platform initialized" }
    }

    fun createWindow(width: Int, height: Int): Window {
        val w = Window(width, height)
        w.init()
        window = w
        Logger.info("Platform") { "Window created" }
        return w
    }

    val name: String get() = "Desktop (JVM + LWJGL/Vulkan)"

    fun currentTimeMillis(): Long = System.currentTimeMillis()
}
