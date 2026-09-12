#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

RELEASE_KEY=RWReleaseOpenGrindurRQcmR+NovOaU5IEU3LM5l6TcXJvOGYw2m4O+
RELEASE_CERT=2805FDD8F0BADB9424D3244C5E5B3473CEF5B8798EC1117382E89EDA45C3658C
ABIS=(arm64-v8a:arm64-v8a armeabi-v7a:v7a x86_64:x86_64)

tag=${1:-}
if [ -z "$tag" ]; then
	echo "usage: contrib/sign-release.sh <tag>" >&2
	echo "  OPEN_GRIND_MINISIGN_KEY overrides ~/.minisign/minisign.key" >&2
	echo "  OPEN_GRIND_PGP_KEY adds a detached .asc when set" >&2
	exit 2
fi

expected_version=${tag#v}
declared_version=$(grep -oE 'versionName = "[^"]+"' app/build.gradle.kts | cut -d'"' -f2)
if [ "$declared_version" != "$expected_version" ]; then
	echo "tag $tag does not match versionName $declared_version" >&2
	exit 1
fi

for binary in apksigner minisign; do
	if ! command -v "$binary" > /dev/null; then
		echo "$binary not found, run inside 'nix develop'" >&2
		exit 1
	fi
done

src=app/build/outputs/apk/release
out=app/build/outputs/release/$tag
rm -rf "$out"
mkdir -p "$out"

signing_certificate() {
	apksigner verify --print-certs "$1" |
		awk '/certificate SHA-256 digest/ { print toupper($NF); exit }'
}

for pair in "${ABIS[@]}"; do
	built=${pair%%:*}
	published=${pair##*:}
	apk="$src/app-$built-release.apk"
	if [ ! -f "$apk" ]; then
		echo "missing $apk" >&2
		echo "build with GRINDR_OAUTH_KEYSTORE_PROPERTIES set" >&2
		exit 1
	fi

	asset="$out/open-grind-google-oauth-$tag-$published.apk"
	cp "$apk" "$asset"

	fingerprint=$(signing_certificate "$asset")
	if [ "$fingerprint" != "$RELEASE_CERT" ]; then
		echo "$published is signed by $fingerprint, expected $RELEASE_CERT" >&2
		exit 1
	fi

	minisign -Sm "$asset" ${OPEN_GRIND_MINISIGN_KEY:+-s "$OPEN_GRIND_MINISIGN_KEY"}
	minisign -Vm "$asset" -P "$RELEASE_KEY" > /dev/null

	named=$(sed -n '3p' "$asset.minisig" | tr '\t' '\n' | sed -n 's/^file://p')
	if [ "$named" != "$(basename "$asset")" ]; then
		echo "signature names $named, not $(basename "$asset")" >&2
		exit 1
	fi

	if [ -n "${OPEN_GRIND_PGP_KEY:-}" ]; then
		gpg --armor --detach-sign --default-key "$OPEN_GRIND_PGP_KEY" "$asset"
	fi

	echo "signed $(basename "$asset")"
done

echo
echo "upload every file in $out"
ls -1 "$out"
