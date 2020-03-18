package caves.visualization.rendering;

import caves.visualization.window.DeviceContext;
import org.lwjgl.vulkan.*;

import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public final class GPUImage implements AutoCloseable {
    private final DeviceContext deviceContext;

    private final long image;
    private final long imageMemory;

    /**
     * Gets a handle to the underlying GPU image.
     *
     * @return the image handle
     */
    public long getHandle() {
        return this.image;
    }

    /**
     * Allocates a new image on the GPU.
     *
     * @param deviceContext    the device to use
     * @param width            width of the image
     * @param height           height of the image
     * @param format           image format
     * @param tiling           tiling options to use
     * @param usage            image usage flags
     * @param memoryProperties memory property flags
     */
    public GPUImage(
            final DeviceContext deviceContext,
            final int width,
            final int height,
            final int format,
            final int tiling,
            final int usage,
            final int memoryProperties
    ) {
        this.deviceContext = deviceContext;

        this.image = createImage(deviceContext.getDeviceHandle(), width, height, format, tiling, usage);
        this.imageMemory = allocateImageMemory(deviceContext, this.image, memoryProperties);
    }

    private static long createImage(
            final VkDevice device,
            final int width,
            final int height,
            final int format,
            final int tiling,
            final int usage
    ) {
        try (var stack = stackPush()) {
            final var imageInfo = VkImageCreateInfo.callocStack()
                                                   .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                                                   .imageType(VK_IMAGE_TYPE_2D)
                                                   .mipLevels(1)
                                                   .arrayLayers(1)
                                                   .format(format)
                                                   .tiling(tiling)
                                                   .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                                                   .usage(usage)
                                                   .samples(VK_SAMPLE_COUNT_1_BIT)
                                                   .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            imageInfo.extent().set(width, height, 1);

            final var pImage = stack.callocLong(1);
            final var result = vkCreateImage(device,
                                             imageInfo,
                                             null,
                                             pImage);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Failed to create image: "
                                                        + translateVulkanResult(result));
            }
            return pImage.get(0);
        }

    }

    private static long allocateImageMemory(
            final DeviceContext deviceContext,
            final long image,
            final int memoryProperties
    ) {
        try (var stack = stackPush()) {
            final var memoryRequirements = VkMemoryRequirements.callocStack();
            vkGetImageMemoryRequirements(deviceContext.getDeviceHandle(), image, memoryRequirements);

            final var allocInfo = VkMemoryAllocateInfo
                    .callocStack()
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(memoryRequirements.size())
                    .memoryTypeIndex(deviceContext.findMemoryType(memoryRequirements.memoryTypeBits(), memoryProperties)
                                                  .orElseThrow());

            final var pMemory = stack.callocLong(1);
            final var result = vkAllocateMemory(deviceContext.getDeviceHandle(),
                                                allocInfo,
                                                null,
                                                pMemory);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("Allocating image memory failed: "
                                                        + translateVulkanResult(result));
            }
            vkBindImageMemory(deviceContext.getDeviceHandle(),
                              image,
                              pMemory.get(0),
                              0);

            return pMemory.get(0);
        }
    }

    private static boolean hasStencilComponent(final int format) {
        return format == VK_FORMAT_D32_SFLOAT_S8_UINT || format == VK_FORMAT_D24_UNORM_S8_UINT;
    }

    public void transitionLayout(
            final CommandPool commandPool,
            final int format,
            final int oldLayout,
            final int newLayout
    ) {
        try (var stack = stackPush()) {
            final var allocInfo = VkCommandBufferAllocateInfo
                    .callocStack()
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandPool(commandPool.getHandle())
                    .commandBufferCount(1);

            final var pCommandBuffers = stack.mallocPointer(1);
            vkAllocateCommandBuffers(this.deviceContext.getDeviceHandle(),
                                     allocInfo,
                                     pCommandBuffers);

            final var beginInfo = VkCommandBufferBeginInfo
                    .callocStack()
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            final var commandBuffer = new VkCommandBuffer(pCommandBuffers.get(0),
                                                          this.deviceContext.getDeviceHandle());
            vkBeginCommandBuffer(commandBuffer, beginInfo);

            final var barriers = VkImageMemoryBarrier.callocStack(1);
            barriers.get(0)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .oldLayout(oldLayout)
                    .newLayout(newLayout)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(this.image);
            barriers.get(0).subresourceRange()
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);

            if (newLayout == VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
                barriers.get(0)
                        .subresourceRange()
                        .aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT);
                if (hasStencilComponent(format)) {
                    final var aspectMask = barriers.get(0).subresourceRange()
                                                   .aspectMask();
                    barriers.get(0)
                            .subresourceRange()
                            .aspectMask(aspectMask | VK_IMAGE_ASPECT_STENCIL_BIT);
                }
            } else {
                barriers.get(0)
                        .subresourceRange()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
            }

            final int srcStage;
            final int dstStage;
            if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                barriers.get(0)
                        .srcAccessMask(0)
                        .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);

                srcStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
            } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL
                    && newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                barriers.get(0)
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT);

                srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
                dstStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
            } else if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED
                    && newLayout == VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
                barriers.get(0)
                        .srcAccessMask(0)
                        .dstAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                                               | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);

                srcStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                dstStage = VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;
            } else {
                throw new UnsupportedOperationException("Unsupported layout transition!");
            }

            vkCmdPipelineBarrier(commandBuffer,
                                 srcStage,
                                 dstStage,
                                 0,
                                 null,
                                 null,
                                 barriers);

            vkEndCommandBuffer(commandBuffer);

            final var submitInfos = VkSubmitInfo.callocStack(1);
            submitInfos.get(0)
                       .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                       .pCommandBuffers(pCommandBuffers);
            vkQueueSubmit(this.deviceContext.getGraphicsQueue(),
                          submitInfos,
                          VK_NULL_HANDLE);
            vkQueueWaitIdle(this.deviceContext.getTransferQueue());

            vkFreeCommandBuffers(this.deviceContext.getDeviceHandle(),
                                 commandPool.getHandle(),
                                 commandBuffer);
        }
    }

    @Override
    public void close() {
        vkDestroyImage(this.deviceContext.getDeviceHandle(), this.image, null);
        vkFreeMemory(this.deviceContext.getDeviceHandle(), this.imageMemory, null);
    }
}
