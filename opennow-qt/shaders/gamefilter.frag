#version 440
// One NVIDIA Freestyle-compatible game filter per pass, run at the stream's resolution on the
// decoded, still transfer-encoded R'G'B' (Freestyle reads the game's swap chain without any
// sRGB decode). The pass writes a UNORM target, so every filter's output is clamped before
// the next filter reads it. The formulas are Cloudlight's own GLSL restatement of Freestyle's
// filter maths; no NVIDIA shader file or texture is shipped (the film dirt is procedural).
//
// The Sharpen filter is a port of the GeForce Experience image sharpening filter:
//   Image sharpening filter from GeForce Experience. Provided by NVIDIA Corporation.
//
//   Copyright 2019 Suketu J. Shah. All rights reserved.
//
//   Redistribution and use in source and binary forms, with or without modification,
//   are permitted provided that the following conditions are met:
//
//     1. Redistributions of source code must retain the above copyright notice, this
//        list of conditions and the following disclaimer.
//     2. Redistributions in binary form must reproduce the above copyright notice,
//        this list of conditions and the following disclaimer in the documentation
//        and/or other materials provided with the distribution.
//     3. Neither the name of the copyright holder nor the names of its contributors
//        may be used to endorse or promote products derived from this software
//        without specific prior written permission.
//
//   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
//   ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
//   WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
//   DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
//   ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
//   (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
//   LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
//   ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
//   (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
//   SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//
// Sharpen+ is a port of the sharpen-only path (NVSharpen) of the NVIDIA Image Scaling SDK
// v1.0.3, NIS/NIS_Scaler.h and NIS/NIS_Config.h:
//
//   The MIT License(MIT)
//
//   Copyright(c) 2022 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
//
//   Permission is hereby granted, free of charge, to any person obtaining a copy of
//   this software and associated documentation files(the "Software"), to deal in
//   the Software without restriction, including without limitation the rights to
//   use, copy, modify, merge, publish, distribute, sublicense, and / or sell copies of
//   the Software, and to permit persons to whom the Software is furnished to do so,
//   subject to the following conditions :
//
//   The above copyright notice and this permission notice shall be included in all
//   copies or substantial portions of the Software.
//
//   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
//   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
//   FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.IN NO EVENT SHALL THE AUTHORS OR
//   COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
//   IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
//   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

layout(location = 0) in vec2 uv;
layout(location = 0) out vec4 fragColor;
// Keep in sync with StreamGameFilterChain::uniformFloats.
layout(std140, binding = 0) uniform Parameters {
    vec4 geometry;   // 0, 0, NDC y sign (framegen.vert), elapsed milliseconds
    vec4 dimensions; // width, height, 1 / width, 1 / height
    vec4 stage;      // filter type, 0, 0, 0
    vec4 p0;
    vec4 p1;
};
layout(binding = 1) uniform sampler2D inputTexture;
layout(binding = 2) uniform sampler2D auxTexture; // Details: horizontal blur of the input

const int BlackWhite = 1;
const int BrightnessContrast = 2;
const int Color = 3;
const int Colorblind = 4;
const int Details = 5;
const int Letterbox = 6;
const int NightMode = 7;
const int OldFilm = 8;
const int Sharpen = 9;
const int Vignette = 10;
const int SharpenPlus = 11;
const int DetailsBlur = 100;

float luma601(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }
float luma709(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
float sq(float x) { return x * x; }

// HLSL smoothstep, which (unlike GLSL) is defined for edge0 > edge1 as a reversed ramp.
float hsmooth(float e0, float e1, float x)
{
    float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}
vec3 hsmooth(float e0, float e1, vec3 x)
{
    vec3 t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

ivec2 pixel() { return ivec2(gl_FragCoord.xy); }
vec2 position() { return gl_FragCoord.xy * dimensions.zw; }

vec3 fetchInput(ivec2 offset)
{
    ivec2 p = clamp(pixel() + offset, ivec2(0), ivec2(dimensions.xy) - 1);
    return texelFetch(inputTexture, p, 0).rgb;
}

// Separable Gaussian with 15 bilinear tap pairs at ±(2i − 0.5) texels.
vec3 gauss(sampler2D source, vec2 at, vec2 axis)
{
    float norm = -1.35914091423 / (15.0 * 15.0);
    vec3 sum = textureLod(source, at, 0.0).rgb;
    float weights = 0.5;
    for (int i = 1; i <= 15; ++i) {
        float offset = float(i) * 2.0 - 0.5;
        float weight = exp(float(i * i) * norm);
        vec2 delta = axis * dimensions.zw * offset;
        sum += (textureLod(source, at + delta, 0.0).rgb + textureLod(source, at - delta, 0.0).rgb) * weight;
        weights += weight;
    }
    return sum / (2.0 * weights);
}

vec3 brightnessContrast(vec3 c)
{
    float exposure = p0.x, contrast = p0.y, highlights = p0.z, shadows = p0.w, gamma = p1.x;
    c *= exp2(2.0 * exposure);
    vec3 shadow = hsmooth(0.666, 0.0, c);
    vec3 highlight = hsmooth(0.333, 1.0, c);
    vec3 rest = 1.0 - shadow - highlight;
    vec3 exponent = shadow * exp2(shadows) + highlight * exp2(-highlights) + exp2(-2.0 * gamma) + rest - 2.0;
    c = pow(clamp(c, 0.0, 1.0), exp2(exponent));
    float k = exp(log(0.5) + (contrast * 0.5 + 0.5) * (log(2.0) - log(0.5)));
    return (c - 0.5) * k + 0.5;
}

vec3 colorFilter(vec3 c)
{
    float tintColor = p0.x, tintIntensity = p0.y, temperature = p0.z, vibrance = p0.w;
    float Y = dot(c, vec3(0.299, 0.587, 0.114));
    float U = dot(c, vec3(-0.14713, -0.28886, 0.436));
    float V = dot(c, vec3(0.615, -0.51499, -0.10001));
    U -= temperature * Y * 0.35;
    V += temperature * Y * 0.35;
    float tint = tintIntensity * tintIntensity;
    U += sin(tintColor * 6.283185307) * tint * Y;
    V += cos(tintColor * 6.283185307) * tint * Y;
    c = clamp(vec3(Y + 1.13983 * V, Y - 0.39465 * U - 0.58060 * V, Y + 2.03211 * U), 0.0, 1.0);
    float saturation = max(max(c.r, c.g), c.b) - min(min(c.r, c.g), c.b);
    float luma = luma601(c);
    return vibrance > 0.0 ? mix(vec3(luma), c, 1.0 + vibrance * (1.0 - saturation))
                          : mix(vec3(luma), c, clamp(1.0 + vibrance * (1.0 + saturation), 0.0, 1.0));
}

vec3 colorblind(vec3 c)
{
    vec3 L = vec3(dot(c, vec3(17.8824, 43.5161, 4.11935)),
                  dot(c, vec3(3.45565, 27.1554, 3.86714)),
                  dot(c, vec3(0.0299566, 0.184309, 1.46709)));
    vec3 D = L;
    D.x = mix(D.x, dot(L, vec3(0.0, 2.02344, -2.52581)), p0.x);
    D.y = mix(D.y, dot(L, vec3(0.494207, 0.0, 1.24827)), p0.y);
    D.z = mix(D.z, dot(L, vec3(-0.395913, 0.801109, 0.0)), p0.z);
    vec3 d = vec3(dot(D, vec3(0.0809444479, -0.130504409, 0.116721066)),
                  dot(D, vec3(-0.0102485335, 0.0540193266, -0.113614708)),
                  dot(D, vec3(-0.000365296938, -0.00412161469, 0.693511405)));
    vec3 o = c;
    o.g += 0.7 * (c.r - d.r) + (c.g - d.g);
    o.b += 0.7 * (c.r - d.r) + (c.b - d.b);
    return o;
}

vec3 details(vec3 c)
{
    float sharpen = p0.x, clarity = p0.y, hdrToning = p0.z, bloom = p0.w;
    vec2 at = position();
    vec3 large = gauss(auxTexture, at, vec2(0.0, 1.0));
    const vec3 taps[8] = vec3[8](vec3(0.5, 1.5, 1.5), vec3(1.5, -0.5, 1.5), vec3(-0.5, -1.5, 1.5),
                                 vec3(-1.5, 0.5, 1.5), vec3(2.5, 1.5, 1.0), vec3(1.5, -2.5, 1.0),
                                 vec3(-2.5, -1.5, 1.0), vec3(-1.5, 2.5, 1.0));
    vec3 small = vec3(0.0);
    for (int i = 0; i < 8; ++i)
        small += textureLod(inputTexture, at + taps[i].xy * dimensions.zw, 0.0).rgb * taps[i].z;
    small /= 10.0;
    // Pure black has no luma to scale; NVIDIA's division there is undefined (usually 0).
    float a = max(luma601(c), 1.0e-6);
    float b = luma601(large);
    float root = sqrt(a);
    float toned = root * (b > 0.5 ? (2.0 * root * b - 2.0 * b + 1.0) : root * (2.0 * a * b - a - 2.0 * b + 2.0));
    c = c / (a + 1.0e-6) * mix(a, toned, hdrToning);
    float limit = mix(0.25, 0.6, sharpen);
    float s = clamp(luma601(c - small), -limit, limit);
    c = c / a * mix(a, a + s, sharpen);
    float clarityLight = 0.5 + a - b;
    clarityLight = a > b ? 2.0 * (1.0 - clarityLight) + (2.0 * clarityLight - 1.0) * inversesqrt(a)
                         : 2.0 * clarityLight + a * (1.0 - 2.0 * clarityLight);
    c *= mix(1.0, clarityLight, clarity);
    return 1.0 - (1.0 - c) * (1.0 - large * bloom);
}

vec3 letterbox(vec3 c)
{
    float screenAspect = dimensions.x / max(dimensions.y, 1.0);
    float target = p0.x / max(p0.y, 1.0);
    vec2 p = position() * 2.0 - 1.0;
    if (target < screenAspect) p.x *= screenAspect / target;
    else p.y /= screenAspect / target;
    return all(greaterThan(1.0 - p * p, vec2(0.0))) ? c : vec3(0.0);
}

vec3 nightMode(vec3 c)
{
    float r = 1.0 - p0.x;
    c.g *= pow(r * 0.95 + 0.05, 0.4);
    c.b *= pow(clamp(r * 0.95 - 0.05, 0.0, 1.0), 0.333) * 2.05 - 0.95;
    return c;
}

uint hash(uvec3 v)
{
    v = v * 1664525u + 1013904223u;
    v.x += v.y * v.z; v.y += v.z * v.x; v.z += v.x * v.y;
    v ^= v >> 16u;
    v.x += v.y * v.z; v.y += v.z * v.x; v.z += v.x * v.y;
    return v.x ^ v.y ^ v.z;
}
float random(uvec3 v) { return float(hash(v) >> 8u) / 16777216.0; }

float valueNoise(vec2 p, uint seed, uvec2 period)
{
    vec2 cell = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    uvec2 c0 = uvec2(mod(cell, vec2(period))), c1 = uvec2(mod(cell + 1.0, vec2(period)));
    float a = random(uvec3(c0.x, c0.y, seed)), b = random(uvec3(c1.x, c0.y, seed));
    float c = random(uvec3(c0.x, c1.y, seed)), d = random(uvec3(c1.x, c1.y, seed));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// Cloudlight's own tileable film dirt, one independent layer per channel: soft blotches,
// thin vertical scratches and specks over a slightly grey base (values mostly 0.8–1.0).
float filmDirt(vec2 t, int layer)
{
    t = fract(t);
    uint seed = uint(layer) * 977u + 13u;
    vec2 p = t * vec2(1920.0, 1080.0);
    float dirt = 0.84 + 0.12 * (valueNoise(t * vec2(12.0, 7.0), seed, uvec2(12u, 7u)) - 0.5);
    float blotch = valueNoise(t * vec2(24.0, 14.0), seed + 1u, uvec2(24u, 14u));
    dirt -= 0.28 * smoothstep(0.78, 0.95, blotch);
    uint column = uint(p.x / 2.0);
    float scratch = random(uvec3(column, seed, 7u));
    if (scratch > 0.992) {
        float along = valueNoise(vec2(float(column), t.y * 9.0), seed + 2u, uvec2(960u, 9u));
        dirt -= (0.35 + 0.3 * random(uvec3(column, seed, 9u))) * smoothstep(0.35, 0.6, along);
    }
    vec2 cell = floor(p / 12.0);
    vec2 cellPeriod = vec2(160.0, 90.0);
    uvec2 cellId = uvec2(mod(cell, cellPeriod));
    if (random(uvec3(cellId, seed + 3u)) > 0.985) {
        vec2 centre = (cell + vec2(random(uvec3(cellId, seed + 4u)), random(uvec3(cellId, seed + 5u)))) * 12.0;
        float radius = 0.8 + 2.2 * random(uvec3(cellId, seed + 6u));
        dirt -= 0.55 * (1.0 - smoothstep(radius * 0.5, radius, length(p - centre)));
    }
    return clamp(dirt, 0.36, 1.0);
}

vec3 oldFilm(vec3 c)
{
    float gamma = p0.x, exposure = p0.y, contrast = p0.z, vignette = p0.w;
    float strength = p1.x, dirtStrength = p1.y;
    vec2 at = position();
    float gammaK = mix(0.4, 0.01, gamma), tint = -2.0 * strength;
    c *= 0.33;
    c = mix(c, vec3(luma601(c)), strength);
    c = pow(c, mix(vec3(3.0), vec3(1.0), c)) * 4.5;
    c *= exposure * 1.1;
    c = pow(c, vec3(gammaK * 0.9));
    c = mix(c, c * c * (3.0 - 2.0 * c), contrast);
    c *= mix(vec3(1.0), vec3(0.0, 0.42, 1.28), 0.1 * tint * clamp(1.0 - dot(c, vec3(0.333)), 0.0, 1.0));
    vec2 v = at - 0.5;
    float vig = dot(v, v);
    c *= mix(1.0, 0.0, clamp((1.0 - dot(c, vec3(0.333))) * vig * vignette * 8.0, 0.0, 1.0));
    // A new dirt layout every 83.3 ms, as in Freestyle.
    float f = floor(geometry.w / 83.3);
    vec2 flip = vec2(sin(f * 233.22) > 0.0 ? 1.0 : 0.0, sin(f * 122.1 + 0.22) < 0.0 ? 1.0 : 0.0);
    vec2 shift = vec2(sin(f * 17.1 - 0.25), sin(f * 23.1 + 4.25));
    int layer = int(mod(floor(fract(f * 1.618) * 19.0), 3.0));
    float d = filmDirt(mix(at, -at, flip) + shift, layer);
    d = mix(d, 1.0, clamp(1.0 - vig * 0.8, 0.0, 1.0));
    d = mix(1.0, d, dirtStrength * 3.0);
    c *= clamp(d, 0.0, 1.0);
    return mix(clamp(c, 0.0, 1.0), vec3(1.0), 0.16);
}

vec3 sharpenFilter(vec3 c)
{
    float sharpen = p0.x, ignoreGrain = p0.y;
    float lx = luma601(c);
    float la = luma601(fetchInput(ivec2(-1, 0))), lb = luma601(fetchInput(ivec2(1, 0)));
    float lc = luma601(fetchInput(ivec2(0, 1))), ld = luma601(fetchInput(ivec2(0, -1)));
    float le = luma601(fetchInput(ivec2(-1, -1))), lf = luma601(fetchInput(ivec2(1, 1)));
    float lg = luma601(fetchInput(ivec2(-1, 1))), lh = luma601(fetchInput(ivec2(1, -1)));
    float ncmin = min(min(le, lf), min(lg, lh)), ncmax = max(max(le, lf), max(lg, lh));
    float npmin = min(min(min(la, lb), min(lc, ld)), lx), npmax = max(max(max(la, lb), max(lc, ld)), lx);
    float lmin = 0.5 * min(ncmin, npmin) + 0.5 * npmin;
    float lmax = 0.5 * max(ncmax, npmax) + 0.5 * npmax;
    float lw = lmin / (lmax + 1.0 / 256.0);
    float hw = sq(1.0 - sq(max(lmax - 0.65, 0.0) / 0.35));
    // kDenoiseMax is -0.1 in the reference implementation; kept as is.
    float kd = 1.0 / (0.001 + (-0.1 - 0.001) * clamp(ignoreGrain, 0.0, 1.0));
    float nw = sq((lmax - lmin) * kd);
    float k = min(min(lw, hw), nw) * (-1.0 / 14.0 + (-1.0 / 6.5 + 1.0 / 14.0) * clamp(sharpen, 0.0, 1.0));
    float acc = (lx + (la + lb + lc + ld) * k + (le + lf + lg + lh) * 0.5 * k) / (1.0 + 6.0 * k);
    return c + vec3(acc - lx);
}

vec3 vignetteFilter(vec3 c)
{
    vec2 p = position() - 0.5;
    p.x *= 1.2;
    float v = mix(1.0, hsmooth(0.7, 0.0, dot(p, p)), p0.x);
    return c * v * v;
}

// --- Sharpen+ (NVSharpen, SDR constants from NVScalerUpdateConfig) -------------------------
float nisY(ivec2 offset) { return luma709(fetchInput(offset)); }

vec4 nisEdgeMap(float p[25])
{
    // GetEdgeMap(p, 1, 1) on the 5x5 support: the 3x3 block centred on the pixel.
    #define P(i, j) p[(i + 1) * 5 + (j + 1)]
    const float kDetectRatio = 2.0 * 1127.0 / 1024.0;
    const float kDetectThres = 64.0 / 1024.0;
    float g0 = abs(P(0,0) + P(0,1) + P(0,2) - P(2,0) - P(2,1) - P(2,2));
    float g45 = abs(P(1,0) + P(0,0) + P(0,1) - P(2,1) - P(2,2) - P(1,2));
    float g90 = abs(P(0,0) + P(1,0) + P(2,0) - P(0,2) - P(1,2) - P(2,2));
    float g135 = abs(P(1,0) + P(2,0) + P(2,1) - P(0,1) - P(0,2) - P(1,2));
    #undef P
    float g090max = max(g0, g90), g090min = min(g0, g90);
    float g45135max = max(g45, g135), g45135min = min(g45, g135);
    if (g090max + g45135max == 0.0) return vec4(0.0);
    float e090 = min(g090max / (g090max + g45135max), 1.0);
    float e45135 = 1.0 - e090;
    bool c090 = g090max > g090min * kDetectRatio && g090max > kDetectThres && g090max > g45135min;
    bool c45135 = g45135max > g45135min * kDetectRatio && g45135max > kDetectThres && g45135max > g090min;
    bool cg090 = g090max == g0;
    bool cg45135 = g45135max == g45;
    float fe090 = c090 && c45135 ? e090 : 1.0;
    float fe45135 = c090 && c45135 ? e45135 : 1.0;
    return vec4(c090 && cg090 ? fe090 : 0.0, c090 && !cg090 ? fe090 : 0.0,
                c45135 && cg45135 ? fe45135 : 0.0, c45135 && !cg45135 ? fe45135 : 0.0);
}

float nisLti(float y0, float y1, float y2, float y3, float y4)
{
    const float kMinContrastRatio = 2.0;
    const float kRatioNorm = 1.0 / (10.0 - 2.0);
    const float kEps = 1.0 / 255.0;
    float aCont = max(max(y0, y1), y2) - min(min(y0, y1), y2);
    float bCont = max(max(y2, y3), y4) - min(min(y2, y3), y4);
    float ratio = max(aCont, bCont) / (min(aCont, bCont) + kEps);
    return 1.0 - clamp((ratio - kMinContrastRatio) * kRatioNorm, 0.0, 1.0);
}

float nisUsm(float y0, float y1, float y2, float y3, float y4, float strength, float limit)
{
    float usm = (-0.6001 * y1 + 1.2002 * y2 - 0.6001 * y3) * strength;
    return clamp(usm, -limit, limit) * nisLti(y0, y1, y2, y3, y4);
}

vec3 sharpenPlus(vec3 c)
{
    // p[i][j]: row i (y), column j (x), centred on this pixel, as NIS loads its tile.
    float p[25];
    for (int i = 0; i < 5; ++i)
        for (int j = 0; j < 5; ++j)
            p[i * 5 + j] = nisY(ivec2(j - 2, i - 2));
    #define P(i, j) p[(i) * 5 + (j)]
    float slider = clamp(p0.x, 0.0, 1.0) - 0.5;
    float maxScale = slider >= 0.0 ? 1.25 : 1.75;
    float minScale = slider >= 0.0 ? 1.25 : 1.0;
    float limitScale = slider >= 0.0 ? 1.25 : 1.0;
    float strengthMin = max(0.0, 0.4 + slider * minScale * 1.2);
    float strengthMax = 1.6 + slider * maxScale * 1.8;
    float limitMin = max(0.1, 0.14 + slider * limitScale * 0.32);
    float limitMax = 0.5 + slider * limitScale * 0.6;
    const float kSharpStartY = 0.45;
    const float kSharpScaleY = 1.0 / (0.9 - 0.45);
    float scaleY = 1.0 - clamp((P(2,2) - kSharpStartY) * kSharpScaleY, 0.0, 1.0);
    float strength = scaleY * (strengthMax - strengthMin) + strengthMin;
    float limit = (scaleY * (limitMax - limitMin) + limitMin) * P(2,2);
    vec4 usm;
    usm.x = nisUsm(P(0,2), P(1,2), P(2,2), P(3,2), P(4,2), strength, limit);
    usm.y = nisUsm(P(2,0), P(2,1), P(2,2), P(2,3), P(2,4), strength, limit);
    usm.z = nisUsm(P(1,1), mix(P(2,1), P(1,2), 0.5), P(2,2), mix(P(3,2), P(2,3), 0.5), P(3,3), strength, limit);
    usm.w = nisUsm(P(3,1), mix(P(3,2), P(2,1), 0.5), P(2,2), mix(P(2,3), P(1,2), 0.5), P(1,3), strength, limit);
    #undef P
    return c + vec3(dot(usm, nisEdgeMap(p)));
}

void main()
{
    int type = int(stage.x + 0.5);
    if (type == DetailsBlur) {
        fragColor = vec4(gauss(inputTexture, position(), vec2(1.0, 0.0)), 1.0);
        return;
    }
    vec3 c = fetchInput(ivec2(0));
    if (type == BlackWhite) c = mix(c, vec3(luma709(c)), p0.x);
    else if (type == BrightnessContrast) c = brightnessContrast(c);
    else if (type == Color) c = colorFilter(c);
    else if (type == Colorblind) c = colorblind(c);
    else if (type == Details) c = details(c);
    else if (type == Letterbox) c = letterbox(c);
    else if (type == NightMode) c = nightMode(c);
    else if (type == OldFilm) c = oldFilm(c);
    else if (type == Sharpen) c = sharpenFilter(c);
    else if (type == Vignette) c = vignetteFilter(c);
    else if (type == SharpenPlus) c = sharpenPlus(c);
    // Non-finite results (e.g. pow of a negative base) write black, like a UNORM target.
    if (any(isnan(c)) || any(isinf(c))) c = vec3(0.0);
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
