# Bharat Kirana (Express Kirana Store Pickup) 🛒🇮🇳

**Bharat Kirana** is a modern Android application built with Kotlin, Jetpack Compose, Material Design 3, Room, and Supabase. It provides an express in-store grocery ordering and digital QR pickup workflow tailored for Indian local Kirana stores.

---

## 🔒 Secret & Environment Variable Management

To protect credentials and prevent sensitive keys from being committed to GitHub, this project uses the **Secrets Gradle Plugin** with `.env` and `.env.example`.

### How It Works:
1. **`.gitignore` Exclusion**: The `.env` file containing local keys, signing keystores (`*.jks`, `debug.keystore`), and `local.properties` are strictly ignored by Git and never committed to the repository.
2. **Template File (`.env.example`)**: Documents all environment variables needed by the application with default placeholders.
3. **Build Injection**: During Gradle builds, the Secrets Gradle Plugin injects variables from `.env` (or `.env.example` as fallback) into `com.kks.bharatkirana.BuildConfig` fields.

### Setting Up Secrets on a New Clone:

1. **Clone the repository:**
   ```bash
   git clone https://github.com/your-username/bharat-kirana.git
   cd bharat-kirana
   ```

2. **Create your `.env` file from the template:**
   ```bash
   cp .env.example .env
   ```

3. **Configure your keys in `.env`:**
   ```properties
   # Supabase Configuration
   SUPABASE_URL=https://your-project.supabase.co
   SUPABASE_ANON_KEY=your_supabase_publishable_anon_key

   # Optional Gemini AI API Key (if AI features are used)
   # GEMINI_API_KEY=your_gemini_api_key
   ```

4. **In AI Studio**:
   - You can also add or modify secrets directly in the **AI Studio Secrets panel**, which automatically injects them into the build environment.

### ⚠️ Security Notice:
- **Client-Side Keys**: The `SUPABASE_ANON_KEY` is a client-safe publishable key protected by Supabase Row-Level Security (RLS) policies.
- **Service Role Keys**: **NEVER** place Supabase `service_role` (admin) keys or private master keys in the Android client or `.env` file, as Android APKs can be decompiled.

---

## 🛠️ Tech Stack & Architecture

- **UI Framework**: Jetpack Compose & Material 3 (Dynamic palette, adaptive layouts)
- **Language**: Kotlin 2.x with Coroutines & StateFlow
- **Architecture**: MVVM (Model-View-ViewModel) + Repository Pattern
- **Cloud Backend**: Supabase (REST API for products/inventory/orders & Auth OTP)
- **Local Cache**: Room Database for offline-friendly shopping cart and order history
- **Image Loading**: Coil Compose
- **Testing**: Robolectric & Roborazzi screenshot verification

---

## 🚀 Building & Running

### Prerequisites:
- Android Studio Ladybug or later
- JDK 17 / 21
- Android SDK 36 (Minimum SDK 24 / Android 7.0+)

### Commands:
- **Assemble Debug APK:**
  ```bash
  ./gradlew assembleDebug
  ```
- **Run Unit & Robolectric Tests:**
  ```bash
  ./gradlew :app:testDebugUnitTest
  ```
- **Lint Check:**
  ```bash
  ./gradlew lint
  ```
