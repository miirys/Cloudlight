use crate::push::PushError;
use crate::push::registration::Registration;

const PUSH_SERVICE_NAME: &str = "io.github.miirys.cloudlight.push";
// Registrations saved before the Cloudlight rename. They are read when no
// current registration exists, copied to the current service, and removed only
// after that copy was stored.
const LEGACY_PUSH_SERVICE_NAME: &str = "app.opennow.push";

pub trait PushStateStore: Send + Sync {
    fn load(&self, account: &str) -> Result<Option<Registration>, PushError>;
    fn save(&self, account: &str, registration: &Registration) -> Result<(), PushError>;
    fn clear(&self, account: &str) -> Result<(), PushError>;
}

pub struct RegistrationStore {
    service: String,
    legacy_service: Option<String>,
}

impl RegistrationStore {
    pub fn new(service: &str) -> Self {
        Self {
            service: service.to_owned(),
            legacy_service: None,
        }
    }

    pub fn default_service() -> Self {
        Self {
            service: PUSH_SERVICE_NAME.to_owned(),
            legacy_service: Some(LEGACY_PUSH_SERVICE_NAME.to_owned()),
        }
    }

    fn entry(service: &str, account: &str) -> Result<keyring::Entry, PushError> {
        keyring::Entry::new(service, &format!("registration:{account}")).map_err(|_| {
            PushError::new(
                "push_store_unavailable",
                "The OS credential store is unavailable for the push registration",
            )
        })
    }

    fn load_from(service: &str, account: &str) -> Result<Option<Registration>, PushError> {
        match Self::entry(service, account)?.get_password() {
            Ok(encoded) => serde_json::from_str(&encoded).map(Some).map_err(|_| {
                PushError::new(
                    "push_store_corrupt",
                    "The stored push registration could not be read",
                )
            }),
            Err(keyring::Error::NoEntry) => Ok(None),
            Err(_) => Err(PushError::new(
                "push_store_unavailable",
                "The OS credential store is unavailable for the push registration",
            )),
        }
    }

    fn save_to(service: &str, account: &str, registration: &Registration) -> Result<(), PushError> {
        let encoded = serde_json::to_string(registration).map_err(|_| {
            PushError::new(
                "push_store_failed",
                "The push registration could not be encoded",
            )
        })?;
        Self::entry(service, account)?
            .set_password(&encoded)
            .map_err(|_| {
                PushError::new(
                    "push_store_failed",
                    "The push registration could not be stored",
                )
            })
    }

    fn clear_from(service: &str, account: &str) -> Result<(), PushError> {
        match Self::entry(service, account)?.delete_credential() {
            Ok(()) | Err(keyring::Error::NoEntry) => Ok(()),
            Err(_) => Err(PushError::new(
                "push_store_failed",
                "The push registration could not be removed",
            )),
        }
    }
}

/// Loads `current`, falling back to `legacy` when it holds nothing. A legacy
/// value is copied to the current store and the legacy copy is removed only
/// after that write succeeded; a failed copy keeps the legacy value in place.
fn load_with_legacy<T>(
    current: impl FnOnce() -> Result<Option<T>, PushError>,
    legacy: Option<(
        impl FnOnce() -> Result<Option<T>, PushError>,
        impl FnOnce(&T) -> Result<(), PushError>,
        impl FnOnce() -> Result<(), PushError>,
    )>,
) -> Result<Option<T>, PushError> {
    if let Some(value) = current()? {
        return Ok(Some(value));
    }
    let Some((load_legacy, save_current, clear_legacy)) = legacy else {
        return Ok(None);
    };
    let Some(value) = load_legacy()? else {
        return Ok(None);
    };
    if save_current(&value).is_ok() {
        let _ = clear_legacy();
    }
    Ok(Some(value))
}

impl PushStateStore for RegistrationStore {
    fn load(&self, account: &str) -> Result<Option<Registration>, PushError> {
        let legacy = self.legacy_service.as_deref().map(|legacy| {
            (
                move || Self::load_from(legacy, account),
                move |registration: &Registration| {
                    Self::save_to(&self.service, account, registration)
                },
                move || Self::clear_from(legacy, account),
            )
        });
        load_with_legacy(|| Self::load_from(&self.service, account), legacy)
    }

    fn save(&self, account: &str, registration: &Registration) -> Result<(), PushError> {
        Self::save_to(&self.service, account, registration)?;
        if let Some(legacy) = &self.legacy_service {
            let _ = Self::clear_from(legacy, account);
        }
        Ok(())
    }

    fn clear(&self, account: &str) -> Result<(), PushError> {
        let current = Self::clear_from(&self.service, account);
        let legacy = match &self.legacy_service {
            Some(legacy) => Self::clear_from(legacy, account),
            None => Ok(()),
        };
        current.and(legacy)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::cell::RefCell;

    struct Stores {
        current: RefCell<Option<u32>>,
        legacy: RefCell<Option<u32>>,
        current_writable: bool,
    }

    impl Stores {
        fn new(current: Option<u32>, legacy: Option<u32>, current_writable: bool) -> Self {
            Self {
                current: RefCell::new(current),
                legacy: RefCell::new(legacy),
                current_writable,
            }
        }

        fn load(&self) -> Result<Option<u32>, PushError> {
            load_with_legacy(
                || Ok(*self.current.borrow()),
                Some((
                    || Ok(*self.legacy.borrow()),
                    |value: &u32| {
                        if !self.current_writable {
                            return Err(PushError::new("push_store_failed", "locked"));
                        }
                        *self.current.borrow_mut() = Some(*value);
                        Ok(())
                    },
                    || {
                        *self.legacy.borrow_mut() = None;
                        Ok(())
                    },
                )),
            )
        }
    }

    #[test]
    fn pre_rename_registration_moves_to_the_current_store() {
        let stores = Stores::new(None, Some(7), true);
        assert_eq!(stores.load().unwrap(), Some(7));
        assert_eq!(*stores.current.borrow(), Some(7));
        assert_eq!(*stores.legacy.borrow(), None);
    }

    #[test]
    fn failed_copy_keeps_the_pre_rename_registration() {
        let stores = Stores::new(None, Some(7), false);
        assert_eq!(stores.load().unwrap(), Some(7));
        assert_eq!(*stores.current.borrow(), None);
        assert_eq!(*stores.legacy.borrow(), Some(7));
    }

    #[test]
    fn current_registration_wins_and_leaves_the_legacy_store_alone() {
        let stores = Stores::new(Some(1), Some(7), true);
        assert_eq!(stores.load().unwrap(), Some(1));
        assert_eq!(*stores.legacy.borrow(), Some(7));
        assert_eq!(Stores::new(None, None, true).load().unwrap(), None);
    }

    #[test]
    fn current_store_errors_are_not_mistaken_for_a_missing_registration() {
        let legacy_read = RefCell::new(false);
        let result: Result<Option<u32>, PushError> = load_with_legacy(
            || Err(PushError::new("push_store_unavailable", "locked")),
            Some((
                || {
                    *legacy_read.borrow_mut() = true;
                    Ok(Some(7))
                },
                |_: &u32| Ok(()),
                || Ok(()),
            )),
        );
        assert_eq!(result.unwrap_err().code, "push_store_unavailable");
        assert!(!*legacy_read.borrow());
    }
}
