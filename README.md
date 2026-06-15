# Pocket Automator

Android app that records and replays user interactions via the accessibility tree. Built for capturing structured automation training data from real app flows.

## Features

- **Record** taps, text input, and scrolls while you use any app
- **Replay** saved recordings with smart node resolution (WhatsApp, LinkedIn, and more)
- **Export** recordings as JSON for training pipelines
- **Floating overlay** controls while recording or replaying

## Requirements

- Android 10+ (API 29)
- Android Studio Hedgehog or newer
- JDK 17

## Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/YOUR_USERNAME/android-recorder.git
   cd android-recorder
   ```

2. Create `local.properties` with your Android SDK path (or copy the example):
   ```bash
   cp local.properties.example local.properties
   # Edit sdk.dir to point to your SDK
   ```

3. Open the project in Android Studio, or build from the command line:
   ```bash
   ./gradlew assembleDebug
   ```

4. Install on a device or emulator:
   ```bash
   ./gradlew installDebug
   ```

## Usage

1. Install and open **Pocket Automator**.
2. Enable the **Pocket Automator** accessibility service in system settings.
3. Grant overlay and notification permissions when prompted.
4. Tap **Record**, name your task, then interact with the target app.
5. Stop recording from the overlay or notification.
6. Replay or export recordings from the main screen.

## Project structure

```
app/src/main/java/com/pocketautomator/
├── core/       # Recording, replay, and node-matching logic
├── export/     # JSON export and training data formatting
├── model/      # Action and recording data models
├── service/    # Accessibility service
├── storage/    # Room database for saved recordings
└── ui/         # Compose UI and floating overlay
```

## Testing

```bash
./gradlew test
```

## License

MIT
