# Laterbox — CI/CD Architecture & Automated Build Specification

This document details the automated continuous integration and continuous delivery (CI/CD) strategy for Laterbox using **GitHub Actions**.

---

## 1. Design Principles

1. **Deterministic & Hermetic**: Every build runs on clean Ubuntu runners (`ubuntu-latest`) using pinned JDK and Android Gradle tools.
2. **Zero In-Repo Credentials**: All signing keys, passwords, and tokens are injected via GitHub Actions Secrets at runtime and never committed to version control.
3. **Fail-Fast Quality Gates**: Code formatting, Android Lint, unit tests, and build checks execute automatically on every push and pull request.
4. **Downloadable Artifacts**: Every successful CI run produces a testable debug APK attached to the workflow run.
5. **Milestone Activation**: The workflows will be activated in `.github/workflows/` during **Milestone 1** as soon as the Gradle wrapper and root `build.gradle.kts` are created. (Activating them before Gradle wrapper files exist would cause immediate build failures on an empty repository).

---

## 2. Continuous Integration Workflow (`ci.yml`)

### Triggers
- Pushes to branch `main`.
- Pull requests targeting `main`.
- Manual trigger via `workflow_dispatch`.

### Pipeline Stages
1. **Checkout**: Checks out source code using `actions/checkout@v4`.
2. **JDK Setup**: Sets up OpenJDK 17 with Temurin distribution via `actions/setup-java@v4` with automatic Gradle dependency caching (`cache: 'gradle'`).
3. **Gradle Validation**: Validates the Gradle Wrapper checksum via `gradle/actions/wrapper-validation@v4` to prevent wrapper tampering.
4. **Lint Verification**: Executes `./gradlew lintDebug` to catch Android API misuse, layout issues, and accessibility warnings.
5. **Unit Tests**: Runs `./gradlew testDebugUnitTest` (executing Room database tests, URL normalizer tests, Jsoup metadata parser tests, and Robolectric Compose UI tests).
6. **Debug Build**: Executes `./gradlew assembleDebug` to compile `app-debug.apk`.
7. **Artifact Archive**: Uploads the debug APK using `actions/upload-artifact@v4` with a 14-day retention period.

### Target Workflow Specification (`.github/workflows/ci.yml`)
```yaml
name: Laterbox CI

on:
  push:
    branches: [ "main" ]
  pull_request:
    branches: [ "main" ]
  workflow_dispatch:

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build:
    name: Build & Verify (Debug)
    runs-on: ubuntu-latest
    timeout-minutes: 25

    steps:
      - name: Checkout Code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
          cache: 'gradle'

      - name: Validate Gradle Wrapper
        uses: gradle/actions/wrapper-validation@v4

      - name: Grant Execute Permission for Gradlew
        run: chmod +x gradlew

      - name: Run Android Lint
        run: ./gradlew lintDebug

      - name: Run Unit Tests
        run: ./gradlew testDebugUnitTest

      - name: Build Debug APK
        run: ./gradlew assembleDebug

      - name: Upload Test Reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: app/build/reports/tests/testDebugUnitTest/
          retention-days: 7

      - name: Upload Debug APK
        uses: actions/upload-artifact@v4
        with:
          name: laterbox-debug-apk
          path: app/build/outputs/apk/debug/*.apk
          retention-days: 14
```

---

## 3. Release & Distribution Workflow (`release.yml`)

### Triggers
- Git tag pushes matching the pattern `v*` (e.g., `v1.0.0`, `v1.0.1`, `v1.1.0`).
- Manual trigger via `workflow_dispatch` with a release version input.

### Security & Release Signing
In a public repository, signing credentials must never touch version control. We use the following GitHub Actions Secrets:
- `KEYSTORE_BASE64`: The PKCS12/JKS release keystore file, base64-encoded.
- `KEYSTORE_PASSWORD`: The keystore password.
- `KEY_ALIAS`: The signing key alias.
- `KEY_PASSWORD`: The key password.

At build time:
1. The runner decodes `KEYSTORE_BASE64` to a temporary runner file `/tmp/keystore.jks`.
2. Gradle builds `./gradlew assembleRelease` using environment variables passed from secrets.
3. The keystore is immediately removed from `/tmp` upon build completion.
4. The signed APK (`app-release.apk`) is attached to a new GitHub Release created via `softprops/action-gh-release@v2`.

### Target Workflow Specification (`.github/workflows/release.yml`)
```yaml
name: Laterbox Release

on:
  push:
    tags:
      - 'v*'
  workflow_dispatch:
    inputs:
      tag_name:
        description: 'Release tag (e.g. v1.0.0)'
        required: true

jobs:
  release:
    name: Build & Publish Release APK
    runs-on: ubuntu-latest
    timeout-minutes: 30

    steps:
      - name: Checkout Code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
          cache: 'gradle'

      - name: Validate Gradle Wrapper
        uses: gradle/actions/wrapper-validation@v4

      - name: Grant Execute Permission for Gradlew
        run: chmod +x gradlew

      - name: Run Full Test Suite
        run: ./gradlew testDebugUnitTest

      - name: Decode Release Keystore
        if: env.HAS_SECRETS == 'true'
        env:
          HAS_SECRETS: ${{ secrets.KEYSTORE_BASE64 != '' }}
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
        run: |
          echo "$KEYSTORE_BASE64" | base64 --decode > /tmp/release.keystore

      - name: Build Release APK
        env:
          SIGNING_STORE_FILE: /tmp/release.keystore
          SIGNING_STORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          SIGNING_KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          SIGNING_KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: ./gradlew assembleRelease

      - name: Clean Keystore Temp File
        if: always()
        run: rm -f /tmp/release.keystore

      - name: Create GitHub Release and Attach APK
        uses: softprops/action-gh-release@v2
        with:
          name: "Laterbox ${{ github.ref_name }}"
          generate_release_notes: true
          files: app/build/outputs/apk/release/*.apk
```

---

## 4. Activation Milestone

These workflows will be committed directly to `.github/workflows/` in **Milestone 1 (Foundation)** immediately after initializing the Gradle wrapper and project configuration files.
