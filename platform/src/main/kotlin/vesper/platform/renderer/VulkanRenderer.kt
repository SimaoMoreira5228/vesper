package vesper.platform.renderer

import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.EXTDebugUtils.*
import org.lwjgl.vulkan.KHRSurface.*
import org.lwjgl.vulkan.KHRSwapchain.*
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2
import org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface
import org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions
import org.lwjgl.vulkan.*
import vesper.common.Logger
import vesper.core.gpu.IRenderer
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.min

class VulkanRenderer : IRenderer {
    private lateinit var vkInst: VkInstance
    private lateinit var physicalDevice: VkPhysicalDevice
    private lateinit var device: VkDevice
    private lateinit var queue: VkQueue
    private var queueFamilyIndex: Int = -1
    private var surface: Long = 0
    override val instanceHandle: Long get() = if (::vkInst.isInitialized) vkInst.address() else 0L
    override val surfaceHandle: Long get() = surface
    private var swapchainHandle: Long = 0
    private var swapchainImages: LongArray = LongArray(0)
    private var swapchainImageViews: LongArray = LongArray(0)
    private var swapchainImageFormat: Int = VK_FORMAT_B8G8R8A8_UNORM
    private var swapchainExtent: VkExtent2D = VkExtent2D.calloc()
    private var renderPassHandle: Long = 0
    private var pipelineLayout: Long = 0
    private var pipeline: Long = 0
    private var commandPoolHandle: Long = 0
    private var commandBuffers: Array<VkCommandBuffer> = emptyArray()
    private var framebufferHandles: LongArray = LongArray(0)
    private var imageAvailableSemaphore: Long = 0
    private var renderFinishedSemaphore: Long = 0
    private var inFlightFence: Long = 0
    private var descriptorPool: Long = 0
    private var descriptorSetLayout: Long = 0
    private var descriptorSet: Long = 0
    private var storageBuffer: Long = 0
    private var storageBufferMemory: Long = 0
    private var storageBufferSize: Long = 0
    private var mappedMemory: ByteBuffer? = null
    private var fbWidth: Int = 480
    private var fbHeight: Int = 272
    private var debugMessenger: Long = 0

    companion object {
        private val LOG_TAG = "VulkanRenderer"
        private const val ENABLE_DEBUG = false

        private fun strBuf(stack: MemoryStack, s: String): ByteBuffer {
            val bytes = s.encodeToByteArray()
            val buf = stack.malloc(bytes.size + 1)
            buf.put(bytes); buf.put(0); buf.flip(); return buf
        }

        private fun loadSpirv(path: String): ByteBuffer {
            val stream = VulkanRenderer::class.java.classLoader.getResourceAsStream(path)
                ?: throw RuntimeException("Shader not found: $path")
            val baos = ByteArrayOutputStream()
            stream.use { it.copyTo(baos) }
            val bytes = baos.toByteArray()
            val buf = memAlloc(bytes.size)
            buf.put(bytes); buf.flip()
            return buf
        }
    }

    override fun init(windowHandle: Long, width: Int, height: Int) {
        fbWidth = width; fbHeight = height
        MemoryStack.stackPush().use { stack ->
            vkInst = createInstance(stack)
            surface = createSurface(vkInst, windowHandle, stack)
            physicalDevice = pickPhysicalDevice(stack)
            queueFamilyIndex = findQueueFamily(stack)
            device = createDevice(stack)
            val pQueue = stack.mallocPointer(1)
            vkGetDeviceQueue(device, queueFamilyIndex, 0, pQueue)
            queue = VkQueue(pQueue[0], device)
            createSwapchain(stack)
            createRenderPass(stack)
            createDescriptorSetLayout(stack)
            createPipeline(stack)
            createCommandPool(stack)
            createStorageBuffer(stack)
            createDescriptorSet(stack)
            createSyncObjects(stack)
            createFramebuffers(stack)
            createCommandBuffers()
        }
        Logger.info(LOG_TAG) { "Vulkan initialized" }
    }

    private fun createSurface(inst: VkInstance, windowHandle: Long, stack: MemoryStack): Long {
        val pSurface = stack.mallocLong(1)
        val err = glfwCreateWindowSurface(inst, windowHandle, null, pSurface)
        if (err != VK_SUCCESS) throw RuntimeException("Failed to create surface: $err")
        return pSurface[0]
    }

    override fun presentFrame(data: ByteArray, width: Int, height: Int, format: Int) {
        MemoryStack.stackPush().use { stack ->
            vkWaitForFences(device, inFlightFence, true, Long.MAX_VALUE)
            vkResetFences(device, inFlightFence)
            uploadPixelData(data)

            val imageIndex = acquireNextImage(stack)
            if (imageIndex < 0) return

            val cmd = commandBuffers[imageIndex]
            vkResetCommandBuffer(cmd, 0)
            beginCommandBuffer(cmd, stack)

            val clearValue = VkClearValue.calloc(1, stack)
            clearValue[0].color().float32(stack.floats(0f, 0f, 0f, 1f))

            val area = VkRect2D.calloc(stack)
            area.offset().set(0, 0)
            area.extent().set(swapchainExtent.width(), swapchainExtent.height())

            val rpBegin = VkRenderPassBeginInfo.calloc(stack)
            rpBegin.`sType$Default`()
            rpBegin.renderPass(renderPassHandle)
            rpBegin.framebuffer(framebufferHandles[imageIndex])
            rpBegin.renderArea(area)
            rpBegin.clearValueCount(1)
            rpBegin.pClearValues(clearValue)

            vkCmdBeginRenderPass(cmd, rpBegin, VK_SUBPASS_CONTENTS_INLINE)
            vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline)
            vkCmdBindDescriptorSets(
                cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineLayout, 0,
                stack.longs(descriptorSet), null
            )

            val pushBuf = memAlloc(16)
            pushBuf.putInt(width).putInt(height).putInt(format).putInt(0)
            pushBuf.flip()
            vkCmdPushConstants(cmd, pipelineLayout, VK_SHADER_STAGE_FRAGMENT_BIT, 0, pushBuf)
            vkCmdDraw(cmd, 4, 1, 0, 0)
            vkCmdEndRenderPass(cmd)
            vkCheck(vkEndCommandBuffer(cmd))

            val submitInfo = VkSubmitInfo.calloc(stack)
            submitInfo.`sType$Default`()
            submitInfo.waitSemaphoreCount(1)
            submitInfo.pWaitSemaphores(stack.longs(imageAvailableSemaphore))
            submitInfo.pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
            submitInfo.pCommandBuffers(stack.pointers(cmd))
            submitInfo.pSignalSemaphores(stack.longs(renderFinishedSemaphore))

            vkCheck(vkQueueSubmit(queue, submitInfo, inFlightFence))

            val presentInfo = VkPresentInfoKHR.calloc(stack)
            presentInfo.`sType$Default`()
            presentInfo.pWaitSemaphores(stack.longs(renderFinishedSemaphore))
            presentInfo.swapchainCount(1)
            presentInfo.pSwapchains(stack.longs(swapchainHandle))
            presentInfo.pImageIndices(stack.ints(imageIndex))
            vkQueuePresentKHR(queue, presentInfo)
        }
    }

    override fun shutdown() {
        if (::device.isInitialized) {
            vkDeviceWaitIdle(device)
            cleanupSwapchain()
            for (iv in swapchainImageViews) vkDestroyImageView(device, iv, null)
            vkDestroyDescriptorPool(device, descriptorPool, null)
            vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null)
            vkDestroyBuffer(device, storageBuffer, null)
            vkFreeMemory(device, storageBufferMemory, null)
            vkDestroySemaphore(device, renderFinishedSemaphore, null)
            vkDestroySemaphore(device, imageAvailableSemaphore, null)
            vkDestroyFence(device, inFlightFence, null)
            vkDestroyCommandPool(device, commandPoolHandle, null)
            vkDestroyDevice(device, null)
        }
        if (::vkInst.isInitialized) {
            if (debugMessenger != 0L) vkDestroyDebugUtilsMessengerEXT(vkInst, debugMessenger, null)
            vkDestroyInstance(vkInst, null)
        }
        Logger.info(LOG_TAG) { "Vulkan shutdown" }
    }

    private fun createInstance(stack: MemoryStack): VkInstance {
        val appInfo = VkApplicationInfo.calloc(stack)
        appInfo.`sType$Default`()
        appInfo.pApplicationName(strBuf(stack, "Vesper"))
        appInfo.applicationVersion(VK_MAKE_VERSION(0, 1, 0))
        appInfo.pEngineName(strBuf(stack, "Vesper"))
        appInfo.engineVersion(VK_MAKE_VERSION(0, 1, 0))
        appInfo.apiVersion(VK_API_VERSION_1_2)

        val requiredExts = glfwGetRequiredInstanceExtensions()
        val extList = mutableListOf<ByteBuffer>()
        if (requiredExts != null) {
            for (i in 0 until requiredExts.remaining()) {
                val name = memUTF8(requiredExts[i])
                extList.add(strBuf(stack, name))
            }
        }
        if (ENABLE_DEBUG) extList.add(strBuf(stack, VK_EXT_DEBUG_UTILS_EXTENSION_NAME))

        val ppEnabledExtensionNames = stack.mallocPointer(extList.size)
        extList.forEach { ppEnabledExtensionNames.put(it) }
        ppEnabledExtensionNames.flip()

        val layerNames = if (ENABLE_DEBUG) {
            val layers = stack.mallocPointer(1)
            layers.put(strBuf(stack, "VK_LAYER_KHRONOS_validation"))
            layers.flip(); layers
        } else null

        val createInfo = VkInstanceCreateInfo.calloc(stack)
        createInfo.`sType$Default`()
        createInfo.pApplicationInfo(appInfo)
        createInfo.ppEnabledExtensionNames(ppEnabledExtensionNames)
        createInfo.ppEnabledLayerNames(layerNames)

        val pInstance = stack.mallocPointer(1)
        vkCheck(vkCreateInstance(createInfo, null, pInstance))
        val inst = VkInstance(pInstance[0], createInfo)
        if (ENABLE_DEBUG) setupDebugMessenger(inst, stack)
        return inst
    }

    private fun setupDebugMessenger(inst: VkInstance, stack: MemoryStack) {
        val ci = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack)
        ci.`sType$Default`()
        ci.messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT or VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT)
        ci.messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT or VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT or VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT)
        val pMessenger = stack.mallocLong(1)
        vkCheck(vkCreateDebugUtilsMessengerEXT(inst, ci, null, pMessenger))
        debugMessenger = pMessenger[0]
    }

    private fun pickPhysicalDevice(stack: MemoryStack): VkPhysicalDevice {
        val pDeviceCount = stack.mallocInt(1)
        vkCheck(vkEnumeratePhysicalDevices(vkInst, pDeviceCount, null))
        if (pDeviceCount[0] == 0) throw RuntimeException("No Vulkan devices found")
        val pDevices = stack.mallocPointer(pDeviceCount[0])
        vkCheck(vkEnumeratePhysicalDevices(vkInst, pDeviceCount, pDevices))
        for (i in 0 until pDeviceCount[0]) {
            val dev = VkPhysicalDevice(pDevices[i], vkInst)
            val props = VkPhysicalDeviceProperties.calloc(stack)
            vkGetPhysicalDeviceProperties(dev, props)
            if (hasGraphicsAndPresent(dev, stack)) {
                Logger.info(LOG_TAG) { "GPU: ${props.deviceNameString()}" }; return dev
            }
        }
        throw RuntimeException("No suitable GPU found")
    }

    private fun hasGraphicsAndPresent(dev: VkPhysicalDevice, stack: MemoryStack): Boolean {
        val pCount = stack.mallocInt(1)
        vkGetPhysicalDeviceQueueFamilyProperties(dev, pCount, null)
        val props = VkQueueFamilyProperties.calloc(pCount[0], stack)
        vkGetPhysicalDeviceQueueFamilyProperties(dev, pCount, props)
        for (i in 0 until pCount[0]) {
            if (props[i].queueFlags() and VK_QUEUE_GRAPHICS_BIT != 0) {
                val pSupported = stack.mallocInt(1)
                if (vkGetPhysicalDeviceSurfaceSupportKHR(
                        dev,
                        i,
                        surface,
                        pSupported
                    ) == VK_SUCCESS && pSupported[0] == VK_TRUE
                ) return true
            }
        }
        return false
    }

    private fun findQueueFamily(stack: MemoryStack): Int {
        val pCount = stack.mallocInt(1)
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pCount, null)
        val props = VkQueueFamilyProperties.calloc(pCount[0], stack)
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pCount, props)
        for (i in 0 until pCount[0]) {
            if (props[i].queueFlags() and VK_QUEUE_GRAPHICS_BIT != 0) {
                val pSupported = stack.mallocInt(1)
                if (vkGetPhysicalDeviceSurfaceSupportKHR(
                        physicalDevice,
                        i,
                        surface,
                        pSupported
                    ) == VK_SUCCESS && pSupported[0] == VK_TRUE
                ) return i
            }
        }
        throw RuntimeException("No graphics queue family found")
    }

    private fun createDevice(stack: MemoryStack): VkDevice {
        val queueInfo = VkDeviceQueueCreateInfo.calloc(1, stack)
        queueInfo[0].`sType$Default`()
        queueInfo[0].queueFamilyIndex(queueFamilyIndex)
        queueInfo[0].pQueuePriorities(stack.floats(1f))

        val names = stack.mallocPointer(1)
        names.put(strBuf(stack, "VK_KHR_swapchain"))
        names.flip()

        val features = VkPhysicalDeviceFeatures.calloc(stack)
        val createInfo = VkDeviceCreateInfo.calloc(stack)
        createInfo.`sType$Default`()
        createInfo.pQueueCreateInfos(queueInfo)
        createInfo.ppEnabledExtensionNames(names)
        createInfo.pEnabledFeatures(features)

        val pDevice = stack.mallocPointer(1)
        vkCheck(vkCreateDevice(physicalDevice, createInfo, null, pDevice))
        return VkDevice(pDevice[0], physicalDevice, createInfo)
    }

    private fun createSwapchain(stack: MemoryStack) {
        val caps = VkSurfaceCapabilitiesKHR.calloc(stack)
        vkCheck(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, caps))

        val pFmtCount = stack.mallocInt(1)
        vkCheck(vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, pFmtCount, null))
        val fmts = VkSurfaceFormatKHR.calloc(pFmtCount[0], stack)
        vkCheck(vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, pFmtCount, fmts))
        var sf = fmts[0]
        for (i in 0 until pFmtCount[0]) {
            if (fmts[i].format() == VK_FORMAT_B8G8R8A8_UNORM) {
                sf = fmts[i]; break
            }
        }
        swapchainImageFormat = sf.format()

        val pPmCount = stack.mallocInt(1)
        vkCheck(vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, pPmCount, null))
        val pms = stack.mallocInt(pPmCount[0])
        vkCheck(vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, pPmCount, pms))
        var pm = VK_PRESENT_MODE_FIFO_KHR
        for (i in 0 until pPmCount[0]) {
            if (pms[i] == VK_PRESENT_MODE_MAILBOX_KHR) {
                pm = pms[i]; break
            }
        }

        swapchainExtent.set(caps.currentExtent())
        val imgCount = min(caps.minImageCount() + 1, caps.maxImageCount().coerceAtLeast(1))

        val ci = VkSwapchainCreateInfoKHR.calloc(stack)
        ci.`sType$Default`()
        ci.surface(surface); ci.minImageCount(imgCount)
        ci.imageFormat(sf.format()); ci.imageColorSpace(sf.colorSpace())
        ci.imageExtent().set(swapchainExtent.width(), swapchainExtent.height())
        ci.imageArrayLayers(1); ci.imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
        ci.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
        ci.preTransform(caps.currentTransform()); ci.compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
        ci.presentMode(pm); ci.clipped(true)

        val pSwapchain = stack.mallocLong(1)
        vkCheck(vkCreateSwapchainKHR(device, ci, null, pSwapchain))
        swapchainHandle = pSwapchain[0]

        val pImgCount = stack.mallocInt(1)
        vkCheck(vkGetSwapchainImagesKHR(device, swapchainHandle, pImgCount, null))
        val imgs = stack.mallocLong(pImgCount[0])
        vkCheck(vkGetSwapchainImagesKHR(device, swapchainHandle, pImgCount, imgs))
        swapchainImages = LongArray(pImgCount[0]) { imgs[it] }
        swapchainImageViews =
            LongArray(pImgCount[0]) { i -> createImageView(swapchainImages[i], swapchainImageFormat, stack) }
    }

    private fun createImageView(img: Long, fmt: Int, stack: MemoryStack): Long {
        val ci = VkImageViewCreateInfo.calloc(stack)
        ci.`sType$Default`()
        ci.image(img); ci.viewType(VK_IMAGE_VIEW_TYPE_2D); ci.format(fmt)
        ci.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
        ci.subresourceRange().baseMipLevel(0); ci.subresourceRange().levelCount(1)
        ci.subresourceRange().baseArrayLayer(0); ci.subresourceRange().layerCount(1)
        val pView = stack.mallocLong(1)
        vkCheck(vkCreateImageView(device, ci, null, pView))
        return pView[0]
    }

    private fun createRenderPass(stack: MemoryStack) {
        val att = VkAttachmentDescription.calloc(1, stack)
        att[0].format(swapchainImageFormat); att[0].samples(VK_SAMPLE_COUNT_1_BIT)
        att[0].loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR); att[0].storeOp(VK_ATTACHMENT_STORE_OP_STORE)
        att[0].stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE); att[0].stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
        att[0].initialLayout(VK_IMAGE_LAYOUT_UNDEFINED); att[0].finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)

        val cr = VkAttachmentReference.calloc(1, stack)
        cr[0].attachment(0); cr[0].layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)

        val sp = VkSubpassDescription.calloc(1, stack)
        sp[0].pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
        sp[0].colorAttachmentCount(1); sp[0].pColorAttachments(cr)

        val ci = VkRenderPassCreateInfo.calloc(stack)
        ci.`sType$Default`()
        ci.pAttachments(att); ci.pSubpasses(sp)
        val pRp = stack.mallocLong(1)
        vkCheck(vkCreateRenderPass(device, ci, null, pRp))
        renderPassHandle = pRp[0]
    }

    private fun createDescriptorSetLayout(stack: MemoryStack) {
        val b = VkDescriptorSetLayoutBinding.calloc(1, stack)
        b[0].binding(0); b[0].descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
        b[0].descriptorCount(1); b[0].stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT)
        val ci = VkDescriptorSetLayoutCreateInfo.calloc(stack)
        ci.`sType$Default`(); ci.pBindings(b)
        val pLayout = stack.mallocLong(1)
        vkCheck(vkCreateDescriptorSetLayout(device, ci, null, pLayout))
        descriptorSetLayout = pLayout[0]
    }

    private fun createPipeline(stack: MemoryStack) {
        val vs = createShaderModule(loadSpirv("shaders/fullscreen.vert.spv"))
        val fs = createShaderModule(loadSpirv("shaders/fullscreen.frag.spv"))

        val stages = VkPipelineShaderStageCreateInfo.calloc(2, stack)
        val ss = { i: Int, stage: Int, mod: Long ->
            stages[i].`sType$Default`(); stages[i].stage(stage); stages[i].module(mod)
            stages[i].pName(strBuf(stack, "main"))
        }
        ss(0, VK_SHADER_STAGE_VERTEX_BIT, vs)
        ss(1, VK_SHADER_STAGE_FRAGMENT_BIT, fs)

        val ds = stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR)
        val dyn = VkPipelineDynamicStateCreateInfo.calloc(stack); dyn.`sType$Default`(); dyn.pDynamicStates(ds)

        val pcr = VkPushConstantRange.calloc(1, stack)
        pcr[0].stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT); pcr[0].offset(0); pcr[0].size(16)

        val pli = VkPipelineLayoutCreateInfo.calloc(stack); pli.`sType$Default`()
        pli.pSetLayouts(stack.longs(descriptorSetLayout))
        pli.pPushConstantRanges(pcr)
        val pPL = stack.mallocLong(1)
        vkCheck(vkCreatePipelineLayout(device, pli, null, pPL))
        pipelineLayout = pPL[0]

        val pi = VkGraphicsPipelineCreateInfo.calloc(1, stack)
        pi[0].`sType$Default`()
        pi[0].pStages(stages)
        val vi = VkPipelineVertexInputStateCreateInfo.calloc(stack); vi.`sType$Default`(); pi[0].pVertexInputState(vi)
        val ia = VkPipelineInputAssemblyStateCreateInfo.calloc(stack); ia.`sType$Default`()
        ia.topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP); pi[0].pInputAssemblyState(ia)
        val vps = VkPipelineViewportStateCreateInfo.calloc(stack); vps.`sType$Default`()
        vps.viewportCount(1); vps.scissorCount(1); pi[0].pViewportState(vps)
        val rs = VkPipelineRasterizationStateCreateInfo.calloc(stack); rs.`sType$Default`()
        rs.polygonMode(VK_POLYGON_MODE_FILL); rs.cullMode(VK_CULL_MODE_NONE)
        rs.frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE); rs.lineWidth(1f); pi[0].pRasterizationState(rs)
        val ms = VkPipelineMultisampleStateCreateInfo.calloc(stack); ms.`sType$Default`()
        ms.rasterizationSamples(VK_SAMPLE_COUNT_1_BIT); pi[0].pMultisampleState(ms)
        val cbs = VkPipelineColorBlendStateCreateInfo.calloc(stack); cbs.`sType$Default`()
        val ba = VkPipelineColorBlendAttachmentState.calloc(1, stack)
        ba[0].blendEnable(false)
        ba[0].colorWriteMask(
            VK_COLOR_COMPONENT_R_BIT or VK_COLOR_COMPONENT_G_BIT or
                    VK_COLOR_COMPONENT_B_BIT or VK_COLOR_COMPONENT_A_BIT
        )
        cbs.pAttachments(ba); pi[0].pColorBlendState(cbs)
        pi[0].pDynamicState(dyn); pi[0].layout(pipelineLayout)
        pi[0].renderPass(renderPassHandle); pi[0].subpass(0)

        val pPipe = stack.mallocLong(1)
        vkCheck(vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pi, null, pPipe))
        pipeline = pPipe[0]
        vkDestroyShaderModule(device, vs, null); vkDestroyShaderModule(device, fs, null)
    }

    private fun createShaderModule(code: ByteBuffer): Long {
        MemoryStack.stackPush().use { stack ->
            val ci = VkShaderModuleCreateInfo.calloc(stack); ci.`sType$Default`(); ci.pCode(code)
            val pMod = stack.mallocLong(1); vkCheck(vkCreateShaderModule(device, ci, null, pMod))
            return pMod[0]
        }
    }

    private fun createCommandPool(stack: MemoryStack) {
        val ci = VkCommandPoolCreateInfo.calloc(stack); ci.`sType$Default`()
        ci.flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
        ci.queueFamilyIndex(queueFamilyIndex)
        val pPool = stack.mallocLong(1); vkCheck(vkCreateCommandPool(device, ci, null, pPool))
        commandPoolHandle = pPool[0]
    }

    private fun createStorageBuffer(stack: MemoryStack) {
        val maxSize = fbWidth * fbHeight * 4L; storageBufferSize = maxSize
        val bi = VkBufferCreateInfo.calloc(stack); bi.`sType$Default`()
        bi.size(maxSize); bi.usage(VK_BUFFER_USAGE_STORAGE_BUFFER_BIT)
        bi.sharingMode(VK_SHARING_MODE_EXCLUSIVE)
        val pBuf = stack.mallocLong(1); vkCheck(vkCreateBuffer(device, bi, null, pBuf))
        storageBuffer = pBuf[0]

        val mr = VkMemoryRequirements.calloc(stack); vkGetBufferMemoryRequirements(device, storageBuffer, mr)
        val mp = VkPhysicalDeviceMemoryProperties.calloc(stack)
        vkGetPhysicalDeviceMemoryProperties(physicalDevice, mp)

        var memType = -1
        for (i in 0 until mp.memoryTypeCount()) {
            if (mr.memoryTypeBits() and (1 shl i) != 0 &&
                mp.memoryTypes(i).propertyFlags() and VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT != 0 &&
                mp.memoryTypes(i).propertyFlags() and VK_MEMORY_PROPERTY_HOST_COHERENT_BIT != 0
            ) {
                memType = i; break
            }
        }
        if (memType < 0) throw RuntimeException("No suitable memory type")

        val ai = VkMemoryAllocateInfo.calloc(stack); ai.`sType$Default`()
        ai.allocationSize(mr.size()); ai.memoryTypeIndex(memType)
        val pMem = stack.mallocLong(1); vkCheck(vkAllocateMemory(device, ai, null, pMem))
        storageBufferMemory = pMem[0]
        vkBindBufferMemory(device, storageBuffer, storageBufferMemory, 0)
        val pMapped = stack.mallocPointer(1)
        vkCheck(vkMapMemory(device, storageBufferMemory, 0, maxSize, 0, pMapped))
        mappedMemory = memByteBuffer(pMapped[0], maxSize.toInt())
    }

    private fun createDescriptorSet(stack: MemoryStack) {
        val ps = VkDescriptorPoolSize.calloc(1, stack)
        ps[0].type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER); ps[0].descriptorCount(1)
        val poolInfo = VkDescriptorPoolCreateInfo.calloc(stack); poolInfo.`sType$Default`()
        poolInfo.maxSets(1); poolInfo.pPoolSizes(ps)
        val pPool = stack.mallocLong(1); vkCheck(vkCreateDescriptorPool(device, poolInfo, null, pPool))
        descriptorPool = pPool[0]

        val ai = VkDescriptorSetAllocateInfo.calloc(stack); ai.`sType$Default`()
        ai.descriptorPool(descriptorPool); ai.pSetLayouts(stack.longs(descriptorSetLayout))
        val pSet = stack.mallocLong(1); vkCheck(vkAllocateDescriptorSets(device, ai, pSet))
        descriptorSet = pSet[0]

        val bi = VkDescriptorBufferInfo.calloc(1, stack)
        bi[0].buffer(storageBuffer); bi[0].offset(0); bi[0].range(storageBufferSize)
        val wd = VkWriteDescriptorSet.calloc(1, stack)
        wd[0].`sType$Default`(); wd[0].dstSet(descriptorSet); wd[0].dstBinding(0)
        wd[0].descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER); wd[0].pBufferInfo(bi)
        vkUpdateDescriptorSets(device, wd, null)
    }

    private fun createSyncObjects(stack: MemoryStack) {
        val si = VkSemaphoreCreateInfo.calloc(stack); si.`sType$Default`()
        val fi = VkFenceCreateInfo.calloc(stack); fi.`sType$Default`(); fi.flags(VK_FENCE_CREATE_SIGNALED_BIT)
        val p = stack.mallocLong(1)
        vkCreateSemaphore(device, si, null, p); imageAvailableSemaphore = p[0]
        vkCreateSemaphore(device, si, null, p); renderFinishedSemaphore = p[0]
        vkCreateFence(device, fi, null, p); inFlightFence = p[0]
    }

    private fun createFramebuffers(stack: MemoryStack) {
        framebufferHandles = LongArray(swapchainImageViews.size)
        for (i in swapchainImageViews.indices) {
            val ci = VkFramebufferCreateInfo.calloc(stack); ci.`sType$Default`()
            ci.renderPass(renderPassHandle); ci.pAttachments(stack.longs(swapchainImageViews[i]))
            ci.width(swapchainExtent.width()); ci.height(swapchainExtent.height()); ci.layers(1)
            val pFb = stack.mallocLong(1); vkCreateFramebuffer(device, ci, null, pFb)
            framebufferHandles[i] = pFb[0]
        }
    }

    private fun createCommandBuffers() {
        MemoryStack.stackPush().use { stack ->
            val ai = VkCommandBufferAllocateInfo.calloc(stack); ai.`sType$Default`()
            ai.commandPool(commandPoolHandle); ai.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
            ai.commandBufferCount(swapchainImages.size)
            val pBufs = memAllocPointer(swapchainImages.size)
            vkAllocateCommandBuffers(device, ai, pBufs)
            commandBuffers = Array(swapchainImages.size) { i -> VkCommandBuffer(pBufs.get(i), device) }
        }
    }

    private fun beginCommandBuffer(cmd: VkCommandBuffer, stack: MemoryStack) {
        val bi = VkCommandBufferBeginInfo.calloc(stack); bi.`sType$Default`()
        bi.flags(VK_COMMAND_BUFFER_USAGE_SIMULTANEOUS_USE_BIT)
        vkBeginCommandBuffer(cmd, bi)
        val vp = VkViewport.calloc(1, stack)
        vp[0].x(0f); vp[0].y(0f)
        vp[0].width(swapchainExtent.width().toFloat()); vp[0].height(swapchainExtent.height().toFloat())
        vp[0].minDepth(0f); vp[0].maxDepth(1f)
        vkCmdSetViewport(cmd, 0, vp)
        val sc = VkRect2D.calloc(1, stack)
        sc[0].offset().set(0, 0); sc[0].extent().set(swapchainExtent.width(), swapchainExtent.height())
        vkCmdSetScissor(cmd, 0, sc)
    }

    private fun acquireNextImage(stack: MemoryStack): Int {
        val pImageIndex = stack.mallocInt(1)
        val err =
            vkAcquireNextImageKHR(device, swapchainHandle, Long.MAX_VALUE, imageAvailableSemaphore, NULL, pImageIndex)
        if (err == VK_ERROR_OUT_OF_DATE_KHR || err == VK_SUBOPTIMAL_KHR) {
            recreateSwapchain(stack); return -1
        }
        vkCheck(err); return pImageIndex[0]
    }

    private fun uploadPixelData(data: ByteArray) {
        val buf = mappedMemory ?: return; buf.clear()
        buf.put(data, 0, min(data.size, buf.remaining())); buf.flip()
    }

    private fun recreateSwapchain(stack: MemoryStack) {
        vkDeviceWaitIdle(device); cleanupSwapchain()
        createSwapchain(stack); createFramebuffers(stack); createCommandBuffers()
    }

    private fun cleanupSwapchain() {
        for (fb in framebufferHandles) vkDestroyFramebuffer(device, fb, null)
        for (iv in swapchainImageViews) vkDestroyImageView(device, iv, null)
        if (swapchainHandle != 0L) vkDestroySwapchainKHR(device, swapchainHandle, null)
        for (cb in commandBuffers) vkFreeCommandBuffers(device, commandPoolHandle, cb)
    }

    private fun vkCheck(err: Int) {
        if (err != VK_SUCCESS) throw RuntimeException("Vulkan error: $err")
    }

}