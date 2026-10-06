#include "diagnostics/CrashLog.h"

#include "diagnostics/DiagnosticsPaths.h"

#include <QDateTime>
#include <QDir>
#include <QFile>
#include <QMutex>
#include <QString>

#include <cstdio>

#ifdef Q_OS_WIN
#include <windows.h>
#include <dbghelp.h>
#endif

namespace {

QString s_messagesPath;
QtMessageHandler s_previousHandler = nullptr;
QMutex s_messagesMutex;

void appendMessage(QtMsgType type, const QMessageLogContext &context, const QString &message)
{
    if (type == QtDebugMsg || type == QtInfoMsg) return;
    const char *kind = type == QtWarningMsg ? "warning" : type == QtCriticalMsg ? "critical" : "fatal";
    QByteArray line = QDateTime::currentDateTimeUtc().toString(Qt::ISODateWithMs).toUtf8()
        + ' ' + kind + ' ' + (context.category ? context.category : "default") + ": "
        + message.toUtf8().left(4096) + '\n';
    const QMutexLocker lock(&s_messagesMutex);
    QFile file(s_messagesPath);
    if (file.size() > 1024 * 1024) {
        QFile::remove(s_messagesPath + QStringLiteral(".previous"));
        QFile::rename(s_messagesPath, s_messagesPath + QStringLiteral(".previous"));
    }
    if (file.open(QIODevice::WriteOnly | QIODevice::Append)) file.write(line);
}

void messageHandler(QtMsgType type, const QMessageLogContext &context, const QString &message)
{
    if (!s_messagesPath.isEmpty()) appendMessage(type, context, message);
    if (s_previousHandler) s_previousHandler(type, context, message);
}

#ifdef Q_OS_WIN
wchar_t s_crashLogPath[MAX_PATH]{};
wchar_t s_dumpPath[MAX_PATH]{};

LONG WINAPI writeCrash(EXCEPTION_POINTERS *exception)
{
    static volatile LONG entered = 0;
    if (InterlockedExchange(&entered, 1) != 0) return EXCEPTION_CONTINUE_SEARCH;
    const auto *record = exception ? exception->ExceptionRecord : nullptr;
    const auto address = record ? reinterpret_cast<ULONG_PTR>(record->ExceptionAddress) : 0;
    HMODULE module = nullptr;
    wchar_t moduleName[MAX_PATH] = L"?";
    if (address && GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS
                                          | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
                                      reinterpret_cast<LPCWSTR>(address), &module)) {
        wchar_t fullName[MAX_PATH]{};
        if (GetModuleFileNameW(module, fullName, MAX_PATH)) {
            const wchar_t *base = fullName;
            for (const wchar_t *cursor = fullName; *cursor; ++cursor)
                if (*cursor == L'\\' || *cursor == L'/') base = cursor + 1;
            lstrcpynW(moduleName, base, MAX_PATH);
        }
    }
    SYSTEMTIME now{};
    GetSystemTime(&now);
    char line[768]{};
    const int length = std::snprintf(line, sizeof line,
        "%04u-%02u-%02uT%02u:%02u:%02uZ crash code=0x%08lX address=0x%llX module=%ls+0x%llX thread=%lu\r\n",
        now.wYear, now.wMonth, now.wDay, now.wHour, now.wMinute, now.wSecond,
        record ? static_cast<unsigned long>(record->ExceptionCode) : 0ul,
        static_cast<unsigned long long>(address), moduleName,
        static_cast<unsigned long long>(module ? address - reinterpret_cast<ULONG_PTR>(module) : 0),
        static_cast<unsigned long>(GetCurrentThreadId()));
    const HANDLE log = CreateFileW(s_crashLogPath, FILE_APPEND_DATA, FILE_SHARE_READ, nullptr,
                                   OPEN_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (log != INVALID_HANDLE_VALUE) {
        DWORD written = 0;
        if (length > 0) WriteFile(log, line, static_cast<DWORD>(length), &written, nullptr);
        CloseHandle(log);
    }
    const HANDLE dump = CreateFileW(s_dumpPath, GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS,
                                    FILE_ATTRIBUTE_NORMAL, nullptr);
    if (dump != INVALID_HANDLE_VALUE) {
        MINIDUMP_EXCEPTION_INFORMATION info{GetCurrentThreadId(), exception, FALSE};
        MiniDumpWriteDump(GetCurrentProcess(), GetCurrentProcessId(), dump,
                          static_cast<MINIDUMP_TYPE>(MiniDumpWithThreadInfo
                                                     | MiniDumpWithIndirectlyReferencedMemory),
                          exception ? &info : nullptr, nullptr, nullptr);
        CloseHandle(dump);
    }
    return EXCEPTION_CONTINUE_SEARCH;
}
#endif

} // namespace

void installCrashLog()
{
    const auto root = coreDiagnosticsDataRoot();
    if (root.isEmpty()) return;
    QDir directory(root);
    if (!directory.mkpath(QStringLiteral("diagnostics")) || !directory.cd(QStringLiteral("diagnostics")))
        return;
    s_messagesPath = directory.filePath(QStringLiteral("qt-messages.log"));
    s_previousHandler = qInstallMessageHandler(messageHandler);
#ifdef Q_OS_WIN
    const auto crashLog = QDir::toNativeSeparators(directory.filePath(QStringLiteral("crash.log")));
    const auto dump = QDir::toNativeSeparators(directory.filePath(
        QStringLiteral("crash-%1.dmp").arg(GetCurrentProcessId())));
    if (crashLog.size() < MAX_PATH && dump.size() < MAX_PATH) {
        crashLog.toWCharArray(s_crashLogPath);
        dump.toWCharArray(s_dumpPath);
        SetUnhandledExceptionFilter(writeCrash);
    }
#endif
}
