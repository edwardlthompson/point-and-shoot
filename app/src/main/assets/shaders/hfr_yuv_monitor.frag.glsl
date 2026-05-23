#version 300 es

precision mediump float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexY;
uniform sampler2D uTexU;
uniform sampler2D uTexV;

// BT.709 limited-range YUV → RGB (monitor path; no LUT).
void main() {
    float y = texture(uTexY, vTexCoord).r;
    float u = texture(uTexU, vTexCoord).r - 0.5;
    float v = texture(uTexV, vTexCoord).r - 0.5;
    vec3 rgb;
    rgb.r = y + 1.5748 * v;
    rgb.g = y - 0.1873 * u - 0.4681 * v;
    rgb.b = y + 1.8556 * u;
    fragColor = vec4(clamp(rgb, 0.0, 1.0), 1.0);
}
