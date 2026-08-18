# Play Store Readiness Plan

Prepare the "Bharat Kirana" app for production release on the Google Play Store. This includes technical optimizations, security configurations, and ensuring all metadata meets Google's requirements.

## User Review Required

> [!IMPORTANT]
> **Release Keystore**: You must provide a valid release keystore and its credentials. I have configured the build script to use environment variables for security, but you will need to set these up in your build environment.
> **Privacy Policy URL**: Google Play requires a public URL to your privacy policy. While the app has a `PrivacyPolicyScreen`, you must also host this content on a website.
> **Firebase Configuration**: Ensure you have downloaded the production `google-services.json` from the Firebase Console and placed it in the `app/` directory.

## Proposed Changes

### Build Configuration

#### [MODIFY] [build.gradle.kts](file:///Users/bharatjain/Downloads/bharat-kirana%20(1)/app/build.gradle.kts)
- Enable R8 minification and resource shrinking for the `release` build type.
- Enable PNG crunching for optimized assets.

### Manifest & Permissions

#### [MODIFY] [AndroidManifest.xml](file:///Users/bharatjain/Downloads/bharat-kirana%20(1)/app/src/main/AndroidManifest.xml)
- Review and verify that `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` are essential for the app's core functionality to avoid potential rejection or extra scrutiny.

### ProGuard Rules

#### [MODIFY] [proguard-rules.pro](file:///Users/bharatjain/Downloads/bharat-kirana%20(1)/app/proguard-rules.pro)
- Add basic rules for common libraries used in the project (Retrofit, Moshi, Room, Compose) to ensure they work correctly with R8 minification.

---

## Verification Plan

### Automated Tests
- Run `./gradlew assembleRelease` to verify that the release build completes without errors.
- Run `./gradlew bundleRelease` to generate the App Bundle (.aab) for Play Store upload.

### Manual Verification
- Install the release APK on a device to ensure that R8 minification hasn't broken any functionality (especially JSON parsing and database operations).
- Verify the app icon and label appear correctly on the launcher.
