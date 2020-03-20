package caves.visualization.rendering.uniform;

import caves.visualization.rendering.GPUBuffer;
import caves.visualization.rendering.swapchain.RecreatedWithSwapChain;
import caves.visualization.rendering.swapchain.SwapChain;
import caves.visualization.window.DeviceContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.vulkan.*;

import static caves.visualization.window.VKUtil.translateVulkanResult;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public final class UniformBufferObject implements RecreatedWithSwapChain {
    private static final long SIZE_IN_BYTES = 3 * 16 * Float.BYTES;
    private static final int MATRIX_SIZE_IN_BYTES = 16 * Float.BYTES;

    private final DeviceContext deviceContext;

    private final long descriptorSetLayout;
    private final SwapChain swapChain;
    private final DescriptorPool descriptorPool;

    private final GPUBuffer[] uniformBuffers;
    private final Matrix4f tmpModel = new Matrix4f();
    private final Matrix4f tmpView = new Matrix4f();
    private final Matrix4f tmpProjection = new Matrix4f();
    private long[] descriptorSets;
    private boolean cleanedUp;

    /**
     * Gets a handle for the descriptor set layout.
     *
     * @return the descriptor set layout
     */
    public long getDescriptorSetLayout() {
        return this.descriptorSetLayout;
    }

    /**
     * Allocates a new uniform buffer object. Automagically allocates separate buffers for all
     * swapchain image indices.
     *
     * @param deviceContext  device to allocate on
     * @param swapChain      swapchain which rendering the UBO is used for
     * @param descriptorPool the descriptor pool to allocate the descriptor sets on
     */
    public UniformBufferObject(
            final DeviceContext deviceContext,
            final SwapChain swapChain,
            final DescriptorPool descriptorPool
    ) {
        this.deviceContext = deviceContext;
        this.swapChain = swapChain;
        this.descriptorPool = descriptorPool;
        this.descriptorSetLayout = createDescriptorSetLayout(this.deviceContext.getDeviceHandle());
        this.uniformBuffers = new GPUBuffer[swapChain.getImageCount()];

        this.cleanedUp = true;

        recreate();
    }

    private static long createDescriptorSetLayout(final VkDevice device) {
        try (var stack = stackPush()) {
            final var bindings = VkDescriptorSetLayoutBinding.callocStack(1, stack);
            bindings.get(0)
                    .binding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT);

            final var layoutInfo = VkDescriptorSetLayoutCreateInfo
                    .callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                    .pBindings(bindings);
            final var pLayout = stack.mallocLong(1);
            final var error = vkCreateDescriptorSetLayout(device, layoutInfo, null, pLayout);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Creating descriptor set layout failed: "
                                                        + translateVulkanResult(error));
            }
            return pLayout.get(0);
        }
    }

    private static long[] allocateDescriptorSets(
            final VkDevice device,
            final SwapChain swapChain,
            final long descriptorSetLayout,
            final long descriptorPool
    ) {
        try (var stack = stackPush()) {
            final var layouts = stack.mallocLong(swapChain.getImageCount());
            for (var i = 0; i < swapChain.getImageCount(); ++i) {
                layouts.put(descriptorSetLayout);
            }
            layouts.flip();

            final var allocInfo = VkDescriptorSetAllocateInfo
                    .callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                    .descriptorPool(descriptorPool)
                    .pSetLayouts(layouts);

            final var descriptorSets = new long[swapChain.getImageCount()];
            final var error = vkAllocateDescriptorSets(device, allocInfo, descriptorSets);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Allocating descriptor sets failed: "
                                                        + translateVulkanResult(error));
            }

            return descriptorSets;
        }
    }

    /**
     * Gets the descriptor set for given image index from this UBO.
     *
     * @param imageIndex swapchain image index to fetch the descriptor set for
     *
     * @return the descriptor set handle for the given swapchain image index
     */
    public long getDescriptorSet(final int imageIndex) {
        return this.descriptorSets[imageIndex];
    }

    /**
     * Updates buffers for the given image index.
     *
     * @param imageIndex     image index to update
     * @param angle          model matrix angle
     * @param lookAtDistance how far from the origin the camera is placed
     */
    public void update(final int imageIndex, final double angle, final float lookAtDistance) {
        final var lookAtHeight = lookAtDistance / 3.0f;

        this.tmpModel.identity().rotateLocalY((float) Math.toRadians(angle));
        this.tmpView.setLookAt(new Vector3f(0, lookAtHeight, -lookAtDistance * 1.25f),
                               new Vector3f(0.0f, 0.0f, 0.0f),
                               new Vector3f(0.0f, 1.0f, 0.0f));

        final var swapChainExtent = this.swapChain.getExtent();
        final var aspectRatio = swapChainExtent.width() / (float) swapChainExtent.height();

        final var fovY = (float) Math.toRadians(45.0f);
        final float zNear = 0.1f;
        final float zFar = 1000.0f;
        this.tmpProjection.setPerspective(fovY, aspectRatio, zNear, zFar, true)
                          .scale(1, -1, 1);

        try (var stack = stackPush()) {
            final var buffer = stack.malloc((int) SIZE_IN_BYTES);
            this.tmpModel.get(0, buffer);
            this.tmpView.get(MATRIX_SIZE_IN_BYTES, buffer);
            this.tmpProjection.get(2 * MATRIX_SIZE_IN_BYTES, buffer);
            this.uniformBuffers[imageIndex].pushMemory(buffer);
        }
    }

    @Override
    public void recreate() {
        if (!this.cleanedUp) {
            throw new IllegalStateException("Tried to recreate UBO without clearing it first!");
        }

        for (var i = 0; i < this.swapChain.getImageCount(); ++i) {
            this.uniformBuffers[i] = new GPUBuffer(
                    this.deviceContext,
                    SIZE_IN_BYTES,
                    VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        }

        this.descriptorSets = allocateDescriptorSets(this.deviceContext.getDeviceHandle(),
                                                     this.swapChain,
                                                     this.descriptorSetLayout,
                                                     this.descriptorPool.getHandle());

        for (var i = 0; i < this.swapChain.getImageCount(); ++i) {
            try (var stack = stackPush()) {
                final var bufferInfos = VkDescriptorBufferInfo.callocStack(1, stack);
                bufferInfos.get(0)
                           .buffer(this.uniformBuffers[i].getBufferHandle())
                           .offset(0)
                           .range(SIZE_IN_BYTES);

                final var descriptorWrites = VkWriteDescriptorSet
                        .callocStack(1, stack)
                        .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                        .dstSet(this.descriptorSets[i])
                        .dstBinding(0)
                        .dstArrayElement(0)
                        .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                        .descriptorCount(1)
                        .pBufferInfo(bufferInfos);

                vkUpdateDescriptorSets(this.deviceContext.getDeviceHandle(), descriptorWrites, null);
            }
        }

        this.cleanedUp = false;
    }

    @Override
    public void cleanup() {
        if (this.cleanedUp) {
            throw new IllegalStateException("Tried to cleanup already cleared UBO!");
        }

        for (final var buffer : this.uniformBuffers) {
            buffer.close();
        }

        this.cleanedUp = true;
    }

    @Override
    public void close() {
        RecreatedWithSwapChain.super.close();
        vkDestroyDescriptorSetLayout(this.deviceContext.getDeviceHandle(), this.descriptorSetLayout, null);
    }
}
