#pragma once

#include <QAbstractNativeEventFilter>
#include <QObject>
#include <QPointer>
#include <QRectF>
#include <QVector>

class QWindow;

// Owns Cloudlight's own title bar on Windows. The window keeps its native
// resize frame, Aero Snap, shadow and minimise/maximise animations; only the
// system caption is removed so QML can draw the bar. QML reports the caption
// height and the rectangles of its interactive controls, which stay client
// area; the rest of the caption strip is reported to Windows as HTCAPTION.
// Other platforms keep their system decorations and `supported` is false.
class WindowChrome final : public QObject, public QAbstractNativeEventFilter
{
    Q_OBJECT
    Q_PROPERTY(bool supported READ supported CONSTANT)
    Q_PROPERTY(bool active READ active NOTIFY activeChanged)
    Q_PROPERTY(bool maximized READ maximized NOTIFY maximizedChanged)

public:
    explicit WindowChrome(QObject *parent = nullptr);
    ~WindowChrome() override;

    [[nodiscard]] bool supported() const;
    [[nodiscard]] bool active() const;
    [[nodiscard]] bool maximized() const;

    void attach(QWindow *window);

    Q_INVOKABLE void setEnabled(bool enabled);
    // Logical (device-independent) pixels, relative to the window's top-left.
    Q_INVOKABLE void setCaption(qreal height, const QVariantList &interactiveRects);
    Q_INVOKABLE void minimize();
    Q_INVOKABLE void toggleMaximized();
    Q_INVOKABLE void close();
    Q_INVOKABLE void startMove();

    bool nativeEventFilter(const QByteArray &eventType, void *message, qintptr *result) override;

signals:
    void activeChanged();
    void maximizedChanged();

private:
    void applyStyle();
    void updateMaximized();

    QPointer<QWindow> m_window;
    bool m_enabled = false;
    bool m_active = false;
    bool m_maximized = false;
    qreal m_captionHeight = 0;
    QVector<QRectF> m_interactive;
};
