use std::sync::Arc;
use std::sync::Mutex;
use std::sync::atomic::{AtomicBool, Ordering};

use crate::{CapturedInput, CapturedInputQueue};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum EmbeddedLocalAction {
    Guide,
    Screenshot,
    RecordingToggle,
}

/// The letterboxed video rectangle in physical screen pixels. With it, the
/// platform input thread can sample the OS cursor itself in absolute mode, the
/// way GeForce NOW does, instead of waiting on the UI thread once per frame.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct InputViewport {
    pub left: i32,
    pub top: i32,
    pub width: u16,
    pub height: u16,
}

impl InputViewport {
    pub fn new(left: i32, top: i32, width: u32, height: u32) -> Option<Self> {
        let width = u16::try_from(width).ok().filter(|width| *width > 0)?;
        let height = u16::try_from(height).ok().filter(|height| *height > 0)?;
        Some(Self {
            left,
            top,
            width,
            height,
        })
    }

    /// Maps a physical screen point to a host absolute-mouse sample, clamped to
    /// the video edge, and whether the point is inside the video.
    pub fn sample(self, screen_x: i32, screen_y: i32) -> (CapturedInput, bool) {
        let dx = i64::from(screen_x) - i64::from(self.left);
        let dy = i64::from(screen_y) - i64::from(self.top);
        let inside = (0..i64::from(self.width)).contains(&dx)
            && (0..i64::from(self.height)).contains(&dy);
        let x = dx.clamp(0, i64::from(self.width) - 1) as u16;
        let y = dy.clamp(0, i64::from(self.height) - 1) as u16;
        (
            CapturedInput::MouseAbsolute {
                x,
                y,
                width: self.width,
                height: self.height,
            },
            inside,
        )
    }
}

pub struct EmbeddedInputCapture {
    queue: Arc<CapturedInputQueue>,
    active: AtomicBool,
    gamepads: Mutex<[Option<u16>; 4]>,
    viewport: Mutex<Option<InputViewport>>,
    #[cfg(target_os = "linux")]
    raw: Mutex<Option<crate::linux_xinput::LinuxXInputController>>,
    #[cfg(target_os = "windows")]
    raw: Mutex<Option<crate::windows_raw_input::WindowsRawInputController>>,
}

impl EmbeddedInputCapture {
    pub fn new(queue: Arc<CapturedInputQueue>) -> Self {
        Self {
            queue,
            active: AtomicBool::new(false),
            gamepads: Mutex::new([None; 4]),
            viewport: Mutex::new(None),
            #[cfg(any(target_os = "linux", target_os = "windows"))]
            raw: Mutex::new(None),
        }
    }

    pub fn submit(&self, input: CapturedInput) {
        let mut gamepads = self
            .gamepads
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        if let CapturedInput::Gamepad {
            controller_id,
            bitmap,
            buttons,
            left_trigger,
            right_trigger,
            left_stick_x,
            left_stick_y,
            right_stick_x,
            right_stick_y,
        } = &input
        {
            if usize::from(*controller_id) >= gamepads.len() {
                return;
            }
            let neutral = (
                *buttons,
                *left_trigger,
                *right_trigger,
                *left_stick_x,
                *left_stick_y,
                *right_stick_x,
                *right_stick_y,
            ) == (0, 0, 0, 0, 0, 0, 0);
            if !self.active.load(Ordering::Acquire) && !neutral {
                return;
            }
            for value in gamepads.iter_mut().flatten() {
                *value = *bitmap;
            }
            gamepads[usize::from(*controller_id)] = Some(*bitmap);
            if neutral && !self.active.load(Ordering::Acquire) {
                self.queue.release_gamepad(*controller_id, *bitmap);
                return;
            }
        }
        if self.active.load(Ordering::Acquire) {
            self.queue.push(input);
        }
    }

    pub fn submit_local_action(&self, action: EmbeddedLocalAction) {
        self.submit(match action {
            EmbeddedLocalAction::Guide => CapturedInput::Guide,
            EmbeddedLocalAction::Screenshot => CapturedInput::Screenshot,
            EmbeddedLocalAction::RecordingToggle => CapturedInput::RecordingToggle,
        });
    }

    pub fn submit_text(
        &self,
        bytes: &[u8],
    ) -> Result<(), opennow_streamer_protocol::text_input::TextInputError> {
        let _guard = self
            .gamepads
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        if !self.active.load(Ordering::Acquire) {
            return Err(opennow_streamer_protocol::text_input::TextInputError::Unavailable);
        }
        self.queue.submit_text(bytes)
    }

    /// Publishes where the video is on screen. `None` hands absolute input
    /// back to the UI toolkit.
    pub fn set_viewport(&self, viewport: Option<InputViewport>) {
        *self
            .viewport
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner) = viewport;
        #[cfg(target_os = "windows")]
        {
            let raw = self
                .raw
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner);
            if let Some(raw) = raw.as_ref() {
                raw.set_viewport(viewport);
            }
        }
    }

    pub fn viewport(&self) -> Option<InputViewport> {
        *self
            .viewport
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
    }

    pub fn set_active(&self, active: bool, relative_mouse: bool, window_handle: usize) -> bool {
        let mut gamepads = self
            .gamepads
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        if !active {
            self.queue.discard_text();
            if self.active.load(Ordering::Acquire) {
                for (controller_id, bitmap) in gamepads.iter_mut().enumerate() {
                    if let Some(bitmap) = bitmap.take() {
                        self.queue.release_gamepad(controller_id as u8, bitmap);
                    }
                }
            }
            self.active.store(false, Ordering::Release);
        } else {
            self.active.store(true, Ordering::Release);
        }

        #[cfg(target_os = "linux")]
        {
            let raw_enabled = x11_raw_capture_enabled(active, relative_mouse, window_handle);
            let mut raw = self
                .raw
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner);
            if raw_enabled && raw.is_none() {
                match crate::linux_xinput::LinuxXInputController::start(Arc::clone(&self.queue)) {
                    Ok(controller) => *raw = Some(controller),
                    Err(error) => eprintln!("Embedded XInput2 capture unavailable: {error}"),
                }
            }
            if let Some(raw) = raw.as_ref() {
                raw.set_enabled(raw_enabled);
                raw_enabled
            } else {
                false
            }
        }

        #[cfg(target_os = "windows")]
        {
            // One owner for position, buttons and wheel. With a published video
            // viewport the Raw Input thread owns all three in both cursor modes:
            // in absolute mode it samples the OS cursor on every mouse report, so
            // host positions follow the mouse instead of the UI thread's frame
            // pacing. Without a viewport Qt keeps absolute mode, and Raw Input
            // only serves relative motion.
            let viewport = self.viewport();
            let raw_enabled =
                windows_raw_capture_enabled(active, relative_mouse, viewport.is_some());
            let mut raw = self
                .raw
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner);
            if active && raw.is_none() && window_handle != 0 {
                match crate::windows_raw_input::WindowsRawInputController::start(
                    window_handle as isize,
                    Arc::clone(&self.queue),
                ) {
                    Ok(controller) => *raw = Some(controller),
                    Err(error) => eprintln!("Embedded Raw Input capture unavailable: {error}"),
                }
            }
            if let Some(raw) = raw.as_ref() {
                if window_handle != 0 {
                    raw.set_foreground_owner(window_handle as isize);
                }
                raw.set_viewport(viewport);
                raw.set_capture(raw_enabled, relative_mouse);
                raw_enabled
            } else {
                false
            }
        }

        #[cfg(not(any(target_os = "linux", target_os = "windows")))]
        {
            let _ = (relative_mouse, window_handle);
            false
        }
    }

    pub fn queue(&self) -> Arc<CapturedInputQueue> {
        Arc::clone(&self.queue)
    }
}

#[cfg(any(target_os = "linux", test))]
const fn x11_raw_capture_enabled(active: bool, relative_mouse: bool, x11_window: usize) -> bool {
    raw_capture_enabled(active, relative_mouse) && x11_window != 0
}

#[cfg(any(target_os = "linux", test))]
const fn raw_capture_enabled(active: bool, relative_mouse: bool) -> bool {
    active && relative_mouse
}

#[cfg(any(target_os = "windows", test))]
const fn windows_raw_capture_enabled(active: bool, relative_mouse: bool, has_viewport: bool) -> bool {
    active && (relative_mouse || has_viewport)
}

#[cfg(test)]
mod tests {
    #[test]
    fn old_session_teardown_cannot_cancel_or_enable_new_session_text() {
        use opennow_streamer_protocol::text_input::TextInputError;
        let queue = std::sync::Arc::new(crate::CapturedInputQueue::default());
        let capture = super::EmbeddedInputCapture::new(queue.clone());
        capture.set_active(true, false, 0);
        queue.set_text_ready(1, true);
        capture.submit_text(b"old").unwrap();
        let Some(crate::CapturedInput::Text(old)) = queue.take() else {
            panic!("missing old paste")
        };
        queue.set_text_ready(2, false);
        assert!(old.is_cancelled());
        drop(old);
        queue.set_text_ready(1, true);
        assert_eq!(
            capture.submit_text(b"new"),
            Err(TextInputError::Unavailable)
        );
        queue.set_text_ready(2, true);
        capture.submit_text(b"new").unwrap();
        queue.set_text_ready(1, false);
        let Some(crate::CapturedInput::Text(new)) = queue.take() else {
            panic!("missing new paste")
        };
        assert!(!new.is_cancelled());
    }

    #[test]
    fn text_requires_capture_and_session_and_cancels_in_flight_on_focus_loss() {
        use opennow_streamer_protocol::text_input::TextInputError;
        let queue = std::sync::Arc::new(crate::CapturedInputQueue::default());
        let capture = super::EmbeddedInputCapture::new(queue.clone());
        assert_eq!(
            capture.submit_text(b"paste"),
            Err(TextInputError::Unavailable)
        );
        capture.set_active(true, false, 0);
        assert_eq!(
            capture.submit_text(b"paste"),
            Err(TextInputError::Unavailable)
        );
        queue.set_text_ready(1, true);
        assert_eq!(capture.submit_text(b"paste"), Ok(()));
        assert_eq!(capture.submit_text(b"again"), Err(TextInputError::Busy));
        let Some(crate::CapturedInput::Text(text)) = queue.take() else {
            panic!("missing text")
        };
        assert_eq!(capture.submit_text(b"again"), Err(TextInputError::Busy));
        capture.set_active(false, false, 0);
        assert!(text.is_cancelled());
        drop(text);
        capture.set_active(true, false, 0);
        assert_eq!(capture.submit_text(b"again"), Ok(()));
        queue.set_text_ready(1, false);
        assert!(queue.take().is_none());
        assert_eq!(
            capture.submit_text(b"again"),
            Err(TextInputError::Unavailable)
        );
    }

    #[test]
    fn full_capture_queue_rejects_paste_atomically_without_control_overflow() {
        use opennow_streamer_protocol::text_input::TextInputError;
        let queue = std::sync::Arc::new(crate::CapturedInputQueue::default());
        let capture = super::EmbeddedInputCapture::new(queue.clone());
        capture.set_active(true, false, 0);
        queue.set_text_ready(1, true);
        for _ in 0..256 {
            capture.submit(crate::CapturedInput::Guide);
        }
        assert_eq!(capture.submit_text(b"paste"), Err(TextInputError::Busy));
        assert!(!queue.take_overflowed());
        for _ in 0..256 {
            assert_eq!(queue.take(), Some(crate::CapturedInput::Guide));
        }
        assert!(queue.take().is_none());
        assert_eq!(capture.submit_text(b"paste"), Ok(()));
    }

    use super::*;

    fn gamepad(controller_id: u8, bitmap: u16, buttons: u16) -> CapturedInput {
        CapturedInput::Gamepad {
            controller_id,
            bitmap,
            buttons,
            left_trigger: 0,
            right_trigger: 0,
            left_stick_x: 0,
            left_stick_y: 0,
            right_stick_x: 0,
            right_stick_y: 0,
        }
    }

    #[test]
    fn closing_capture_neutralizes_all_controllers_before_rejecting_held_input() {
        let queue = Arc::new(CapturedInputQueue::default());
        let capture = EmbeddedInputCapture::new(Arc::clone(&queue));
        capture.set_active(true, false, 0);
        for id in 0..4 {
            capture.submit(gamepad(id, 0x0f0f, 0x1000));
            assert_eq!(queue.take(), Some(gamepad(id, 0x0f0f, 0x1000)));
        }
        capture.set_active(false, false, 0);
        capture.submit(gamepad(0, 0x0f0f, 0x1000));
        for id in 0..4 {
            assert_eq!(queue.take(), Some(gamepad(id, 0x0f0f, 0)));
        }
        assert_eq!(queue.take(), None);
        capture.set_active(false, false, 0);
        assert_eq!(queue.take(), None);
    }

    #[test]
    fn controller_disconnect_is_preserved_after_capture_closes() {
        let queue = Arc::new(CapturedInputQueue::default());
        let capture = EmbeddedInputCapture::new(Arc::clone(&queue));
        capture.set_active(true, false, 0);
        capture.submit(gamepad(0, 0x0101, 0x1000));
        capture.set_active(false, false, 0);
        capture.submit(gamepad(0, 0, 0));
        assert_eq!(queue.take(), Some(gamepad(0, 0, 0)));
        assert_eq!(queue.take(), None);
    }

    #[test]
    fn concurrent_controller_submission_cannot_follow_a_capture_closure_neutral() {
        let queue = Arc::new(CapturedInputQueue::default());
        let capture = Arc::new(EmbeddedInputCapture::new(Arc::clone(&queue)));
        capture.set_active(true, false, 0);
        capture.submit(gamepad(0, 0x0101, 0x1000));
        queue.take();
        let barrier = Arc::new(std::sync::Barrier::new(2));
        let producer = {
            let capture = Arc::clone(&capture);
            let barrier = Arc::clone(&barrier);
            std::thread::spawn(move || {
                barrier.wait();
                for _ in 0..64 {
                    capture.submit(gamepad(0, 0x0101, 0x1000));
                }
            })
        };
        barrier.wait();
        capture.set_active(false, false, 0);
        producer.join().unwrap();
        assert_eq!(queue.take(), Some(gamepad(0, 0x0101, 0)));
        assert_eq!(queue.take(), None);
        assert!(!queue.take_overflowed());
    }

    #[test]
    fn capture_closure_resets_triggers_and_both_sticks_without_a_button_press() {
        let queue = Arc::new(CapturedInputQueue::default());
        let capture = EmbeddedInputCapture::new(Arc::clone(&queue));
        capture.set_active(true, false, 0);
        capture.submit(CapturedInput::Gamepad {
            controller_id: 0,
            bitmap: 0x0101,
            buttons: 0,
            left_trigger: 255,
            right_trigger: 128,
            left_stick_x: 32767,
            left_stick_y: -32768,
            right_stick_x: 24000,
            right_stick_y: -24000,
        });
        queue.take();
        capture.set_active(false, false, 0);
        assert_eq!(queue.take(), Some(gamepad(0, 0x0101, 0)));
        assert_eq!(queue.take(), None);
    }

    #[test]
    fn capture_closure_reserves_neutral_slots_even_when_normal_queue_is_full() {
        let queue = Arc::new(CapturedInputQueue::default());
        let capture = EmbeddedInputCapture::new(Arc::clone(&queue));
        capture.set_active(true, false, 0);
        for id in 0..4 {
            capture.submit(gamepad(id, 0x0f0f, 0x1000));
            queue.take();
        }
        for _ in 0..256 {
            capture.submit_local_action(EmbeddedLocalAction::Guide);
        }
        capture.set_active(false, false, 0);
        assert!(!queue.take_overflowed());
        for _ in 0..256 {
            assert_eq!(queue.take(), Some(CapturedInput::Guide));
        }
        for id in 0..4 {
            assert_eq!(queue.take(), Some(gamepad(id, 0x0f0f, 0)));
        }
        assert_eq!(queue.take(), None);
    }

    #[test]
    fn capture_reacquisition_does_not_replay_stale_controller_state() {
        let queue = Arc::new(CapturedInputQueue::default());
        let capture = EmbeddedInputCapture::new(Arc::clone(&queue));
        for _ in 0..3 {
            capture.set_active(true, false, 0);
            assert_eq!(queue.take(), None);
            capture.submit(gamepad(0, 0x0101, 0x1000));
            capture.set_active(false, false, 0);
            assert_eq!(queue.take(), Some(gamepad(0, 0x0101, 0)));
            assert_eq!(queue.take(), None);
        }
    }

    #[test]
    fn xinput_requires_an_explicit_x11_window_even_with_relative_mode_enabled() {
        assert!(!x11_raw_capture_enabled(true, true, 0));
        assert!(x11_raw_capture_enabled(true, true, 42));
        assert!(!x11_raw_capture_enabled(true, false, 42));
        assert!(!x11_raw_capture_enabled(false, true, 42));
    }

    #[test]
    fn inactive_capture_drops_input_and_active_capture_preserves_typed_events() {
        let queue = Arc::new(CapturedInputQueue::default());
        let capture = EmbeddedInputCapture::new(Arc::clone(&queue));
        capture.submit(CapturedInput::Key {
            virtual_key: 0x57,
            modifiers: 0,
            pressed: true,
        });
        assert_eq!(queue.take(), None);

        capture.active.store(true, Ordering::Release);
        capture.submit_local_action(EmbeddedLocalAction::Guide);
        assert_eq!(queue.take(), Some(CapturedInput::Guide));
    }

    #[test]
    fn windows_raw_capture_owns_absolute_mode_only_with_a_viewport() {
        assert!(!windows_raw_capture_enabled(false, true, true));
        assert!(windows_raw_capture_enabled(true, true, false));
        assert!(!windows_raw_capture_enabled(true, false, false));
        assert!(windows_raw_capture_enabled(true, false, true));
    }

    #[test]
    fn viewport_samples_are_clamped_to_the_video_and_report_inside() {
        let viewport = InputViewport::new(100, 50, 1920, 1080).unwrap();
        assert_eq!(
            viewport.sample(100, 50),
            (
                CapturedInput::MouseAbsolute {
                    x: 0,
                    y: 0,
                    width: 1920,
                    height: 1080
                },
                true
            )
        );
        assert_eq!(
            viewport.sample(2019, 1129),
            (
                CapturedInput::MouseAbsolute {
                    x: 1919,
                    y: 1079,
                    width: 1920,
                    height: 1080
                },
                true
            )
        );
        let (outside, inside) = viewport.sample(2020, 10);
        assert!(!inside);
        assert_eq!(
            outside,
            CapturedInput::MouseAbsolute {
                x: 1919,
                y: 0,
                width: 1920,
                height: 1080
            }
        );
        assert!(InputViewport::new(0, 0, 0, 10).is_none());
        assert!(InputViewport::new(0, 0, 70_000, 10).is_none());
    }

    #[test]
    fn published_viewport_is_kept_for_the_next_capture() {
        let capture = EmbeddedInputCapture::new(Arc::new(CapturedInputQueue::default()));
        assert_eq!(capture.viewport(), None);
        let viewport = InputViewport::new(-1920, 0, 1280, 720);
        capture.set_viewport(viewport);
        assert_eq!(capture.viewport(), viewport);
        capture.set_viewport(None);
        assert_eq!(capture.viewport(), None);
    }

    #[test]
    fn raw_capture_is_reserved_for_relative_mouse_mode() {
        assert!(!raw_capture_enabled(false, false));
        assert!(!raw_capture_enabled(true, false));
        assert!(!raw_capture_enabled(false, true));
        assert!(raw_capture_enabled(true, true));
    }
}
