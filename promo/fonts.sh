#!/usr/bin/env bash
# Fonts are not committed: Noto Sans SC from notofonts, DM Mono from the app.
set -e
cd "$(dirname "$0")"
mkdir -p site/fonts
for w in Regular Medium Bold; do
  curl -sL -o site/fonts/NotoSansSC-$w.otf "https://cdn.jsdelivr.net/gh/notofonts/noto-cjk@main/Sans/SubsetOTF/SC/NotoSansSC-$w.otf"
done
cp ../app/src/main/res/font/dm_mono.ttf ../app/src/main/res/font/dm_mono_medium.ttf site/fonts/
