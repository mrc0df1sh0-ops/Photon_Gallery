<p align="center">
  <img src="screenshots/logo.png" width="160" alt="Photon Gallery Logo" />
</p>

<h1 align="center">Photon Gallery</h1>

<p align="center">
  <a href="https://github.com/Bn5prS/Photon_Gallery/stargazers"><img src="https://img.shields.io/github/stars/Bn5prS/Photon_Gallery?style=flat&logo=github&color=blue" alt="GitHub Stars" /></a>
  <a href="https://github.com/Bn5prS/Photon_Gallery/releases"><img src="https://img.shields.io/github/downloads/Bn5prS/Photon_Gallery/total?style=flat&logo=github&color=green" alt="Downloads" /></a>
  <a href="https://github.com/Bn5prS/Photon_Gallery/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-Proprietary-red.svg?style=flat" alt="License" /></a>
  <a href="https://github.com/Bn5prS/Photon_Gallery"><img src="https://komarev.com/ghpvc/?username=Bn5prS&repo=Photon_Gallery&label=Views&color=blue" alt="Views" /></a>
  <a href="https://github.com/Bn5prS/Photon_Gallery"><img src="https://img.shields.io/github/repo-size/Bn5prS/Photon_Gallery?style=flat&color=orange" alt="Repo Size" /></a>
</p>

<p align="center">
  <strong>A high-performance Android gallery app built with 100% Jetpack Compose, Material 3 Expressive, and Coil 3.</strong><br>
  <strong>On-device AI search, biometric vault, and Telegram cloud backups — v2.0.0</strong>
</p>

<p align="center">
  <a href="https://github.com/Bn5prS/Photon_Gallery/releases/latest/download/app-release.apk"><img src="https://img.shields.io/badge/Download-Latest%20APK-3DDC84?style=flat&logo=android&logoColor=white" alt="Download Latest APK" /></a>
</p>

---

## Features

### 🖼️ Core Experience
- **Smooth Media Browsing**: Browse all your local photos and videos smoothly with highly responsive grids, fluid image loading, and fast interactions.
- **Material 3 Expressive Design**: Modern layout utilizing shape-morphing animations, emphasized variable font weights, and physics-based spring motions.
- **Rich Format Support**: Full support for GIFs, animated WebP, animated HEIF, and SVG files — all rendered natively via Coil 3 decoders.
- **Auto-Hiding Pill Dock**: Navigation toolbar that collapses dynamically on down-scroll and re-appears on minor up-scroll to maximize screen space.

### 🔍 Smart Search
- **Smart On-Device Search**: Secure, offline search powered by on-device MobileCLIP semantic models for natural-language prompt-based search. All inference runs 100% on-device via ONNX Runtime — your photos never leave your phone.
- **OCR Text Search**: Automatically indexes text found in photos using ML Kit OCR, so you can search for screenshots, documents, and signs by their content.

### ☁️ Cloud & Backup
- **Telegram Cloud Backup**: Synchronize selected media folders securely to your private Telegram chat using a personal Telegram userbot via TDLib (MTProto). Includes Wi-Fi only upload constraints, low-battery pausing, and location metadata stripping for enhanced privacy.

### 🔒 Privacy & Security
- **Private Space**: A biometric-protected vault (fingerprint / face unlock) to keep sensitive photos and videos hidden from the main gallery. Secured with AndroidX Biometric.

### 🛠️ Organization & Tools
- **Photo Map**: View your geotagged photos on an interactive OpenStreetMap, clustered by location. Tap a cluster to browse photos taken in that area.
- **Duplicate Cleaner**: Finds duplicate photos using perceptual hashing and lets you review and remove them in bulk to reclaim storage.
- **Collage Builder**: Select multiple photos to instantly create custom photo collages.
- **Stitch Tool**: Combine multiple images into a single stitched panorama.
- **Local Album Management**: Create folders directly in public device storage and manage a dedicated Recycle Bin next to standard folders.
- **Pinned Albums**: Pin your most-used albums to the top for quick access. Long-press any album to pin or unpin it.
- **Exclude Folders**: Hide unwanted folders from your main gallery view while keeping them accessible via search.

---

## Screenshots

| Home Screen | Albums | Image Viewer | Video Player |
| :---: | :---: | :---: | :---: |
| <img src="screenshots/01-home-screen.jpg" width="200" alt="Home Screen" /> | <img src="screenshots/02-album-screen.jpg" width="200" alt="Albums" /> | <img src="screenshots/03-image-viewer.jpg" width="200" alt="Image Viewer" /> | <img src="screenshots/04-video-player.jpg" width="200" alt="Video Player" /> |

| Cloud Backup | Smart Search | Collage Builder | Settings |
| :---: | :---: | :---: | :---: |
| <img src="screenshots/05-cloud-backup.jpg" width="200" alt="Cloud Backup" /> | <img src="screenshots/06-search.jpg" width="200" alt="Smart Search" /> | <img src="screenshots/07-collage.jpg" width="200" alt="Collage Builder" /> | <img src="screenshots/08-settings.jpg" width="200" alt="Settings" /> |

---

## Getting Started

### Prerequisites
- Android SDK 37 (compileSdk 37, minSdk 31 / Android 12+)
- Java JDK 11+
- Gradle 8.x+

### Build and Deploy
1. Clone the repository:
   ```bash
   git clone https://github.com/Bn5prS/Photon_Gallery.git
   ```
2. Compile and check build:
   ```bash
   ./gradlew compileDebugKotlin
   ```
3. Assemble the signed release APK:
   ```bash
   ./gradlew assembleRelease
   ```
4. Deploy the APK to your connected phone:
   ```bash
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```

---

## Privacy

Photon Gallery is designed with privacy as a core principle:

- **100% On-Device AI**: All smart search, OCR indexing, and image embeddings run entirely on your phone. No data is sent to any cloud AI service.
- **No Analytics or Telemetry**: The app contains zero tracking, crash reporting services, or usage analytics.
- **Telegram Backup is Opt-In**: Cloud backup only activates if you explicitly configure your own Telegram account. Media is sent to your private Saved Messages — not to any third-party server.
- **Location Stripping**: When uploading to Telegram, EXIF location metadata is automatically stripped to protect your location privacy.
- **Biometric Vault**: Private Space is protected by your device's biometric authentication. Vault files are stored separately and never appear in the main gallery.

---

## Contributing

Contributions are welcome! Here's how to get started:

1. **Fork** the repository
2. **Create a feature branch**: `git checkout -b feature/my-feature`
3. **Commit your changes**: `git commit -m "Add my feature"`
4. **Push to the branch**: `git push origin feature/my-feature`
5. **Open a Pull Request**

---

## License

This project is licensed under a **Proprietary Source-Available License** — you may view and study the code, but redistribution and commercial use are prohibited. See the [LICENSE](LICENSE) file for details.