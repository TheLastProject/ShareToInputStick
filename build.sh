#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

### build.sh
### Reproducibly builds Share To InputStick

if [ -z "${ANDROID_SDK_ROOT:-}" ]; then
  echo "ANDROID_SDK_ROOT is not set, setting to $HOME/Android/Sdk";
  export ANDROID_SDK_ROOT=$HOME/Android/Sdk
fi

if [ -z "${JAVA_HOME:-}" ]; then
  echo "JAVA_HOME is not set, setting to Java 21"
  if [ -f "/etc/debian_version" ]; then
    echo "Debian-based distro, Java 21 is /usr/lib/jvm/java-21-openjdk-amd64"
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
  else
    echo "Not Debian-based, assuming Fedora and setting Java 21 as /usr/lib/jvm/java-21-openjdk"
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
  fi
fi

echo "Starting build"
./gradlew clean assembleRelease

echo "Build finished (unsigned)"
echo "Your build is at app/build/outputs/apk/release/app-release-unsigned.apk"

if [ -z "${KEYSTORE:-}" ]; then
  echo "KEYSTORE not set, skipping signing..."
else
  if [ -z "${KEYSTORE_ALIAS:-}" ]; then
    echo "KEYSTORE_ALIAS is not set, setting to key0"
    KEYSTORE_ALIAS=key0
  fi

  apksigner_version="$(ls -1 "$HOME/Android/Sdk/build-tools/" | tail -n 1)"
  cp app/build/outputs/apk/release/app-release-unsigned.apk app/build/outputs/apk/release/app-release.apk
  "$HOME/Android/Sdk/build-tools/$apksigner_version/apksigner" sign -v --ks "$KEYSTORE" --ks-key-alias "$KEYSTORE_ALIAS" app/build/outputs/apk/release/app-release.apk

  echo "Build finished (signed)"
  echo "Your build is at app/build/outputs/apk/release/app-release.apk"
fi

pushd app/build/outputs/apk/release/
sha256sum -- *.apk > SHA256SUMS
popd

echo "SHA256SUMS generated"
echo "Your SHA256SUMS is at app/build/outputs/apk/release/SHA256SUMS"
