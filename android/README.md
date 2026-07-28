# Lunentous — Android

Not a working app yet (no real screens), but the foundation is in place:
a Catppuccin-themed adaptive nav shell (bottom bar in portrait, side rail
in landscape, both icons-only, 5 destinations mirroring the web app), an
optional server connection managed from Settings (URL + API key, saved via
Keystore-backed encrypted storage -- the app is fully usable with no
server ever configured), and a complete local-first data layer: a Room
database mirroring every server entity, a Retrofit client covering the
full REST API, and repositories that read from Room and write through to
the network when connected (or straight to Room, marked local-only, when
not). See the plan this is being built from and `ARCHITECTURE.md` at the
repo root for the backend it talks to.

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

## Installing a release build

Debug builds are auto-signed with a throwaway key generated per-machine —
fine for iterating, but every fresh checkout gets a different one, and
Google Play-style app stores aren't involved here anyway. For an install
you actually want to keep updating over time, build and sign a release
APK instead.

**One-time: generate a signing key** (skip if `android/keystore.properties`
already exists — e.g. it was set up on another machine and you copied it
over):

```bash
cd android
keytool -genkeypair -v \
  -keystore release.keystore.jks \
  -alias lunentous \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Lunentous, OU=Personal, O=Lunentous, L=Unknown, ST=Unknown, C=US"
```

`keytool` will prompt for a password (used for both the keystore and the
key — newer `keytool` versions require them to match). Then point Gradle
at it:

```bash
cat > keystore.properties <<EOF
storeFile=release.keystore.jks
storePassword=<the password you just set>
keyAlias=lunentous
keyPassword=<the same password>
EOF
```

`release.keystore.jks` and `keystore.properties` are both gitignored —
**back them up somewhere safe**. Lose them and you lose the ability to
install an updated release build over an existing one; you'd have to
uninstall first and start over (losing any data that hadn't synced to
your server yet).

**Build and install:**

```bash
source ~/.android-toolchain/env.sh
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

If you already have a **debug** build installed, that install will fail
with a signature mismatch (debug and release use different keys, same
`applicationId`) — uninstall it first:

```bash
adb uninstall com.lunentous.app
adb install -r app/build/outputs/apk/release/app-release.apk
```

From then on, release-to-release updates (`assembleRelease` + `adb
install -r`) work fine with no uninstall needed, as long as you're
signing with the same keystore.

## Project layout

Standard single-module Android Gradle project:

```
android/
  settings.gradle.kts   plugin/dependency repositories, includes :app
  build.gradle.kts       top-level plugin version declarations (incl. KSP)
  app/
    build.gradle.kts      applicationId com.lunentous.app, minSdk 26,
                          target/compileSdk 35, Compose + Room (via KSP)
    src/main/
      AndroidManifest.xml   INTERNET permission, usesCleartextTraffic
                            (the server has no built-in TLS -- see root
                            README.md's Network exposure note)
      java/com/lunentous/app/
        MainActivity.kt, LunentousApplication.kt   entry point + app-scoped DI
        di/AppContainer.kt      manual DI (no Hilt) -- database, network,
                                repositories, all app-scoped singletons
        data/
          auth/SessionStore.kt        encrypted server URL + API key storage
          local/                       Room: entity/, dao/, LunentousDatabase,
                                       Converters -- local-ID-first schema,
                                       see ARCHITECTURE.md's Android section
          remote/                      LunentousApi (Retrofit, full REST
                                       surface), dto/, ApiKeyInterceptor,
                                       DynamicBaseUrlInterceptor, NetworkModule
          repository/                  Plant/ReminderType/PhaseType/
                                       ReminderRule/ReminderState/PhaseWindow/
                                       TimelineRepository -- Room reads,
                                       network-passthrough writes when
                                       connected, local-only when not
        ui/
          theme/Theme.kt      Catppuccin Mocha/Latte + JetBrains Mono
          nav/NavDestination.kt, MainScaffold.kt   adaptive bottom bar/rail
          settings/SettingsScreen.kt   server connect/disconnect + status
      res/font/                JetBrains Mono TTFs (SIL licensed, same
                               files the web self-hosts via @fontsource)
      res/values/strings.xml
```

Kotlin 2.0.20, AGP 8.6.0, Gradle 8.9, Compose BOM 2024.09.00, Room 2.6.1 —
bump these together when the time comes, they're cross-version-sensitive.

No outbox/offline-write-queue yet -- writes go straight through to the
network when connected (or straight to Room, local-only, when not). The
outbox is a later build phase; see the plan's "Data layer & offline sync"
section for the full design.
