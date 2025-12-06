# Build Notes for Android 16 Compatibility

## Changes Made

This project has been upgraded to be compatible with Android 16 (API level 36). The following changes were made:

### Gradle Configuration

1. **Updated Gradle Wrapper** to version 7.6 (from 5.4.1)
   - Location: `gradle/wrapper/gradle-wrapper.properties`

2. **Updated Android Gradle Plugin** to version 7.4.2 (from 3.5.4)
   - Location: `build.gradle`
   - Requires access to Google's Maven repository (dl.google.com)

3. **Updated Repositories**
   - Removed deprecated `jcenter()` repository
   - Using `mavenCentral()` and Google Maven (`maven.google.com`)

### Android SDK Configuration

Updated in `app/build.gradle`:
- **compileSdkVersion**: 36 (from 30) - Android 16
- **targetSdkVersion**: 36 (from 30) - Android 16
- **buildToolsVersion**: '36.0.0' (from '28.0.3')
- **minSdkVersion**: 21 (from 9) - Increased for AndroidX compatibility
- **namespace**: Added 'com.github.axet.mover' (required for AGP 7.0+)

### Dependencies

1. **Migrated to AndroidX**:
   - Replaced `com.android.support:design:25.3.1` with `com.google.android.material:material:1.12.0`
   - Updated JUnit from 4.12 to 4.13.2
   - Enabled AndroidX and Jetifier in `gradle.properties`

2. **Gradle Properties** (`gradle.properties`):
   - Added `android.useAndroidX=true`
   - Added `android.enableJetifier=true` (auto-converts old support libraries)

### AndroidManifest.xml Updates

1. **Removed package attribute** - Now defined as namespace in build.gradle
2. **Added android:exported attributes** for all components with intent-filters (required for API 31+):
   - MainActivity: `android:exported="true"` (LAUNCHER activity)
   - OnBootReceiver: `android:exported="true"` (receives broadcast)
   - OnUpgradeReceiver: `android:exported="true"` (receives broadcast)
   - OnExternalReceiver: `android:exported="true"` (receives broadcast)
   - SettingsActivity: `android:exported="false"` (internal only)
   - MoverService: `android:exported="false"` (internal only)

3. **Added Foreground Service Type**:
   - Added permission: `FOREGROUND_SERVICE_DATA_SYNC`
   - Added `android:foregroundServiceType="dataSync"` to MoverService (required for API 34+)

## Build Requirements

### Required for Building

1. **Internet Access**: Access to the following domains is required to download dependencies:
   - `dl.google.com` - Google's Maven repository (Android Gradle Plugin and Android dependencies)
   - `repo1.maven.org` - Maven Central (other dependencies)
   - `services.gradle.org` - Gradle distributions

2. **Android SDK**: 
   - Android SDK Platform 36 (Android 16)
   - Android SDK Build-Tools 36.0.0 or higher

3. **Java**: JDK 11 or higher (required for Gradle 7.6)

### Building the App

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing configuration)
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug
```

## Known Issues

### Current Build Blocker

The build currently fails due to inability to access `dl.google.com`, which is required to download the Android Gradle Plugin. Error:
```
Could not GET 'https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/7.4.2/gradle-7.4.2.pom'.
> dl.google.com: No address associated with hostname
```

**Resolution**: This domain must be allowlisted in the network configuration to proceed with the build.

## Compatibility Notes

- **Minimum Android Version**: Android 5.0 (API 21)
- **Target Android Version**: Android 16 (API 36)
- **AndroidX**: Fully migrated with Jetifier enabled for legacy library compatibility
- **Foreground Services**: Properly configured with data sync service type for Android 14+ compliance

## Testing Recommendations

After successfully building:
1. Test on Android 16 (API 36) device/emulator
2. Test on minimum supported version (API 21)
3. Verify foreground service functionality
4. Test storage permissions on Android 11+ (scoped storage)
5. Verify all broadcast receivers work correctly
