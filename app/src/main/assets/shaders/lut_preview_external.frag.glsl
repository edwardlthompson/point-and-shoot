#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require

// Samples GL_TEXTURE_EXTERNAL_OES (camera) then optional 3D LUT (same contract
// as lut_apply.frag.glsl).

precision mediump float;

in vec2 vOesTexCoord;
out vec4 fragColor;

uniform samplerExternalOES uSourceTex;
uniform sampler3D uLutTex;
uniform float uLutSize;
uniform float uLutEnabled;
uniform vec3 uReadoutWbRgb;

uniform float uPeakingEnabled;
uniform vec3 uPeakingRgb;
uniform float uPeakingSensitivity;

vec3 sampleLut(vec3 src) {
    float scale = (uLutSize - 1.0) / uLutSize;
    float bias = 0.5 / uLutSize;
    vec3 uvw = clamp(src, 0.0, 1.0) * scale + bias;
    return texture(uLutTex, uvw).rgb;
}

void main() {
    vec3 src = clamp(texture(uSourceTex, vOesTexCoord).rgb * uReadoutWbRgb, 0.0, 1.0);
    vec3 mixed = mix(src, sampleLut(src), uLutEnabled);
    if (uPeakingEnabled > 0.5) {
        float L = dot(mixed, vec3(0.299, 0.587, 0.114));
        vec2 g = vec2(dFdx(L), dFdy(L));
        float mag = length(g);
        float thr = mix(0.11, 0.014, clamp(uPeakingSensitivity, 0.0, 1.0));
        float peak = smoothstep(thr * 0.55, thr * 1.35, mag);
        mixed = mix(mixed, uPeakingRgb, peak * 0.82);
    }
    fragColor = vec4(mixed, 1.0);
}
