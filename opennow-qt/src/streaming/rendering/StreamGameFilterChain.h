#pragma once

#include "streaming/rendering/StreamVideoFilter.h"

#include <QDebug>
#include <QElapsedTimer>
#include <QFile>
#include <QSize>
#include <rhi/qrhi.h>
#include <rhi/qshader.h>
#include <array>
#include <cmath>
#include <memory>

// Render-thread owner of the game filter passes. Each filter in the active style is one
// full-screen pass at the decoded frame's resolution, in list order, ping-ponging between
// two textures in the source's own UNORM/float format, so every result is clamped before
// the next filter reads it (as Freestyle chains its passes). Details adds a horizontal blur
// pre-pass into a third texture. The chain runs before upscaling and composition; on any
// resource failure it returns the source unchanged and the unfiltered path keeps working.
class StreamGameFilterChain
{
public:
    static constexpr int maxPasses = StreamVideoFilter::maxStages * 2;
    static constexpr int uniformFloats = 20;
    static constexpr int detailsBlurType = 100;

    QRhiTexture *render(QRhi *rhi, QRhiCommandBuffer *cb, QRhiTexture *source,
                        const StreamVideoFilter &filter)
    {
        if (!rhi || !cb || !source || !filter.active()) {
            release();
            return source;
        }
        const QSize size = source->pixelSize();
        const auto format = source->format() == QRhiTexture::BGRA8
            ? QRhiTexture::RGBA8 : source->format();
        if (size.width() < 1 || size.height() < 1
            || (format != QRhiTexture::RGBA8 && format != QRhiTexture::RGB10A2
                && format != QRhiTexture::RGBA16F)
            || !rhi->isTextureFormatSupported(format, QRhiTexture::RenderTarget)) {
            release();
            return source;
        }
        if (!m_state || m_state->rhi != rhi || m_state->size != size || m_state->format != format) {
            release();
            m_state = std::make_unique<State>();
            m_state->rhi = rhi;
            m_state->size = size;
            m_state->format = format;
            m_state->ready = m_state->create();
            if (!m_state->ready) qWarning("Game filter resources unavailable; showing the unfiltered stream");
        }
        if (!m_state->ready) return source;
        if (!m_clock.isValid()) m_clock.start();
        // Wrapped so float precision stays well below one 83.3 ms dirt frame.
        const float elapsed = float(std::fmod(double(m_clock.elapsed()), 83.3 * 4096.0));

        auto &state = *m_state;
        QRhiTexture *input = source;
        int pass = 0;
        int written = 0;
        for (int index = 0; index < filter.count; ++index) {
            const auto &stage = filter.stages[size_t(index)];
            const auto values = stage.shaderValues();
            if (stage.type == StreamVideoFilterStage::Details) {
                if (!state.draw(cb, pass++, detailsBlurType, values, elapsed, input, input, 2))
                    return source;
            }
            QRhiTexture *aux = stage.type == StreamVideoFilterStage::Details
                ? state.textures[2].get() : input;
            const int output = written++ % 2;
            if (!state.draw(cb, pass++, stage.type, values, elapsed, input, aux, output))
                return source;
            input = state.textures[size_t(output)].get();
        }
        return input;
    }

    void forgetSource(QRhiTexture *source)
    {
        if (!m_state || !source) return;
        for (auto &slot : m_state->passes)
            for (auto &binding : slot.bindings)
                if (binding.input == source->globalResourceId()
                    || binding.aux == source->globalResourceId()) binding = Binding{};
    }

    void release()
    {
        m_state.reset();
        m_clock.invalidate();
    }

    bool ready() const { return m_state && m_state->ready; }

private:
    static QShader shader(const char *name)
    {
        QFile file(QStringLiteral(":/opennow/shaders/") + QString::fromLatin1(name)
                   + QStringLiteral(".qsb"));
        return file.open(QIODevice::ReadOnly) ? QShader::fromSerialized(file.readAll()) : QShader{};
    }

    struct Binding {
        quint64 input = 0;
        quint64 inputNative = 0;
        quint64 aux = 0;
        std::unique_ptr<QRhiShaderResourceBindings> resource;
    };
    // One uniform buffer per pass position; bindings are cached per (input, aux) so rotating
    // decoder slots do not recreate descriptors every frame.
    struct PassSlot {
        std::unique_ptr<QRhiBuffer> uniform;
        std::array<Binding, 12> bindings;
        size_t replacement = 0;
    };
    struct State {
        QRhi *rhi = nullptr;
        QSize size;
        QRhiTexture::Format format = QRhiTexture::UnknownFormat;
        std::unique_ptr<QRhiSampler> sampler;
        std::array<std::unique_ptr<QRhiTexture>, 3> textures;
        std::array<std::unique_ptr<QRhiTextureRenderTarget>, 3> targets;
        std::unique_ptr<QRhiRenderPassDescriptor> descriptor;
        std::unique_ptr<QRhiGraphicsPipeline> pipeline;
        std::array<PassSlot, maxPasses> passes;
        bool ready = false;

        bool create()
        {
            const auto vertex = shader("framegen.vert");
            const auto fragment = shader("gamefilter.frag");
            if (!vertex.isValid() || !fragment.isValid()) return false;
            sampler.reset(rhi->newSampler(QRhiSampler::Linear, QRhiSampler::Linear,
                QRhiSampler::None, QRhiSampler::ClampToEdge, QRhiSampler::ClampToEdge));
            if (!sampler->create()) return false;
            for (size_t index = 0; index < textures.size(); ++index) {
                textures[index].reset(rhi->newTexture(format, size, 1, QRhiTexture::RenderTarget));
                if (!textures[index]->create()) return false;
                targets[index].reset(rhi->newTextureRenderTarget(QRhiTextureRenderTargetDescription(
                    QRhiColorAttachment(textures[index].get()))));
                if (!descriptor) descriptor.reset(targets[index]->newCompatibleRenderPassDescriptor());
                targets[index]->setRenderPassDescriptor(descriptor.get());
                if (!targets[index]->create()) return false;
            }
            for (auto &slot : passes) {
                slot.uniform.reset(rhi->newBuffer(QRhiBuffer::Dynamic, QRhiBuffer::UniformBuffer,
                                                  quint32(uniformFloats * sizeof(float))));
                if (!slot.uniform->create()) return false;
            }
            auto *layout = bind(0, textures[0].get(), textures[1].get());
            if (!layout) return false;
            pipeline.reset(rhi->newGraphicsPipeline());
            pipeline->setTopology(QRhiGraphicsPipeline::Triangles);
            pipeline->setShaderStages({{QRhiShaderStage::Vertex, vertex},
                                       {QRhiShaderStage::Fragment, fragment}});
            pipeline->setShaderResourceBindings(layout);
            pipeline->setRenderPassDescriptor(descriptor.get());
            return pipeline->create();
        }

        QRhiShaderResourceBindings *bind(int pass, QRhiTexture *input, QRhiTexture *aux)
        {
            auto &slot = passes[size_t(pass)];
            for (auto &entry : slot.bindings) {
                if (entry.resource && entry.input == input->globalResourceId()
                    && entry.inputNative == input->nativeTexture().object
                    && entry.aux == aux->globalResourceId()) return entry.resource.get();
            }
            auto &entry = slot.bindings[slot.replacement++ % slot.bindings.size()];
            entry = Binding{};
            entry.resource.reset(rhi->newShaderResourceBindings());
            entry.resource->setBindings({
                QRhiShaderResourceBinding::uniformBuffer(0,
                    QRhiShaderResourceBinding::VertexStage | QRhiShaderResourceBinding::FragmentStage,
                    slot.uniform.get()),
                QRhiShaderResourceBinding::sampledTexture(1,
                    QRhiShaderResourceBinding::FragmentStage, input, sampler.get()),
                QRhiShaderResourceBinding::sampledTexture(2,
                    QRhiShaderResourceBinding::FragmentStage, aux, sampler.get())});
            if (!entry.resource->create()) { entry = Binding{}; return nullptr; }
            entry.input = input->globalResourceId();
            entry.inputNative = input->nativeTexture().object;
            entry.aux = aux->globalResourceId();
            return entry.resource.get();
        }

        bool draw(QRhiCommandBuffer *cb, int pass, int type,
                  const std::array<float, StreamVideoFilterStage::maxParameters> &values,
                  float elapsed, QRhiTexture *input, QRhiTexture *aux, int output)
        {
            if (pass >= maxPasses) return false;
            auto *bindings = bind(pass, input, aux);
            if (!bindings) return false;
            const float flip = rhi->isYUpInFramebuffer() == rhi->isYUpInNDC() ? 1.0f : -1.0f;
            const std::array<float, uniformFloats> data = {
                0.0f, 0.0f, flip, elapsed,
                float(size.width()), float(size.height()),
                1.0f / float(size.width()), 1.0f / float(size.height()),
                float(type), 0.0f, 0.0f, 0.0f,
                values[0], values[1], values[2], values[3],
                values[4], values[5], values[6], values[7]};
            auto *updates = rhi->nextResourceUpdateBatch();
            updates->updateDynamicBuffer(passes[size_t(pass)].uniform.get(), 0,
                                         quint32(data.size() * sizeof(float)), data.data());
            cb->beginPass(targets[size_t(output)].get(), Qt::black, {1.0f, 0}, updates);
            cb->setGraphicsPipeline(pipeline.get());
            cb->setShaderResources(bindings);
            cb->setViewport(QRhiViewport(0, 0, float(size.width()), float(size.height())));
            cb->draw(3);
            cb->endPass();
            return true;
        }
    };

    std::unique_ptr<State> m_state;
    QElapsedTimer m_clock;
};
