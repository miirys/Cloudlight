#pragma once

#include <QDir>
#include <QFileInfo>
#include <QStandardPaths>
#include <QString>

inline QString mediaPicturesRoot()
{
    if (!qEnvironmentVariableIsSet("OPENNOW_PICTURES_DIR"))
        return QStandardPaths::writableLocation(QStandardPaths::PicturesLocation);
    const auto overridePath = qEnvironmentVariable("OPENNOW_PICTURES_DIR");
    if (overridePath.isEmpty()) return {};
    return QDir::cleanPath(QFileInfo(overridePath).absoluteFilePath());
}

// The core renames Pictures/OpenNOW to Pictures/Cloudlight once at startup. When
// that rename was not possible the core keeps the OpenNOW folder, so follow the
// same choice and never create a second, empty Cloudlight folder beside it.
inline QString mediaFolder()
{
    const auto root = mediaPicturesRoot();
    if (root.isEmpty()) return {};
    const QDir pictures(root);
    const auto current = QStringLiteral("Cloudlight");
    const auto legacy = QStringLiteral("OpenNOW");
    const bool useLegacy = !QFileInfo::exists(pictures.filePath(current))
        && QFileInfo(pictures.filePath(legacy)).isDir();
    return pictures.filePath(useLegacy ? legacy : current);
}

inline QString mediaScreenshotsDirectory()
{
    const auto folder = mediaFolder();
    return folder.isEmpty() ? QString{} : QDir(folder).filePath(QStringLiteral("Screenshots"));
}

inline QString mediaRecordingsDirectory()
{
    const auto folder = mediaFolder();
    return folder.isEmpty() ? QString{} : QDir(folder).filePath(QStringLiteral("Recordings"));
}
