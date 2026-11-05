#!/bin/sh
# Fetches sherpa-onnx's Android library (Tokkip's voice) into app/libs/.
# It's 50 MB, so it stays out of git; CI runs this before building.
set -e
V=1.13.8
cd "$(dirname "$0")/../app"
mkdir -p libs
[ -f "libs/sherpa-onnx-$V.aar" ] || curl -fsSL -o "libs/sherpa-onnx-$V.aar" "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$V/sherpa-onnx-$V.aar"
