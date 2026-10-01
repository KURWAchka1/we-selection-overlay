#version 330
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    vec3 rgb = mix(vec3(1.0), vertexColor.rgb, texCoord0.x) * (1.0 - texCoord0.y);
    fragColor = vec4(rgb, vertexColor.a * texture(Sampler0, texCoord0).a) * ColorModulator;
}
