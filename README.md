# Laterbox

> *"Find something interesting. Save it. Get back to it later."*

Laterbox is a calm, local-first native Android application for saving and organising links encountered online—articles, YouTube videos, Reddit discussions, reference websites, tutorials, and inspiration.

Instead of leaving dozens of tabs open or scattering bookmarks across messaging apps and notes, Laterbox provides one quiet, thoughtfully crafted place to keep them.

---

## Product Philosophy & Design

Laterbox is conceived as a **personal digital library**, not a task manager, corporate dashboard, or social network. There are no overdue badges, streaks, or noisy notifications.

- **Warm Editorial Aesthetic**: Built on a warm ivory canvas (`#F8F7F3`) in light mode and deep charcoal (`#141414`) in dark mode, with crisp typography and subtle hairline borders instead of drop shadows.
- **Expressive Typography**: Pairs an editorial serif (**Newsreader**) for contemplative titles and empty states with a modern sans-serif (**Inter**) for dense UI legibility and a monospace face (**JetBrains Mono**) for metadata.
- **Adaptive Link Cards**: Seamlessly displays rich preview images when available, while treating text-only cards as first-class editorial citizens when sites lack Open Graph images.
- **Local-First & Private**: All data is stored on-device in a SQLite/Room database. No user accounts, no telemetry, no tracking SDKs, and no external metadata proxy servers.

---

## Technical Architecture

Laterbox is built as a 100% native Android application:

- **Language & Toolchain**: Kotlin 2.0 with Kotlin Symbol Processing (KSP).
- **UI Framework**: Jetpack Compose with custom Material 3 token-driven theming.
- **Local Persistence**: Room database with reactive Kotlin Coroutines and `Flow`.
- **User Preferences**: Jetpack DataStore (Preferences).
- **Metadata Extraction**: Jsoup for lightweight HTML `<head>` and Open Graph parsing with asynchronous, non-blocking execution.
- **Image Loading**: Coil Compose for memory-efficient favicon and thumbnail caching.
- **Sharing Integration**: Native Android `ACTION_SEND` and `ACTION_SEND_MULTIPLE` intent receiver (`ShareReceiverActivity`).

---

## Project Structure

When application development begins in Milestone 1, the repository will follow the standard Android Gradle project hierarchy:

```text
Laterbox/
├── app/                        # Android application module
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/laterbox/app/
│   │   │   │   ├── data/       # Room database, DAOs, repositories, DataStore preferences
│   │   │   │   ├── domain/     # Models, URL normalization, shared text parser
│   │   │   │   ├── media/      # Media engine: extraction adapters, staged pipeline, downloads, storage
│   │   │   │   │   ├── data/       # Adapters (yt-dlp, QuickJS, FxEmbed, oEmbed), DB, download workers
│   │   │   │   │   ├── domain/     # Media models, platform resolver, extraction contracts
│   │   │   │   │   └── ui/         # MediaSection, CandidateCard, DownloadTaskRow, LinkDetail
│   │   │   │   ├── metadata/   # Jsoup extractor & offline worker
│   │   │   │   ├── sharing/    # ShareReceiverActivity & intent handling
│   │   │   │   └── ui/         # Compose screens, theme tokens, components
│   │   │   ├── res/            # Vector drawables, fonts, strings
│   │   │   └── AndroidManifest.xml
│   │   └── test/               # Unit, Room, and Robolectric test suites
│   └── build.gradle.kts
├── docs/                       # Architectural specifications & CI/CD guides
├── .github/                    # GitHub Actions workflows (activated in Milestone 1)
├── build.gradle.kts            # Root build script
├── settings.gradle.kts         # Module inclusion & plugin repositories
└── gradle/                     # Gradle wrapper & version catalogs
```

---

## Continuous Integration & Release Strategy

Laterbox uses **GitHub Actions** for automated build and release verification:

- **CI Pipeline**: On every push and pull request to `main`, workflows validate the Gradle wrapper, run Android Lint, execute the complete unit and Robolectric test suite, compile the debug APK, and upload the build artifact.
- **Release Distribution**: Pushing a version tag (`v*`) triggers an automated build that signs the release APK via secure GitHub Actions Secrets and attaches multi-ABI APKs (`Laterbox-ARM32.apk`, `Laterbox-ARM64.apk`, `Laterbox-Universal.apk`) directly to a GitHub Release.
- Detailed pipeline specifications and secret configurations are documented in [docs/ci-cd-architecture.md](docs/ci-cd-architecture.md).

---

## Adaptive Layout Status & Roadmap

- **Design System Standardisation**: Completed. All screens, dialogs, buttons, cards, and input fields strictly use Laterbox tokens (`LaterboxTheme.colors`, `spacing`, `typography`, `LaterboxShapeTokens`, and `LaterboxButton`/`LaterboxCard` components) without hardcoded colors or ad-hoc shapes.
- **Adaptive Split-Pane & Large Screens**: Large-screen adaptations (such as media-first split-pane detail sheet, `WindowSizeClass` adaptations, and tablet/foldable navigation rail) remain active backlog items for subsequent milestones, maintaining a clean single-column responsive layout for handset form factors.

---

## Git Workflow & Contributing

- The `main` branch always reflects the verified, stable state of the application.
- All new features and fixes are developed in isolated topic branches (`feature/*`, `fix/*`) and verified against tests and lint before merging.
- Commits follow the [Conventional Commits](https://www.conventionalcommits.org/) specification.
- For complete branching, style, and verification guidelines, see [CONTRIBUTING.md](CONTRIBUTING.md).

---

## Public Repository Security & Privacy

This is a public repository:
- Machine-specific files (`local.properties`), keystores (`*.jks`, `*.keystore`), and credential files are strictly excluded via `.gitignore`.
- Release signing secrets are managed solely via GitHub Secrets.
- Sample data and automated test fixtures use strictly synthetic, public domains (`example.com`, `wikipedia.org`) with zero private user information.

### Media Library, Privacy & Third-Party Services

Laterbox includes an on-device Media Library for discovering and downloading media directly into local app storage:
- **Zero Third-Party Relays**: All media downloads and byte transfers occur strictly on-device directly between your phone and the original content servers. Links, cookies, and downloaded files are never proxied or forwarded to intermediate servers.
- **Local yt-dlp & QuickJS Engine**: The media extraction pipeline runs locally on your device using a bundled Python runtime with QuickJS for JavaScript player signature deciphering.
- **Public X/Twitter Metadata (FxEmbed)**: For X (formerly Twitter) links only, Laterbox queries `api.fxtwitter.com` strictly to resolve public post metadata and media URLs when saved, without requiring accounts, API keys, or personal identifiers.
- **yt-dlp Extractor Engine Updates**: To keep extractors functional when platforms update their web players, Laterbox periodically queries the official `yt-dlp` GitHub releases repository (checked at most once every 24 hours on app launch, or manually via Settings). This request contacts GitHub solely to check for binary releases—no user links, history, or identifiers are transmitted.
- **Smart Reminders (Optional)**: If you configure a personal Google AI Studio API key in Settings, link text snippets are sent directly to Google AI Studio to extract scheduled dates. No data is logged or retained.

---

## Licensing Notice

Source code in this repository is currently publicly visible with **All Rights Reserved**. 

*Note for Review*: While the code is hosted publicly on GitHub, making a repository public does not automatically make it open source. Selecting a formal open-source license (such as **Apache 2.0**, **MIT**, or **GPLv3**) or maintaining proprietary copyright is a distinct decision reserved for the repository owner.
