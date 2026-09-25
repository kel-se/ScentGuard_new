# Implementation Plan - Anti-Slop UI Refinement

Refine ScentGuard's UI to remove generic AI-generated design patterns ("slop") and replace them with intentional, professional, and product-specific hierarchy. This plan focuses on restraint, consistent rounding, and removing unnecessary motion while preserving the brand's core identity.

## User Review Required

> [!IMPORTANT]
> **Aesthetic Shift**: We are moving away from the "soft/bubbly" look (40dp radii, breathing animations) towards a more "Industrial/Professional" look (16dp-24dp radii, static status indicators).
>
> **Hierarchy Update**: The "Gas Level" will be established as the primary dashboard metric, with other values (Temp, Humidity) becoming visually secondary to improve immediate cognitive recognition.

## Proposed Changes

### 1. Design Tokens & Core Components

#### [MODIFY] [AuthUIConfig.kt](file:///Users/michaelangelotorre/StudioProjects/ScentGuard_new/app/src/main/java/com/example/scentguard/ui/theme/AuthUIConfig.kt)
- Update `CardCornerRadius` from 40dp to **24dp**.
- Update `FieldCornerRadius` from 20dp to **12dp**.
- Remove `BackgroundGradient` in favor of a solid `SoftMint` or `BaseGray`.
- Reduce `TaglineLetterSpacing` for better readability.

#### [MODIFY] [ScentGuardCard.kt](file:///Users/michaelangelotorre/StudioProjects/ScentGuard_new/app/src/main/java/com/example/scentguard/ui/components/ScentGuardCard.kt)
- Update default `cornerRadius` from 28dp to **16dp**.
- Refine `borderColor` to be slightly more visible (0.1f alpha instead of 0.05f) to define surfaces without relying on heavy shadows.

### 2. Dashboard Refinement

#### [MODIFY] [DashboardScreen.kt](file:///Users/michaelangelotorre/StudioProjects/ScentGuard_new/app/src/main/java/com/example/scentguard/ui/screens/dashboard/DashboardScreen.kt)
- **AirQualityHero**:
    - Remove `auraScale` and `auraAlpha` animations.
    - Replace the "Breathing Aura" with a static, semi-transparent circle background.
    - Downgrade "Status" typography from `displayLarge` to **`headlineLarge`**.
    - Downgrade "ppm" typography from `titleLarge` to **`titleMedium`**.
- **MetricsGrid**:
    - Establish hierarchy: Increase the weight of the "Gas Level" card and slightly diminish the "System Temp" card's visual weight (e.g., smaller icon or subtler text).
    - Tighten `Arrangement.spacedBy` to **12dp** for a more compact, tool-like feel.

### 3. Authentication Screens

#### [MODIFY] [LoginScreen.kt](file:///Users/michaelangelotorre/StudioProjects/ScentGuard_new/app/src/main/java/com/example/scentguard/ui/screens/login/LoginScreen.kt) & [SignUpScreen.kt](file:///Users/michaelangelotorre/StudioProjects/ScentGuard_new/app/src/main/java/com/example/scentguard/ui/screens/signup/SignUpScreen.kt)
- Replace the multi-tone vertical gradient with a solid `SoftMint` background.
- Rely on the white card surface and subtle borders to create depth.
- Ensure the tagline "DETECT. VENTILATE. PROTECT." is clean and non-decorative.

## Verification Plan

### Automated Tests
- Run `app:compileDebugKotlin` to ensure no component reference regressions.

### Manual Verification
1. **Motion Audit**: Confirm the Dashboard no longer has "always-on" animations.
2. **Rounding Audit**: Check that all cards (Dashboard, Auth, History) use the new, tighter radii consistently.
3. **State Check**: Simulate SAFE, WARN, and DANGER states; ensure the color transitions are the primary driver of status communication.
4. **Accessibilty**: Verify that the removal of gradients improves text contrast on the background surfaces.
