# LiveWallpaper 🎨

A sophisticated Android live wallpaper application that brings dynamic, interactive backgrounds to your device. This app features OpenGL-powered animations, sensor-based motion controls, and customizable scenes with sprite editing capabilities.

## Features 🌟

### Core Features
- **Live Wallpaper Integration**: Set as your device's live wallpaper through system settings
- **OpenGL Rendering**: GPU-accelerated graphics for smooth, high-performance animations
- **Sprite Management**: Edit and customize sprite-based scenes with a user-friendly interface
- **Scene System**: Load, save, and manage multiple wallpaper scenes
- **Time-Based Rendering**: Automatic scene transitions based on time of day (Dawn, Day, Sunset, Night)
- **Persistent Configuration**: Scenes and settings are saved and restored across app restarts

### Motion Controls
- **Scroll Motion**: Enable/disable parallax effects triggered by device scrolling
- **Gyro Motion**: Utilize device gyroscope for immersive motion parallax effects
- **Time of Day Override**: Manually override the time of day for testing different scene variations

### Developer Features (Dev Version)
- **Scene Editor**: Full-featured UI for editing wallpaper scenes in real-time
- **Sprite Editor**: Add, modify, and delete sprites with texture editing capabilities
- **Logging System**: Comprehensive debug logging using Timber and OrhanObut Logger
- **Live Preview**: See changes immediately in the preview window
- **File Management**: Browse and manage scene files with URI-based file operations

## Download 📲

You can download the pre-built APK files from the [Releases](./releases/) folder:

- **[livewallpaper-user-v1.0.apk](./releases/livewallpaper-user-v1.0.apk)** - Standard user version
  - Clean UI focused on enjoying the wallpaper
  - Smaller installation footprint
  - Perfect for end-users

- **[livewallpaper-dev-v1.0.apk](./releases/livewallpaper-dev-v1.0.apk)** - Developer/Editor version
  - Full scene and sprite editing capabilities
  - Debugging tools and logging
  - Perfect for customization and development
  - Size: ~44 MB

### Installation Steps
1. Enable "Unknown Sources" in your device's security settings
2. Download the desired APK file from the releases folder
3. Open the APK file to install
4. Grant the required permissions when prompted
5. Open the app and set it as your live wallpaper through Settings → Display → Wallpaper

## Requirements 📋

### Minimum Requirements
- **Android**: 7.0 (API level 24)
- **Target Android**: 15 (API level 36)
- **RAM**: 512 MB minimum (1 GB recommended)
- **Storage**: ~50 MB free space

### Permissions Required
- `SET_WALLPAPER`: To set the app as live wallpaper
- `READ_EXTERNAL_STORAGE`: To load custom scene files (Android 12 and below)
- `WRITE_EXTERNAL_STORAGE`: To save scenes and settings (Android 12 and below)

## Usage Guide 📖

### User Version
1. **Set as Wallpaper**: Tap the "Set as Wallpaper" button
2. **View Scenes**: Browse available wallpaper scenes
3. **Reload Scenes**: Refresh the scene list
4. **Motion Settings**: Toggle scroll and gyro motion effects
5. **Time Override**: Manually select specific times of day

### Developer Version
Everything in the User version, plus:

1. **Edit Scenes**: 
   - Tap "View Scenes" and select a scene
   - Add or remove sprites
   - Edit sprite properties (position, rotation, texture)

2. **Manage Sprites**:
   - Long-press on sprite options for more actions
   - Edit sprite name and texture
   - Delete unwanted sprites

3. **Save Changes**: 
   - Changes are saved immediately to persistent storage
   - Confirm save when prompted

4. **Preview**: 
   - See real-time changes in the wallpaper preview
   - Toggle motion effects to test interactivity

## Project Structure 🏗️

```
livewallpaper/
├── app/
│   ├── src/
│   │   ├── main/                    # Shared source code
│   │   │   ├── java/com/example/livewallpaper/
│   │   │   │   ├── gl/              # OpenGL rendering engine
│   │   │   │   ├── sensors/         # Motion sensor processing
│   │   │   │   ├── scene/           # Scene management
│   │   │   │   ├── managers/        # File and configuration managers
│   │   │   │   ├── logging/         # Logging utilities
│   │   │   │   └── ui/              # Shared UI components
│   │   │   └── res/                 # Resources (layouts, strings, etc.)
│   │   ├── dev/                     # Developer-specific code
│   │   │   └── java/com/hashilab/dev/editor/
│   │   │       ├── activities/      # Editor activities
│   │   │       ├── adapters/        # List adapters
│   │   │       ├── builders/        # UI builders
│   │   │       ├── controllers/     # Business logic
│   │   │       ├── utils/           # Utility functions
│   │   │       └── views/           # Custom views
│   │   └── user/                    # User-specific code
│   │       └── java/com/example/livewallpaper/ui/
│   ├── build.gradle.kts             # App-level build configuration
│   └── proguard-rules.pro            # Obfuscation rules
├── build.gradle.kts                 # Root build configuration
├── settings.gradle.kts              # Gradle settings
├── gradle.properties                # Gradle properties
├── releases/                        # Pre-built APK files
├── README.md                        # This file
└── gradle/wrapper/                  # Gradle wrapper
```

## Technical Stack 🛠️

### Core Technologies
- **Language**: Java & Kotlin
- **UI Framework**: Jetpack Compose (for future UI updates)
- **Graphics**: OpenGL ES
- **Build System**: Gradle with Kotlin DSL
- **Android SDK**: API 24-36

### Key Libraries
- **Jetpack Libraries**:
  - AndroidX Core, AppCompat, Activity, Lifecycle
  - Compose UI Framework
  - DocumentFile for URI-based file operations
  - CardView for modern layouts

- **Utilities**:
  - **Timber** (v5.0.1): Logging framework
  - **OrhanObut Logger** (v2.2.0): Advanced logging
  - **GSON** (v2.10.1): JSON serialization
  - **Robolectric** (v4.11.1): Unit testing
  - **Mockito** (v5.2.0): Mocking framework

### Architecture Patterns
- **Product Flavors**: Separate dev and user builds with different features
- **Thread Safety**: Volatile fields and synchronized access for motion configuration
- **Dependency Injection**: Manual service initialization pattern
- **MVC Pattern**: Clear separation of models, views, and controllers

## Building from Source 🔨

### Prerequisites
- Android Studio (latest version recommended)
- Android SDK (API level 36)
- JDK 11 or higher
- Gradle 9.1.0 (included via wrapper)

### Build Steps

1. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd livewallpaper
   ```

2. **Build Debug APKs** (for testing):
   ```bash
   ./gradlew assembleDevDebug assembleUserDebug
   ```

3. **Build Release APKs** (for distribution):
   ```bash
   ./gradlew assembleDevRelease assembleUserRelease
   ```

4. **Run Tests**:
   ```bash
   ./gradlew testDebugUnitTest
   ```

5. **Run on Device/Emulator**:
   ```bash
   ./gradlew installDevDebug
   ./gradlew installUserDebug
   ```

The built APKs will be located in:
- Debug: `app/build/outputs/apk/{dev,user}/debug/`
- Release: `app/build/outputs/apk/{dev,user}/release/`

## Configuration 🔧

### Motion Configuration
Motion settings are stored in SharedPreferences and include:
- `scroll_motion_enabled`: Enable/disable scroll parallax (default: true)
- `gyro_motion_enabled`: Enable/disable gyro parallax (default: true)
- `time_of_day_override`: Current time override (default: AUTO)

### Scene Format
Scenes are stored as JSON files with sprite definitions, including:
- Sprite position and rotation
- Texture references
- Animation parameters
- Layer ordering

### Logging
- **Timber**: Main logging framework
- **Logger**: File-based logging for debugging
- **Log Tags**: Follow the pattern `com.example.livewallpaper.*`

## Troubleshooting 🔍

### App Crashes on Launch
- Clear app cache: Settings → Apps → LiveWallpaper → Storage → Clear Cache
- Reinstall the app
- Check device compatibility (minimum Android 7.0)

### Wallpaper Not Updating
- Force stop and restart the app
- Tap "Reload Scenes" button
- Check if motion sensors are working (gyro version)

### Motion Effects Not Working
- Verify motion is enabled in the app settings
- Check if your device has the required sensors (gyroscope for gyro motion)
- Ensure app has necessary permissions

### Performance Issues
- Disable motion effects to reduce CPU load
- Close other apps running in background
- Consider upgrading to device with better GPU

## Performance Optimization 📊

- **GPU-Accelerated Rendering**: OpenGL ES for smooth 60 FPS animations
- **Efficient Resource Loading**: Lazy loading of textures and scenes
- **Motion Sensor Batching**: Optimized sensor event processing
- **Memory Management**: Proper lifecycle management and resource cleanup

## Testing 🧪

The project includes comprehensive unit tests:

```bash
# Run all tests
./gradlew testDebugUnitTest

# Run specific test suite
./gradlew testDevDebugUnitTest
./gradlew testUserDebugUnitTest
```

Test coverage includes:
- Motion configuration (MotionConfigTest)
- Scene file management
- Sensor data processing
- UI component behavior

## Contributing 🤝

Contributions are welcome! Here's how you can help:

1. **Report Bugs**: Use the issues section with detailed description
2. **Suggest Features**: Propose improvements with use cases
3. **Submit Code**: Fork, create a branch, and submit a pull request
4. **Test**: Help test on various devices and Android versions

### Code Guidelines
- Follow Java/Kotlin coding conventions
- Add unit tests for new features
- Document complex logic with comments
- Use meaningful variable and method names
- Respect the existing architecture patterns

## License 📄

This project is provided as-is for personal and educational use. Please check the LICENSE file for more details.

## Changelog 📝

### Version 1.0
- Initial release
- OpenGL-based wallpaper rendering
- Sensor-based motion controls
- Scene and sprite management
- Dev and user build variants
- Comprehensive logging system

## Support 💬

For issues, questions, or feedback:
- Check existing GitHub issues
- Review troubleshooting section above
- Check app logs via adb: `adb logcat | grep livewallpaper`

## Future Roadmap 🚀

Planned features for future versions:
- Cloud scene synchronization
- More animation effects and transitions
- Scene sharing with community
- Performance profiling tools
- Extended customization options
- Support for custom music/audio triggers

---

**Enjoy your dynamic live wallpaper! 🎨**

For the latest updates and releases, visit the [GitHub repository](https://github.com/your-username/livewallpaper).

