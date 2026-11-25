#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
layout(std140) uniform CinematicConfig { vec4 Parameters; };
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
void main() {
    vec4 pixel = texture(InSampler, texCoord);
    float luminance = dot(pixel.rgb, vec3(0.2126, 0.7152, 0.0722));
    pixel.rgb = mix(pixel.rgb, vec3(luminance), Parameters.x);
    if (texCoord.y < Parameters.y || texCoord.y > 1.0 - Parameters.y) pixel.rgb = vec3(0.0);
    fragColor = pixel;
}
