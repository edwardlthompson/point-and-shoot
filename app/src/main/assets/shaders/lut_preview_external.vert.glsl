#version 300 es
// Camera preview: map normalized view UV -> buffer UV, then apply SurfaceTexture
// transform for sampling GL_TEXTURE_EXTERNAL_OES.

in vec4 aPosition;
in vec2 aTexCoord;

uniform mat4 uStMatrix;
uniform float uViewW;
uniform float uViewH;
uniform float uBufW;
uniform float uBufH;
uniform float uCoverCrop;
uniform float uBufKnown;

out vec2 vOesTexCoord;

vec2 viewToBufferUv(vec2 vn) {
    float vw = max(uViewW, 1.0);
    float vh = max(uViewH, 1.0);
    float bw = max(uBufW, 1.0);
    float bh = max(uBufH, 1.0);
    float vx = vn.x * vw;
    float vy = vn.y * vh;
    float scale =
        uCoverCrop > 0.5 ? max(vw / bw, vh / bh) : min(vw / bw, vh / bh);
    float dx = (vw - bw * scale) * 0.5;
    float dy = (vh - bh * scale) * 0.5;
    float bx = clamp((vx - dx) / scale, 0.0, bw);
    float by = clamp((vy - dy) / scale, 0.0, bh);
    return vec2(bx / bw, by / bh);
}

void main() {
    gl_Position = aPosition;
    vec2 bufUv =
        uBufKnown > 0.5 ? viewToBufferUv(aTexCoord) : aTexCoord;
    vec4 t = uStMatrix * vec4(bufUv, 0.0, 1.0);
    vOesTexCoord = t.xy / max(t.w, 1e-6);
}
