# Contributing to Laterbox — Development & Git Workflow

Laterbox is an independent, craft-focused Android application. To keep development simple, predictable, and clean, we adhere to the following workflow.

---

## 1. Branching Strategy

We follow a disciplined branch-and-merge model tailored for indie development:

- **`main`**: The single source of truth. It represents the production-ready, buildable state of the application. Commits are never pushed directly to `main` without verification.
- **`feature/<name>`**: For new capabilities (e.g., `feature/share-sheet-receiver`, `feature/room-database`).
- **`fix/<issue>`**: For bug fixes and edge-case hardening (e.g., `fix/url-normalizer-trailing-slash`).
- **`refactor/<scope>`**: For architectural cleanups and code reorganization without functional changes.
- **`docs/<topic>`**: For documentation and specification updates.

---

## 2. Commit Message Guidelines

We use [Conventional Commits](https://www.conventionalcommits.org/) to maintain a clear, automated changelog-ready git history:

```text
<type>(<optional scope>): <description>

[optional body]

[optional footer(s)]
```

### Allowed Types
- **`feat`**: A new user-facing feature or capability.
- **`fix`**: A bug fix.
- **`docs`**: Documentation changes only.
- **`style`**: Changes that do not affect code meaning (white-space, formatting, missing semi-colons).
- **`refactor`**: Code changes that neither fix a bug nor add a feature.
- **`test`**: Adding missing tests or correcting existing tests.
- **`chore`**: Maintenance tasks, build system updates, or dependency version bumps.

### Examples
- `feat(sharing): add ShareReceiverActivity for ACTION_SEND intents`
- `fix(metadata): handle HTTP 403 gracefully with domain title fallback`
- `refactor(database): migrate collection foreign key to onDelete SET_NULL`
- `docs(ci): document release keystore secret decoding steps`

---

## 3. Pre-Merge Verification Checklist

Before merging any branch into `main`:

1. **Gradle Build Verification**: The project builds cleanly (`./gradlew assembleDebug`).
2. **Unit & Integration Tests**: All unit and Robolectric tests pass (`./gradlew testDebugUnitTest`).
3. **Android Lint**: Code passes Android Lint checks with no errors (`./gradlew lintDebug`).
4. **Code Cleanliness**: Code conforms to `.editorconfig` style rules.
5. **Security Check**: Confirm no credentials, debug secrets, personal tokens, or real user data are included in test fixtures.

---

## 4. Public Repository Security Rules

Because Laterbox is a public repository:

- **No Secrets**: Never commit `local.properties`, API tokens, private keys, or keystores.
- **No Personal Data in Fixtures**: Never include real private personal links or browsing histories in sample data or test fixtures. Use generic, public test domains (`example.com`, `wikipedia.org`, `github.com`).
- **Review Diffs**: Always run `git diff --cached` before committing to inspect the exact lines staged.
