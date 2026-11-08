# Implementation Plan: Login & Create Account UI Redesign

Redesign the Login and Create Account screens of the ScentGuard Android app into a modern, premium, minimalist authentication experience on the `scentguard-experimental` branch, eliminating form cards/containers and placing all elements naturally on the background canvas.

## User Review Required

> [!IMPORTANT]
> - **Canvas-First Layout**: Removed `ScentGuardCard` containers around form fields. Inputs, buttons, and social login buttons will now sit naturally and cleanly on the screen background.
> - **Typography & Headings**: Updated headings to "Welcome Back" on Login and "Create Account" on Registration with refined spacing and typography.
> - **Backend Integrity**: All Firebase Auth, Google Sign-In, ViewModels, navigation, and validation logic remain 100% intact.

## Proposed Changes

### UI Layer

#### [MODIFY] [LoginScreen.kt](file:///Users/michaelangelotorre/StudioProjects/ScentGuard_new/app/src/main/java/com/example/scentguard/ui/screens/login/LoginScreen.kt)
- Remove `ScentGuardCard` wrapper around inputs and buttons.
- Update heading to "Welcome Back".
- Ensure clean spacing, subtle input field borders, prominent primary green button, subtle divider with "or continue with", and Google sign-in button directly on the background canvas.

#### [MODIFY] [SignUpScreen.kt](file:///Users/michaelangelotorre/StudioProjects/ScentGuard_new/app/src/main/java/com/example/scentguard/ui/screens/signup/SignUpScreen.kt)
- Remove `ScentGuardCard` wrapper around inputs and buttons.
- Update heading to "Create Account" (and "Finalize profile" when in complete profile mode).
- Maintain Manager/Staff tab switching, form fields, loading progress indicator, and navigation naturally on the background canvas.

## Verification Plan

### Automated Tests
- Run `./gradlew app:assembleDebug` to verify compilation and UI integrity.

### Manual Verification
- Verify that Login and Create Account screens match the minimalist canvas-first design (no rounded white card containers).
- Verify all authentication interactions (Sign In, Google Sign In, Forgot Password, Manager/Staff registration, profile completion) function correctly.
