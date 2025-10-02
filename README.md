# HexaSyntax — Crash Detection MVP

**Swadeshi smartphone-only crash detection + SMS alert (MVP for SIH 2025 PPT submission)**

## Overview
This Android Kotlin app demonstrates a minimal working prototype:
- Detects high-impact events using smartphone accelerometer
- Shows a confirmation pop-up with 30s countdown
- Sends an SMS with Google Maps link of last known location to emergency contact

## Features (MVP)
- Accelerometer-based detection (threshold-based)
- Confirmation dialog (cancel within 30 seconds)
- SMS alert with Google Maps link (uses `SmsManager`)
- Simple UI to set emergency contact and enable detection
- Demo simulate button for presentations

## Requirements
- Android Studio Arctic Fox or newer
- Android device with SIM (for SMS testing). Emulator SMS delivery may not work.
- Minimum SDK 24

## How to build
1. Open this project in Android Studio.
2. Let Gradle sync and resolve dependencies.
3. Connect an Android device or choose an emulator.
4. Build & Run. Or Build -> Generate Signed Bundle / APK to create an APK.

## Permissions
App requests at runtime:
- ACCESS_FINE_LOCATION
- SEND_SMS

## Testing (demo)
- Install on a physical device.
- Enter emergency contact number (example +9198XXXXXXXX).
- Toggle "Enable Crash Detection".
- Press **Simulate Crash (for demo)** to show the confirmation dialog and auto-send SMS after 30s.

## Future Work
- TensorFlow Lite crash/no-crash filter
- Multi-channel alerts (WhatsApp, cloud push)
- Emergency service integration

## License
MIT

##Fixes Required in Installation of APK file
- when you are installing the app in the mobile it doesnt allow for notification hence follow the steps below
- Step1: Open Setting-> App-> Open the HexaSyntax_MVP
- Step2: Click on ⋮ and select **Allow Restricted Settings**
- Step3: Relaunch app.
