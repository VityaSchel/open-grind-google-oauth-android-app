{
  description = "Grindr Google OAuth Android companion — declarative build toolchain";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
    }:
    flake-utils.lib.eachSystem
      [
        "x86_64-linux"
        "aarch64-linux"
        "x86_64-darwin"
        "aarch64-darwin"
      ]
      (
        system:
        let
          pkgs = import nixpkgs {
            inherit system;
            config = {
              android_sdk.accept_license = true;
              allowUnfree = true;
            };
          };

          androidPlatformVersion = "36";
          androidBuildToolsVersion = "36.0.0";

          androidComposition = pkgs.androidenv.composeAndroidPackages {
            platformVersions = [ androidPlatformVersion ];
            buildToolsVersions = [ androidBuildToolsVersion ];
            includeNDK = false;
            includeEmulator = false;
            includeSources = false;
            includeSystemImages = false;
            includeExtras = [ ];
          };

          androidSdk = androidComposition.androidsdk;
          androidSdkRoot = "${androidSdk}/libexec/android-sdk";
          buildToolsBin = "${androidSdkRoot}/build-tools/${androidBuildToolsVersion}";

          jdk = pkgs.jdk17_headless;

          toolchainInputs = [
            jdk
            androidSdk
            pkgs.coreutils
          ];

          buildEnv = {
            JAVA_HOME = jdk.home;
            ANDROID_HOME = androidSdkRoot;
            ANDROID_SDK_ROOT = androidSdkRoot;
            GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${buildToolsBin}/aapt2";
          };

          envExports = pkgs.lib.concatStringsSep "\n" (
            pkgs.lib.mapAttrsToList (k: v: "export ${k}=${v}") buildEnv
          );

          buildAndroidScript = pkgs.writeShellApplication {
            name = "grindr-oauth-build-android";
            runtimeInputs = toolchainInputs;
            text = ''
              set -euo pipefail

              ${envExports}
              export PATH="${buildToolsBin}:$PATH"

              ROOT="''${GRINDR_OAUTH_ROOT:-$PWD}"
              cd "$ROOT"

              if [ ! -e extension/geckoview/manifest.json ]; then
                echo "error: the 'extension' submodule is not checked out." >&2
                echo "       run: git submodule update --init" >&2
                exit 1
              fi

              TASK="''${1:-:app:assembleRelease}"
              ./gradlew "$TASK"

              kp="''${GRINDR_OAUTH_KEYSTORE_PROPERTIES:-}"
              case "$kp" in "~"*) kp="$HOME''${kp#\~}" ;; esac
              if [ -n "$kp" ] && [ -f "$kp" ]; then sfx=""; else sfx="-unsigned"; fi

              echo
              case "$TASK" in
                *ebug*)
                  echo "Produced:"
                  apks=("$ROOT/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk")
                  ;;
                *)
                  echo "Produced (release, one APK per ABI):"
                  apks=(
                    "$ROOT/app/build/outputs/apk/release/app-arm64-v8a-release$sfx.apk"
                    "$ROOT/app/build/outputs/apk/release/app-armeabi-v7a-release$sfx.apk"
                    "$ROOT/app/build/outputs/apk/release/app-x86_64-release$sfx.apk"
                  )
                  ;;
              esac
              for apk in "''${apks[@]}"; do
                if [ -f "$apk" ]; then
                  printf '  %s (%s)\n' "$apk" "$(du -h "$apk" | cut -f1)"
                else
                  printf '  %s\n' "$apk"
                fi
              done
            '';
          };
        in
        {
          devShells.default = pkgs.mkShell (
            buildEnv
            // {
              packages = toolchainInputs;
              shellHook = ''
                export PATH="${buildToolsBin}:$PATH"

                echo "Grindr Google OAuth dev shell: Android toolchain pinned via Nix."
                echo "  JDK: $JAVA_HOME"
                echo "  SDK: $ANDROID_HOME"
              '';
            }
          );

          packages = {
            default = buildAndroidScript;
            build-android = buildAndroidScript;
          };

          apps = {
            default = {
              type = "app";
              program = "${buildAndroidScript}/bin/grindr-oauth-build-android";
            };
            build-android = {
              type = "app";
              program = "${buildAndroidScript}/bin/grindr-oauth-build-android";
            };
          };

          formatter = pkgs.nixfmt-rfc-style;
        }
      );
}
