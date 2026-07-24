package vesper.app

import vesper.common.Logger
import vesper.common.LogLevel
import vesper.core.cpu.Cpu
import vesper.core.gpu.Framebuffer
import vesper.core.gpu.GeState
import vesper.core.kernel.Kernel
import vesper.core.loader.ElfLoader
import vesper.core.memory.MemoryBus
import vesper.platform.Platform
import vesper.platform.Window
import vesper.platform.renderer.VulkanRenderer

fun main() {
    Platform.init()
    Logger.setMinLevel(LogLevel.INFO)

    val mem = MemoryBus()
    val cpu = Cpu(mem)
    val geState = GeState()
    val kernel = Kernel(mem, cpu, geState = geState)
    kernel.init()
    cpu.kernel = kernel

    val homebrewElf = System.getProperty("vesper.elf", "")
    if (homebrewElf.isNotEmpty()) {
        val elfBytes = java.io.File(homebrewElf).readBytes()
        val result = ElfLoader().load(elfBytes)
        if (result.isSuccess) {
            val image = result.getOrThrow()
            for (seg in image.segments) {
                mem.loadSegment(vesper.core.memory.Address(seg.vaddr), seg.data)
            }
            cpu.state.pc = image.entryPoint
            cpu.state.setGpr(29, 0x08800000 + 0x100000)
            Logger.info("Main") { "Loaded ELF: ${image.moduleName}" }
        }
    }

    val window: Window
    val renderer: VulkanRenderer
    try {
        window = Platform.createWindow(960, 544)
        window.setKernel(kernel)

        if (!Window.isVulkanSupported()) {
            Logger.warn("Main") { "Vulkan not supported, running headless" }
            cpu.run(1000000)
            println("CPU executed ${cpu.state} instructions (headless mode)")
            return
        }

        renderer = VulkanRenderer()
        renderer.init(window.handle, 480, 272)
    } catch (e: Exception) {
        Logger.error("Main") { "Failed to initialize Vulkan: ${e.message}" }
        println("Running in headless mode")
        cpu.run(1000000)
        return
    }

    var frameCount = 0
    while (!kernel.isExitRequested() && !window.shouldClose) {
        window.update()
        cpu.run(10000)

        val data = Framebuffer.readRawContiguous(mem, geState)
        if (data != null && geState.pixelDataSize() > 0) {
            renderer.presentFrame(data, geState.framebufferWidth, geState.framebufferHeight, geState.pixelFormat)
        }

        frameCount++
        if (frameCount % 100 == 0) {
            window.setTitle("Vesper PSP [FPS: ~${frameCount}]")
        }
    }

    renderer.shutdown()
    window.shutdown()
    Logger.info("Main") { "Shutdown after $frameCount frames" }
}
