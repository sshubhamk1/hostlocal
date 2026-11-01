# Set up and build

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 17 | The Android Gradle Plugin 8.7 needs 17. JDK 11 will not work. |
| Android SDK | platform 36, build-tools 35+ | Install with Android Studio or `sdkmanager`. |
| adb | any recent | Part of platform-tools. |
| Device | Android 8.0+ (API 26) | The author's phone runs Android 13 (OxygenOS 13). |

macOS with Homebrew: `brew install --cask temurin@17`, then `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`.

Point Gradle at the SDK with `local.properties` in the repo root (Android Studio writes this for you; the file is gitignored):

```properties
sdk.dir=/Users/you/Library/Android/sdk
```

## Build and install

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Run the unit tests (JVM, no device):

```bash
./gradlew testDebugUnitTest
```

??? tip "If `./gradlew` can't download Gradle"
    On some networks Java's connection to the Gradle download times out while `curl` works. Download the zip yourself and run that Gradle directly:

    ```bash
    curl -L -o gradle-8.10.2-bin.zip https://services.gradle.org/distributions/gradle-8.10.2-bin.zip
    unzip gradle-8.10.2-bin.zip -d ~/gradle
    ~/gradle/gradle-8.10.2/bin/gradle assembleDebug
    ```

    Or open the project in Android Studio, which manages Gradle itself.

## First run on a device

1. Open **HostLocal**. A **Setup** checklist lists what is missing:
    - **All-files access**: needed to read videos with real file paths (see [decisions](decisions.md#storage-all-files-access)).
    - **Notifications**: for the foreground-service notification (Android 13+).
    - **Battery optimization**: recommended, so the server survives with the screen off.
2. Choose the folder to share (default `/storage/emulated/0/Movies`).
3. Turn on the hotspot, or join a Wi-Fi network.
4. Tap **Start**. The screen shows `ftp://<ip>:2121` and the status of each server.

## Release build and signing

The release build is signed only if `keystore.properties` exists in the repo root (gitignored, as is `*.jks`):

```properties
storeFile=/absolute/path/to/release.jks
storePassword=...
keyAlias=hostlocal
keyPassword=...
```

```bash
keytool -genkeypair -keystore ~/.hostlocal/release.jks -alias hostlocal \
  -keyalg RSA -keysize 2048 -validity 10000
./gradlew assembleRelease
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

!!! warning "Back up the keystore"
    If the signing key is lost, a new APK can't be installed over the old one. Keep the keystore and `keystore.properties` somewhere safe and out of git.

Without `keystore.properties`, `assembleRelease` still builds, but the APK is unsigned and can't be installed.

R8 minification is **off** on purpose: MINA and JmDNS rely on reflection. Don't enable it without testing both servers on a device.

## Building the docs

```bash
python3 -m venv .venv-docs && source .venv-docs/bin/activate
pip install -r requirements-docs.txt
mkdocs serve          # live preview at http://127.0.0.1:8000
mkdocs build --strict # what CI should run; fails on broken links
```
