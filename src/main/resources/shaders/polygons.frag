#version 450
#extension GL_ARB_separate_shader_objects : enable

layout(location = 0) in vec3 fragColor;
layout(location = 1) in vec3 fragNormal;

layout(location = 0) out vec4 outColor;

void main() {
    vec3 kd = fragColor;
    vec3 ka = vec3(0.0, 0.0, 0.0);
    vec3 illumination = normalize(vec3(0.0, -1.0, 0.0));

    vec3 shaded = kd * ka + kd * max(0.25, dot(illumination, fragNormal));
    outColor = vec4(shaded, 1.0);
}
