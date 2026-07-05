# Sakhi Android Design System Mapping

This document maps the current iOS design-system sources to the planned Android Compose implementation in `:core:designsystem` and `:core:ui`.

Use it as a build reference, not as a redesign brief. Android must match iOS exactly.

## Source set reviewed

Primary iOS design-system sources:

- `SakhiApp/DesignSystem/SakhiDesignSystem.swift`
- `SakhiApp/DesignSystem/Styles/DSConstants.swift`
- `SakhiApp/DesignSystem/Styles/Typography.swift`
- `SakhiApp/DesignSystem/SakhiTextField.swift`
- `SakhiApp/DesignSystem/Components/DSButton.swift`
- `SakhiApp/DesignSystem/Components/DSButtonVariants.swift`
- `SakhiApp/DesignSystem/Components/DSTextField.swift`
- `SakhiApp/DesignSystem/Components/DSOTPTextField.swift`
- `SakhiApp/DesignSystem/Components/GlassCard.swift`
- `SakhiApp/DesignSystem/Components/Sheets.swift`
- `SakhiApp/DesignSystem/SakhiSheetHeader.swift`
- `SakhiApp/DesignSystem/SakhiCalendarView.swift`
- `SakhiApp/DesignSystem/SakhiShimmer.swift`
- `SakhiApp/DesignSystem/SakhiLoadingView.swift`
- `SakhiApp/DesignSystem/SakhiAlert.swift`
- `SakhiApp/DesignSystem/NetworkOfflineBanner.swift`
- `SakhiApp/DesignSystem/PhaseBackground.swift`
- `SakhiApp/DesignSystem/PhaseColors.swift`
- `SakhiApp/DesignSystem/PhaseColorManager.swift`

Related non-DS sources that still define real UI behaviour:

- `SakhiApp/Core/Navigation/ToastManager/ToastManager.swift`
- `SakhiApp/Features/Home/Views/DayDetail/HomeDayDetailGlassView+Cards.swift`

## Token mapping

| iOS source | iOS contract | Planned Android owner | Compose note |
| --- | --- | --- | --- |
| `SakhiDesignSystem.swift`, `PhaseColors.swift`, `PhaseColorManager.swift` | Color tokens come from KMM `DesignTokens` / `SakhiColorSystem` / `ResolvedBundle`. Brand colors in plan: `#F61887`, `#BB2968`, `#6D1743`, `#F8F2F4`, `#F8E5EC`, `#1C1C1E`, `#6B6B7A`, `#E5E4EA`, `#34C759`. | `:core:designsystem` | Build `SakhiColorScheme` from KMM first. Do not re-enter hex values in feature modules. Keep phase bundles as structured objects, not loose colors. |
| `SakhiDesignSystem.swift` | Spacing scale: `4, 8, 12, 16, 20, 24, 28, 32, 40, 48`. Semantic slots: screen horizontal `24`, card horizontal `18`, card vertical `20`, between cards `12`, title-to-subtitle `8`, subtitle-to-content `24`, content-to-button `20`, panel horizontal `20`, icon-to-text `12`, icon-to-title `10`. | `:core:designsystem` | Expose raw scale plus semantic layout constants. Screens should use semantic slots where possible. |
| `DSConstants.swift`, `SakhiDesignSystem.swift` | Radii: system card `12`, onboarding card `16`, glass card `24`, icon badge `10`, bottom sheet top corners `40`, CTA uses capsule, not fixed radius. | `:core:designsystem` | Model radii separately from Material defaults. `RoundedCornerShape` or custom sheet container will be needed for `40` top-only corners. |
| `SakhiDesignSystem.swift`, `Typography.swift` | Typography: `screenTitle 28 bold`, `introTitle 26 bold`, `sectionHeader 20 bold`, `cardTitle 17 bold`, `buttonLabel 17 bold`, `body 15`, `cardDescription 14`, `caption 13`, `footer 12`, `largeValue 64 bold`, `valueUnit 22`, `calendarMonth 16 bold`, `calendarDay 14`, `calendarWeekday 12`, `wheelPicker 20`. Older fallback file still carries `34/28/22/20/17/16/15/13/12`. | `:core:designsystem` | Compose typography should expose the newer DS semantic names, with fallback comments pointing to the older file for legacy screens still using it. |
| `SakhiDesignSystem.swift`, `PhaseBackground.swift` | Visual contexts: Pink, System, Aurora. Pink = blush background + white cards at `r16`. System = grouped background + white/profile cards at `r12`. Aurora = phase gradient + ultra-thin-material cards at `r24`. | `:core:designsystem` | Create `SakhiVisualContext` enum and context-aware container modifiers. Avoid direct `MaterialTheme.colorScheme.surface` use in feature code. |

## Component mapping

| iOS component | iOS source | Planned Compose equivalent | Key values and behaviour to preserve |
| --- | --- | --- | --- |
| Primary CTA | `SakhiDesignSystem.swift`, `DSButtonVariants.swift`, `DSButton.swift` | `SakhiPrimaryButton` in `:core:ui` | Full-width pink capsule, height `56`, white label, loading swaps label to spinner, no disabled-empty-input pattern. |
| Secondary CTA | `SakhiDesignSystem.swift` | `SakhiSecondaryButton` in `:core:ui` | Text-only action, Lato `16 regular`, pink foreground, subtle pressed opacity. |
| Back button | `SakhiDesignSystem.swift` | `SakhiBackButton` in `:core:ui` | Floating circle `44x44` on older systems, glass circle treatment on newer systems, pink icon, scale-to-press animation. |
| Close button | `SakhiDesignSystem.swift`, `SakhiSheetHeader.swift` | `SakhiCloseButton` in `:core:ui` | Same geometry and behaviour as back button, supports toolbar and gradient-header variants. |
| Refresh button | `SakhiDesignSystem.swift` | `SakhiRefreshButton` in `:core:ui` | Same circular chrome as back/close, spinner state replaces icon. |
| Unified text field | `SakhiTextField.swift` | `SakhiTextField` in `:core:ui` | Height `56`, context-aware card surface, DS error border, optional prefix/suffix slots, dropdown mode, keyboard-focus handling. |
| Legacy DS text field set | `DSTextField.swift` | `SakhiTextField` wrappers or migration adapters in `:core:ui` | Older namespace shows standard, labelled, secure, search, validated, phone and OTP patterns. Keep support paths but prefer the unified field API. |
| OTP field | `DSTextField.swift`, `DSOTPTextField.swift`, plan Section 4 | `SakhiOtpField` in `:core:ui` | Six-cell OTP experience is required by the Android plan. Exact cell sizing is not explicit in the reviewed source, value TBD, read from source usage when implementing the real screen. |
| Secure text field | `DSTextField.swift`, `DSSecureTextField.swift` | `SakhiSecureTextField` in `:core:ui` | Same base field styling as text field, optional visibility toggle pattern. |
| Card surface, Pink | `SakhiDesignSystem.swift` | `SakhiSurfaceCard(context = Pink)` in `:core:ui` | White/profile card fill, radius `16`, no default shadow. |
| Card surface, System | `SakhiDesignSystem.swift` | `SakhiSurfaceCard(context = System)` in `:core:ui` | System/profile white card fill, radius `12`, no default shadow. |
| Glass card, Aurora | `GlassCard.swift`, `SakhiDesignSystem.swift` | `SakhiGlassCard` in `:core:ui` | Radius `24`, ultra-thin blur, white tint overlay, top-weighted highlight, 1pt gradient stroke. |
| Icon badge | `SakhiDesignSystem.swift` | `SakhiIconBadge` in `:core:ui` | `40x40`, radius `10`, default fill `DS.Colors.lightPink`. |
| Bottom panel shell | `SakhiDesignSystem.swift` | `SakhiBottomPanel` in `:core:ui` | Top-only radius `40`, horizontal padding `20`, top padding `28`, system background fill. |
| Sheet header | `SakhiSheetHeader.swift` | `SakhiSheetHeader` in `:core:ui` | Horizontal padding `24`, top padding `20`, bottom padding `24`, title `22 bold`, subtitle `14 regular`, close button on trailing side. |
| Universal bottom sheet | `Sheets.swift`, `Footers.swift` | `SakhiBottomSheetScaffold` in `:core:ui` | Header/footer lanes, gradient background option for onboarding, internal tab paging, non-dismissible option, alerts must stay inside sheet hierarchy. |
| Sheet footer | `SakhiFooter.swift`, `Footers.swift`, `Sheets.swift` | `SakhiSheetFooter` in `:core:ui` | Shared CTA area for paging sheets. Exact spacing comes from semantic DS spacing values. |
| Alert sheet | `SakhiAlert.swift` | `SakhiAlertSheet` in `:core:ui` | Fixed height `300`, detent height `300`, corner radius `40`, drag indicator hidden, non-dismissable, two-button row with `50` height capsule buttons. |
| Calendar | `SakhiCalendarView.swift`, `SakhiDesignSystem.swift` | `SakhiCalendar` in `:core:ui` | Month header with nav buttons, weekday row, 6-row grid, default cell height `44`, row spacing `6`, nav button size `36`, weekday font `11`, selected/today/period/ovulation states driven by shared phase data. |
| Shimmer modifier | `SakhiShimmer.swift` | `Modifier.sakhiShimmer()` in `:core:ui` | Redacted placeholder plus animated sweep, duration `1.4s`, reusable loading modifier. |
| Shimmer skeletons | `SakhiShimmer.swift` | `SakhiShimmer.*` in `:core:ui` | Text line `14h r6`, row avatar default `42`, card default height `110` radius `16`, calendar cell default `36`, profile skeleton uses onboarding-card radius. |
| Loading state | `SakhiLoadingView.swift` | `SakhiLoadingView` in `:core:ui` | Dashed concentric rings `180/128/82`, logo size `44`, title/subtitle bottom-aligned with `64` bottom padding, optional cycling messages for setup flows. |
| Empty state, card | `SakhiDesignSystem.swift` | `SakhiEmptyStateCard` in `:core:ui` | Pink-context placeholder card with light pink background, icon badge block `40x40`, card radius `16`, horizontal padding `24`. |
| Empty state, centered | `SakhiDesignSystem.swift` | `SakhiEmptyStateCentered` in `:core:ui` | Vertical icon-title-description stack, no card background, centered, vertical padding `28`, horizontal padding `24`. |
| Offline banner | `NetworkOfflineBanner.swift` | `OfflineBanner` in `:core:ui` | Capsule with system background fill, top padding `16`, horizontal padding `16`, vertical padding `8`, label font `13`, shadow `radius 6 y 2 opacity 0.08`. |
| Toast | `Core/Navigation/ToastManager/ToastManager.swift` | `SakhiToastHost` in `:core:ui` or app-shell lane | Not in `DesignSystem/` folder. Current iOS toast is window-level, top safe-area aligned, dark capsule, icon + title, optional inline action, spring-in animation, shadow stack. Treat this as a shell component, not just a feature-local helper. |
| Phase badge | `Features/Home/Views/DayDetail/HomeDayDetailGlassView+Cards.swift`, `PhaseColors.swift`, `PhaseColorManager.swift` | `PhaseBadge` in `:core:ui` | No standalone DS component file found. Badge and chip styling must be derived from shared phase palette, especially `tileFill`, `tileStroke`, `primary`, `secondary`, and context-specific text colors. Read live feature source at implementation time. |

## Visual context rules

| Context | iOS source | Android placement | Rules |
| --- | --- | --- | --- |
| Pink | `SakhiDesignSystem.swift`, `PhaseBackground.swift`, plan Section 4 | `:core:designsystem` theme + screen background helpers | Default onboarding and auth context. Use blush app background and white cards at radius `16`. |
| System | `SakhiDesignSystem.swift` | `:core:designsystem` theme + grouped surfaces | Use grouped background, system/profile card fill, radius `12`, sheet and settings presentation. |
| Aurora | `SakhiDesignSystem.swift`, `GlassCard.swift`, `PhaseBackground.swift`, `PhaseColors.swift` | `:core:designsystem` phase-aware home theme | Use phase-driven gradient background and glass cards at radius `24`. No generic Material card replacement here. |

## Phase and brand color notes

- Brand pink is the primary interactive color. The Android plan already locks the base set:
  - Primary Pink `#F61887`
  - Deep Pink `#BB2968`
  - Deep Burgundy `#6D1743`
  - Background Blush `#F8F2F4`
  - Soft Blush `#F8E5EC`
  - Text Black `#1C1C1E`
  - Body Gray `#6B6B7A`
  - Light Gray `#E5E4EA`
  - Success Green `#34C759`
- Phase-specific colors do not live as hardcoded hex values in the reviewed Swift files. They come from KMM `SakhiColorSystem` and `ResolvedBundle`.
- Compose should expose phase palettes as structured objects with:
  - `primary`
  - `secondary`
  - `surface`
  - `bgTop`, `bgMid`, `bgBot`
  - `ring`
  - `accent`
  - `gradTop`, `gradBot`
  - `cardGradTop`, `cardGradBot`
  - `chart`
  - `tileFill`
  - `tileStroke`

## Build guidance for `:core:designsystem`

- Put tokens, theme, visual contexts, typography, spacing, radii, and phase palette objects in `:core:designsystem`.
- Keep high-level reusable Composables in `:core:ui`.
- Do not let feature modules define colors, typography, or spacing constants locally.
- Add screenshot parity checks per component before wiring full screens.

## Open points to resolve during implementation

- OTP box exact dimensions are not explicit in the reviewed DS files. Read the final iOS OTP screen layout before freezing the Compose size tokens.
- The toast surface is implemented outside `DesignSystem/`, so the Compose shell should copy its app-level presentation behaviour from `ToastManager.swift`, not invent a Snackbar.
- A dedicated reusable phase-badge component was not found in `DesignSystem/`. Re-read the live Home and Calendar feature sources when implementing `PhaseBadge`.
- Some older DS namespaces (`DesignSystem.*`) and newer `DS.*` helpers coexist. The Compose side should follow the newer `DS` semantic contract first, while still supporting the older patterns during migration.
