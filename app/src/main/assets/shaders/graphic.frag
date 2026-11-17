// ===========================================================================
// STYLE 3 - GRAPHIC (crime-game promotional illustration look)
//
//   Kuwahara smoothing -> local-contrast re-injection -> shadow gamma +
//   soft posterisation -> skin-aware saturation (no orange faces) -> cool
//   shadows / warm highlights -> dramatic highlight bloom -> selective dark
//   outlines (suppressed on skin) -> light print grain.
// ===========================================================================

void main() {
    vec2 uv = vUV;
    vec3 orig = fetchBase(uv);
    vec3 sm   = fetchSmooth(uv);
    float skin = skinMask(rgb2hsv(sm));

    // Smooth surfaces, but keep some micro-detail and add LOCAL contrast
    // (difference between the smoothed pixel and a wider blur of it).
    vec2 b = uTexel * uStep * 7.0;
    vec3 wide = 0.25 * (fetchSmooth(uv + vec2( b.x, 0.0)) + fetchSmooth(uv + vec2(-b.x, 0.0))
                      + fetchSmooth(uv + vec2(0.0,  b.y)) + fetchSmooth(uv + vec2(0.0, -b.y)));
    vec3 col = sm + (orig - sm) * 0.15 + (sm - wide) * 0.8 * uStrength;
    col = clamp(col, 0.0, 1.0);

    // Dramatic tone: shadow gamma + soft posterised transitions.
    float lum = luma(col);
    float lt = pow(clamp(lum, 0.0, 1.0), mix(1.0, 1.45, uShadow));
    float n = floor(mix(4.0, 9.0, uLevels) + 0.5);
    float lp = softQuant(lt, n, 0.16);
    float lf = mix(lt, lp, 0.8 * uStrength);
    col = clamp(col * min(lf / max(lum, 0.03), 2.5), 0.0, 1.0);

    // Punchy colour, but protect skin from turning orange/red.
    vec3 hsv = rgb2hsv(col);
    float boost = 1.0 + (uSaturation - 0.5) * 1.4 + 0.22 * uStrength;
    boost = mix(boost, 1.0 + (boost - 1.0) * 0.35, skin);
    hsv.y = clamp(hsv.y * boost, 0.0, 1.0);
    hsv.x = mix(hsv.x, 0.075, skin * 0.35);          // nudge skin toward a natural hue
    col = hsv2rgb(hsv);

    // Split-tone: cool shadows, warm highlights (skin excluded).
    float l3 = luma(col);
    col = mix(col, col * vec3(0.84, 0.95, 1.12), (1.0 - smoothstep(0.0, 0.5, l3)) * 0.45 * (1.0 - skin));
    col = mix(col, col * vec3(1.10, 1.02, 0.92), smoothstep(0.55, 1.0, l3) * 0.35 * (1.0 - skin));
    float hl = max(l3 - 0.65, 0.0) / 0.35;
    col += vec3(1.0, 0.96, 0.88) * hl * hl * uHighlight * 0.30;

    vec3 res = mix(orig, col, uStrength);

    // Selective outlines: thin, dark navy, weaker on skin so faces stay clean.
    vec2 d = uTexel * uStep * mix(0.7, 2.0, uThickness);
    float e = sobelMag(uv, d);
    float line = smoothstep(0.10, 0.38, e) * uEdge * mix(1.0, 0.5, skin);
    res = mix(res, vec3(0.02, 0.03, 0.06), line * 0.92 * uStrength);

    res = gradeTone(res);
    vec2 px = uv / (uTexel * uStep);
    res += (hash12(px + uSeed * 17.0) - 0.5) * 0.05 * uGrain;
    res = applyVignette(res, uv);
    fragColor = vec4(clamp(res, 0.0, 1.0), 1.0);
}
