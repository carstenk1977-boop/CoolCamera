// Pass 1: Kuwahara filter - an edge-PRESERVING smoother that gives a painted,
// flat-region look while keeping silhouettes sharp.
//
// The (2r+1)^2 neighbourhood is split into four overlapping (r+1)^2 quadrants
// around the pixel. We output the mean colour of the quadrant with the LOWEST
// luminance variance, so the result never averages across an edge.
uniform int uRadius;

void main() {
    int r = uRadius;
    vec2 o = uTexel * uStep;
    vec3 s0 = vec3(0.0); vec3 s1 = vec3(0.0); vec3 s2 = vec3(0.0); vec3 s3 = vec3(0.0);
    float l0 = 0.0; float l1 = 0.0; float l2 = 0.0; float l3 = 0.0;
    float q0 = 0.0; float q1 = 0.0; float q2 = 0.0; float q3 = 0.0;

    for (int j = -r; j <= r; j++) {
        for (int i = -r; i <= r; i++) {
            vec3 c = fetchBase(vUV + vec2(float(i), float(j)) * o);
            float lm = luma(c);
            float sq = lm * lm;
            if (i <= 0 && j <= 0) { s0 += c; l0 += lm; q0 += sq; }
            if (i >= 0 && j <= 0) { s1 += c; l1 += lm; q1 += sq; }
            if (i >= 0 && j >= 0) { s2 += c; l2 += lm; q2 += sq; }
            if (i <= 0 && j >= 0) { s3 += c; l3 += lm; q3 += sq; }
        }
    }
    float n = float((r + 1) * (r + 1));
    float v0 = q0 / n - (l0 / n) * (l0 / n);
    float v1 = q1 / n - (l1 / n) * (l1 / n);
    float v2 = q2 / n - (l2 / n) * (l2 / n);
    float v3 = q3 / n - (l3 / n) * (l3 / n);

    vec3 outc = s0 / n; float best = v0;
    if (v1 < best) { best = v1; outc = s1 / n; }
    if (v2 < best) { best = v2; outc = s2 / n; }
    if (v3 < best) { best = v3; outc = s3 / n; }
    fragColor = vec4(outc, 1.0);
}
