package caves.visualization.memory;

import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class GPUAllocation implements AutoCloseable {
    private final VkDevice device;

    private final long memoryHandle;
    private final long size;

    private final List<GPUMemorySlice> slices = new ArrayList<>();

    /**
     * Gets the underlying native GPU memory handle/pointer. The handle can be used as the
     * <code>memory</code> parameter in (for example) {@link org.lwjgl.vulkan.VK10#vkBindBufferMemory(VkDevice,
     * long, long, long) vkBindBufferMemory} and {@link org.lwjgl.vulkan.VK10#vkBindImageMemory(VkDevice,
     * long, long, long) vkBindImageMemory}.
     *
     * @return the memory handle
     */
    long getHandle() {
        return this.memoryHandle;
    }

    /**
     * Gets the handle for the device this allocation resides on.
     *
     * @return the device handle
     */
    VkDevice getDevice() {
        return this.device;
    }

    /**
     * Gets the size of this allocation.
     *
     * @return the size of this allocation
     */
    public long getSize() {
        return this.size;
    }

    public long getAmountOfMemoryInUse() {
        return this.slices.stream()
                          .mapToLong(GPUMemorySlice::getSize)
                          .sum();
    }

    /**
     * Creates a new memory allocation.
     *
     * @param device     device to allocate on
     * @param memoryType the type of the memory to allocate
     * @param size       size of the memory to allocate
     */
    public GPUAllocation(
            final VkDevice device,
            final int memoryType,
            final long size
    ) {
        this.device = device;
        this.size = size;

        try (final var stack = stackPush()) {
            final var allocInfo = VkMemoryAllocateInfo
                    .callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(size)
                    .memoryTypeIndex(memoryType);

            final var pMemory = stack.mallocLong(1);
            final var error = vkAllocateMemory(this.device, allocInfo, null, pMemory);
            if (error != VK_SUCCESS) {
                throw new IllegalStateException("Could not allocate memory for a GPU buffer!");
            }

            this.memoryHandle = pMemory.get(0);
        }
    }

    /**
     * Tries to get a slice of memory from this allocation. Memory types should always be validated
     * before calling this; e.g. implementation may safely assume that the memory type bits from the
     * requirements match this allocation's memory type.
     *
     * @param memoryRequirements memory requirements for the slice
     *
     * @return slice for the required memory or empty if there is not enough space
     */
    public Optional<GPUMemorySlice> slice(final VkMemoryRequirements memoryRequirements) {
        final long sliceAlignment = memoryRequirements.alignment();
        final long sliceSize = memoryRequirements.size();

        // Fail immediately if this allocation is not large enough
        if (sliceSize > this.size) {
            return Optional.empty();
        }

        // Find first such index at which the next possible offset from slice's last index (next multiple of alignment
        // greater than the last index of the slice at sliceIndex) can fit the whole new slice.
        long offset = 0;
        var anyOverlaps = true;
        while (anyOverlaps) {
            // Find any overlaps and move the offset past them.
            // TODO: Use incrementing index instead of naive iteration, that could avoid sort, too
            anyOverlaps = false;
            for (final var slice : this.slices) {
                if (slice.overlaps(offset, offset + sliceSize)) {
                    final var lastIndex = slice.getOffset() + slice.getSize();
                    final var multiple = (long) (Math.ceil(lastIndex / (double) sliceAlignment));

                    offset = multiple * sliceAlignment;
                    anyOverlaps = true;
                }
            }

            // Make sure the slice still fits
            if (offset + sliceSize > this.size) {
                return Optional.empty();
            }
        }

        // The offset should now be at alignment which the slice can fit
        assert offset % sliceAlignment == 0;
        assert offset + sliceSize <= this.size;

        final var slice = new GPUMemorySlice(this, offset, sliceSize);
        this.slices.add(slice);
        this.slices.sort(Comparator.comparingLong(GPUMemorySlice::getOffset));
        return Optional.of(slice);
    }

    @Override
    public void close() {
        vkFreeMemory(this.device, this.memoryHandle, null);
    }

    void releaseSlice(final GPUMemorySlice slice) {
        this.slices.remove(slice);
    }
}
