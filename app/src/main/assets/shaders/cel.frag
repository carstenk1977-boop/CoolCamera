// ===========================================================================
// STYLE 2 - CEL SHADING
//
//   Kuwahara smoothing (pass 1) -> hue-preserving tonal banding (separate
//   shadow steps and light steps) -> flattened saturation -> cool shadow tint
//   -> optional comic halftone -> dark Sobel outlines of adjustable width.
// ===========================================================================
void main() {
    vec2 uv = vUV;
    vec3 orig = fetchBase(uv);
    vec3 sm   = fetchSmooth(uv);
    vec3 col = mix(sm, orig, 0.06);
    vec3 hsv = rgb2hsv(col);

    // Colour levels: total tonal bands 3..8. Shadow steps: how many of those
    // bands live in the dark half of the range.
    float n  = floor(mix(3.0, 8.0, uLevels) + 0.5);
    float sn = floor(mix(1.0, 4.0, uShadow) + 0.5);
    float ln = max(n - sn, 2.0);
    float v = hsv.z;
    float vq = (v < 0.5)
        ? 0.5 * bandQuant(v * 2.0, sn + 1.0, 0.08)
        : 0.5 + 0.5 * bandQuant(v * 2.0 - 1.0, ln, 0.08);

    // Flat colour fields: boost then partially quantise saturation.
    float skin = skinMask(hsv);
    float boost = 1.05 + (uSaturation - 0.5) * 1.6;
    boost = mix(boost, 1.0 + (boost - 1.0) * 0.3, skin);   // protect faces
    float sat = clamp(hsv.y * boost, 0.0, 1.0);
    sat = mix(sat, softQuant(sat, 5.0, 0.25), 0.6 * (1.0 - skin));
    sat = mix(sat, sat * 0.75, skin * (1.0 - vq));        // warmer, less red skin shadows
    hsv.x = mix(hsv.x, 0.075, skin * 0.35);
    vec3 cel = hsv2rgb(vec3(hsv.x, sat, vq));

    // Cool, slightly blue shadows; small specular lift on the brightest areas.
    cel *= mix(vec3(1.0), vec3(0.88, 0.92, 1.08), (1.0 - vq) * 0.5);
    cel += uHighlight * 0.12 * smoothstep(0.85, 1.0, v);

    // Comic halftone dots in the shadows (driven by the Grain slider).
    vec2 px = uv / (uTexel * uStep);
    vec2 g = fract(px / 5.0) - 0.5;
    float dots = step(length(g), 0.5 * (1.0 - vq)) * step(0.45, 1.0 - vq);
    cel *= 1.0 - dots * 0.35 * uGrain;

    vec3 res = mix(orig, cel, uStrength);

    // Outlines on the smoothed image so camera noise does not create speckle.
    vec2 d = uTexel * uStep * mix(0.7, 2.8, uThickness);
    float e = sobelMag(uv, d);
    float line = smoothstep(0.10, 0.32, e) * uEdge;
    res = mix(res, vec3(0.03, 0.02, 0.05), line * uStrength);

    res = gradeTone(res);
    res = applyVignette(res, uv);
    fragColor = vec4(clamp(res, 0.0, 1.0), 1.0);
}
