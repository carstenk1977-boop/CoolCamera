// Full-screen quad. vUV = image coordinates, origin top-left, y down.
layout(location = 0) in vec2 aPos;
out vec2 vUV;
void main() {
    vUV = vec2(aPos.x * 0.5 + 0.5, 0.5 - aPos.y * 0.5);
    gl_Position = vec4(aPos, 0.0, 1.0);
}
