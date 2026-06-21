#!/usr/bin/env sh
set -eu

GRADLE_VERSION=8.10.2
GRADLE_USER_HOME="${GRADLE_USER_HOME:-"$HOME/.gradle"}"
DIST_DIR="$GRADLE_USER_HOME/wrapper/dists/gradle-$GRADLE_VERSION-bin"
GRADLE_HOME="$DIST_DIR/gradle-$GRADLE_VERSION"
GRADLE_ZIP="$DIST_DIR/gradle-$GRADLE_VERSION-bin.zip"
GRADLE_URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
    mkdir -p "$DIST_DIR"
    if [ ! -f "$GRADLE_ZIP" ]; then
        curl -L "$GRADLE_URL" -o "$GRADLE_ZIP"
    fi
    unzip -q -o "$GRADLE_ZIP" -d "$DIST_DIR"
fi

exec "$GRADLE_HOME/bin/gradle" "$@"
