# Releases 📦

This document contains information about all released versions of LiveWallpaper.

## Latest Release: v1.0

**Release Date**: April 6, 2026

### Downloads

| Version | Variant | Size | Download |
|---------|---------|------|----------|
| 1.0 | User | 50 MB | [livewallpaper-user-v1.0.apk](./livewallpaper-user-v1.0.apk) |
| 1.0 | Developer | 44 MB | [livewallpaper-dev-v1.0.apk](./livewallpaper-dev-v1.0.apk) |

### What's Included

#### User Version (livewallpaper-user-v1.0.apk)
- Clean, polished user interface
- Set wallpaper functionality
- Scene browsing and selection
- Motion control toggles (scroll and gyro)
- Time of day override feature
- Optimized for end-users

#### Developer Version (livewallpaper-dev-v1.0.apk)
- All user version features
- Full scene editor with real-time preview
- Sprite management and editing
- Texture upload and editing
- Name customization for sprites
- Delete and duplicate capabilities
- Advanced logging and debugging tools
- Perfect for creators and developers

### Features

✨ **Display Features**
- OpenGL ES rendering for smooth animations
- 60 FPS capable with efficient graphics pipeline
- Support for dynamic wallpaper changes

🎮 **Motion Controls**
- Scroll-based parallax effect
- Gyroscope-based motion tracking
- Configurable sensitivity
- Enable/disable individual motion types

⏰ **Time-Based Animation**
- Automatic scene transitions (Dawn → Day → Sunset → Night)
- Manual time override for testing
- 24-hour time tracking

🖼️ **Scene Management** (Dev version)
- Load custom scenes from files
- Create and edit sprite-based scenes
- Real-time preview while editing
- Save changes persistently

### System Requirements

- **Minimum Android**: 7.0 (API 24)
- **Target Android**: 15 (API 36)
- **RAM**: 512 MB minimum (1 GB recommended)
- **Free Storage**: 50 MB minimum
- **Processor**: ARMv7, ARM64, or x86 architecture

### Installation Instructions

1. **Enable Installation from Unknown Sources**
   - Go to Settings → Security (or Privacy in newer Android)
   - Enable "Unknown Sources" or "Install from Unknown Sources"

2. **Download the APK**
   - Choose User or Developer version
   - Click the download link above

3. **Install the APK**
   - Open file manager and navigate to Downloads folder
   - Tap the APK file to start installation
   - Grant permissions when prompted
   - Wait for installation to complete

4. **Set as Live Wallpaper**
   - Open Settings → Display (or Home screen settings)
   - Select "Wallpaper"
   - Choose "LiveWallpaper"
   - Select the app from the list
   - Tap "Set Wallpaper"

### Permissions

The app requires the following permissions:

| Permission | Purpose | Required For |
|------------|---------|--------------|
| SET_WALLPAPER | Set app as live wallpaper | All versions |
| READ_EXTERNAL_STORAGE | Load scene files | Dev version, custom scenes |
| WRITE_EXTERNAL_STORAGE | Save scenes and settings | Dev version |

**Note**: Permissions are only requested when needed on Android 6.0+

### Changelog

#### Version 1.0 - Initial Release
- ✨ Initial public release
- 🎨 OpenGL-powered animation engine
- 🎮 Dual motion control systems (scroll + gyro)
- 🎬 Scene management with sprite editing
- ⏰ Time-of-day based scene transitions
- 🔧 Developer edition with full editor
- 📊 Comprehensive logging system
- 🧪 Full unit test coverage

### Known Issues & Limitations

- **Signing**: Release APKs are unsigned for compatibility with various security configurations
- **Scenes**: Maximum of 100 sprites per scene recommended for optimal performance
- **Sensors**: Gyro motion requires a compatible accelerometer/gyroscope
- **Storage**: Some Android versions may require file permissions for custom scenes

### Recommendations

**For End-Users**: Download the **User Version**
- Cleaner interface
- Optimized performance
- Focused on wallpaper enjoyment

**For Developers/Creators**: Download the **Developer Version**
- Full editing capabilities
- Scene customization
- Advanced controls and logging

### Uninstallation

To uninstall LiveWallpaper:
1. Open Settings → Apps (or Application Manager)
2. Find and tap "LiveWallpaper"
3. Tap "Uninstall"
4. Confirm the action

Or use ADB:
```bash
adb uninstall com.example.livewallpaper     # User version
adb uninstall com.example.livewallpaper.dev # Developer version
```

### Bug Reports & Support

If you encounter issues:

1. **Check the Troubleshooting Guide** in [README.md](../README.md)
2. **Collect Debug Info**:
   ```bash
   adb logcat > livewallpaper_logs.txt
   ```
3. **Report the Issue** with:
   - Device model and Android version
   - Steps to reproduce
   - Log output
   - Screenshots if applicable

### Building from Source

To build these APKs yourself:

```bash
# Clone repository
git clone <repository-url>
cd livewallpaper

# Build dev and user release variants
./gradlew assembleDevRelease assembleUserRelease

# APKs will be in: app/build/outputs/apk/{dev,user}/release/
```

See [README.md](../README.md) for complete build instructions.

### Update Policy

- New versions will be released periodically with bug fixes and features
- Each release will be tested on multiple devices and Android versions
- Breaking changes will be communicated in advance

### Feedback

We'd love to hear your feedback:
- 🐛 Report bugs
- ✨ Suggest features
- 💭 Share your creations
- ⭐ Star the repository

---

**Enjoy your LiveWallpaper! 🎨**

*Last Updated: April 6, 2026*

