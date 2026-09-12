#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

RELEASE_KEY=RWReleaseOpenGrindurRQcmR+NovOaU5IEU3LM5l6TcXJvOGYw2m4O+
RELEASE_CERT=2805FDD8F0BADB9424D3244C5E5B3473CEF5B8798EC1117382E89EDA45C3658C
ABIS=(arm64-v8a:arm64-v8a armeabi-v7a:v7a x86_64:x86_64)

tag=${1:-}
artifacts=${2:-app/build/outputs/apk/release}
if [ -z "$tag" ]; then
	echo "usage: contrib/sign-release.sh <tag> [artifacts-dir]" >&2
	echo "  artifacts-dir holds the apks, default app/build/outputs/apk/release" >&2
	echo "  GRINDR_OAUTH_KEYSTORE_PROPERTIES signs app-<abi>-release-unsigned.apk" >&2
	echo "  OPEN_GRIND_MINISIGN_KEY overrides ~/.minisign/minisign.key" >&2
	exit 2
fi

expected_version=${tag#v}
declared_version=$(grep -oE 'versionName = "[^"]+"' app/build.gradle.kts | cut -d'"' -f2)
if [ "$declared_version" != "$expected_version" ]; then
	echo "tag $tag does not match versionName $declared_version" >&2
	exit 1
fi

if [ ! -d "$artifacts" ]; then
	echo "no such directory: $artifacts" >&2
	exit 1
fi

for binary in apksigner minisign; do
	if ! command -v "$binary" > /dev/null; then
		echo "$binary not found, run inside 'nix develop'" >&2
		exit 1
	fi
done

untilde() { printf '%s' "${1/#\~/$HOME}"; }

store=""
key_alias=""

read_keystore() {
	local properties=${GRINDR_OAUTH_KEYSTORE_PROPERTIES:-}
	if [ -z "$properties" ]; then
		echo "GRINDR_OAUTH_KEYSTORE_PROPERTIES is not set, cannot sign unsigned apks" >&2
		echo "see contrib/keystore.properties.example" >&2
		exit 1
	fi
	properties=$(untilde "$properties")
	if [ ! -f "$properties" ]; then
		echo "GRINDR_OAUTH_KEYSTORE_PROPERTIES points at $properties, which does not exist" >&2
		exit 1
	fi
	local value
	value=$(sed -n 's/^[[:space:]]*storeFile[[:space:]]*=[[:space:]]*//p' "$properties" | head -1)
	store=$(untilde "$value")
	key_alias=$(sed -n 's/^[[:space:]]*keyAlias[[:space:]]*=[[:space:]]*//p' "$properties" | head -1)
	KEYSTORE_PASSWORD=$(sed -n 's/^[[:space:]]*password[[:space:]]*=[[:space:]]*//p' "$properties" | head -1)
	export KEYSTORE_PASSWORD
	if [ -z "$store" ] || [ -z "$key_alias" ] || [ -z "$KEYSTORE_PASSWORD" ]; then
		echo "$properties must set storeFile, keyAlias and password" >&2
		exit 1
	fi
	if [ ! -f "$store" ]; then
		echo "storeFile $store does not exist" >&2
		exit 1
	fi
}

signing_certificate() {
	apksigner verify --print-certs "$1" |
		awk '/certificate SHA-256 digest/ { print toupper($NF); exit }'
}

out=app/build/outputs/release/$tag
rm -rf "$out"
mkdir -p "$out"

for pair in "${ABIS[@]}"; do
	if [ -f "$artifacts/app-${pair%%:*}-release-unsigned.apk" ]; then
		read_keystore
		break
	fi
done

for pair in "${ABIS[@]}"; do
	built=${pair%%:*}
	published=${pair##*:}
	unsigned="$artifacts/app-$built-release-unsigned.apk"
	presigned="$artifacts/app-$built-release.apk"
	asset="$out/open-grind-google-oauth-$tag-$published.apk"

	if [ -f "$unsigned" ]; then
		apksigner sign \
			--alignment-preserved \
			--ks "$store" \
			--ks-key-alias "$key_alias" \
			--ks-pass env:KEYSTORE_PASSWORD \
			--key-pass env:KEYSTORE_PASSWORD \
			--out "$asset" \
			"$unsigned"
		rm -f "$asset.idsig"
	elif [ -f "$presigned" ]; then
		cp "$presigned" "$asset"
	else
		echo "missing $unsigned" >&2
		echo "download the apks from the build workflow, or build locally with GRINDR_OAUTH_KEYSTORE_PROPERTIES set" >&2
		exit 1
	fi

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

	echo "signed $(basename "$asset")"
done

echo
echo "upload every file in $out"
ls -1 "$out"
