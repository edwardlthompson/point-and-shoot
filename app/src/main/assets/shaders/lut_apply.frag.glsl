#version 300 es
// Point & Shoot - GLES 3.0 LUT-apply fragment shader.
//
// Samples a 3D LUT (sampler3D) with hardware trilinear interpolation. The
// LUT must be uploaded as a GL_RGB16F (or higher) texture with size in
// {17, 33, 65}, matching Lut3D.SUPPORTED_SIZES on the Kotlin side.
//
// Inputs:
//   uSourceTex     : the camera/preview color source (sRGB-encoded)
//   uLutTex        : the 3D LUT (linear-light, normalized [0, 1])
//   uLutSize       : LUT grid size (17 / 33 / 65) - used for the (size-1)/size
//                    + 0.5/size half-texel correction so we sample at cell
//                    centers, not at the cube edges (which would clip).
//   uLutEnabled    : 1.0 to apply the LUT, 0.0 to bypass entirely. The host-
//                    side LutShaderProgram uses this to short-circuit when
//                    Lut3D.isIdentity() is true so an "off" LUT costs nothing
//                    beyond the sourceTex sample.
//
// Color spaces:
//   The LUT is authored against the same color space the source is in
//   (sRGB display by default; calibration LUTs from CalibrationToLut.toLut3D
//   bake the WB -> CCM -> bias chain, so they expect WB-uncorrected linear
//   sensor RGB). Picking the wrong source for a given LUT is a calling-code
//   bug, not a shader bug; the shader is colour-space-agnostic.

precision mediump float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uSourceTex;
uniform sampler3D uLutTex;
uniform float uLutSize;
uniform float uLutEnabled;

vec3 sampleLut(vec3 src) {
    // Half-texel inset so we sample at cell centers. Without this we'd clip
    // at the cube edges and rounded out-of-gamut input would smear into
    // neighbouring border cells.
    float scale = (uLutSize - 1.0) / uLutSize;
    float bias = 0.5 / uLutSize;
    vec3 uvw = clamp(src, 0.0, 1.0) * scale + bias;
    return texture(uLutTex, uvw).rgb;
}

void main() {
    vec3 src = texture(uSourceTex, vTexCoord).rgb;
    vec3 mixed = mix(src, sampleLut(src), uLutEnabled);
    fragColor = vec4(mixed, 1.0);
}
