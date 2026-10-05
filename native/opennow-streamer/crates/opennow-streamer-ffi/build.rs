fn main() {
    match std::env::var("CARGO_CFG_TARGET_OS").as_deref() {
        Ok("linux") => {
            println!("cargo:rustc-link-arg=-Wl,-soname,libcloudlight_streamer_ffi.so");
        }
        Ok("macos") => {
            println!(
                "cargo:rustc-link-arg=-Wl,-install_name,@rpath/libcloudlight_streamer_ffi.dylib"
            );
        }
        Ok("windows") if std::env::var("CARGO_CFG_TARGET_ENV").as_deref() == Ok("msvc") => {
            println!("cargo:rustc-link-arg=/DELAYLOAD:mfplat.dll");
            println!("cargo:rustc-link-lib=delayimp");
        }
        _ => {}
    }
}
