// ===========================================================================
// STYLE 1 - 1930s BRITISH RAILWAY POSTER
//
// Pipeline (see README):
//   Kuwahara smoothing (pass 1)  -> tonal simplification -> tonal posterisation
//   -> hue-aware palette mapping -> selective ink edges -> lithographic paper
//   and print grain -> final grade + vignette.
// ===========================================================================
void main() {
    vec2 uv = vUV;
    vec3 orig = fetchBase(uv);
    vec3 sm   = fetchSmooth(uv);

    // 1. Painted base: mostly the smoothed frame, a little real detail so faces survive.
    vec3 col = mix(sm, orig, 0.10);
    float lum = luma(col);

    // 2. Tonal simplification. "Shadow depth" is a gamma that deepens the darks;
    //    a gentle S-curve gives the poster its bold value structure.
    float gam = mix(0.75, 1.45, uShadow);
    float l2 = pow(clamp(lum, 0.0, 1.0), gam);
    l2 = mix(l2, l2 * l2 * (3.0 - 2.0 * l2), 0.55);
    l2 = clamp(l2 + (uHighlight - 0.5) * 0.30 * smoothstep(0.5, 1.0, l2), 0.0, 1.0);

    // 3. Posterise: 4..7 tonal bands, soft shoulders (printed ink, not hard digital steps).
    float n = floor(mix(3.0, 6.0, uLevels) + 0.5) + 1.0;
    float lq = softQuant(l2, n, 0.10);
    vec3 hsv = rgb2hsv(col);
    vec3 q = hsv2rgb(vec3(hsv.x, clamp(hsv.y * 0.9, 0.0, 1.0), mix(0.17, 0.98, lq)));

    // 4. Palette mapping. Find the nearest palette ink by hue / saturation / value
    //    (hue only counts when both colours are chromatic), then re-impose the
    //    posterised luminance on it so shadow shapes are kept.
    vec3 qh = rgb2hsv(q);
    float skin = skinMask(hsv);
    float qs = qh.y * 0.62;   // match on a desaturated colour so skin finds cream/ochre, not brick red
    float best = 1.0e9;
    vec3 pc = q;
    for (int i = 0; i < 8; i++) {
        if (i >= uPalCount) break;
        vec3 p = uPal[i];
        vec3 ph = rgb2hsv(p);
        float dh = abs(qh.x - ph.x);
        dh = min(dh, 1.0 - dh);
        float d = dh * dh * (4.0 + 60.0 * skin) * max(qs, 0.3 * skin) * ph.y
                + (qs - ph.y) * (qs - ph.y) * 0.5
                + (qh.z - ph.z) * (qh.z - ph.z) * 1.6;
        if (d < best) { best = d; pc = p; }
    }
    vec3 pcAdj = clamp(pc * (luma(q) / max(luma(pc), 0.08)), 0.0, 1.0);
    vec3 poster = mix(q, mix(pc, pcAdj, 0.7), 0.85);
    vec3 res = mix(orig, poster, uStrength);

    // 5. Selective illustrated outlines: high threshold so only real object
    //    boundaries get ink, in a dark brown tinted by the local colour.
    vec2 d = uTexel * uStep * mix(0.8, 2.0, uThickness);
    float e = sobelMag(uv, d);
    float line = smoothstep(0.16, 0.45, e) * uEdge * 0.7;
    vec3 ink = mix(vec3(0.12, 0.08, 0.06), res * 0.30, 0.4);
    res = mix(res, ink, line * uStrength);

    // 6. Final grade
    res = gradeTone(res);
    res = satAdjust(res, uSaturation);

    // 7. Lithographic paper + print grain (static, resolution independent).
    vec2 px = uv / (uTexel * uStep);
    float fibre = vnoise(px * 0.9) * 0.5 + vnoise(px * 2.3 + 7.1) * 0.5;
    fibre += uPhoto * (vnoise(px * 5.0 + 3.7) - 0.5) * 0.5;   // finer fibres in photo mode
    res *= 1.0 + (fibre - 0.5) * 0.14 * uGrain;
    float speck = step(0.994, hash12(floor(px * 0.5))) * 0.12;
    res -= speck * uGrain * 0.5;
    res *= mix(vec3(1.0), vec3(1.03, 0.99, 0.91), uGrain);          // warm cream paper tint

    res = applyVignette(res, uv);
    fragColor = vec4(clamp(res, 0.0, 1.0), 1.0);
}
