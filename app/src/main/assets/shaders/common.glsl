// ---------------------------------------------------------------------------
// common.glsl - prepended to smooth.frag and every style shader.
//
// Coordinate convention used by ALL passes:
//   vUV is an "image" coordinate: (0,0) = top-left, (1,1) = bottom-right.
//   Intermediate render targets are stored GL-native (row 0 = bottom), so every
//   read from them goes through fb() which flips Y.
// ---------------------------------------------------------------------------

uniform sampler2D uBase;     // texture unit 0: camera frame at processing resolution
uniform sampler2D uSmooth;   // texture unit 1: edge-preserving (Kuwahara) smoothed frame
uniform vec2  uTexel;        // 1 / processing-buffer size
uniform float uStep;         // resolution scale so kernels look the same at any size
uniform float uPhoto;        // 0 = live, 1 = high quality photo pass
uniform float uSeed;         // changes every frame (temporal grain)

// Normalised (0..1) user parameters. 0.5 is "style default" for saturation,
// contrast, brightness and warmth.
uniform float uStrength;
uniform float uSaturation;
uniform float uContrast;
uniform float uBrightness;
uniform float uShadow;
uniform float uHighlight;
uniform float uLevels;
uniform float uEdge;
uniform float uThickness;
uniform float uSmoothing;
uniform float uGrain;
uniform float uWarmth;
uniform float uVignette;
uniform float uPixel;
uniform float uGlow;
uniform float uChunk;
uniform float uGrade;

uniform int  uPalette;
uniform int  uPalCount;
uniform vec3 uPal[8];

in vec2 vUV;
out vec4 fragColor;

vec2 fb(vec2 p) { return vec2(p.x, 1.0 - p.y); }
vec3 fetchBase(vec2 p)   { return texture(uBase,   fb(p)).rgb; }
vec3 fetchSmooth(vec2 p) { return texture(uSmooth, fb(p)).rgb; }

float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// Quantise v (0..1) to n evenly spaced values (0 .. 1) with soft shoulders.
// w is the half-width of the transition (0.02 = crisp, 0.5 = almost linear).
float softQuant(float v, float n, float w) {
    float x = clamp(v, 0.0, 1.0) * (n - 1.0);
    float f = floor(x);
    float t = fract(x);
    return clamp((f + smoothstep(0.5 - w, 0.5 + w, t)) / (n - 1.0), 0.0, 1.0);
}

// Quantise v (0..1) into n flat BANDS (value = band centre) with soft edges.
// This is the classic cel-shading step function.
float bandQuant(float v, float n, float w) {
    float x = clamp(v, 0.0, 0.9999) * n;
    float f = floor(x);
    float t = fract(x);
    float up   = smoothstep(1.0 - w, 1.0, t) * 0.5;
    float down = (1.0 - smoothstep(0.0, w, t)) * 0.5;
    return clamp((f + 0.5 + up - down) / n, 0.0, 1.0);
}

// Sobel gradient magnitude of the SMOOTHED image using all three colour
// channels (so equal-luminance colour edges are found too). Result is roughly
// the colour difference across the edge (0..1).
float sobelMag(vec2 uv, vec2 d) {
    vec3 tl = fetchSmooth(uv + vec2(-d.x, -d.y));
    vec3 tc = fetchSmooth(uv + vec2( 0.0, -d.y));
    vec3 tr = fetchSmooth(uv + vec2( d.x, -d.y));
    vec3 ml = fetchSmooth(uv + vec2(-d.x,  0.0));
    vec3 mr = fetchSmooth(uv + vec2( d.x,  0.0));
    vec3 bl = fetchSmooth(uv + vec2(-d.x,  d.y));
    vec3 bc = fetchSmooth(uv + vec2( 0.0,  d.y));
    vec3 br = fetchSmooth(uv + vec2( d.x,  d.y));
    vec3 gx = (tr + 2.0 * mr + br) - (tl + 2.0 * ml + bl);
    vec3 gy = (bl + 2.0 * bc + br) - (tl + 2.0 * tc + tr);
    return sqrt(dot(gx, gx) + dot(gy, gy)) * 0.1443;   // 0.25 / sqrt(3)
}

// Brightness / contrast / warmth (saturation is handled per style).
vec3 gradeTone(vec3 c) {
    c += (uBrightness - 0.5) * 0.4;
    c = (c - 0.5) * (1.0 + (uContrast - 0.5) * 1.2) + 0.5;
    c += vec3(0.10, 0.03, -0.10) * (uWarmth - 0.5) * 2.0;
    return clamp(c, 0.0, 1.0);
}

vec3 satAdjust(vec3 c, float amount) {   // amount 0.5 = unchanged
    float l = luma(c);
    return clamp(mix(vec3(l), c, 1.0 + (amount - 0.5) * 1.6), 0.0, 1.0);
}

vec3 applyVignette(vec3 c, vec2 uv) {
    float d = distance(uv, vec2(0.5));
    float v = smoothstep(0.35, 0.9, d * 1.15);
    return c * (1.0 - v * uVignette * 0.7);
}

// 0..1 mask of "probably skin": orange hue, mid saturation, not too dark.
// Used to stop saturation boosts from turning faces orange/red.
float skinMask(vec3 hsv) {
    float hm = smoothstep(-0.02, 0.01, hsv.x) * (1.0 - smoothstep(0.09, 0.13, hsv.x));
    float sm = smoothstep(0.12, 0.28, hsv.y) * (1.0 - smoothstep(0.62, 0.80, hsv.y));
    float vm = smoothstep(0.25, 0.45, hsv.z);
    return hm * sm * vm;
}
