# Building Expanda

## Requirements

- JDK 17
- Android SDK Platform 36
- Android SDK Build Tools
- Git

Android Studio can install the JDK and SDK components, but the build itself runs through the checked-in Gradle wrapper.

## Debug build

```bash
git clone https://github.com/diegomarzaa/expanda.git
cd expanda
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug
```

The APK will be written under `app/build/outputs/apk/debug/`.

## Local checks

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

For a release-oriented check:

```bash
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

The release task creates an unsigned APK unless you configure a local signing key.

## Release signing

Keep the keystore and passwords outside version control. The project ignores `*.jks`, `*.keystore` and `signing.properties`.

Create `signing.properties` in the repository root:

```properties
storeFile=signing/your-release-key.jks
storePassword=your-keystore-password
keyAlias=your-key-alias
keyPassword=your-key-password
```

Then run:

```bash
./gradlew :app:assembleRelease
```

Do not commit `signing.properties`, the keystore or passwords. Back up the keystore securely: Android will reject future updates signed with another key.

## Verify an APK

```bash
sha256sum app-release.apk
$ANDROID_HOME/build-tools/36.1.0/apksigner verify --print-certs app-release.apk
```

Compare the certificate digest with the fingerprint published in [README.md](README.md).

## Local Gradle cache

The repository ignores `gradle-home/`. You can keep a project-local dependency cache with:

```bash
export GRADLE_USER_HOME="$PWD/gradle-home"
```

Gradle may need network access the first time it downloads the wrapper and dependencies. Later builds can reuse that cache.
