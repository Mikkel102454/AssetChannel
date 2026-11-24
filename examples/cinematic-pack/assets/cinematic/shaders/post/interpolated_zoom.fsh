#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:globals.glsl>
uniform sampler2D InSampler;
layout(std140) uniform ClientZoomConfig { vec4 Parameters; };
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

uint controlByte(int slot) { return uint(round(texelFetch(InSampler, ivec2(slot, 0), 0).a * 255.0)); }
uint controlShort(int slot) { return controlByte(slot) | (controlByte(slot + 1) << 8u); }
float curve(float t, uint easing) {
    t = clamp(t, 0.0, 1.0);
    if (easing == 1u) return t * t * (3.0 - 2.0 * t);
    if (easing == 2u) return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    if (easing == 3u) return t * t;
    if (easing == 4u) return 1.0 - (1.0 - t) * (1.0 - t);
    if (easing == 5u) return step(1.0, t);
    return t;
}
void main() {
    ivec2 size = textureSize(InSampler, 0);
    bool valid = size.x >= int(Parameters.z);
    for (int i = 0; i < int(Parameters.z) && valid; i++) {
        vec3 marker = texelFetch(InSampler, ivec2(i, 0), 0).rgb;
        valid = marker.r > 0.99 && marker.g < 0.01 && marker.b > 0.99;
    }
    float zoom = 1.0;
    float roll = 0.0;
    if (valid) {
        float from = mix(Parameters.x, Parameters.y, float(controlShort(0)) / 65535.0);
        float to = mix(Parameters.x, Parameters.y, float(controlShort(2)) / 65535.0);
        float progress = float(controlShort(4)) / 65535.0;
        uint durationBits = controlByte(6) | (controlByte(7) << 8u) | (controlByte(8) << 16u) | (controlByte(9) << 24u);
        float duration = uintBitsToFloat(durationBits);
        float elapsed = mod(GameTime * Parameters.w - float(controlShort(10)) + Parameters.w, Parameters.w);
        if (duration > 0.0) progress += elapsed / duration;
        zoom = mix(from, to, curve(progress, controlByte(12)));
        if (Parameters.z >= 18.0) {
            int current = int(controlShort(13)), next = int(controlShort(15));
            if (current > 32767) current -= 65536;
            if (next > 32767) next -= 65536;
            float startRoll = float(current) / 32767.0 * 180.0;
            float endRoll = float(next) / 32767.0 * 180.0;
            float delta = mod(endRoll - startRoll + 540.0, 360.0) - 180.0;
            roll = radians(startRoll + delta * (controlByte(17) != 0u ? clamp(elapsed, 0.0, 1.0) : 0.0));
        }
    }
    vec2 uv = (texCoord - 0.5) / max(zoom, 0.001) + 0.5;
    float aspect = float(size.x) / float(size.y);
    vec2 centred = (uv - 0.5) * vec2(aspect, 1.0);
    centred = mat2(cos(roll), -sin(roll), sin(roll), cos(roll)) * centred;
    uv = centred / vec2(aspect, 1.0) + 0.5;
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) { fragColor = vec4(0.0, 0.0, 0.0, 1.0); return; }
    // Hide the tiny transport strip using its neighbouring image row. At zoom > 1
    // this corner is outside the sampled image anyway.
    if (uv.y * float(size.y) < 1.5 && uv.x * float(size.x) < Parameters.z + 0.5) uv.y = 1.5 / float(size.y);
    fragColor = vec4(texture(InSampler, uv).rgb, 1.0);
}
