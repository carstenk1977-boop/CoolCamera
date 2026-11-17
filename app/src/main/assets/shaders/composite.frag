// Final pass: draw original / filtered / split view to the screen.
uniform sampler2D uOrig;
uniform sampler2D uFilt;
uniform int   uMode;     // 0 filtered, 1 original, 2 split
uniform float uSplit;    // split position as a fraction of the VIEW width
uniform float uViewW;
in vec2 vUV;
out vec4 fragColor;

void main() {
    vec2 t = vec2(vUV.x, 1.0 - vUV.y);   // render targets are stored GL-native
    vec3 a = texture(uOrig, t).rgb;
    vec3 b = texture(uFilt, t).rgb;
    vec3 c = b;
    if (uMode == 1) {
        c = a;
    } else if (uMode == 2) {
        c = (gl_FragCoord.x / uViewW < uSplit) ? a : b;
    }
    fragColor = vec4(c, 1.0);
}
