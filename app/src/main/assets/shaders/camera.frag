// Pass 0: raw camera buffer (or imported bitmap) -> upright, mirrored,
// aspect-cropped processing buffer.
uniform sampler2D uRaw;
uniform int   uRot;          // clockwise rotation (0/90/180/270) that makes the raw frame upright
uniform float uMirror;       // 1 = mirror horizontally (front camera)
uniform float uViewAspect;   // aspect of the output buffer (w/h)
uniform float uImgAspect;    // aspect of the UPRIGHT source image (w/h)
in vec2 vUV;
out vec4 fragColor;

void main() {
    // "Aspect fill": crop the source so it covers the output without stretching.
    vec2 s = vec2(1.0);
    if (uImgAspect > uViewAspect) s.x = uViewAspect / uImgAspect;
    else                          s.y = uImgAspect / uViewAspect;
    vec2 q = (vUV - 0.5) * s + 0.5;          // coordinate in the upright image
    if (uMirror > 0.5) q.x = 1.0 - q.x;

    // Upright image coordinate -> raw buffer coordinate (inverse of the rotation).
    vec2 r;
    if (uRot == 90)       r = vec2(q.y, 1.0 - q.x);
    else if (uRot == 180) r = vec2(1.0 - q.x, 1.0 - q.y);
    else if (uRot == 270) r = vec2(1.0 - q.y, q.x);
    else                  r = q;
    fragColor = vec4(texture(uRaw, r).rgb, 1.0);
}
