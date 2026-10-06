#pragma once

// Records how the process ended when it ends abnormally, so a stream that
// closes Cloudlight leaves evidence in the diagnostics folder:
// - Windows: an unhandled structured exception writes crash-<pid>.dmp and one
//   line (code, address, module+offset) to crash.log, then lets Windows
//   continue its normal crash handling.
// - All platforms: Qt warnings, criticals and fatals (QML errors included) are
//   appended to qt-messages.log, bounded to 1 MiB with one rotated copy.
// Paths are resolved once at install time; the crash path allocates nothing.
void installCrashLog();
