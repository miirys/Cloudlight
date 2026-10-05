#pragma once

#include <QString>
#include <QVariant>
#include <QVariantMap>

#include <algorithm>
#include <cmath>

// Typed, bounded game-filter parameters for the stream composition shader.
// QML supplies an untrusted QVariantMap: every key is clamped to its documented
// range, and missing, non-numeric, NaN or infinite values fall back to neutral.
// A neutral filter is inactive and the shader skips the filter path entirely.
struct StreamVideoFilter
{
    float brightness = 0.0f;         // -1..1
    float contrast = 1.0f;           // 0..2
    float saturation = 1.0f;         // 0..2
    float vibrance = 0.0f;           // -1..1
    float temperature = 0.0f;        // -1 (cooler)..1 (warmer)
    float grayscale = 0.0f;          // 0..1
    float sepia = 0.0f;              // 0..1
    float vignette = 0.0f;           // 0..1
    float sharpen = 0.0f;            // 0..1
    float details = 0.0f;            // 0..1
    float grain = 0.0f;              // 0..1
    float nightMode = 0.0f;          // 0..1
    float letterbox = 0.0f;          // 0..1, 1 = 2.39:1 visible area
    int colorblindMode = 0;          // 0 none, 1 protanopia, 2 deuteranopia, 3 tritanopia
    float colorblindStrength = 0.0f; // 0..1

    bool operator==(const StreamVideoFilter &) const = default;

    [[nodiscard]] bool colorblindActive() const
    {
        return colorblindMode != 0 && colorblindStrength > 0.0f;
    }

    [[nodiscard]] bool active() const
    {
        StreamVideoFilter neutral;
        neutral.colorblindMode = colorblindMode;
        neutral.colorblindStrength = colorblindStrength;
        return *this != neutral || colorblindActive();
    }

    [[nodiscard]] static StreamVideoFilter fromVariantMap(const QVariantMap &map)
    {
        const auto number = [&map](const char *key, double &value) {
            const auto found = map.constFind(QString::fromLatin1(key));
            if (found == map.cend()) return false;
            bool ok = false;
            value = found.value().toDouble(&ok);
            return ok && std::isfinite(value);
        };
        const auto read = [&number](const char *key, float neutral, double low, double high) {
            double value = 0.0;
            if (!number(key, value)) return neutral;
            const auto clamped = float(std::clamp(value, low, high));
            // Snap imperceptible offsets so they cannot keep the filter path alive.
            return std::abs(clamped - neutral) < 1.0e-4f ? neutral : clamped;
        };
        StreamVideoFilter filter;
        filter.brightness = read("brightness", 0.0f, -1.0, 1.0);
        filter.contrast = read("contrast", 1.0f, 0.0, 2.0);
        filter.saturation = read("saturation", 1.0f, 0.0, 2.0);
        filter.vibrance = read("vibrance", 0.0f, -1.0, 1.0);
        filter.temperature = read("temperature", 0.0f, -1.0, 1.0);
        filter.grayscale = read("grayscale", 0.0f, 0.0, 1.0);
        filter.sepia = read("sepia", 0.0f, 0.0, 1.0);
        filter.vignette = read("vignette", 0.0f, 0.0, 1.0);
        filter.sharpen = read("sharpen", 0.0f, 0.0, 1.0);
        filter.details = read("details", 0.0f, 0.0, 1.0);
        filter.grain = read("grain", 0.0f, 0.0, 1.0);
        filter.nightMode = read("nightMode", 0.0f, 0.0, 1.0);
        filter.letterbox = read("letterbox", 0.0f, 0.0, 1.0);
        double mode = 0.0;
        if (number("colorblindMode", mode)) {
            const double rounded = std::round(mode);
            filter.colorblindMode = rounded >= 1.0 && rounded <= 3.0 ? int(rounded) : 0;
        }
        filter.colorblindStrength = read("colorblindStrength", 0.0f, 0.0, 1.0);
        return filter;
    }

    [[nodiscard]] QVariantMap toVariantMap() const
    {
        return {
            {QStringLiteral("brightness"), double(brightness)},
            {QStringLiteral("contrast"), double(contrast)},
            {QStringLiteral("saturation"), double(saturation)},
            {QStringLiteral("vibrance"), double(vibrance)},
            {QStringLiteral("temperature"), double(temperature)},
            {QStringLiteral("grayscale"), double(grayscale)},
            {QStringLiteral("sepia"), double(sepia)},
            {QStringLiteral("vignette"), double(vignette)},
            {QStringLiteral("sharpen"), double(sharpen)},
            {QStringLiteral("details"), double(details)},
            {QStringLiteral("grain"), double(grain)},
            {QStringLiteral("nightMode"), double(nightMode)},
            {QStringLiteral("letterbox"), double(letterbox)},
            {QStringLiteral("colorblindMode"), double(colorblindMode)},
            {QStringLiteral("colorblindStrength"), double(colorblindStrength)},
            {QStringLiteral("active"), active()},
        };
    }
};
