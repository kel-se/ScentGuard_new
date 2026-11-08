# Implementation Plan: Google Sign-In Error Handling & UI Improvement

Improve Google Sign-In error mapping and present user-friendly inline error notices on the Login screen, ensuring clean handling of cancellations, missing accounts, and network errors without altering any backend authentication logic.

## User Review Required

> [!IMPORTANT]
> - **Zero Backend Changes**: Firebase Authentication, Google Sign-In API options, ViewModels, and repositories remain completely untouched.
> - **Cancellation Handling**: User cancellation of Google Sign-In will be treated as a normal user action with no error displayed.
> - **Inline Error Notice UI**: A modern, subtle, soft-neutral/light-red error banner with an icon will be added to the Login screen for authentication errors.

## Proposed Changes

### UI Layer

#### [MODIFY] [LoginScreen.kt](file:///Users/michaelangelotorre/StudioProjects/ScentGuard_new/app/src/main/java/com/example/scentguard/ui/screens/login/LoginScreen.kt)
- Add state for inline authentication error message (`errorMessage`).
- Implement user-friendly error mapping for Google Sign-In exceptions and login failures:
  - **Cancellation (`GetCredentialCancellationException`)**: Ignore / treat as normal user action.
  - **No account / missing profile**: "No account found. Please sign up first or try another sign-in method."
  - **Network / Connection issue**: "Connection problem. Please check your internet connection and try again."
  - **General Google failure**: "Unable to sign in with Google. Please try again."
- Create a reusable `AuthErrorNotice` composable component adhering to ScentGuard's design (soft-neutral/light-red error background, warning icon, rounded corners, concise text).
- Position the error notice within the Login screen form flow.

## Verification Plan

### Automated Tests
- Run `./gradlew app:assembleDebug` to verify compilation.

### Manual Verification
- Test canceling Google Sign-In (verify no error is shown).
- Test invalid login / network errors (verify friendly error notice appears).
- Test email/password login remains fully functional.
