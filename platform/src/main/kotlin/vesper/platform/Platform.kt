package vesper.platform

object Platform {
    fun init() {
        // Platform layer initialization stub
        // Will set up LWJGL (GLFW, Vulkan, OpenAL) in M5+
    }

    val name: String get() = "Desktop (JVM)"

    fun currentTimeMillis(): Long = System.currentTimeMillis()
}
