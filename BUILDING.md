# Building

First read [BUILDING.md in Open Grind repository](https://git.opengrind.org/open-grind/open-grind/src/branch/main/BUILDING.md) for basics.

- `arm64-v8a` is any modern phone
- `armeabi-v7a` is old 32-bit devices
- `x86_64` is emulators/ChromeOS
- Debug builds only `arm64-v8a`

## Prerequisites

Clone repository:

```bash
git clone --recurse-submodules https://git.opengrind.org/open-grind/google-oauth-app.git
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

Official releases are signed with the Open Grind release keystore. Open Grind sends token requests only to a Google OAuth app signed with that key, and this app answers only Open Grind builds whose certificate SHA-256 is listed in `SIGNING_CERTS_SHA256` in [OpenGrindTrust.kt](./app/src/main/java/org/opengrind/google_oauth/OpenGrindTrust.kt): the Open Grind release key and Google Play's app signing key.

To sign with your own key, create a keystore with the [keytool recipe](https://git.opengrind.org/open-grind/open-grind/src/branch/main/BUILDING.md#sign-android-build), then copy [contrib/keystore.properties.example](./contrib/keystore.properties.example). Token requests and hand-back between your own builds work only after you add your certificate's SHA-256 to `SIGNING_CERTS_SHA256` and set it as `RELEASE_CERT_SHA256` in Open Grind's [InstallGate.kt](https://git.opengrind.org/open-grind/open-grind/src/branch/main/src-tauri/android-logic/src/main/kotlin/org/opengrind/update/InstallGate.kt).

```bash
GRINDR_OAUTH_KEYSTORE_PROPERTIES=/home/you/.config/open-grind/keystore.properties \
  nix run .#build-android
```

## Verifying a release

Follow [Open Grind's § Verify minisign signature](https://git.opengrind.org/open-grind/open-grind/src/branch/main/BUILDING.md#verify-minisign-signature).

## Reproducibility

Follow [Open Grind's REPRODUCIBILITY.md](https://git.opengrind.org/open-grind/open-grind/src/branch/main/REPRODUCIBILITY.md).

| Component                                    | Pinned in                                  |
| -------------------------------------------- | ------------------------------------------ |
| Gradle distribution (+ SHA-256)              | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin, GeckoView, AndroidX   | `gradle/libs.versions.toml`                |
| compileSdk / minSdk / targetSdk, build-tools | `app/build.gradle.kts`                     |
| JDK + Android SDK (Nix build)                | `flake.nix`                                |
| nixpkgs revision                             | `flake.lock`                               |
| Bundled web extension                        | submodule commit (`.gitmodules` + gitlink) |
