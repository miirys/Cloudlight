#pragma once

#include <QString>
#include <QVariant>
#include <QVariantList>
#include <QVariantMap>

#include <algorithm>
#include <array>
#include <cmath>
#include <iterator>

// The active game filter style as an ordered chain of NVIDIA Freestyle-compatible filters.
// QML supplies the persisted style (an untrusted list of {type, <slider>: integer}); each
// entry is validated against the catalogue below, its sliders clamped to NVIDIA's ranges,
// and missing or non-finite values take the default a filter starts with when added.
// Unknown types are dropped. The renderer runs one pass per stage in list order.
struct StreamVideoFilterStage
{
    enum Type : int {
        None = 0,
        BlackWhite = 1,
        BrightnessContrast = 2,
        Color = 3,
        Colorblind = 4,
        Details = 5,
        Letterbox = 6,
        NightMode = 7,
        OldFilm = 8,
        Sharpen = 9,
        Vignette = 10,
        SharpenPlus = 11,
    };
    static constexpr int maxParameters = 8;

    int type = None;
    std::array<int, maxParameters> values{}; // slider values as shown in the UI

    bool operator==(const StreamVideoFilterStage &) const = default;

    struct Parameter {
        const char *key;
        int fallback;
        int minimum;
        int maximum;
        float low;  // shader value at `minimum`
        float high; // shader value at `maximum`
    };
    struct Definition {
        Type type;
        const char *name;
        std::array<Parameter, maxParameters> parameters;
        int count;
    };

    // Keep in sync with native/opennow-core settings.rs game_filter_parameters and the
    // overlay's catalogue in OverlayFiltersPage.qml.
    static const std::array<Definition, 11> &catalogue()
    {
        static const std::array<Definition, 11> definitions = {{
            {BlackWhite, "black-white", {{{"intensity", 100, 0, 100, 0.0f, 1.0f}}}, 1},
            {BrightnessContrast, "brightness-contrast", {{
                {"exposure", 0, -100, 100, -1.0f, 1.0f},
                {"contrast", 30, -100, 100, -1.0f, 1.0f},
                {"highlights", 20, -100, 100, -1.0f, 1.0f},
                {"shadows", -30, -100, 100, -1.0f, 1.0f},
                {"gamma", 0, -100, 100, -1.0f, 1.0f}}}, 5},
            {Color, "color", {{
                {"tintColor", 20, 0, 100, 0.0f, 1.0f},
                {"tintIntensity", 30, 0, 100, 0.0f, 1.0f},
                {"temperature", 0, -100, 100, -1.0f, 1.0f},
                {"vibrance", 0, -100, 100, -1.0f, 1.0f}}}, 4},
            {Colorblind, "colorblind", {{
                {"protanopia", 0, 0, 100, 0.0f, 1.0f},
                {"deuteranopia", 100, 0, 100, 0.0f, 1.0f},
                {"tritanopia", 0, 0, 100, 0.0f, 1.0f}}}, 3},
            {Details, "details", {{
                {"sharpen", 50, 0, 100, 0.0f, 1.0f},
                {"clarity", 70, -100, 100, -1.0f, 1.0f},
                {"hdrToning", 60, -100, 100, -1.0f, 1.0f},
                {"bloom", 15, 0, 100, 0.0f, 1.0f}}}, 4},
            {Letterbox, "letterbox", {{
                {"horizontal", 21, 1, 30, 1.0f, 30.0f},
                {"vertical", 9, 1, 30, 1.0f, 30.0f}}}, 2},
            {NightMode, "night-mode", {{{"intensity", 30, 0, 100, 0.0f, 1.0f}}}, 1},
            {OldFilm, "old-film", {{
                {"gamma", 50, 0, 100, 0.0f, 1.0f},
                {"exposure", 50, 0, 100, 0.0f, 1.0f},
                {"contrast", 50, 0, 100, 0.0f, 4.0f},
                {"vignette", 50, 0, 100, 0.0f, 1.0f},
                {"strength", 100, 0, 100, 0.0f, 1.0f},
                {"dirt", 100, 0, 100, 0.0f, 1.0f}}}, 6},
            {Sharpen, "sharpen", {{
                {"sharpen", 50, 0, 100, 0.0f, 1.0f},
                {"ignoreGrain", 15, 0, 100, 0.0f, 1.0f}}}, 2},
            {Vignette, "vignette", {{{"intensity", 70, 0, 100, 0.0f, 1.0f}}}, 1},
            // NVIDIA Image Scaling's sharpen-only pass; the slider is NIS's 0-100% sharpness.
            {SharpenPlus, "sharpen-plus", {{{"sharpen", 50, 0, 100, 0.0f, 1.0f}}}, 1},
        }};
        return definitions;
    }

    static const Definition *definition(int type)
    {
        for (const auto &entry : catalogue())
            if (entry.type == type) return &entry;
        return nullptr;
    }

    static const Definition *definition(const QString &name)
    {
        for (const auto &entry : catalogue())
            if (name == QLatin1String(entry.name)) return &entry;
        return nullptr;
    }

    // The values the shader receives, in catalogue order (see gamefilter.frag).
    [[nodiscard]] std::array<float, maxParameters> shaderValues() const
    {
        std::array<float, maxParameters> result{};
        const auto *entry = definition(type);
        if (!entry) return result;
        for (int index = 0; index < entry->count; ++index) {
            const auto &parameter = entry->parameters[size_t(index)];
            const float t = float(values[size_t(index)] - parameter.minimum)
                / float(parameter.maximum - parameter.minimum);
            result[size_t(index)] = parameter.low + t * (parameter.high - parameter.low);
        }
        return result;
    }
};

struct StreamVideoFilter
{
    static constexpr int maxStages = 8;

    std::array<StreamVideoFilterStage, maxStages> stages{};
    int count = 0;

    bool operator==(const StreamVideoFilter &) const = default;

    [[nodiscard]] bool active() const { return count > 0; }

    // Old film's dirt changes every 83.3 ms, so the chain must re-run on every frame.
    [[nodiscard]] bool animated() const
    {
        return std::any_of(stages.begin(), stages.begin() + count, [](const auto &stage) {
            return stage.type == StreamVideoFilterStage::OldFilm && stage.values[5] > 0;
        });
    }

    [[nodiscard]] static StreamVideoFilter fromVariantList(const QVariantList &list)
    {
        StreamVideoFilter filter;
        for (const auto &item : list) {
            if (filter.count >= maxStages) break;
            const auto map = item.toMap();
            const auto *entry = StreamVideoFilterStage::definition(
                map.value(QStringLiteral("type")).toString());
            if (!entry) continue;
            auto &stage = filter.stages[size_t(filter.count++)];
            stage.type = entry->type;
            for (int index = 0; index < entry->count; ++index) {
                const auto &parameter = entry->parameters[size_t(index)];
                const auto found = map.constFind(QString::fromLatin1(parameter.key));
                bool ok = false;
                const double value = found == map.cend() ? 0.0 : found.value().toDouble(&ok);
                stage.values[size_t(index)] = ok && std::isfinite(value)
                    ? int(std::clamp(std::round(value), double(parameter.minimum), double(parameter.maximum)))
                    : parameter.fallback;
            }
        }
        return filter;
    }

    [[nodiscard]] QVariantList toVariantList() const
    {
        QVariantList list;
        for (int index = 0; index < count; ++index) {
            const auto &stage = stages[size_t(index)];
            const auto *entry = StreamVideoFilterStage::definition(stage.type);
            if (!entry) continue;
            QVariantMap map{{QStringLiteral("type"), QString::fromLatin1(entry->name)}};
            for (int parameter = 0; parameter < entry->count; ++parameter)
                map.insert(QString::fromLatin1(entry->parameters[size_t(parameter)].key),
                           stage.values[size_t(parameter)]);
            list.append(map);
        }
        return list;
    }
};
