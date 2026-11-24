#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
layout(std140) uniform CinematicConfig { vec4 Parameters; };
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
void main() {
    vec2 uv = (texCoord - vec2(0.5)) / Parameters.x + vec2(0.5);
    vec4 pixel = texture(InSampler, uv);
    float luminance = dot(pixel.rgb, vec3(0.2126, 0.7152, 0.0722));
    pixel.rgb = mix(pixel.rgb, vec3(luminance), Parameters.z);
    pixel.rgb *= 1.0 - Parameters.y;
    if (texCoord.y < Parameters.w || texCoord.y > 1.0 - Parameters.w) pixel.rgb = vec3(0.0);
    fragColor = pixel;
}
