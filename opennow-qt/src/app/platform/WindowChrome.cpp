#include "app/platform/WindowChrome.h"

#include <QCoreApplication>
#include <QRectF>

#include <algorithm>
#include <QTimer>
#include <QVariantMap>
#include <QWindow>

#if defined(Q_OS_WIN)
#include <qt_windows.h>
#include <dwmapi.h>
#include <windowsx.h>
#pragma comment(lib, "dwmapi.lib")
#pragma comment(lib, "user32.lib")
#endif

WindowChrome::WindowChrome(QObject *parent)
    : QObject(parent)
{
}

WindowChrome::~WindowChrome()
{
    if (QCoreApplication::instance())
        QCoreApplication::instance()->removeNativeEventFilter(this);
}

bool WindowChrome::supported() const
{
#if defined(Q_OS_WIN)
    return true;
#else
    return false;
#endif
}

bool WindowChrome::active() const { return m_active; }
bool WindowChrome::maximized() const { return m_maximized; }

void WindowChrome::attach(QWindow *window)
{
    if (!window || m_window == window) return;
    m_window = window;
    if (supported()) QCoreApplication::instance()->installNativeEventFilter(this);
    connect(window, &QWindow::windowStateChanged, this, [this] {
        updateMaximized();
        // Qt rewrites the window style when leaving fullscreen; put the
        // resize frame back afterwards.
        QTimer::singleShot(0, this, &WindowChrome::applyStyle);
    });
    if (m_enabled) setEnabled(true);
}

void WindowChrome::setEnabled(bool enabled)
{
    m_enabled = enabled;
    const bool active = supported() && enabled && m_window;
    if (m_window) {
        const auto flags = m_window->flags();
        const auto wanted = active ? flags | Qt::FramelessWindowHint : flags & ~Qt::FramelessWindowHint;
        if (wanted != flags) m_window->setFlags(wanted);
    }
    if (m_active != active) {
        m_active = active;
        emit activeChanged();
    }
    QTimer::singleShot(0, this, &WindowChrome::applyStyle);
}

void WindowChrome::setCaption(qreal height, const QVariantList &interactiveRects)
{
    m_captionHeight = std::max<qreal>(0, height);
    m_interactive.clear();
    m_interactive.reserve(interactiveRects.size());
    for (const auto &value : interactiveRects) {
        const auto rect = value.toRectF();
        if (rect.isValid()) m_interactive.append(rect);
    }
}

void WindowChrome::minimize()
{
    if (m_window) m_window->showMinimized();
}

void WindowChrome::toggleMaximized()
{
    if (!m_window) return;
    if (m_window->windowStates() & Qt::WindowMaximized) m_window->showNormal();
    else m_window->showMaximized();
}

void WindowChrome::close()
{
    if (m_window) m_window->close();
}

void WindowChrome::startMove()
{
    if (m_window) m_window->startSystemMove();
}

void WindowChrome::updateMaximized()
{
    const bool maximized = m_window && (m_window->windowStates() & Qt::WindowMaximized);
    if (maximized == m_maximized) return;
    m_maximized = maximized;
    emit maximizedChanged();
}

void WindowChrome::applyStyle()
{
#if defined(Q_OS_WIN)
    if (!m_window || !m_window->handle()) return;
    auto hwnd = reinterpret_cast<HWND>(m_window->winId());
    if (m_window->windowStates() & Qt::WindowFullScreen) return;
    auto style = static_cast<LONG_PTR>(GetWindowLongPtrW(hwnd, GWL_STYLE));
    const LONG_PTR frame = WS_CAPTION | WS_THICKFRAME | WS_MINIMIZEBOX | WS_MAXIMIZEBOX | WS_SYSMENU;
    const auto updated = m_active ? (style & ~static_cast<LONG_PTR>(WS_POPUP)) | frame : style;
    if (updated != style) SetWindowLongPtrW(hwnd, GWL_STYLE, updated);
    // A one-pixel DWM frame keeps the system shadow and rounded corners.
    const MARGINS margins{0, 0, m_active ? 1 : 0, 0};
    DwmExtendFrameIntoClientArea(hwnd, &margins);
    SetWindowPos(hwnd, nullptr, 0, 0, 0, 0,
                 SWP_FRAMECHANGED | SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE
                     | SWP_NOOWNERZORDER);
#endif
}

bool WindowChrome::nativeEventFilter(const QByteArray &eventType, void *message, qintptr *result)
{
#if defined(Q_OS_WIN)
    if (!m_active || !m_window || eventType != "windows_generic_MSG") return false;
    auto *msg = static_cast<MSG *>(message);
    if (!m_window->handle() || msg->hwnd != reinterpret_cast<HWND>(m_window->winId())) return false;
    const bool fullscreen = m_window->windowStates() & Qt::WindowFullScreen;
    if (fullscreen) return false;
    const auto dpi = GetDpiForWindow(msg->hwnd);
    const int border = GetSystemMetricsForDpi(SM_CXSIZEFRAME, dpi)
        + GetSystemMetricsForDpi(SM_CXPADDEDBORDER, dpi);
    switch (msg->message) {
    case WM_NCCALCSIZE: {
        if (!msg->wParam) return false;
        auto *params = reinterpret_cast<NCCALCSIZE_PARAMS *>(msg->lParam);
        // The whole window is client area. A maximised window overhangs the
        // monitor by its frame, so pull the client back inside the work area.
        if (IsZoomed(msg->hwnd)) {
            params->rgrc[0].left += border;
            params->rgrc[0].top += border;
            params->rgrc[0].right -= border;
            params->rgrc[0].bottom -= border;
        }
        *result = 0;
        return true;
    }
    case WM_NCHITTEST: {
        POINT point{GET_X_LPARAM(msg->lParam), GET_Y_LPARAM(msg->lParam)};
        RECT window{};
        GetWindowRect(msg->hwnd, &window);
        if (!IsZoomed(msg->hwnd)) {
            const bool left = point.x < window.left + border;
            const bool right = point.x >= window.right - border;
            const bool top = point.y < window.top + border;
            const bool bottom = point.y >= window.bottom - border;
            if (top && left) { *result = HTTOPLEFT; return true; }
            if (top && right) { *result = HTTOPRIGHT; return true; }
            if (bottom && left) { *result = HTBOTTOMLEFT; return true; }
            if (bottom && right) { *result = HTBOTTOMRIGHT; return true; }
            if (left) { *result = HTLEFT; return true; }
            if (right) { *result = HTRIGHT; return true; }
            if (bottom) { *result = HTBOTTOM; return true; }
            // The top edge is a thin resize band inside the caption strip.
            if (point.y < window.top + std::max(1, border / 2)) { *result = HTTOP; return true; }
        }
        ScreenToClient(msg->hwnd, &point);
        const qreal ratio = std::max<qreal>(1, m_window->devicePixelRatio());
        const QPointF logical(point.x / ratio, point.y / ratio);
        if (logical.y() >= 0 && logical.y() < m_captionHeight) {
            for (const auto &rect : std::as_const(m_interactive))
                if (rect.contains(logical)) { *result = HTCLIENT; return true; }
            *result = HTCAPTION;
            return true;
        }
        *result = HTCLIENT;
        return true;
    }
    default:
        return false;
    }
#else
    Q_UNUSED(eventType)
    Q_UNUSED(message)
    Q_UNUSED(result)
    return false;
#endif
}
