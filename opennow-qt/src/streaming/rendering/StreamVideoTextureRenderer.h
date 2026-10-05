#pragma once

#include "streaming/rendering/StreamFsrUpscaler.h"
#include "streaming/rendering/StreamVideoFilter.h"

#include <QFile>
#include <QMatrix4x4>
#include <QRectF>
#include <rhi/qrhi.h>
#include <rhi/qshader.h>
#include <algorithm>
#include <array>
#include <iterator>
#include <memory>

// Render-thread owner for imported video textures and the scene-graph material.
// Each QRhi slot keeps its own import/bindings; rotating slots must not recreate
// descriptors every frame. This class never owns the native producer surface.
class StreamVideoTextureRenderer
{
public:
    // std140 `Composition` block shared by streamvideo.vert/.frag (all vec4-aligned):
    //   [0..15] matrix, [16..19] bounds, [20..23] videoRect,
    //   [24..27] parameters (opacity, dither step, filters active, grain seed),
    //   [28..31] colorParameters (source space, output mode, white nits, HDR supported),
    //   [32..35] filterTone (brightness, contrast, saturation, vibrance),
    //   [36..39] filterColor (temperature, night mode, grayscale, sepia),
    //   [40..43] filterDetail (sharpen, details, vignette, grain),
    //   [44..47] filterFrame (letterbox, colorblind mode, colorblind strength, 0),
    //   [48..51] filterTexel (1/width, 1/height of the bound video texture, 0, 0).
    static constexpr int compositionFloats = 52;
    static constexpr quint32 compositionBytes = quint32(compositionFloats * sizeof(float));
    static constexpr quint32 colorParametersOffset = quint32(28 * sizeof(float));

    ~StreamVideoTextureRenderer() { release(); }

    void initialize(QRhi *rhi, QRhiRenderTarget *target)
    {
        if (m_rhi != rhi) release();
        const auto format = target->renderPassDescriptor()->serializedFormat();
        if (m_passFormat != format || m_sampleCount != target->sampleCount())
            for (auto &pipeline : m_pipelines) pipeline.reset();
        m_rhi = rhi;
        m_target = target;
        m_passFormat = format;
        m_sampleCount = target->sampleCount();
        m_outputBits = 0;
        if (target->resourceType() == QRhiResource::SwapChainRenderTarget) {
            const auto *swapChain = static_cast<QRhiSwapChainRenderTarget *>(target)->swapChain();
            if (swapChain->format() == QRhiSwapChain::SDR) m_outputBits = 8;
            else if (swapChain->format() == QRhiSwapChain::HDR10) m_outputBits = 10;
            else m_outputBits = 16;
        } else if (target->resourceType() == QRhiResource::TextureRenderTarget) {
            const auto description = static_cast<QRhiTextureRenderTarget *>(target)->description();
            if (description.colorAttachmentCount() > 0) {
                const auto *texture = description.colorAttachmentAt(0)->texture();
                if (texture) {
                    switch (texture->format()) {
                    case QRhiTexture::RGBA8:
                    case QRhiTexture::BGRA8: m_outputBits = 8; break;
                    case QRhiTexture::RGB10A2: m_outputBits = 10; break;
                    case QRhiTexture::RGBA16F: m_outputBits = 16; break;
                    default: break;
                    }
                }
            }
        }
        m_composition[25] = m_outputBits == 8 ? 1.0f / 255.0f : 0.0f;
    }

    int outputBits() const { return m_outputBits; }

    void setComposition(const QMatrix4x4 &matrix, const QRectF &bounds,
                        const QRectF &video, float opacity)
    {
        std::copy_n(matrix.constData(), 16, m_composition.begin());
        const auto put = [this](int offset, const QRectF &rect) {
            m_composition[offset] = float(rect.x());
            m_composition[offset + 1] = float(rect.y());
            m_composition[offset + 2] = float(rect.width());
            m_composition[offset + 3] = float(rect.height());
        };
        put(16, bounds);
        put(20, video);
        m_composition[24] = opacity;
    }

    // Render thread. Values are already bounded by StreamVideoFilter; an inactive filter
    // only clears the shader flag so the unfiltered composition path stays unchanged.
    void setFilter(const StreamVideoFilter &filter)
    {
        const bool active = filter.active();
        m_grainActive = active && filter.grain > 0.0f;
        m_composition[26] = active ? 1.0f : 0.0f;
        if (!m_grainActive) m_composition[27] = 0.0f;
        const float values[16] = {
            filter.brightness, filter.contrast, filter.saturation, filter.vibrance,
            filter.temperature, filter.nightMode, filter.grayscale, filter.sepia,
            filter.sharpen, filter.details, filter.vignette, filter.grain,
            filter.letterbox, filter.colorblindActive() ? float(filter.colorblindMode) : 0.0f,
            filter.colorblindActive() ? filter.colorblindStrength : 0.0f, 0.0f};
        std::copy(std::begin(values), std::end(values), m_composition.begin() + 32);
    }

    bool filterActive() const { return m_composition[26] > 0.5f; }

    bool prepare(QRhiCommandBuffer *cb)
    {
        if (!m_uniforms) {
            m_uniforms.reset(m_rhi->newBuffer(QRhiBuffer::Dynamic, QRhiBuffer::UniformBuffer,
                                              compositionBytes));
            if (!m_uniforms->create()) { m_uniforms.reset(); return false; }
        }
        if (!m_sampler) {
            m_sampler.reset(m_rhi->newSampler(QRhiSampler::Linear, QRhiSampler::Linear,
                                             QRhiSampler::None, QRhiSampler::ClampToEdge,
                                             QRhiSampler::ClampToEdge));
            if (!m_sampler->create()) { m_sampler.reset(); return false; }
        }
        if (m_grainActive) {
            m_grainFrame = (m_grainFrame + 1) % 4096;
            m_composition[27] = float(m_grainFrame);
        }
        // Sharpen/details tap spacing follows the texture bound by the previous frame;
        // the shader never samples closer than one output pixel, so a stale or unknown
        // size after a resolution change only affects the tap radius for one frame.
        const QSize boundSize = boundTextureSize();
        m_composition[48] = boundSize.width() > 0 ? 1.0f / float(boundSize.width()) : 0.0f;
        m_composition[49] = boundSize.height() > 0 ? 1.0f / float(boundSize.height()) : 0.0f;
        auto *updates = m_rhi->nextResourceUpdateBatch();
        updates->updateDynamicBuffer(m_uniforms.get(), 0, compositionBytes, m_composition.data());
        cb->resourceUpdate(updates);
        ensurePipelines();
        return !m_imports[m_currentSlot].bindings || (m_pipelines[0] && m_pipelines[1]);
    }

    bool importFrame(size_t slot, QRhiTexture::NativeTexture native,
                     QRhiTexture::Format format, const QSize &size)
    {
        if (slot >= m_imports.size() || !m_uniforms || !m_sampler) return false;
        auto &entry = m_imports[slot];
        if (!entry.texture || entry.resource != native.object || entry.format != format
            || entry.texture->pixelSize() != size) {
            m_fsr.forgetSource(entry.texture.get());
            entry.bindings.reset();
            entry.texture.reset(m_rhi->newTexture(format, size, 1));
            if (!entry.texture->createFrom(native)) { entry.texture.reset(); return false; }
            entry.resource = native.object;
            entry.format = format;
        }
        if (!entry.bindings) {
            entry.bindings.reset(m_rhi->newShaderResourceBindings());
            entry.bindings->setBindings({
                QRhiShaderResourceBinding::uniformBuffer(
                    0, QRhiShaderResourceBinding::VertexStage | QRhiShaderResourceBinding::FragmentStage,
                    m_uniforms.get()),
                QRhiShaderResourceBinding::sampledTexture(
                    1, QRhiShaderResourceBinding::FragmentStage, entry.texture.get(), m_sampler.get())
            });
            if (!entry.bindings->create()) { entry.bindings.reset(); return false; }
        }
        m_currentSlot = slot;
        m_externalSlot = -1;
        ensurePipelines();
        return m_pipelines[0] && m_pipelines[1];
    }

    QRhiTexture *importedTexture() const { return m_imports[m_currentSlot].texture.get(); }

    void setColorSpace(int sourceSpace, int outputMode, float whiteNits, bool hdrSupported)
    {
        m_composition[28] = float(sourceSpace);
        m_composition[29] = float(outputMode);
        m_composition[30] = whiteNits;
        m_composition[31] = hdrSupported ? 1.0f : 0.0f;
    }

    void updateColorSpace(QRhiCommandBuffer *cb, int sourceSpace)
    {
        m_composition[28] = float(sourceSpace);
        auto *updates = m_rhi->nextResourceUpdateBatch();
        updates->updateDynamicBuffer(m_uniforms.get(), colorParametersOffset, quint32(4 * sizeof(float)),
                                     m_composition.data() + 28);
        cb->resourceUpdate(updates);
    }

    bool selectTexture(QRhiTexture *texture)
    {
        if (!texture) return false;
        for (size_t slot = 0; slot < m_external.size(); ++slot) {
            auto &entry = m_external[slot];
            if (entry.texture && entry.texture != texture) continue;
            if (!entry.bindings) {
                entry.bindings.reset(m_rhi->newShaderResourceBindings());
                entry.bindings->setBindings({
                    QRhiShaderResourceBinding::uniformBuffer(
                        0, QRhiShaderResourceBinding::VertexStage | QRhiShaderResourceBinding::FragmentStage,
                        m_uniforms.get()),
                    QRhiShaderResourceBinding::sampledTexture(
                        1, QRhiShaderResourceBinding::FragmentStage, texture, m_sampler.get())
                });
                if (!entry.bindings->create()) { entry.bindings.reset(); return false; }
                entry.texture = texture;
            }
            m_externalSlot = int(slot);
            return true;
        }
        return false;
    }

    void clearExternalTextures()
    {
        clearUpscaling();
        for (auto &entry : m_external) {
            entry.bindings.reset();
            entry.texture = nullptr;
        }
        m_externalSlot = -1;
    }

    void prepareUpscaling(QRhiCommandBuffer *cb, const QSize &target, bool enabled,
                          bool sdr, int sharpness)
    {
        auto *source = m_externalSlot >= 0 ? m_external[m_externalSlot].texture
                                          : importedTexture();
        if (!m_uniforms || !m_sampler) return;
        if (!enabled || !sdr || !m_fsr.matchesConfiguration(m_rhi, source, target)) {
            m_fsrBinding.reset();
            m_fsrOutputId = 0;
        }
        auto *output = m_fsr.render(m_rhi, cb, source, target, enabled, sdr, sharpness);
        if (!output || output == source) {
            m_fsrBinding.reset();
            m_fsrOutputId = 0;
            m_fsrOutputSize = {};
            return;
        }
        m_fsrOutputSize = output->pixelSize();
        if (m_fsrBinding && m_fsrOutputId == output->globalResourceId()) return;
        m_fsrBinding.reset(m_rhi->newShaderResourceBindings());
        m_fsrBinding->setBindings({
            QRhiShaderResourceBinding::uniformBuffer(0,
                QRhiShaderResourceBinding::VertexStage | QRhiShaderResourceBinding::FragmentStage,
                m_uniforms.get()),
            QRhiShaderResourceBinding::sampledTexture(1,
                QRhiShaderResourceBinding::FragmentStage, output, m_sampler.get())});
        if (!m_fsrBinding->create()) { m_fsrBinding.reset(); return; }
        m_fsrOutputId = output->globalResourceId();
    }

    void render(QRhiCommandBuffer *cb, bool stencil, int reference)
    {
        auto *pipeline = m_pipelines[stencil ? 1 : 0].get();
        auto *bindings = m_fsrBinding ? m_fsrBinding.get()
                         : m_externalSlot >= 0 ? m_external[m_externalSlot].bindings.get()
                                           : m_imports[m_currentSlot].bindings.get();
        if (!pipeline || !bindings) return;
        cb->setGraphicsPipeline(pipeline);
        cb->setShaderResources(bindings);
        if (stencil) cb->setStencilRef(reference);
        cb->draw(6);
    }

    void release()
    {
        for (auto &pipeline : m_pipelines) pipeline.reset();
        clearFrames();
        m_uniforms.reset();
        m_sampler.reset();
        m_rhi = nullptr;
        m_target = nullptr;
        m_passFormat.clear();
    }

    // Render-thread only. Preserve shaders/composition but discard every session's imports.
    void clearFrames()
    {
        clearExternalTextures();
        for (auto &entry : m_imports) {
            entry.bindings.reset();
            entry.texture.reset();
            entry.resource = 0;
        }
    }

    size_t importedSlotCount() const
    {
        return std::count_if(m_imports.begin(), m_imports.end(),
                             [](const auto &entry) { return bool(entry.bindings); });
    }

private:
    void clearUpscaling()
    {
        m_fsrBinding.reset();
        m_fsrOutputId = 0;
        m_fsrOutputSize = {};
        m_fsr.release();
    }

    QSize boundTextureSize() const
    {
        if (m_fsrBinding) return m_fsrOutputSize;
        if (m_externalSlot >= 0 && m_external[m_externalSlot].texture)
            return m_external[m_externalSlot].texture->pixelSize();
        if (const auto *texture = m_imports[m_currentSlot].texture.get())
            return texture->pixelSize();
        return {};
    }

    static constexpr std::array<float, compositionFloats> neutralComposition()
    {
        std::array<float, compositionFloats> values{};
        values[33] = 1.0f; // contrast
        values[34] = 1.0f; // saturation
        return values;
    }

    static QShader loadShader(const char *path)
    {
        QFile file(QString::fromLatin1(path));
        if (!file.open(QIODevice::ReadOnly)) return {};
        return QShader::fromSerialized(file.readAll());
    }

    void ensurePipelines()
    {
        if (!m_imports[m_currentSlot].bindings) return;
        static const QShader vertex = loadShader(":/opennow/shaders/streamvideo.vert.qsb");
        static const QShader fragment = loadShader(":/opennow/shaders/streamvideo.frag.qsb");
        if (!vertex.isValid() || !fragment.isValid()) return;
        for (size_t index = 0; index < m_pipelines.size(); ++index) {
            auto &pipeline = m_pipelines[index];
            if (pipeline) continue;
            pipeline.reset(m_rhi->newGraphicsPipeline());
            pipeline->setTopology(QRhiGraphicsPipeline::Triangles);
            pipeline->setFlags(QRhiGraphicsPipeline::UsesScissor | QRhiGraphicsPipeline::UsesStencilRef);
            pipeline->setDepthTest(true);
            pipeline->setDepthWrite(false);
            pipeline->setDepthOp(QRhiGraphicsPipeline::LessOrEqual);
            pipeline->setSampleCount(m_sampleCount);
            QRhiGraphicsPipeline::TargetBlend blend;
            blend.enable = true;
            pipeline->setTargetBlends({blend});
            pipeline->setStencilTest(index == 1);
            QRhiGraphicsPipeline::StencilOpState stencil;
            stencil.compareOp = QRhiGraphicsPipeline::Equal;
            pipeline->setStencilFront(stencil);
            pipeline->setStencilBack(stencil);
            pipeline->setStencilWriteMask(0);
            pipeline->setShaderStages({{QRhiShaderStage::Vertex, vertex},
                                       {QRhiShaderStage::Fragment, fragment}});
            pipeline->setShaderResourceBindings(m_imports[m_currentSlot].bindings.get());
            pipeline->setRenderPassDescriptor(m_target->renderPassDescriptor());
            if (!pipeline->create()) pipeline.reset();
        }
    }

    struct ImportedFrame {
        std::unique_ptr<QRhiTexture> texture;
        std::unique_ptr<QRhiShaderResourceBindings> bindings;
        quint64 resource = 0;
        QRhiTexture::Format format = QRhiTexture::UnknownFormat;
    };
    struct ExternalFrame {
        QRhiTexture *texture = nullptr;
        std::unique_ptr<QRhiShaderResourceBindings> bindings;
    };
    QRhi *m_rhi = nullptr;
    QRhiRenderTarget *m_target = nullptr;
    std::array<ImportedFrame, 8> m_imports;
    std::array<ExternalFrame, 3> m_external;
    StreamFsrUpscaler m_fsr;
    std::unique_ptr<QRhiShaderResourceBindings> m_fsrBinding;
    quint64 m_fsrOutputId = 0;
    QSize m_fsrOutputSize;
    std::array<std::unique_ptr<QRhiGraphicsPipeline>, 2> m_pipelines;
    std::unique_ptr<QRhiSampler> m_sampler;
    std::unique_ptr<QRhiBuffer> m_uniforms;
    std::array<float, compositionFloats> m_composition = neutralComposition();
    int m_grainFrame = 0;
    bool m_grainActive = false;
    QVector<quint32> m_passFormat;
    size_t m_currentSlot = 0;
    int m_externalSlot = -1;
    int m_sampleCount = 1;
    int m_outputBits = 0;
};
