# Mahdi Cali School - Android Management System (ERP)

[![CI & Android Build](https://github.com/YOUR_GITHUB_USERNAME/YOUR_REPOSITORY_NAME/actions/workflows/ci.yml/badge.svg)](https://github.com/YOUR_GITHUB_USERNAME/YOUR_REPOSITORY_NAME/actions/workflows/ci.yml)
![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4?logo=jetpackcompose&logoColor=white)
![Min SDK](https://img.shields.io/badge/Min%20SDK-24-blue)
![Target SDK](https://img.shields.io/badge/Target%20SDK-36-brightgreen)

An offline-first Android ERP and School Management System designed for **Mahdi Cali School**. Built with modern Android development practices using **Kotlin**, **Jetpack Compose (Material 3)**, **Room Database**, and hybrid **Google Drive & Firebase Cloud Synchronization**.

---

## 📱 Features

- **🎓 Student Information System (SIS)**: Auto-generated student IDs, enrollment tracking, parent/guardian records, and student profiles.
- **📜 Academic Marksheets & Warqadda Ardayga**:
  - Official Primary School Academic Records for **Classes 1 – 4 (Fasallada 1aad – 4aad)**.
  - 4-box annual progress sheets with automatic calculation across 7 standard subjects (700 marks total).
  - Clearance certificates and printable HTML/PDF report cards.
- **💰 Multi-Currency Fee Management**: Track tuition and school fees across USD ($), Somaliland Shillings (SLS), and Ethiopian Birr (ETB) with receipts and ledger history.
- **✅ Attendance Tracking**: Daily attendance logging (Present, Late, Absent) per class with monthly statistics.
- **📝 Examination & Grading**: Term 1 & Term 2 exam record entry, subject configurations, automatic rankings, and annual student promotions.
- **👥 Multi-Role User Access**: Tailored role-based portals for Administrator, Teachers, Cashier/Accountant, Parents, and Students.
- **☁️ Cloud & Local Backup**:
  - Google Drive Storage Access Framework (SAF) direct JSON export and import.
  - Automated Google Apps Script Web App backup engine.
  - Firebase Realtime Database & Firestore cloud sync for multi-device sync and disaster recovery.
- **⚡ 100% Offline-First Architecture**: Operates with full functionality without an active internet connection using a local Room SQLite database, synchronizing whenever internet connectivity is restored.

---

## 🛠️ Technology Stack & Architecture

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material Design 3 (M3)
- **Architecture**: MVVM (Model-View-ViewModel) + Clean Architecture patterns
- **Local Persistence**: Room SQLite Database (KSP symbol processing)
- **State Management**: Kotlin Coroutines, `StateFlow`, `collectAsStateWithLifecycle`
- **Cloud & Networking**: OkHttp3, Retrofit, Firebase SDK, Google Drive SAF
- **Unit & UI Testing**: JUnit 4, Robolectric (JVM-based testing), Roborazzi
- **Build System**: Gradle 9.3.1 (Kotlin DSL), Version Catalog (`libs.versions.toml`)

---

## 📂 Project Structure

```
├── .github/
│   └── workflows/
│       ├── ci.yml                 # Continuous Integration & Automated APK Release
│       └── build-apk.yml          # Standalone debug build workflow
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/
│   │   │   │   ├── MainActivity.kt        # Compose Navigation host and entry point
│   │   │   │   ├── SchoolApplication.kt   # Application class & crash reporting
│   │   │   │   ├── data/                  # Entities, DAOs, Room DB, CloudSync, GoogleDriveSync
│   │   │   │   └── ui/                    # ViewModels, Screens, Components, Theme
│   │   │   └── res/                       # Drawables (School Logo), Mipmap icons, Values
│   │   └── test/                          # Unit and Robolectric JVM test suite
│   ├── build.gradle.kts                   # Module-level Gradle configuration
│   └── proguard-rules.pro                 # ProGuard / R8 optimization rules
├── gradle/
│   └── libs.versions.toml                 # Version Catalog for dependencies
├── .env.example                           # Template environment configuration
├── .gitignore                             # Git ignore rules protecting keys and secrets
├── build.gradle.kts                       # Root project Gradle configuration
└── settings.gradle.kts                    # Project naming and repository settings
```

---

## 🚀 Getting Started

### Prerequisites

- **Java Development Kit (JDK)**: JDK 17 or JDK 21 (Temurin recommended)
- **Android Studio**: Ladybug (2024.2.1) or newer
- **Android SDK**: Compile SDK 36, Min SDK 24

### 1. Clone the Repository

```bash
git clone https://github.com/YOUR_GITHUB_USERNAME/YOUR_REPOSITORY_NAME.git
cd YOUR_REPOSITORY_NAME
```

### 2. Configure Environment Variables

Duplicate `.env.example` to create your local `.env` file:

```bash
cp .env.example .env
```

Edit `.env` to supply any optional credentials (e.g., `GEMINI_API_KEY`, `DEFAULT_SCHOOL_ID`). Note: `.env` is ignored by Git and will never be pushed.

### 3. Open in Android Studio

1. Open Android Studio.
2. Select **File > Open...** and navigate to the project directory.
3. Allow Gradle to sync dependencies automatically.

---

## 🧪 Testing & Building

### Run Unit & Robolectric Tests

You can execute all local unit and Robolectric tests without needing a physical device or emulator:

```bash
gradle :app:testDebugUnitTest
```

### Build Debug APK

```bash
gradle :app:assembleDebug
```

The compiled APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Build Release APK / App Bundle

```bash
gradle :app:assembleRelease
# or for Google Play AAB:
gradle :app:bundleRelease
```

---

## 🤖 Continuous Integration & GitHub Actions

This repository includes a production-ready GitHub Actions workflow (`.github/workflows/ci.yml`):

- **Triggers**:
  - Automatically runs on any **push** to `main`.
  - Automatically runs on **pull requests** targeting `main`.
  - Can be triggered **manually** via the GitHub Actions tab (`workflow_dispatch`).
- **Steps**:
  1. Sets up JDK 21 and Gradle 9.3.1 with automated dependency caching.
  2. Generates a temporary debug keystore for build validation.
  3. Executes all unit and Robolectric test suites.
  4. Builds the debug APK and verifies the generated binary.
  5. Publishes the APK as a workflow artifact.
  6. **Automated Deployment**: When pushed to `main`, it automatically creates a new **GitHub Release** and attaches the compiled `.apk` file for direct download.

---

## 🔒 Security & Best Practices

- **Never Commit Secrets**: Passwords, `.env` files, keystores (`*.keystore`, `*.jks`), and sensitive certificates are explicitly ignored in `.gitignore`.
- **API Keys**: Configure sensitive keys using GitHub Repository Secrets (e.g., `GEMINI_API_KEY`) if needed in CI/CD.

---

## 📄 License

This software is developed for **Mahdi Cali School**. All rights reserved.
