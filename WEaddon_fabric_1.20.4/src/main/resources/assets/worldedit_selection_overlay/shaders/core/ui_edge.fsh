#version 150

uniform vec4 ColorModulator;
uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    float distance = texture(Sampler0, texCoord0).r - 0.5;
    float pixel = max(fwidth(distance), 1.0 / 255.0);
    float coverage = smoothstep(-pixel * 0.6, pixel * 0.6, distance);
    coverage *= 1.0 - smoothstep(pixel * 0.8, pixel * 2.0, distance);
    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * coverage);
}
