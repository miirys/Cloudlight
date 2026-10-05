#version 440
#extension GL_GOOGLE_include_directive : enable
#include "hdrcolor.glsl"
layout(location = 0) in vec2 itemPosition;
layout(location = 0) out vec4 fragColor;
// Keep in sync with StreamVideoTextureRenderer::compositionFloats and streamvideo.vert.
layout(std140, binding = 0) uniform Composition {
    mat4 matrix;
    vec4 bounds;
    vec4 videoRect;
    vec4 parameters;      // opacity, dither step, game filters active, grain seed
    vec4 colorParameters; // source space, output mode, white nits, HDR supported
    vec4 filterTone;      // brightness, contrast, saturation, vibrance
    vec4 filterColor;     // temperature, night mode, grayscale, sepia
    vec4 filterDetail;    // sharpen, details, vignette, grain
    vec4 filterFrame;     // letterbox, colorblind mode, colorblind strength, unused
    vec4 filterTexel;     // texel size of the bound video texture (0 when unknown)
};
layout(binding = 1) uniform sampler2D videoTexture;

const vec3 lumaWeights = vec3(0.2126, 0.7152, 0.0722);

vec3 sampleVideo(vec2 uv)
{
    return texture(videoTexture, clamp(uv, vec2(0.0), vec2(1.0))).rgb;
}

// Sharpen and details work on the transfer-encoded signal (sRGB or PQ/HLG), which is
// already perceptually uniform, so the neighbourhood is decoded only once. Both act
// on luma to avoid colour fringes and are bounded to limit halos.
vec3 sharpenEncoded(vec3 center, vec2 uv, vec2 texel)
{
    if (filterDetail.x > 0.0) {
        vec3 blur = 0.25 * (sampleVideo(uv + vec2(texel.x, 0.0)) + sampleVideo(uv - vec2(texel.x, 0.0))
                          + sampleVideo(uv + vec2(0.0, texel.y)) + sampleVideo(uv - vec2(0.0, texel.y)));
        center += vec3(clamp(dot(center - blur, lumaWeights) * filterDetail.x * 2.0, -0.2, 0.2));
    }
    if (filterDetail.y > 0.0) {
        vec2 radius = texel * 3.5;
        vec3 blur = 0.25 * (sampleVideo(uv + radius) + sampleVideo(uv - radius)
                          + sampleVideo(uv + vec2(radius.x, -radius.y))
                          + sampleVideo(uv + vec2(-radius.x, radius.y)));
        center += vec3(clamp(dot(center - blur, lumaWeights) * filterDetail.y * 1.5, -0.12, 0.12));
    }
    return max(center, vec3(0.0));
}

// Linear BT.709 relative to SDR reference white (1.0 = whiteNits). HDR passthrough keeps
// highlights above 1.0; SDR output keeps the same tone map as videoColor().
vec3 decodeVideoLinear(vec3 encoded)
{
    if (colorParameters.x < 0.5) return sdrToLinear(encoded);
    vec3 nits = bt2020To709(colorParameters.x < 1.5 ? pqToNits(encoded) : hlgToNits(encoded));
    float whiteNits = max(colorParameters.z, 1.0);
    if (colorParameters.y < 0.5 || colorParameters.w < 0.5) return toneMapToSdr(nits, whiteNits);
    return nits / whiteNits;
}

vec3 encodeVideoOutput(vec3 linear)
{
    if (colorParameters.y < 0.5) return linearToSdr(linear);
    float whiteNits = max(colorParameters.z, 1.0);
    return outputColor(linear * whiteNits, colorParameters.y, whiteNits);
}

// Machado et al. 2009 full-severity simulation in linear RGB, then Fidaner-style
// redistribution of the lost contrast onto channels the viewer can distinguish.
vec3 daltonize(vec3 color, float mode, float strength)
{
    vec3 simulated;
    if (mode < 1.5)
        simulated = color * mat3(0.152286, 1.052583, -0.204868,
                                 0.114503, 0.786281, 0.099216,
                                 -0.003882, -0.048116, 1.051998);
    else if (mode < 2.5)
        simulated = color * mat3(0.367322, 0.860646, -0.227968,
                                 0.280085, 0.672501, 0.047413,
                                 -0.011820, 0.042940, 0.968881);
    else
        simulated = color * mat3(1.255528, -0.076749, -0.178779,
                                 -0.078411, 0.930809, 0.147602,
                                 0.004733, 0.691367, 0.303900);
    vec3 error = color - simulated;
    vec3 shift = mode < 2.5
        ? vec3(0.0, 0.7 * error.r + error.g, 0.7 * error.r + error.b)
        : vec3(error.r + 0.7 * error.b, error.g + 0.7 * error.b, 0.0);
    return max(color + shift * strength, vec3(0.0));
}

float grainNoise(vec2 position)
{
    vec3 p = fract(vec3(position.xyx) * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

vec3 filteredVideoColor(vec2 uv, vec2 pixelStep)
{
    float aspect = videoRect.z / max(videoRect.w, 1.0);
    float bar = 0.5 * (1.0 - mix(1.0, min(1.0, aspect / 2.39), filterFrame.x));
    if (uv.y < bar || uv.y > 1.0 - bar) return vec3(0.0);

    vec3 encoded = texture(videoTexture, uv).rgb;
    if (filterDetail.x > 0.0 || filterDetail.y > 0.0)
        encoded = sharpenEncoded(encoded, uv, max(pixelStep, filterTexel.xy));
    vec3 color = decodeVideoLinear(encoded);

    if (filterTone.x != 0.0 || filterTone.y != 1.0) {
        // Gamma-2.2 perceptual domain; contrast pivots at mid grey and leaves the HDR
        // range above reference white unexpanded so highlights stay bounded.
        vec3 perceptual = pow(max(color, vec3(0.0)), vec3(1.0 / 2.2));
        vec3 standard = min(perceptual, vec3(1.0));
        perceptual = (standard - 0.5) * filterTone.y + 0.5 + (perceptual - standard)
                   + filterTone.x * 0.4;
        color = pow(max(perceptual, vec3(0.0)), vec3(2.2));
    }
    if (filterColor.x != 0.0) {
        vec3 gain = vec3(1.0 + 0.18 * filterColor.x, 1.0 + 0.02 * filterColor.x,
                         1.0 - 0.30 * filterColor.x);
        color *= gain / dot(gain, lumaWeights);
    }
    if (filterTone.z != 1.0 || filterTone.w != 0.0) {
        float luma = dot(color, lumaWeights);
        float high = max(max(color.r, color.g), color.b);
        float low = min(min(color.r, color.g), color.b);
        float chroma = high > 0.00001 ? (high - low) / high : 0.0;
        float saturation = filterTone.z * (1.0 + filterTone.w * (1.0 - chroma));
        color = max(vec3(luma) + (color - vec3(luma)) * saturation, vec3(0.0));
    }
    if (filterColor.y > 0.0)
        color *= vec3(1.0, 1.0 - 0.22 * filterColor.y, 1.0 - 0.65 * filterColor.y)
               * (1.0 - 0.2 * filterColor.y);
    if (filterColor.z > 0.0)
        color = mix(color, vec3(dot(color, lumaWeights)), filterColor.z);
    if (filterColor.w > 0.0)
        color = mix(color, dot(color, lumaWeights) * vec3(1.165, 0.978, 0.728), filterColor.w);
    if (filterFrame.y > 0.5 && filterFrame.z > 0.0)
        color = daltonize(color, filterFrame.y, filterFrame.z);
    if (filterDetail.z > 0.0) {
        float radius = length(uv - 0.5) * 1.41421356;
        color *= 1.0 - filterDetail.z * 0.9 * smoothstep(0.35, 1.0, radius);
    }
    if (filterDetail.w > 0.0) {
        float noise = grainNoise(gl_FragCoord.xy + parameters.w * vec2(37.0, 17.0)) - 0.5;
        color = color * (1.0 + noise * filterDetail.w * 0.35) + vec3(noise * filterDetail.w * 0.004);
    }
    color = clamp(color, vec3(0.0), vec3(10000.0 / max(colorParameters.z, 1.0)));
    return encodeVideoOutput(color);
}

void main()
{
    vec2 uv = (itemPosition - videoRect.xy) / max(videoRect.zw, vec2(1.0));
    // Derivatives are taken in uniform control flow; uv is affine across the quad.
    vec2 pixelStep = vec2(0.0);
    if (parameters.z > 0.5) pixelStep = abs(dFdx(uv)) + abs(dFdy(uv));
    vec3 color = vec3(0.0);
    if (all(greaterThanEqual(uv, vec2(0.0))) && all(lessThanEqual(uv, vec2(1.0)))) {
        if (parameters.z > 0.5)
            color = filteredVideoColor(uv, pixelStep);
        else
            color = videoColor(texture(videoTexture, uv).rgb, colorParameters.x,
                               colorParameters.y, colorParameters.z, colorParameters.w);
        if (colorParameters.y < 0.5 && parameters.y > 0.0) {
            uvec2 pixel = uvec2(gl_FragCoord.xy) & uvec2(7u);
            uint rank = ((pixel.x ^ pixel.y) & 1u) * 32u + (pixel.y & 1u) * 16u
                      + ((pixel.x ^ pixel.y) & 2u) * 4u + (pixel.y & 2u) * 2u
                      + ((pixel.x ^ pixel.y) & 4u) / 2u + (pixel.y & 4u) / 4u;
            float dither = ((float(rank) + 0.5) / 64.0 - 0.5) * parameters.y;
            color = floor(clamp(color + vec3(dither), 0.0, 1.0) * 255.0 + 0.5) / 255.0;
        }
    }
    fragColor = vec4(color * parameters.x, parameters.x);
}
