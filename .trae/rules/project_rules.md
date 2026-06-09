# Project Rules

## APK Installation
When installing APK to device, always use **overwrite install** (do NOT uninstall first):
```bash
adb install -r <apk_path>
```
This preserves the app's local data and avoids unnecessary reconfiguration.

## Build Command
```bash
./gradlew clean assembleRelease --no-daemon -x lintVitalAnalyzeRelease -x lintVitalRelease
```

## OCR Model Setup
Before first build, download PaddleOCR models:
```bash
./download_ocr_models.sh
```
Models are placed in `app/src/main/assets/models/ocr/`.
Total size: ~15MB (not tracked in git due to size).
