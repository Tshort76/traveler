# Building Traveler

## Toolchain

- JDK 17. The Makefile finds it with `/usr/libexec/java_home -v 17`.
- Android SDK. `local.properties` holds `sdk.dir=…`; on this Mac that is `/opt/homebrew/share/android-commandlinetools`. The file is gitignored, so a fresh clone needs one.
- Python 3, standard library only, for `tools/`.

`make doctor` reports what is missing. `make check` is the gate before a commit: validator tests, every example validated, and the app's JVM tests (Robolectric included). None of it needs a device.

## Emulator

Create the AVD once:

```bash
SDK=/opt/homebrew/share/android-commandlinetools
$SDK/cmdline-tools/latest/bin/sdkmanager "system-images;android-36;google_apis;arm64-v8a" "emulator" "platform-tools"
$SDK/cmdline-tools/latest/bin/avdmanager create avd -n traveler -k "system-images;android-36;google_apis;arm64-v8a"
```

Then `make emulator` (headless) and `make install`.

`tools/emu/ui.py` drives the running app over adb: `tap <text>` (prefix `=` for an exact match), `texts`, `shot <png>`, `scroll [fromY toY]`, `longdrag`, `back`. Put `platform-tools` on `PATH` first.

To import a file without a file manager, hand it to the app's own FileProvider:

```bash
adb push trip.json /data/local/tmp/
adb shell "run-as dev.tlong.traveler sh -c 'mkdir -p cache/exports && cp /data/local/tmp/trip.json cache/exports/'"
adb shell am start -a android.intent.action.VIEW -t application/json --grant-read-uri-permission \
  -d content://dev.tlong.traveler.files/exports/trip.json -n dev.tlong.traveler/.MainActivity
```

## On a phone

Turn on USB debugging, plug in, `make install`. Or copy `app/build/outputs/apk/debug/app-debug.apk` to the phone and open it (allow installs from that app when asked).

## Release signing

Optional, as in BioDex. Without `keystore.properties` the release APK is unsigned. To sign:

```bash
keytool -genkeypair -v -keystore ~/keys/traveler.jks -alias traveler -keyalg RSA -keysize 4096 -validity 10000
```

and create `keystore.properties` (gitignored) beside `settings.gradle.kts`:

```properties
storeFile=/Users/you/keys/traveler.jks
storePassword=…
keyAlias=traveler
keyPassword=…
```

A debug build and a release build have different signatures, so switching between them on a phone means uninstalling first. Take a backup (Settings → Save backup…) before you do.
