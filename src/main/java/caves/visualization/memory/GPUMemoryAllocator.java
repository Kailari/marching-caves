package caves.visualization.memory;

import caves.visualization.window.LogicalDevice;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.vkGetBufferMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkGetImageMemoryRequirements;

public final class GPUMemoryAllocator implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(GPUMemoryAllocator.class);

    // FIXME: Should this be get from vkPhysicalDeviceLimits::maxMemoryAllocationCount?
    private static final int MAX_ALLOCATIONS = 4096;
    private static final long MIN_ALLOCATION_SIZE = 2048000;

    private final VkPhysicalDeviceMemoryProperties memoryProperties;
    private final VkDevice device;

    private final Map<Integer, List<GPUAllocation>> allocations = new HashMap<>();

    private int allocationCount;

    /**
     * Gets the number of memory allocations made through this memory manager.
     *
     * @return the number of allocations.
     */
    public int getAllocationCount() {
        return this.allocationCount;
    }

    /**
     * Gets the total amount of memory allocations made through this memory manager.
     *
     * @return the total amount of allocated memory.
     */
    public long getAllocationTotal() {
        return this.allocations.values()
                               .stream()
                               .flatMap(Collection::stream)
                               .mapToLong(GPUAllocation::getSize)
                               .sum();
    }

    /**
     * Gets the amount of memory actually in use.
     *
     * @return the amount of memory in use.
     */
    public long getUsageTotal() {
        return this.allocations.values()
                               .stream()
                               .flatMap(Collection::stream)
                               .mapToLong(GPUAllocation::getAmountOfMemoryInUse)
                               .sum();
    }

    /**
     * Gets the maximum allocation size made through this memory manager.
     *
     * @return the size of the largest allocation
     */
    public long getLargestAllocationSize() {
        return this.allocations.values()
                               .stream()
                               .flatMap(Collection::stream)
                               .mapToLong(GPUAllocation::getSize)
                               .max()
                               .orElse(0);
    }

    /**
     * Gets the average allocation size of allocations made through this memory manager.
     *
     * @return the average size of an allocation
     */
    public long getAverageAllocationSize() {
        return (long) Math.ceil(this.allocations.values()
                                                .stream()
                                                .flatMap(Collection::stream)
                                                .mapToLong(GPUAllocation::getSize)
                                                .average()
                                                .orElse(0.0));
    }

    /**
     * Creates a new memory manager.
     *
     * @param memoryProperties memory properties of the physical device
     * @param device           the logical device to allocate on
     */
    public GPUMemoryAllocator(
            final VkPhysicalDeviceMemoryProperties memoryProperties,
            final LogicalDevice device
    ) {
        this.memoryProperties = memoryProperties;
        this.device = device.getHandle();
    }

    /**
     * Allocates memory for a GPU buffer.
     *
     * @param handle        handle to the buffer
     * @param propertyFlags desired properties
     *
     * @return memory slice pointing to the allocated memory
     */
    public GPUMemorySlice allocateBufferMemory(final long handle, final int propertyFlags) {
        final GPUMemorySlice slice;
        try (var stack = stackPush()) {
            final var memoryRequirements = VkMemoryRequirements.callocStack(stack);
            vkGetBufferMemoryRequirements(this.device, handle, memoryRequirements);

            slice = this.findSuitableAllocation(memoryRequirements, propertyFlags)
                        .orElseGet(() -> allocate(memoryRequirements, propertyFlags));
        }

        return slice;
    }

    /**
     * Allocates memory for a GPU image.
     *
     * @param handle        handle to the image
     * @param propertyFlags desired properties
     *
     * @return memory slice pointing to the allocated memory
     */
    public GPUMemorySlice allocateImageMemory(final long handle, final int propertyFlags) {
        final GPUMemorySlice slice;
        try (var stack = stackPush()) {
            final var memoryRequirements = VkMemoryRequirements.callocStack(stack);
            vkGetImageMemoryRequirements(this.device, handle, memoryRequirements);

            slice = this.findSuitableAllocation(memoryRequirements, propertyFlags)
                        .orElseGet(() -> allocate(memoryRequirements, propertyFlags));
        }

        return slice;
    }

    private Optional<GPUMemorySlice> findSuitableAllocation(
            final VkMemoryRequirements memoryRequirements,
            final int propertyFlags
    ) {
        final var memoryType = findMemoryType(memoryRequirements.memoryTypeBits(), propertyFlags)
                .orElseThrow(() -> new IllegalStateException("Could not find suitable memory type!"));

        final var withMatchingType = this.allocations.computeIfAbsent(memoryType, key -> new ArrayList<>());
        for (final var allocation : withMatchingType) {
            final var maybeSlice = allocation.slice(memoryRequirements);
            if (maybeSlice.isPresent()) {
                return maybeSlice;
            }
        }

        return Optional.empty();
    }

    private GPUMemorySlice allocate(
            final VkMemoryRequirements memoryRequirements,
            final int propertyFlags
    ) {
        if (this.allocationCount > MAX_ALLOCATIONS) {
            LOG.warn("Allocating over the specification allocation limit! Number of allocations: {}",
                     this.allocationCount);
        }

        final var memoryType = findMemoryType(memoryRequirements.memoryTypeBits(), propertyFlags)
                .orElseThrow(() -> new IllegalStateException("Could not find suitable memory type!"));

        final var size = Math.max(MIN_ALLOCATION_SIZE,
                                  memoryRequirements.size());
        final var allocation = new GPUAllocation(this.device, memoryType, size);
        this.allocations.computeIfAbsent(memoryType, key -> new ArrayList<>())
                        .add(allocation);
        ++this.allocationCount;

        // SAFETY: This should never throw as long as allocation is at least `memoryRequirements.size()`
        return allocation.slice(memoryRequirements).orElseThrow();
    }

    /**
     * Searches the physical device for a memory type matching the given type filter and property
     * flags. If no such memory type can be found, an empty optional is returned.
     *
     * @param typeFilter    memory type filter bit flags
     * @param propertyFlags property bit flags
     *
     * @return <code>Optional</code> containing the memory type, if available.
     *         <code>Optional.empty()</code> otherwise.
     */
    private Optional<Integer> findMemoryType(final int typeFilter, final int propertyFlags) {
        for (var i = 0; i < this.memoryProperties.memoryTypeCount(); ++i) {
            final var typeIsSuitable = (typeFilter & (1 << i)) != 0;
            final var hasAllProperties =
                    (this.memoryProperties.memoryTypes(i).propertyFlags() & propertyFlags) == propertyFlags;

            if (typeIsSuitable && hasAllProperties) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    @Override
    public void close() {
        for (final var allocations : this.allocations.values()) {
            allocations.forEach(GPUAllocation::close);
        }
    }
}
