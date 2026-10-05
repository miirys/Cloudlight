#pragma once

#include <QDir>
#include <QFileInfo>
#include <QStandardPaths>
#include <QString>
#include <QStringList>

// The core's profile directory sits directly in this platform base; Qt's
// AppDataLocation would add the organization name, unlike the core.
inline QString coreDataBase()
{
#ifdef Q_OS_WIN
    const auto roaming = qEnvironmentVariable("APPDATA");
    if (!roaming.isEmpty())
        return roaming;
#endif
#ifdef Q_OS_MACOS
    return QStandardPaths::writableLocation(QStandardPaths::GenericDataLocation);
#else
    return QStandardPaths::writableLocation(QStandardPaths::GenericConfigLocation);
#endif
}

// Match the core's persisted data directory without changing account/settings
// ownership. The core moves a pre-rename OpenNOW profile to Cloudlight once at
// startup and keeps the OpenNOW profile when it cannot; Qt follows the same
// choice and never creates the Cloudlight directory while an OpenNOW one remains.
inline QString coreDiagnosticsDataRoot()
{
    const auto overridePath = qEnvironmentVariable("OPENNOW_DATA_DIR");
    if (!overridePath.isEmpty())
        return overridePath;
    const auto base = coreDataBase();
    if (base.isEmpty()) return {};
    const QDir directory(base);
    const auto current = directory.filePath(QStringLiteral("Cloudlight"));
    if (QFileInfo::exists(current)) return current;
    QStringList legacy{QStringLiteral("OpenNOW")};
#ifdef Q_OS_LINUX
    legacy.append(QStringLiteral("opennow"));
#endif
    for (const auto &name : legacy) {
        const auto candidate = directory.filePath(name);
        if (QFileInfo(candidate).isDir()) return candidate;
    }
    return current;
}
