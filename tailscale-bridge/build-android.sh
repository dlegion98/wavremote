#!/usr/bin/env bash
# Builds app/libs/tsbridge.aar (Tailscale for WavRemote's built-in Tailscale) with gomobile.
# Needs Go, a JDK, the Android SDK (ANDROID_HOME) and NDK (ANDROID_NDK_HOME).
set -euo pipefail
cd "$(dirname "$0")"
go mod tidy
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest
go get golang.org/x/mobile/bind@latest
export PATH="$(go env GOPATH)/bin:$PATH"
gomobile init
mkdir -p ../app/libs
gomobile bind -target=android/arm64 -androidapi 26 -ldflags="-s -w" -o ../app/libs/tsbridge.aar ./tsbridge
ls -la ../app/libs/tsbridge.aar
