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
