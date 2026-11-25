#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:globals.glsl>

uniform sampler2D InSampler;
layout(std140) uniform CompositorConfig { vec4 Parameters; };
layout(std140) uniform ChannelBounds {
    vec4 Channel0; vec4 Channel1; vec4 Channel2; vec4 Channel3; vec4 Channel4;
};
layout(location = 0) out vec2 texCoord;
layout(location = 1) flat out vec4 screenState;
layout(location = 2) flat out vec4 colourState;

// Timing and transport are uniform across the image. Decode only at the three vertices.
uint controlByte(int slot) { return uint(round(texelFetch(InSampler, ivec2(slot, 0), 0).a * 255.0)); }
uint controlShort(int slot) { return controlByte(slot) | (controlByte(slot + 1) << 8u); }
int signedShort(int slot) { int v = int(controlShort(slot)); return v > 32767 ? v - 65536 : v; }
float elapsed(int slot) {
    return mod(GameTime * Parameters.y - float(controlShort(slot)) + Parameters.y * 1.5, Parameters.y) - Parameters.y * 0.5;
}
float curve(float t, uint easing) {
    t = clamp(t, 0.0, 1.0);
    if (easing == 1u) return t * t * (3.0 - 2.0 * t);
    if (easing == 2u) return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    if (easing == 3u) return t * t;
    if (easing == 4u) return 1.0 - (1.0 - t) * (1.0 - t);
    if (easing == 5u) return step(1.0, t);
    return t;
}
bool channelEnabled(int channel, vec4 bounds) {
    if ((int(Parameters.x) & (1 << channel)) == 0 || textureSize(InSampler, 0).x < int(bounds.w + bounds.z)) return false;
    for (int i = 0; i < int(bounds.z); i++) {
        vec3 marker = texelFetch(InSampler, ivec2(int(bounds.w) + i, 0), 0).rgb;
        if (marker.r < 0.99 || marker.g > 0.01 || marker.b < 0.99) return false;
    }
    return true;
}
float amount(vec4 bounds, bool zoom) {
    int base = int(bounds.w);
    float from = mix(bounds.x, bounds.y, float(controlShort(base)) / 65535.0);
    float to = mix(bounds.x, bounds.y, float(controlShort(base + 2)) / 65535.0);
    float progress = float(controlShort(base + 4)) / 65535.0;
    uint bits = controlByte(base + 6) | (controlByte(base + 7) << 8u) | (controlByte(base + 8) << 16u) | (controlByte(base + 9) << 24u);
    float duration = uintBitsToFloat(bits);
    float time = elapsed(base + 10);
    float correction = float(signedShort(base + (zoom ? 20 : 13))) / 4096.0;
    correction *= 1.0 - smoothstep(1.0, 1.0 + max(2.0, 1.5 * abs(correction)), time);
    if (duration > 0.0) progress += (time - correction) / duration;
    return mix(from, to, curve(progress, controlByte(base + 12)));
}
void main() {
    texCoord = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    gl_Position = vec4(texCoord * 2.0 - 1.0, 0.0, 1.0);
    screenState = vec4(1.0, 0.0, 0.0, 0.0);
    colourState = vec4(0.0, 0.0, Parameters.x, 0.0);
    if (channelEnabled(0, Channel0)) {
        screenState.x = amount(Channel0, true);
        int base = int(Channel0.w);
        float start = float(signedShort(base + 13)) / 32767.0 * 180.0;
        float end = float(signedShort(base + 15)) / 32767.0 * 180.0;
        float delta = mod(end - start + 540.0, 360.0) - 180.0;
        float blend = controlByte(base + 17) != 0u ? clamp(elapsed(base + 18), 0.0, 1.0) : 0.0;
        screenState.y = radians(start + delta * blend);
    }
    if (channelEnabled(1, Channel1)) screenState.z = amount(Channel1, false);
    if (channelEnabled(2, Channel2)) screenState.w = amount(Channel2, false);
    if (channelEnabled(3, Channel3)) colourState.x = amount(Channel3, false);
    if (channelEnabled(4, Channel4)) colourState.y = amount(Channel4, false);
}
