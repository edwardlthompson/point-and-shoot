#version 300 es
// Point & Shoot - GLES 3.0 LUT-apply vertex shader (full-screen quad).
//
// Pairs with lut_apply.frag.glsl. Drives the standard NDC -> texture-coord
// passthrough that the host fills with two triangles covering the surface.

in vec4 aPosition;
in vec2 aTexCoord;

out vec2 vTexCoord;

void main() {
    vTexCoord = aTexCoord;
    gl_Position = aPosition;
}
