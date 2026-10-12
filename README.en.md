<p align="center">
  <img src="promo/site/shiye_mark.svg" alt="" width="96">
</p>

<h1 align="center">Shiye 拾野</h1>

<p align="center">
  Photograph the birds, flowers, plants and small animals you come across. Shiye cuts each one out as a sticker, tells you what it is, and keeps it in your own field guide.
</p>

<p align="center">
  <a href="https://github.com/Wenston-dev/nature-collect/releases/latest"><img src="https://img.shields.io/github/v/release/Wenston-dev/nature-collect?label=download&color=3f6b4f" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3f6b4f" alt="Android 8.0+">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-3f6b4f" alt="MIT"></a>
</p>

<p align="center">
  <a href="README.md">中文</a> · English
</p>

<p align="center">
  <img src="docs/screenshots/capture.jpg" width="23%" alt="Camera">
  <img src="docs/screenshots/identify.jpg" width="23%" alt="Identification">
  <img src="docs/screenshots/guide.jpg" width="23%" alt="Field guide">
  <img src="docs/screenshots/timeline.jpg" width="23%" alt="Species timeline">
</p>
<p align="center">
  <img src="docs/screenshots/map.jpg" width="23%" alt="Map">
  <img src="docs/screenshots/calendar.jpg" width="23%" alt="Calendar">
  <img src="docs/screenshots/board.jpg" width="23%" alt="Sticker board">
  <img src="docs/screenshots/card.jpg" width="23%" alt="Share card">
</p>

<p align="center">
  <a href="https://github.com/Wenston-dev/nature-collect/raw/main/promo/out/shiye_promo.mp4">Watch the promo video (45 s)</a>
</p>

A free, open source Android app. It does not need Google services. The screenshots show the Chinese UI; the app can be switched to English in Settings.

## Download

- **Latest version**: the [Releases page](https://github.com/Wenston-dev/nature-collect/releases/latest), file `shiye-vX.Y.Z.apk`
- **v1.0.0**: [shiye-v1.0.0.apk](https://github.com/Wenston-dev/nature-collect/releases/download/v1.0.0/shiye-v1.0.0.apk)

Copy the APK to your phone and open it. The first time, Android will ask you to allow installing apps from unknown sources.

- Requires Android 8.0 or later on a 64-bit ARM phone (nearly every phone from recent years).
- New versions install over the old one and keep your data.

## Features

- **Cut-out**: take a photo or pick one from the gallery, and the main subject is found automatically and turned into a sticker with a white border. If the cut-out is off, fix it by drawing a rough loop, tapping, or erasing edges, or keep the whole photo.
- **Identification**: after saving, the sticker is sent to an AI model, which returns a Chinese name, an English or scientific name, a category and a one-line description. Without a network or a key, captures wait in a queue and are identified later. If the guess is wrong, pick one of the alternatives or rename it yourself.
- **Field guide**: captures of the same species are grouped together, with the number of sightings and the date of the first one, and can be filtered by category. Categories can be added, renamed and removed, and the AI picks from your list. The whole guide can be exported as one long image.
- **Species timeline**: every sighting of a species in order, with the places you saw it.
- **Map**: stickers pinned where they were taken. The base map is AMap (for China) or OpenStreetMap (elsewhere).
- **Calendar**: each day's square shows what you collected that day.
- **Sticker boards**: several boards where stickers can be moved, scaled and rotated, with text, drawing and backgrounds, exported to the gallery.
- **Share card**: turns one capture into a specimen-style card to save or share.
- **Achievements**: unlocked as your species count grows in each nature category.
- **Also**: Chinese and English UI, backup export and import for moving to a new phone.

## Setting up identification

Identification uses any OpenAI-compatible Chat Completions API. DeepSeek is the default, and you need your own API key.

1. Sign up on the [DeepSeek platform](https://platform.deepseek.com/api_keys), add a small balance, and create an API key.
2. Paste the key when the app first opens, or later under Settings → Identification.

To use another provider, change the Base URL and model name in Settings → Identification, for example `https://api.openai.com/v1`. The model must accept image input.

The key is stored encrypted on the phone. Only the cut-out sticker is uploaded, never the original photo, and the cut-out itself runs on the phone.

## Building from source

You need JDK 17 and the Android SDK. Open the repository root in Android Studio, or run:

```bash
./gradlew assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/`.

## Publishing a release

1. In `app/build.gradle.kts`, increase `versionCode` by one and set `versionName`.
2. Push a tag with the same version:

   ```bash
   git tag v1.0.1
   git push origin v1.0.1
   ```

GitHub Actions builds the APK, creates the release on the Releases page and attaches the APK. The build fails if the tag and `versionName` don't match.

If pushing a tag isn't convenient, run the Android build workflow by hand on the Actions page with "release" checked. It tags the commit with the current `versionName` and publishes it.

### Signing key

Android only installs an update over an existing app if both are signed with the same key. CI reads the key from the repository secret `SIGNING_KEYSTORE_BASE64` (the keystore file as base64 text). Without the secret it falls back to a key kept in the Actions cache, which is deleted after 7 days without a build; later APKs could then no longer update installed apps, so set the secret before publishing. If you fork the project and publish your own builds, use your own key.

## Tech stack

| Part | Choice |
| --- | --- |
| UI | Kotlin + Jetpack Compose + CameraX |
| Cut-out | [EdgeTAM](https://github.com/facebookresearch/EdgeTAM) + [U²-Net](https://github.com/xuebinqin/U-2-Net) (ONNX Runtime, on the phone) |
| Identification | Any OpenAI-compatible API, DeepSeek by default |
| Map | MapLibre with AMap tiles / OpenFreeMap; place names from Nominatim |
| Storage | Room + local image files |

The EdgeTAM model in `app/src/main/assets/sam/` was exported from the official weights by `tools/export_edgetam_onnx.py`, keeping only the single-image segmentation part; its masks match the PyTorch original at an IoU of about 0.997. `u2netp.onnx` is the lightweight U²-Net saliency model, used to find the main subject before EdgeTAM segments it precisely.

`promo/` holds the source of the promo video; see [promo/README.md](promo/README.md) (in Chinese). The screenshots above are taken from the video.

## License

The code is released under the [MIT](LICENSE) license. The bundled EdgeTAM and U²-Net models are Apache-2.0 and the DM Mono font is under the SIL Open Font License. Photos in the promo video come from Wikimedia Commons; authors and licenses are listed in [promo/credits.txt](promo/credits.txt).
