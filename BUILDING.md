# Building

First read [BUILDING.md in Open Grind repository](https://git.opengrind.org/open-grind/open-grind/src/branch/main/BUILDING.md) for basics.

- `arm64-v8a` is any modern phone
- `armeabi-v7a` is old 32-bit devices
- `x86_64` is emulators/ChromeOS
- Debug builds only `arm64-v8a`

## Prerequisites

Clone repository:

```bash
git clone --recurse-submodules https://git.opengrind.org/open-grind/open-grind-google-oauth-android-app.git
```

## Build with Docker (easiest, Linux x86_64 only)

```bash
docker compose build
docker compose run --rm build
# -> app/build/outputs/apk/release/app-<abi>-release-unsigned.apk  (one per ABI)
```

### Clean up Docker

```bash
docker compose down -v
```

## Build with Nix (builds everywhere)

```bash
git submodule update --init
nix run .#build-android
# -> app/build/outputs/apk/release/app-<abi>-release-unsigned.apk  (one per ABI)
```

## Build manually (advanced)

Prerequisites:

- JDK 17
- Android SDK: platform 36 and build-tools 36.0.0

`./gradlew` pins Gradle 9.4.1, so you don't need Gradle installed.

```bash
./gradlew :app:assembleRelease
# -> app/build/outputs/apk/release/app-<abi>-release-unsigned.apk  (one per ABI)
```

## Signing

Must be the same keystore used for Open Grind releases.

```bash
GRINDR_OAUTH_KEYSTORE_PROPERTIES=/home/you/.config/open-grind/keystore.properties \
  nix run .#build-android
```

## Reproducibility

| Component                                    | Pinned in                                  |
| -------------------------------------------- | ------------------------------------------ |
| Gradle distribution (+ SHA-256)              | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin, GeckoView, AndroidX   | `gradle/libs.versions.toml`                |
| compileSdk / minSdk / targetSdk, build-tools | `app/build.gradle.kts`                     |
| JDK + Android SDK (Nix build)                | `flake.nix`                                |
| nixpkgs revision                             | `flake.lock`                               |
| Bundled web extension                        | submodule commit (`.gitmodules` + gitlink) |
