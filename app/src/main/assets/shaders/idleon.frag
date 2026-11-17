// ===========================================================================
// STYLE 4 - IDLEON-INSPIRED fantasy cartoon RPG
//
// SMOOTH mode:  Kuwahara -> hue-selective colour boost -> cartoon shadow bands
//               -> fantasy split-tone -> highlight glow -> chunky outlines.
// PIXEL mode (uPixel > 0): the same pipeline evaluated on a coarse square-cell
//               grid, with ordered dithering and per-channel colour reduction.
//               The camera is NEVER simply pixelated: shading, colour and
//               outlines are computed per cell on the smoothed image.
// ===========================================================================
const float BAYER[16] = float[16](0.0, 8.0, 2.0, 10.0,
                                  12.0, 4.0, 14.0, 6.0,
                                  3.0, 11.0, 1.0, 9.0,
                                  15.0, 7.0, 13.0, 5.0);

float hueBand(float h, float c, float w) {
    float d = abs(h - c);
    d = min(d, 1.0 - d);
    return smoothstep(w, 0.0, d);
}

// Smoothed colour at p; in pixel mode it is snapped to the centre of p's cell.
vec3 smAt(vec2 p, float pix, vec2 grid) {
    vec2 s = (pix > 0.5) ? (floor(p * grid) + 0.5) / grid : p;
    return fetchSmooth(s);
}

float edgeSnap(vec2 uv, vec2 d, float pix, vec2 grid) {
    vec3 tl = smAt(uv + vec2(-d.x, -d.y), pix, grid);
    vec3 tc = smAt(uv + vec2( 0.0, -d.y), pix, grid);
    vec3 tr = smAt(uv + vec2( d.x, -d.y), pix, grid);
    vec3 ml = smAt(uv + vec2(-d.x,  0.0), pix, grid);
    vec3 mr = smAt(uv + vec2( d.x,  0.0), pix, grid);
    vec3 bl = smAt(uv + vec2(-d.x,  d.y), pix, grid);
    vec3 bc = smAt(uv + vec2( 0.0,  d.y), pix, grid);
    vec3 br = smAt(uv + vec2( d.x,  d.y), pix, grid);
    vec3 gx = (tr + 2.0 * mr + br) - (tl + 2.0 * ml + bl);
    vec3 gy = (bl + 2.0 * bc + br) - (tl + 2.0 * tc + tr);
    return sqrt(dot(gx, gx) + dot(gy, gy)) * 0.1443;
}

void main() {
    vec2 uv = vUV;

    // --- pixel grid (square cells regardless of screen shape) ---------------
    float pix = step(0.03, uPixel);
    float across = mix(180.0, 40.0, uPixel);          // cells across the width
    vec2 grid = vec2(across, across * (uTexel.x / uTexel.y));
    vec2 cell = floor(uv * grid);
    vec2 suv = (pix > 0.5) ? (cell + 0.5) / grid : uv;

    vec3 sm;
    if (pix > 0.5) {                                   // average the cell with 4 taps
        vec2 q = 0.25 / grid;
        sm = 0.25 * (fetchSmooth(suv + vec2(-q.x, -q.y)) + fetchSmooth(suv + vec2( q.x, -q.y))
                   + fetchSmooth(suv + vec2(-q.x,  q.y)) + fetchSmooth(suv + vec2( q.x,  q.y)));
    } else {
        sm = fetchSmooth(uv);
    }
    vec3 orig = fetchBase(suv);
    vec3 col = mix(sm, orig, (pix > 0.5) ? 0.0 : 0.10);

    // --- colour: bold, with selective boost of greens/blues/purples/oranges/golds
    vec3 hsv = rgb2hsv(col);
    float boost = hueBand(hsv.x, 0.08, 0.07) * 0.25 + hueBand(hsv.x, 0.13, 0.05) * 0.30
                + hueBand(hsv.x, 0.33, 0.12) * 0.30 + hueBand(hsv.x, 0.60, 0.10) * 0.30
                + hueBand(hsv.x, 0.78, 0.08) * 0.25;
    float skin = skinMask(hsv);
    float satMul = 1.0 + (uSaturation - 0.5) * 1.8 + 0.35 * uStrength + boost * uStrength;
    satMul = mix(satMul, 1.0 + (satMul - 1.0) * 0.3, skin);   // keep faces natural
    float sat = clamp(hsv.y * satMul, 0.0, 1.0);

    // --- cheerful lighting: lifted darks, flat shadow bands, optional dither
    float v = 0.16 + hsv.z * 0.84;
    float L = floor(mix(2.0, 6.0, uLevels) + 0.5);
    if (pix > 0.5) {
        ivec2 c = ivec2(mod(cell, 4.0));
        float dith = (BAYER[c.x + 4 * c.y] + 0.5) / 16.0 - 0.5;
        v += dith * (0.45 / (L + 1.0));
    }
    float vq = bandQuant(clamp(v, 0.0, 1.0), L + 1.0, 0.07);
    v = mix(v, vq, uStrength);

    sat = mix(sat, sat * 0.72, skin * (1.0 - vq));         // warmer, less red skin shadows
    hsv.x = mix(hsv.x, 0.075, skin * 0.35);
    vec3 res = hsv2rgb(vec3(hsv.x, sat, clamp(v, 0.0, 1.0)));

    // --- fantasy grade: purple/blue shadows, golden highlights
    float lv = luma(res);
    res = mix(res, res * vec3(0.66, 0.58, 1.25), (1.0 - smoothstep(0.1, 0.65, lv)) * uGrade * 0.9);
    res += vec3(0.17, 0.10, 0.0) * smoothstep(0.55, 1.0, lv) * uGrade;
    res = mix(res, vec3(1.0, 0.97, 0.82), smoothstep(0.82, 0.95, lv) * uHighlight * 0.5);

    // --- highlight glow: bright areas bleed warm light (8 wide taps)
    vec2 gr = uTexel * uStep * 9.0;
    float g = 0.0;
    g += smoothstep(0.65, 1.0, luma(fetchSmooth(suv + vec2( gr.x, 0.0))));
    g += smoothstep(0.65, 1.0, luma(fetchSmooth(suv + vec2(-gr.x, 0.0))));
    g += smoothstep(0.65, 1.0, luma(fetchSmooth(suv + vec2(0.0,  gr.y))));
    g += smoothstep(0.65, 1.0, luma(fetchSmooth(suv + vec2(0.0, -gr.y))));
    g += smoothstep(0.65, 1.0, luma(fetchSmooth(suv + vec2( gr.x,  gr.y) * 0.7)));
    g += smoothstep(0.65, 1.0, luma(fetchSmooth(suv + vec2(-gr.x,  gr.y) * 0.7)));
    g += smoothstep(0.65, 1.0, luma(fetchSmooth(suv + vec2( gr.x, -gr.y) * 0.7)));
    g += smoothstep(0.65, 1.0, luma(fetchSmooth(suv + vec2(-gr.x, -gr.y) * 0.7)));
    res += vec3(1.0, 0.82, 0.45) * (g / 8.0) * uGlow * 0.55;

    // --- pixel-art colour reduction (per channel)
    if (pix > 0.5) {
        float cl = mix(24.0, 5.0, uPixel) - 1.0;
        res = mix(res, floor(res * cl + 0.5) / cl, 0.7);
    }

    // --- crisp cartoon outlines; thickness grows with Chunkiness. In pixel
    //     mode lines follow the cell grid.
    vec2 d = (pix > 0.5) ? (1.0 / grid)
                         : uTexel * uStep * mix(0.9, 3.2, clamp(uThickness * 0.5 + uChunk * 0.5, 0.0, 1.0));
    float e = edgeSnap(suv, d, pix, grid);
    float line = smoothstep(0.09, 0.30, e) * uEdge;
    res = mix(res, vec3(0.08, 0.04, 0.12), line * uStrength);

    res = gradeTone(res);
    res = applyVignette(res, uv);
    fragColor = vec4(clamp(res, 0.0, 1.0), 1.0);
}
