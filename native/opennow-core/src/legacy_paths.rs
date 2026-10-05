//! One-time moves of directories that carried the OpenNOW name before the
//! Cloudlight rename.
//!
//! A legacy directory is moved only while the Cloudlight directory does not exist
//! yet. Nothing is ever deleted: when the move cannot be completed the caller keeps
//! using the legacy directory, and the next start tries again.

use fs2::FileExt as _;
use std::fs;
use std::io;
use std::path::{Path, PathBuf};

/// How a legacy directory may be carried over.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum MovePolicy {
    /// Rename only. Large user media is never duplicated.
    RenameOnly,
    /// Rename, falling back to a verified copy when the rename fails (for example
    /// across volumes or while Windows holds a handle inside the directory). The
    /// legacy directory stays in place after a copy.
    RenameOrCopy,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Outcome {
    /// The Cloudlight directory already existed, or there was nothing to move.
    Current,
    Renamed,
    Copied,
    /// The legacy directory could not be moved and remains in use.
    KeptLegacy,
}

/// Picks the directory to use without changing anything on disk: the current
/// directory when it exists, otherwise the first existing legacy directory,
/// otherwise the (not yet created) current directory.
pub fn select(current: &Path, legacy: &[PathBuf]) -> PathBuf {
    if exists(current) {
        return current.to_path_buf();
    }
    legacy
        .iter()
        .find(|candidate| candidate.is_dir())
        .cloned()
        .unwrap_or_else(|| current.to_path_buf())
}

/// Moves the first existing legacy directory to `current` when `current` does not
/// exist, and returns the directory to use together with what happened.
///
/// `lock_file` names a file inside the legacy directory that a running owner holds
/// an exclusive lock on. While that lock is held the directory is in use and is
/// left where it is.
pub fn migrate(
    current: &Path,
    legacy: &[PathBuf],
    policy: MovePolicy,
    lock_file: Option<&str>,
) -> (PathBuf, Outcome) {
    if exists(current) {
        return (current.to_path_buf(), Outcome::Current);
    }
    let Some(previous) = legacy.iter().find(|candidate| candidate.is_dir()) else {
        return (current.to_path_buf(), Outcome::Current);
    };
    let keep = || (previous.clone(), Outcome::KeptLegacy);
    if current
        .parent()
        .is_some_and(|parent| fs::create_dir_all(parent).is_err())
    {
        return keep();
    }
    // Probe the owner's lock before moving; the probe handle is closed again so
    // Windows can rename the directory.
    if let Some(name) = lock_file {
        match lock(&previous.join(name)) {
            Ok(Some(_released_on_drop)) => {}
            Ok(None) | Err(_) => return keep(),
        }
    }
    if fs::rename(previous, current).is_ok() {
        return (current.to_path_buf(), Outcome::Renamed);
    }
    if policy == MovePolicy::RenameOnly {
        return keep();
    }
    // Hold the owner's lock for the whole copy so a running legacy build cannot
    // write into the directory while it is being duplicated.
    let _guard = match lock_file {
        Some(name) => match lock(&previous.join(name)) {
            Ok(Some(guard)) => Some(guard),
            Ok(None) | Err(_) => return keep(),
        },
        None => None,
    };
    let Some(staging) = staging_path(current) else {
        return keep();
    };
    let _ = fs::remove_dir_all(&staging);
    let copied =
        copy_tree(previous, &staging, lock_file).and_then(|()| fs::rename(&staging, current));
    if copied.is_err() {
        let _ = fs::remove_dir_all(&staging);
        return keep();
    }
    (current.to_path_buf(), Outcome::Copied)
}

fn exists(path: &Path) -> bool {
    fs::symlink_metadata(path).is_ok()
}

fn staging_path(current: &Path) -> Option<PathBuf> {
    let name = current.file_name()?.to_string_lossy().into_owned();
    Some(current.with_file_name(format!(".{name}.migrating")))
}

/// Returns `Ok(None)` when another process holds the lock.
fn lock(path: &Path) -> io::Result<Option<fs::File>> {
    let file = fs::OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(false)
        .open(path)?;
    match file.try_lock_exclusive() {
        Ok(()) => Ok(Some(file)),
        Err(error) if error.raw_os_error() == fs2::lock_contended_error().raw_os_error() => {
            Ok(None)
        }
        Err(error) => Err(error),
    }
}

fn copy_tree(source: &Path, destination: &Path, skip: Option<&str>) -> io::Result<()> {
    fs::create_dir(destination)?;
    for entry in fs::read_dir(source)? {
        let entry = entry?;
        let name = entry.file_name();
        if skip.is_some_and(|skip| name == skip) {
            continue;
        }
        let from = entry.path();
        let to = destination.join(&name);
        let kind = fs::symlink_metadata(&from)?.file_type();
        if kind.is_dir() {
            copy_tree(&from, &to, None)?;
        } else if kind.is_file() {
            fs::copy(&from, &to)?;
            fs::File::open(&to)?.sync_all()?;
        } else if kind.is_symlink() {
            copy_symlink(&from, &to)?;
        } else {
            return Err(io::Error::other(
                "Unsupported file type in a legacy directory",
            ));
        }
    }
    Ok(())
}

#[cfg(unix)]
fn copy_symlink(from: &Path, to: &Path) -> io::Result<()> {
    std::os::unix::fs::symlink(fs::read_link(from)?, to)
}

#[cfg(not(unix))]
fn copy_symlink(_: &Path, _: &Path) -> io::Result<()> {
    Err(io::Error::other(
        "Symbolic links in a legacy directory are not copied",
    ))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn populated_legacy(root: &Path) -> PathBuf {
        let legacy = root.join("OpenNOW");
        fs::create_dir_all(legacy.join("diagnostics")).unwrap();
        fs::write(legacy.join("settings.json"), b"{\"fps\":120}").unwrap();
        fs::write(legacy.join("diagnostics/core.log"), b"log").unwrap();
        legacy
    }

    #[test]
    fn legacy_directory_is_renamed_when_cloudlight_is_absent() {
        let root = tempfile::tempdir().unwrap();
        let legacy = populated_legacy(root.path());
        let current = root.path().join("Cloudlight");

        let (selected, outcome) = migrate(
            &current,
            std::slice::from_ref(&legacy),
            MovePolicy::RenameOrCopy,
            Some("core.lock"),
        );

        assert_eq!(outcome, Outcome::Renamed);
        assert_eq!(selected, current);
        assert_eq!(
            fs::read(current.join("settings.json")).unwrap(),
            b"{\"fps\":120}"
        );
        assert_eq!(
            fs::read(current.join("diagnostics/core.log")).unwrap(),
            b"log"
        );
        assert!(!legacy.exists());
    }

    #[test]
    fn an_existing_cloudlight_directory_is_never_replaced() {
        let root = tempfile::tempdir().unwrap();
        let legacy = populated_legacy(root.path());
        let current = root.path().join("Cloudlight");
        fs::create_dir_all(&current).unwrap();
        fs::write(current.join("settings.json"), b"{\"fps\":60}").unwrap();

        let (selected, outcome) = migrate(
            &current,
            std::slice::from_ref(&legacy),
            MovePolicy::RenameOrCopy,
            None,
        );

        assert_eq!(outcome, Outcome::Current);
        assert_eq!(selected, current);
        assert_eq!(
            fs::read(current.join("settings.json")).unwrap(),
            b"{\"fps\":60}"
        );
        assert_eq!(
            fs::read(legacy.join("settings.json")).unwrap(),
            b"{\"fps\":120}"
        );
    }

    #[test]
    fn fresh_profiles_use_the_cloudlight_directory() {
        let root = tempfile::tempdir().unwrap();
        let current = root.path().join("Cloudlight");
        let legacy = [root.path().join("OpenNOW"), root.path().join("opennow")];

        assert_eq!(select(&current, &legacy), current);
        let (selected, outcome) = migrate(
            &current,
            &legacy,
            MovePolicy::RenameOrCopy,
            Some("core.lock"),
        );

        assert_eq!(outcome, Outcome::Current);
        assert_eq!(selected, current);
        assert!(!legacy[0].exists());
    }

    #[test]
    fn selection_prefers_cloudlight_then_the_first_existing_legacy_directory() {
        let root = tempfile::tempdir().unwrap();
        let current = root.path().join("Cloudlight");
        let legacy = [root.path().join("first"), root.path().join("second")];
        fs::create_dir_all(&legacy[1]).unwrap();
        assert_eq!(select(&current, &legacy), legacy[1]);
        fs::create_dir_all(&legacy[0]).unwrap();
        assert_eq!(select(&current, &legacy), legacy[0]);
        fs::create_dir_all(&current).unwrap();
        assert_eq!(select(&current, &legacy), current);
    }

    #[test]
    fn a_failed_move_keeps_using_the_legacy_directory_untouched() {
        let root = tempfile::tempdir().unwrap();
        let legacy = populated_legacy(root.path());
        // A regular file where the parent directory should be makes both the rename
        // and the copy fail.
        let blocker = root.path().join("blocked");
        fs::write(&blocker, b"not a directory").unwrap();
        let current = blocker.join("Cloudlight");

        let (selected, outcome) = migrate(
            &current,
            std::slice::from_ref(&legacy),
            MovePolicy::RenameOrCopy,
            Some("core.lock"),
        );

        assert_eq!(outcome, Outcome::KeptLegacy);
        assert_eq!(selected, legacy);
        assert_eq!(
            fs::read(legacy.join("settings.json")).unwrap(),
            b"{\"fps\":120}"
        );
        assert!(!root.path().join("blocked/.Cloudlight.migrating").exists());
    }

    #[test]
    fn a_directory_held_by_a_running_legacy_core_is_left_in_place() {
        let root = tempfile::tempdir().unwrap();
        let legacy = populated_legacy(root.path());
        let current = root.path().join("Cloudlight");
        let held = lock(&legacy.join("core.lock")).unwrap().unwrap();

        let (selected, outcome) = migrate(
            &current,
            std::slice::from_ref(&legacy),
            MovePolicy::RenameOrCopy,
            Some("core.lock"),
        );

        assert_eq!(outcome, Outcome::KeptLegacy);
        assert_eq!(selected, legacy);
        assert!(!current.exists());
        drop(held);
        let (selected, outcome) = migrate(
            &current,
            std::slice::from_ref(&legacy),
            MovePolicy::RenameOrCopy,
            Some("core.lock"),
        );
        assert_eq!(outcome, Outcome::Renamed);
        assert_eq!(selected, current);
    }

    #[test]
    fn copies_skip_the_lock_file_and_leave_the_legacy_directory_as_a_backup() {
        let root = tempfile::tempdir().unwrap();
        let legacy = populated_legacy(root.path());
        fs::write(legacy.join("core.lock"), b"").unwrap();
        let current = root.path().join("Cloudlight");
        let staging = staging_path(&current).unwrap();

        copy_tree(&legacy, &staging, Some("core.lock")).unwrap();
        fs::rename(&staging, &current).unwrap();

        assert_eq!(
            fs::read(current.join("settings.json")).unwrap(),
            b"{\"fps\":120}"
        );
        assert_eq!(
            fs::read(current.join("diagnostics/core.log")).unwrap(),
            b"log"
        );
        assert!(!current.join("core.lock").exists());
        assert!(legacy.join("settings.json").exists());
    }

    #[test]
    fn rename_only_media_never_copies() {
        let root = tempfile::tempdir().unwrap();
        let legacy = root.path().join("OpenNOW");
        fs::create_dir_all(legacy.join("Recordings")).unwrap();
        fs::write(legacy.join("Recordings/OpenNOW-clip.mkv"), b"mkv").unwrap();
        let blocker = root.path().join("blocked");
        fs::write(&blocker, b"").unwrap();

        let (selected, outcome) = migrate(
            &blocker.join("Cloudlight"),
            std::slice::from_ref(&legacy),
            MovePolicy::RenameOnly,
            None,
        );
        assert_eq!((selected, outcome), (legacy.clone(), Outcome::KeptLegacy));

        let current = root.path().join("Cloudlight");
        let (selected, outcome) = migrate(
            &current,
            std::slice::from_ref(&legacy),
            MovePolicy::RenameOnly,
            None,
        );
        assert_eq!((selected, outcome), (current.clone(), Outcome::Renamed));
        assert_eq!(
            fs::read(current.join("Recordings/OpenNOW-clip.mkv")).unwrap(),
            b"mkv"
        );
    }
}
