#version 450
#extension GL_ARB_separate_shader_objects : enable

layout(binding = 0) uniform UniformBufferObject {
    mat4 model;
    mat4 view;
    mat4 proj;
} ubo;

layout(location = 0) in vec3 inPosition;

layout(location = 0) out vec3 fragColor;

void main() {
    mat4 mvp = ubo.proj * ubo.view * ubo.model;
    gl_Position = mvp * vec4(inPosition, 1.0);
    gl_PointSize = 1.0;
    fragColor = vec3(1.0, 1.0, 0.0);
}