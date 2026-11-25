#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
layout(location = 0) in vec2 texCoord;
layout(location = 1) flat in vec4 screenState;
layout(location = 2) flat in vec4 colourState;
layout(location = 0) out vec4 fragColor;

void main() {
    vec2 size = vec2(textureSize(InSampler, 0));
    float aspect = size.x / size.y;
    vec2 centred = (texCoord - 0.5) / max(screenState.x, 0.001) * vec2(aspect, 1.0);
    float c = cos(screenState.y), s = sin(screenState.y);
    vec2 uv = (mat2(c, -s, s, c) * centred) / vec2(aspect, 1.0) + 0.5;
    vec3 colour = vec3(0.0);
    if (all(greaterThanEqual(uv, vec2(0.0))) && all(lessThanEqual(uv, vec2(1.0)))) {
        int channel = int(uv.x * size.x) / 22;
        if (uv.y * size.y < 1.5 && channel < 5 && (int(colourState.z) & (1 << channel)) != 0)
            uv.y = 1.5 / size.y;
        colour = texture(InSampler, uv).rgb;
    }
    colour = mix(colour, vec3(dot(colour, vec3(0.2126, 0.7152, 0.0722))), colourState.x);
    colour *= 1.0 - screenState.z;
    float edge = min(texCoord.y, 1.0 - texCoord.y);
    if (screenState.w > 0.0)
        colour *= smoothstep(screenState.w * 0.12 - 0.5 / size.y, screenState.w * 0.12 + 0.5 / size.y, edge);
    colour *= 1.0 - colourState.y * smoothstep(0.15, 0.71, length(texCoord - 0.5));
    fragColor = vec4(colour, 1.0);
}
