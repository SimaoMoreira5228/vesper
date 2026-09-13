package vesper.platform

import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface
import org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkInstance
import org.lwjgl.vulkan.VkInstanceCreateInfo
import vesper.common.Logger
import vesper.core.kernel.Kernel

class Window(private val initialWidth: Int, private val initialHeight: Int) {
    val handle: Long get() = windowHandle
    private var windowHandle: Long = 0
    var surfaceHandle: Long = 0
        private set

    var shouldClose: Boolean = false
        private set

    private var kernelRef: Kernel? = null
    private val pressedKeys = mutableSetOf<Int>()

    companion object {
        private val LOG_TAG = "Window"

        fun isVulkanSupported(): Boolean = glfwVulkanSupported()

        val requiredInstanceExtensions: PointerBuffer?
            get() = GLFWVulkan.glfwGetRequiredInstanceExtensions()
    }

    fun init() {
        if (!glfwInit()) throw RuntimeException("Failed to initialize GLFW")

        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)

        windowHandle = glfwCreateWindow(initialWidth, initialHeight, "Vesper PSP", MemoryUtil.NULL, MemoryUtil.NULL)
        if (windowHandle == MemoryUtil.NULL) {
            glfwTerminate()
            throw RuntimeException("Failed to create GLFW window")
        }

        glfwSetKeyCallback(windowHandle) { _, key, _, action, _ ->
            when (action) {
                GLFW_PRESS -> pressedKeys.add(key)
                GLFW_RELEASE -> pressedKeys.remove(key)
            }
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) shouldClose = true
        }

        glfwSetWindowCloseCallback(windowHandle) { shouldClose = true }
        glfwSetFramebufferSizeCallback(windowHandle) { _, w, h -> }

        Logger.info(LOG_TAG) { "Window created: ${initialWidth}x$initialHeight" }
    }

    fun createSurface(
        instanceHandle: Long,
        createInfo: VkInstanceCreateInfo,
    ): Long {
        MemoryStack.stackPush().use { stack ->
            val surfaceBuf = stack.mallocLong(1)
            val err =
                glfwCreateWindowSurface(
                    VkInstance(instanceHandle, createInfo),
                    windowHandle,
                    null,
                    surfaceBuf,
                )
            if (err != VK_SUCCESS) throw RuntimeException("Failed to create surface: $err")
            surfaceHandle = surfaceBuf[0]
            return surfaceHandle
        }
    }

    fun setKernel(kernel: Kernel) {
        kernelRef = kernel
    }

    fun update() {
        glfwPollEvents()
        if (kernelRef?.isExitRequested() == true) shouldClose = true
    }

    fun isKeyPressed(key: Int): Boolean = key in pressedKeys

    fun getFramebufferSize(): Pair<Int, Int> {
        MemoryStack.stackPush().use { stack ->
            val w = stack.mallocInt(1)
            val h = stack.mallocInt(1)
            glfwGetFramebufferSize(windowHandle, w, h)
            return Pair(w[0], h[0])
        }
    }

    fun setTitle(title: String) {
        glfwSetWindowTitle(windowHandle, title)
    }

    fun shutdown() {
        glfwFreeCallbacks(windowHandle)
        glfwDestroyWindow(windowHandle)
        glfwTerminate()
    }
}
