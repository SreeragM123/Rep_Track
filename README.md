# Routine Track Android app

Routine Track is a native Android Studio starter project for a dark-mode workout and step tracker.

## Features
- Daily step goal: 8,000 steps
- Android hardware step-counter sensor with an ongoing foreground tracking notification
- Manual daily step correction when needed
- Saved full-body workout routine and per-exercise completion checkboxes
- Workout completion count and body-weight entries stored locally on the device
- Dark interface with Home, Workout, Steps, and Progress tabs
- No account, backend, or network connection required

## Build and install
1. Install Android Studio (current stable release) and Android SDK Platform 35.
2. Open this folder in Android Studio and allow Gradle sync to finish.
3. Connect an Android phone with USB debugging enabled, or use an emulator (step sensors may not be available in emulators).
4. Press Run. Grant Physical activity permission and notification permission when prompted.
5. On some phones, enable background/autostart permission for Routine Track or exclude it from battery optimization if the manufacturer stops background services.

To create an APK: **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

## Step counter notes
- Automatic step counting requires a device with `TYPE_STEP_COUNTER` hardware support and the Physical activity permission.
- On first use, the app establishes a sensor baseline; steps taken before the first sensor reading are not reconstructed.
- A persistent notification is used while the app counts steps in the background. Android and phone-vendor battery policies may still interrupt tracking.
- The sensor counter is a hardware estimate, not a medical device. Test on your own phone before relying on it.
- This project uses local SharedPreferences storage and is a starter release, not a Play Store-ready product. It does not yet sync across devices or export backups.

## Workout defaults
- Goblet squat — 3 × 12–15
- Dumbbell Romanian deadlift — 3 × 12–15
- Push-ups — 3 × 10–15
- Dumbbell floor chest press — 3 × 12–15
- Shoulder press — 3 × 12–15
- Bicep curl — 3 × 12–15
- Overhead triceps extension — 3 × 12–15
- Double crunches — 3 × 12–15
- Reverse crunch — 3 × 12–15
- Plank — 3 × 30–45 seconds

The default dumbbell display is 10 kg each where applicable. Data stays on the phone and can be cleared by clearing app storage or uninstalling.
