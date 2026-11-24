#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:globals.glsl>
layout(std140) uniform ControlConfig { vec4 Parameters; };
void main() {
    vec2 uv = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    vec2 pixel = vec2(Parameters.x, 0.0) + uv;
    gl_Position = vec4(pixel / ScreenSize * 2.0 - 1.0, 0.0, 1.0);
}
