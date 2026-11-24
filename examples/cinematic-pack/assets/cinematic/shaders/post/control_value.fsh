#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform ControlConfig { vec4 Parameters; };
layout(location = 0) out vec4 fragColor;
void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    if (pixel != ivec2(int(Parameters.x), 0)) discard;
    // Alpha carries the byte without colour-space conversion.
    fragColor = vec4(1.0, 0.0, 1.0, Parameters.y / 255.0);
}
