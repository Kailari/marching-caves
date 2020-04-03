/**
 * Utilities for handling Vulkan SwapChains.
 * <p>
 * Swapchain is a circular buffer of images that are used for transferring presentable images to the
 * window surface. That is, a series of images that the frames are rendered on and then presented on
 * the screen.
 */
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@FieldsAreNonnullByDefault
package caves.visualization.rendering.swapchain;

import caves.util.annotation.FieldsAreNonnullByDefault;
import caves.util.annotation.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;
