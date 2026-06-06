# Third-Party Notices

## frp

- Project: https://github.com/fatedier/frp
- Bundled version: v0.61.0
- Bundled artifact: official `frp_0.61.0_android_arm64.tar.gz`
- Additional artifact: x86_64 emulator binary built from the v0.61.0 tag
- License: Apache License 2.0
- License copy: `app/src/main/assets/licenses/frp-LICENSE`

The bundled `frpc` executable is unmodified. It is renamed during the Android
build only so Android installs it in the APK native library directory; it is
still launched as a standalone process.
