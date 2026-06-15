# Pocket Automator

Android app that records and replays user interactions via the accessibility tree. Built for capturing structured automation training data from real app flows.

Companion to **[Android Skill Router](https://huggingface.co/spaces/build-small-hackathon/android-skill-router)** — the on-device half of the classify → bind → replay stack.

## Features

- **Record** taps, text input, and scrolls while you use any app
- **Replay** saved recordings with smart node resolution (WhatsApp, LinkedIn, and more)
- **Parameterize** replay via intent JSON or a parameter dialog — substitute runtime values (contact, message, query) into recorded steps before replay
- **Export** recordings as JSON for training pipelines
- **Floating overlay** controls while recording or replaying

## Related repos

| Repo | Role |
| --- | --- |
| **[Android Skill Router (Space)](https://huggingface.co/spaces/build-small-hackathon/android-skill-router)** | Gradio demo — 3B intent classifier + parameterized trajectory preview |
| **[android-dataset](https://github.com/kriyanshii/android-dataset)** | Classifier training, `skill_schemas.json` bindings, Modal `/predict` API |
| **[Blog post](https://huggingface.co/blog/build-small-hackathon/android-skill-router)** | Architecture write-up |

**End-to-end flow:**

```
Modal /predict (or pasted JSON) → parameter dialog → ParameterBinder → replay → device taps
```

## Requirements

- Android 10+ (API 29)
- Android Studio Hedgehog or newer
- JDK 17

## Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/kriyanshii/pocket-automator.git
   cd pocket-automator
   ```

2. Create `local.properties` with your Android SDK path and API endpoint (or copy the example):
   ```bash
   cp local.properties.example local.properties
   # Edit sdk.dir to point to your SDK
   # Edit api.base.url to point to your skill prediction API (must end with /)
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
6. Replay from the overlay — paste intent JSON from [Android Skill Router](https://huggingface.co/spaces/build-small-hackathon/android-skill-router) or enter parameters in the dialog.
7. Export recordings from the main screen for the training pipeline in `android-dataset`.

## Project structure

```
app/src/main/java/com/pocketautomator/
├── core/       # Recording, replay, ParameterBinder, and node-matching logic
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
