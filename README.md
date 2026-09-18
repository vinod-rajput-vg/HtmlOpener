# HTML Opener for Android TV

A simple Android TV application that:
- Opens HTML files using Android's system file picker.
- Detects installed browser applications.
- Lets the user choose a default browser.
- Saves the browser preference.
- Supports TV remote / D-pad navigation.
- Attempts direct content URI opening and has a local HTTP fallback.

## Build

```powershell
.\gradlew clean
.\gradlew assembleDebug
```

APK:
`app/build/outputs/apk/debug/app-debug.apk`

## Install

```powershell
adb devices
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```
