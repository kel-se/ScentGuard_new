# Implementation Plan: ScentGuard Copyright Deposit Copy & Sanitization

Prepare a clean, sanitized, and secure copy of the ScentGuard project for copyright registration deposit with the National Library of the Philippines (NLP) at `/Users/michaelangelotorre/StudioProjects/ScentGuard_Copyright_Deposit`.

## User Review Required

> [!IMPORTANT]
> - **Source Preservation**: All original source code (Kotlin UI, ViewModels, Services, Repositories, XML resources, and ESP32 firmware) will be preserved.
> - **Credential Sanitization**: Public API keys and Firebase project references in `google-services.json` and `firmware/ScentGuard.ino` will be replaced with secure placeholders (`"AIzaSy...REDACTED"` / `"YOUR_API_KEY"`).
> - **Exclusions**: Build directories (`build/`, `.gradle/`), IDE configuration (`.idea/`), and version control (`.git/`) will be strictly excluded from the copy.

## Proposed Changes

### 1. [NEW] Folder Creation & Copying
- Create target directory: `/Users/michaelangelotorre/StudioProjects/ScentGuard_Copyright_Deposit`
- Copy source files, excluding `.git/`, `build/`, `.gradle/`, and `.idea/`.

### 2. [MODIFY] Sanitizing Sensitive Configuration in Copy
- **`app/google-services.json`**: Replace `current_key` value with `"AIzaSy...REDACTED"`.
- **`firmware/ScentGuard.ino`**: Replace `API_KEY` definition with `"AIzaSy...REDACTED"`.

### 3. [NEW] Second Security Scan & Verification
- Scan the copied directory (`ScentGuard_Copyright_Deposit`) to verify no unredacted private credentials or build artifacts exist.

## Verification & Reporting
- Generate the final security compliance report detailing excluded files, sanitized credential files, retained code, and final copy location.
