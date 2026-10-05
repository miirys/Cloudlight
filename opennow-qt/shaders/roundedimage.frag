#version 440
// Rounded, cover-cropped artwork in one pass: no offscreen layers or mask
// textures per tile. The image is sampled directly from its texture provider.
layout(location = 0) in vec2 qt_TexCoord0;
layout(location = 0) out vec4 fragColor;
layout(std140, binding = 0) uniform buf {
    mat4 qt_Matrix;
    float qt_Opacity;
    vec2 itemSize;      // logical pixels
    vec4 uvRect;        // cover crop: offset.xy, scale.zw
    vec4 fallback;      // premultiplied colour shown under the image
    float radius;       // logical pixels
    float pixelScale;   // device pixels per logical pixel, for edge anti-aliasing
    float imageAmount;  // 0..1 image fade-in
    float scrimStart;   // 0..1 where the bottom scrim starts; >= 1 disables it
};
layout(binding = 1) uniform sampler2D source;

void main() {
    vec2 p = qt_TexCoord0 * itemSize;
    vec2 halfSize = itemSize * 0.5;
    float r = min(radius, min(halfSize.x, halfSize.y));
    vec2 q = abs(p - halfSize) - (halfSize - vec2(r));
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    float coverage = clamp(0.5 - d * pixelScale, 0.0, 1.0);

    vec4 image = texture(source, uvRect.xy + qt_TexCoord0 * uvRect.zw);
    vec4 color = mix(fallback, image + fallback * (1.0 - image.a), imageAmount);
    if (scrimStart < 1.0) {
        float t = clamp((qt_TexCoord0.y - scrimStart) / max(0.0001, 1.0 - scrimStart), 0.0, 1.0);
        color.rgb *= 1.0 - 0.82 * t;
    }
    fragColor = color * coverage * qt_Opacity;
}
