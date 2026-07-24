#version 450

layout(push_constant) uniform PushConstants {
    int fbWidth;
    int fbHeight;
    int format;
    int _pad;
} pc;

layout(location = 0) in vec2 vUV;
layout(location = 0) out vec4 fragColor;

layout(binding = 0) readonly buffer PixelData {
    uint pixels[];
} data;

void main() {
    ivec2 tc = ivec2(vUV * vec2(pc.fbWidth, pc.fbHeight));
    int idx = tc.y * pc.fbWidth + tc.x;
    uint p;

    if (pc.format == 3) {
        p = data.pixels[idx];
    } else {
        p = data.pixels[idx / 2];
        if ((idx & 1) == 0) p = p & 0xFFFFu;
        else p = (p >> 16) & 0xFFFFu;
    }

    vec4 color;
    if (pc.format == 0) {
        color = vec4(
            float((p >> 11) & 0x1Fu) / 31.0,
            float((p >> 5) & 0x3Fu) / 63.0,
            float((p >> 0) & 0x1Fu) / 31.0,
            1.0
        );
    } else if (pc.format == 1) {
        color = vec4(
            float((p >> 11) & 0x1Fu) / 31.0,
            float((p >> 6) & 0x1Fu) / 31.0,
            float((p >> 1) & 0x1Fu) / 31.0,
            float(p & 0x1u)
        );
    } else if (pc.format == 2) {
        color = vec4(
            float((p >> 12) & 0xFu) / 15.0,
            float((p >> 8) & 0xFu) / 15.0,
            float((p >> 4) & 0xFu) / 15.0,
            float(p & 0xFu) / 15.0
        );
    } else {
        color = vec4(
            float((p >> 0) & 0xFFu) / 255.0,
            float((p >> 8) & 0xFFu) / 255.0,
            float((p >> 16) & 0xFFu) / 255.0,
            float((p >> 24) & 0xFFu) / 255.0
        );
    }
    fragColor = color;
}
