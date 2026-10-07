# Helping out with Cloudlight

Thanks for wanting to help! Here's the short version of how things fit together.

## What lives where

- `opennow-qt/` is the app you see: the screens, menus and settings.
- `native/opennow-core/` handles signing in, your settings and the game list.
- `native/opennow-streamer/` does the actual streaming: video, sound and your inputs.
- `locales/` holds the app's text. Only change `en.json`; the other languages are
  filled in automatically.

The folders still use the old OpenNOW names on purpose, so builds and saved settings
keep working.

## Getting it running

You'll need Qt 6.8 or newer (with Quick, Multimedia and ShaderTools), CMake, a C++
compiler, Rust and SDL3. Then:

```bash
git clone https://github.com/miirys/OpenNOW.git
cd OpenNOW
cmake -S opennow-qt -B build/opennow-qt -DCMAKE_BUILD_TYPE=Debug
cmake --build build/opennow-qt
```

To check nothing broke:

```bash
ctest --test-dir build/opennow-qt --output-on-failure
cargo test --manifest-path native/opennow-core/Cargo.toml
cargo test --manifest-path native/opennow-streamer/Cargo.toml --workspace
```

If you changed any app text, also run `npm run locales:check`. That's the only thing
Node is used for.

## Sending a change

1. Make a branch.
2. Keep it to one thing, with clear commit messages.
3. Run the tests above for the parts you touched.
4. Open a pull request and say in a sentence or two what changed and why.

Put screenshots and recordings in the pull request rather than in the repo.
