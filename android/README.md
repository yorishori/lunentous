# Lunentous — Android

Not a working app yet, but past the bare toolchain scaffold: there's a
Catppuccin-themed login screen (server address + API key, saved via
Keystore-backed encrypted storage) and an adaptive navigation shell —
bottom bar in portrait, side rail in landscape, both icons-only — with 5
placeholder destinations mirroring the web app's nav. No data layer yet;
screens don't fetch or show real data. See the plan this is being built
from and `ARCHITECTURE.md` at the repo root for the backend it'll talk to.

No Android Studio is required — everything here works from the command
line with a physical device over `adb`. (Studio works fine too, if you'd
rather use it; just open this `android/` directory as a project.)

## One-time setup

You need a JDK 17 and the Android SDK. Everything below installs to your
own home directory — **no root/sudo needed**.

```bash
# JDK 17 (Eclipse Temurin)
mkdir -p ~/.android-toolchain && cd ~/.android-toolchain
curl -fL -o jdk17.tar.gz \
  "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse?project=jdk"
tar xzf jdk17.tar.gz && rm jdk17.tar.gz
mv jdk-17* jdk-17

# Android SDK command-line tools
mkdir -p ~/Android/Sdk/cmdline-tools
cd ~/Android/Sdk/cmdline-tools
curl -fL -o cmdline-tools.zip \
  "https://dl.google.com/android/repository/commandlinetools-linux-9862592_latest.zip"
unzip -q cmdline-tools.zip && rm cmdline-tools.zip
mv cmdline-tools latest

# env.sh -- source this before any build/adb command in a new shell
cat > ~/.android-toolchain/env.sh <<'EOF'
export JAVA_HOME="$HOME/.android-toolchain/jdk-17"
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
EOF

source ~/.android-toolchain/env.sh
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

If the `commandlinetools-linux-*` URL above 404s by the time you read this
(Google revs the version number periodically), grab the current one from
<https://developer.android.com/studio#command-tools> instead.

Point the project at your SDK once:

```bash
echo "sdk.dir=$HOME/Android/Sdk" > android/local.properties
```

(`local.properties` is gitignored on purpose — it's a local path, never
commit it.)

## Building

```bash
source ~/.android-toolchain/env.sh
cd android
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. First run downloads
the pinned Gradle distribution (via the `gradlew` wrapper script) and a
matching Build-Tools version if AGP wants one you don't have yet — both are
one-time, cached under `~/.gradle` and `~/Android/Sdk` after that.

## Running on a physical phone

No emulator is set up for this project (deliberately — see the plan this
was scaffolded from). Use a real device instead:

1. On the phone: **Settings → About phone**, tap "Build number" 7 times to
   unlock Developer Options, then **Settings → Developer options → USB
   debugging** → on.
2. On your machine: install `adb` if you don't already have it —
   `sudo pacman -S android-tools` on Arch (or use the `platform-tools` from
   the setup above, already on your `PATH` after sourcing `env.sh`).
3. Plug the phone in via USB, accept the "Allow USB debugging?" prompt on
   the device, then:

   ```bash
   adb devices          # confirm the phone shows up, not "unauthorized"
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

You should see a "Lunentous" placeholder screen launch on the phone.

## Project layout

Standard single-module Android Gradle project:

```
android/
  settings.gradle.kts   plugin/dependency repositories, includes :app
  build.gradle.kts       top-level plugin version declarations
  app/
    build.gradle.kts      applicationId com.lunentous.app, minSdk 26,
                          target/compileSdk 35, Compose enabled;
                          Retrofit + WorkManager already declared as
                          dependencies (data layer not built yet)
    src/main/
      AndroidManifest.xml   INTERNET permission, usesCleartextTraffic
                            (the server has no built-in TLS -- see root
                            README.md's Network exposure note)
      java/com/lunentous/app/
        MainActivity.kt       entry point: theme + login-gated nav shell
        data/auth/SessionStore.kt   encrypted server URL + API key storage
        ui/theme/Theme.kt      Catppuccin Mocha/Latte + JetBrains Mono
        ui/login/LoginScreen.kt
        ui/nav/NavDestination.kt, MainScaffold.kt   adaptive bottom bar/rail
      res/font/                JetBrains Mono TTFs (SIL licensed, same
                               files the web self-hosts via @fontsource)
      res/values/strings.xml
```

Kotlin 2.0.20, AGP 8.6.0, Gradle 8.9, Compose BOM 2024.09.00 — bump these
together when the time comes, they're cross-version-sensitive.
