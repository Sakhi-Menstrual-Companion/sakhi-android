# Sakhi Android App, Full Build Plan (Jetpack Compose, KMM-powered, full iOS parity)

## Context

The iOS app already runs on a shared Kotlin Multiplatform brain, `SakhiCore`
(`02-Platforms/00-Shared/SakhiCore`). It owns auth, onboarding, period and cycle
logic, care (Be Her Sakhi), sync, AI classification, reports data, validation,
permissions, design tokens, and Supabase contracts. iOS is a SwiftUI shell over it.

`02-Platforms/02-Android` is a clean slate (only a README and a git remote
`sakhi-android/master`). The goal is a brand-new native Jetpack Compose app that:

- reaches full feature parity with iOS,
- consumes the same KMM brain (never forks business logic),
- feels premium yet simple, like the best women's health apps (Clue's calm
  at-a-glance cycle view, Flo's friendliness), grounded in Sakhi's own brand,
- handles sensitive health data with zero leaks and solid offline behaviour.

Decisions locked with the owner: native Compose (iOS stays SwiftUI, so no Compose
Multiplatform UI); a single shared **Room KMP** database in KMM `commonMain` used by both
platforms; **iOS is migrated off Realm onto that shared Room layer first**, so Android is
built on the same frozen schema rather than re-unifying later; Koin as shared DI in KMM
`commonMain`; full feature parity before first launch (built in phases, released once
complete).

Database note: Realm is end-of-life (MongoDB deprecated Atlas Device Sync, sunset around
Sept 2025, Realm-Kotlin maintenance winding down), so it is not an option for new work.
The choice was Room KMP vs SQLDelight. Room now has full Kotlin Multiplatform support
(2.7+, Google-backed), so it lives in `commonMain` and is shared across Android and iOS,
with type-safe DAOs and strong migration tooling. Picked over SQLDelight for Google's
longevity (fewer long-term data risks), best-in-class tooling, and the most maintainable
path for the team, while still giving fully shared persistence code. SQLDelight remains a
viable alternative if the team prefers hand-written typed SQL.

This file is the plan. On execution it becomes the Android README and live
checklist (intended home: `02-Platforms/02-Android/README.md`, with the phase
checklist mirrored into `01-HQ/05-Product/Engineering/` as a dated checklist doc
following the existing checklist convention).

Execution order is locked:

1. Finish the shared Realm -> Room KMP migration first.
2. Verify the migrated store is lossless and stable.
3. Only then start the Android app shell and feature work.

Android must not start on top of a temporary iOS Realm contract and then re-migrate
later. The shared local database is part of the shared product code, so it is the
critical-path prerequisite.

Android execution approach is also locked: build from the lowest layer upward. First
finish the shared data contract, managers, repositories, platform adapters, DI,
navigation owners, sheet owners, sync boundaries, permission gates, and reusable UI
skeleton. Only after those foundations are stable should feature screens be filled in.
No high-level screen should become its own architecture island. Every feature must plug
into the shared managers, navigators, stores, and design-system skeleton.

---

## 0. Prerequisites, versions, and non-negotiable guardrails

**First execution step:** save this document as `02-Platforms/02-Android/README.md`, and
mirror the phase checklist into `01-HQ/05-Product/Engineering/` as a dated checklist doc
(per the existing checklist convention) so progress is tracked there too.

**Toolchain (pin to avoid KMM metadata mismatch):**
- Kotlin **2.1.20** exactly (must match `SakhiCore`), matching KSP version.
- Android Studio latest stable, JDK 17, AGP latest stable, Gradle wrapper.
- Jetpack Compose BOM (latest stable), Material 3.
- Room **2.7+** (KMP, bundled SQLite driver), Koin (latest).
- `minSdk 26`, `compileSdk` latest, `targetSdk` latest (match `SakhiCore` androidTarget).
- Gradle version catalog (`libs.versions.toml`) for all versions; no inline versions.

**Secrets:** Supabase URL/anon key, Claude, Google Places/Maps, Exotel, Sanity, Razorpay,
USDA come from `gradle.properties` (local, git-ignored) and CI secrets, injected into
`BuildConfig` then `BuildConfigProvider`. Never commit a key.

**Non-negotiable guardrails (CI-enforced):**
1. **No product logic in Android.** Cycle, period, care, sync, validation, AI, report,
   permission, and routing rules come only from KMM. Add an Android thin-shell audit
   script (mirror iOS `Scripts/audit_kmm_thin_shell.py`) that fails CI if Android
   re-implements any shared rule (for example a local phase calc, a local permission
   check, or a hand-rolled cycle/period computation).
2. **Never fork KMM.** If a rule is missing or wrong, fix it in `SakhiCore` `commonMain`
   with a common test, then consume it. Keep iOS building (`jvmTest` green) on every KMM
   change.
3. **100% iOS parity** (screens, flows, sheets, motion, copy), enforced by the visual +
   flow parity gate. Any difference from iOS is a bug.
4. **Privacy:** no health or care data in logs, crash reports, notifications, or realtime
   payloads. Be Her Sakhi stays voluntary and private, never a metric or a prompt.
5. **Offline-first:** core logging works fully offline; sync reconciles on reconnect.
6. **One account, one device** behaviour matches KMM (`revokeOtherDeviceSessions`).

---

## 1. Architecture decisions (and why)

1. **Native Jetpack Compose shell over KMM.** iOS is SwiftUI, so there is no shared
   UI layer to gain from Compose Multiplatform. Android is a native Compose app that
   depends on `SakhiCore` via a Gradle composite build and consumes its `StateFlow`s
   directly. Confirmed by `05-Engineering/Platform-Cheatsheets/Android-2026-Cheatsheet.md`.

2. **KMM owns meaning, Android owns rendering and OS.** KMM owns business rules,
   state, validation, sync, care logic, cycle math, backend contracts, reports, AI
   data rules. Android owns Compose UI, navigation host, and OS integrations (Health
   Connect, FCM, Maps, location, camera, contacts, billing, biometrics, haptics).
   Android must not re-implement any KMM rule.

3. **One shared Room KMP database for both platforms (in KMM `commonMain`), iOS migrated
   first.** Room now supports Kotlin Multiplatform (2.7+, Google-backed), so the entities,
   DAOs, and migrations live once in `commonMain` and run on Android and iOS via the
   bundled SQLite driver. It fills the existing KMM `LocalDatabaseInterface` stub and hosts
   the offline sync-queue. Chosen over Realm (end-of-life) and over SQLDelight for Google
   longevity, type-safe DAOs, and migration tooling. To avoid divergence, iOS is migrated
   off Realm onto this shared Room layer as **Phase A** (the first phase), then Android is
   built on the same frozen schema. This is the highest-risk work (it touches the shipping
   iOS data layer), so it is gated by a no-data-loss Realm->Room migration, full iOS
   regression/parity, and a staged rollout. SQLDelight is the noted alternative.

4. **Koin shared DI (in KMM `commonMain`).** The dependency graph is wired once and
   shared by Android and iOS. KMM currently has no DI (manual instantiation), so we add
   a Koin module in `commonMain` plus a small Android module for platform bindings
   (Context, Health Connect, FCM, Maps, location). Runtime cost is startup-only.

5. **UDF / MVI presentation.** Thin Android `ViewModel`s (androidx.lifecycle) observe
   KMM stores' `StateFlow` with `collectAsStateWithLifecycle`, expose one immutable
   `UiState` per screen (or a few focused flows for complex screens), and send intents
   to KMM stores. No business decisions in Composables or ViewModels.

6. **Bottom-up implementation order.** Android is not built screen-first. The correct
   order is: KMM contracts and Room store, Android platform adapters, DI, managers,
   navigation hosts, sheet hosts, permission gates, sync/offline owners, design-system
   tokens, reusable Compose components, app-wide UI skeleton, then feature screens.
   If a screen needs a manager, navigator, permission gate, modal lane, cache path, or
   shared component, that foundation is built first and reused everywhere. This rule is
   structural, not optional.

7. **Pixel-perfect parity with iOS. The iOS app is the exact visual and behavioural spec.**
   The Android app must look and behave 100% identical to iOS: same screens, **same flows
   (every step, branch, and order), same sheets, modals, and bottom sheets (same content,
   presentation style, drag/dismiss behaviour, and detents), same navigation structure
   and transitions**, same layouts, spacing, radii, colours, typography, icons, component
   styling, motion, haptics, and copy. Nothing is redesigned, reinterpreted, reordered, or
   "improved." Material 3 is used only as the component substrate; wherever an M3 default
   differs from iOS, it is overridden to match iOS exactly. The theme is built from the
   same KMM `DesignTokens` / `SakhiColorSystem` that iOS uses, so values are identical by
   construction, not by eye. No dynamic colour, no new motion iOS does not already have.
   Every screen, flow, and sheet is verified side-by-side against its iOS counterpart. If
   anything differs from iOS, it is a bug to fix, not a choice.

8. **Type-safe Navigation Compose, route-driven by KMM.** Navigation Compose 2.8+ with
   type-safe routes. The root destination is decided by KMM `AppStateStore.appRoute`
   (Splash, SignedOut, Onboarding, Home), never by Android logic. Navigation 3 noted as
   a future upgrade once stable.

9. **Multi-module Gradle.** Clear separation, faster incremental builds, enforced
   boundaries.

---

## 2. Module structure

```
:app                      App entry, DI bootstrap, navigation host, theme host
:core:designsystem        Compose theme from KMM tokens, brand components, phase colours
:core:ui                  Shared composables (cards, buttons, sheets, calendar, states)
:core:common              Android utils, Result/UiState helpers, KMM<->Compose adapters
:core:platform            Android adapters: Health Connect, FCM, Maps, location,
                          ConnectivityManager (NetworkStatus actual), biometric,
                          WorkManager sync, camera/photos, haptics, share, Play Billing
:feature:auth             Phone + OTP, country picker
:feature:onboarding       All 8 onboarding flows (state machine driven by KMM)
:feature:home             Home hub, phase hero, day detail, quick log entry
:feature:calendar         Month/week/year calendar, markers, summary
:feature:logging          Daily log sheet (flow, mood, symptoms, meds, notes)
:feature:care             Be Her Sakhi: invite, accept, permissions, partner view
:feature:ai               Sakhi chat, cards, safe places, suggestions
:feature:recommendations  Curated foods + tips + AI insight
:feature:profile          Settings, account, privacy, Health Connect, data export
:feature:reports          Cycle report build + PDF export + share

SakhiCore (KMM)           Included build; Android depends on its androidTarget
```

Rule: `:feature:*` depends on `:core:*` and `SakhiCore`, never on each other.
`:app` wires DI and navigation across features.

---

## 3. KMM integration contract (what Android must wire)

From the KMM inventory, the shared API is `StateFlow`-based with no Swift-style
bridges needed on Android, and no DI yet. Android startup must:

1. **Initialise platform storage** (already in `androidMain`):
   `PlatformTokenStorage().init(context)` and `PlatformKeyValueStore().init(context)`
   in `Application.onCreate`.
2. **Inject build secrets** via `BuildConfigProvider` (Supabase URL/anon key, Claude,
   Google Places, Exotel, Sanity, Razorpay, USDA) from Android `BuildConfig`, sourced
   from Gradle properties / CI secrets, never committed.
3. **Provide `NetworkStatus` actual** backed by `ConnectivityManager` (the KMM
   `androidMain` stub currently returns always-online).
4. **Provide `LocalDatabaseInterface`** via the new Room KMP implementation (entities +
   DAOs + migrations in `commonMain`, bundled SQLite driver), so the sync queue and
   offline cache work.
5. **Provide a biometric adapter** for the declared (but unimplemented) biometric port.
6. **Observe stores** in ViewModels with `collectAsStateWithLifecycle`:
   - `AppStateStore.appRoute` (root routing)
   - `SessionManager.session` (`can(permission)`, role, target user)
   - `OnboardingFlowStore.state` + `.completion`
   - `CareStore.careState`, `.disconnectNotice`, `.latestRealtimeStatus`
   - `SyncStore.syncState`, `.partnerHealthSnapshot`
   - `AuthRepository.authState`, `.sessionState`
7. **Call repositories** as `suspend` returning `Result<T>` inside `viewModelScope`:
   `authRepository`, `userProfileRepository`, `cycleDataRepository`,
   `periodLogRepository`, `partnerCareRepository`, `aiRepository`,
   `recommendationRepository`, `accountRepository`, `partnerHealthSnapshotRepository`,
   `deviceRepository`, `notificationRepository`, `sanityRepository`,
   `mlTrainingRepository`, `paymentRepository`.
8. **Use shared logic directly** (no re-implementation): `CycleMath`, `CalendarMarker`,
   `PeriodLogPolicy`, `ValidationRules`, `AIQueryClassifier`, `AICardClassifier`,
   `Permission` + `SessionContext.can`, `DeepLinkParser`, `OnboardingFlowPolicy`,
   `AccountClassifier`.

KMM gaps to fill during Phase 0 (in KMM, shared by both platforms where sensible):
Room KMP `LocalDatabaseInterface` impl, `NetworkStatus` Android actual, biometric
port actual, and a Koin `commonMain` module that constructs the stores/repos.

---

## 4. Design system in Compose (exact 1:1 replica of iOS)

Hard rule: the Android UI is a pixel-for-pixel replica of the iOS app. The iOS
`SakhiApp/DesignSystem/` and every iOS screen are the spec. Do not change layouts,
spacing, colours, radii, fonts, icons, copy, transitions, or motion. Where this plan
describes the design, it is describing what already exists on iOS so Android reproduces
it exactly.

Source of truth for values is KMM `team.sakhi.design` (`DesignTokens`, `SakhiColorSystem`,
`PhaseVisualStyle`), the same tokens iOS consumes. `:core:designsystem` maps those tokens
into a Material 3 theme, and `:core:ui` reproduces each iOS DS component 1:1. Build an
explicit "iOS DS component -> Compose component" mapping and verify each with a
side-by-side screenshot against iOS before a screen is considered done.

**Brand foundation (from `04-Design/`):**
- Primary Pink `#F61887` (CTAs, active borders, key icons), Deep Pink `#BB2968`
  (pressed/hover), Deep Burgundy `#6D1743` (dark accents, dark-mode primary).
- Background Blush `#F8F2F4` (app background), Soft Blush `#F8E5EC` (cards/inputs),
  White lifts cards off blush.
- Text Black `#1C1C1E`, Body Gray `#6B6B7A`, Light Gray `#E5E4EA` (dividers/inactive),
  Success Green `#34C759`. Red reserved for critical errors only.
- Never purple as brand (that is Flo), never dark background as default (that is Clue),
  no gradients as the primary device.

**Phase colours:** consume the 13-token phase bundles from KMM (`menstrual`,
`follicular`, `ovulation`, `luteal` reuses follicular, `delayed`, `unknown`) via
`SakhiColorSystem` for light/dark. Drive the Home hero, calendar markers, and phase
chips from these.

**Typography:** General Sans (FontShare) as the brand face, Roboto fallback. Type scale
from KMM `FONT_*` tokens (28sp screen title bold down to 10sp caption). Max two weights
per screen, one dominant heading per screen, 1.4x body line height, 1.1x headings.

**Spacing and shape:** 4dp grid from KMM `SPACE_*`; radii from `RADIUS_*` (12 to 16dp
cards, `RADIUS_FULL` capsule CTAs). Elevation/opacity tokens from KMM.

**Three visual contexts** (mirror iOS): Pink (onboarding/auth, blush fill, white cards
r16), System (sheets/profile/settings, grouped background, white cards r12), Aurora
(Home only, soft phase-gradient blob background, translucent cards r24).

**Component library in `:core:ui`** (Compose equivalents of iOS DS components):
PrimaryButton (capsule), SecondaryButton, BackButton, SakhiTextField (+ selection/error
borders), OtpField (6 cells), GlassCard / SurfaceCard, BottomSheet scaffolds,
SakhiCalendar (month grid + markers), LoadingShimmer, EmptyState, SakhiAlert,
PhaseBadge, ToastHost, OfflineBanner.

**Fidelity rule (not a redesign):** reproduce the iOS experience exactly. Same Home
hero and layout, same calendar, same sheets, same day-detail glass card, same
transitions and timing. Match iOS motion (sheet presentation, phase transition, logging
confirmation, haptics) rather than inventing new motion. Same empty, loading, and error
states as iOS. Same copy as iOS (already in the shared CMS/Sanity content), no new
strings. If something looks different from iOS, it is a bug to fix, not a design choice.

---

## 5. Feature parity map (iOS feature, Compose screens, KMM dependency)

- **Auth**: PhoneScreen, OtpScreen, CountryPicker. KMM `AuthRepository.sendOtp/verifyOtpAndClassify`, `ValidationRules`, `AppStateStore`.
- **Onboarding**: FlowHost rendering steps from `OnboardingFlowStore.state` (intro, mode select, privacy, phone/otp, DOB, height, weight, last period, period/cycle length, health conditions, terms, setup loading, celebration, partner-invite tail). All 8 flows via `OnboardingFlowKind`/`OnboardingFlowPolicy`; validation via `ValidationRules`; Health Connect import in `:core:platform`.
- **Home**: HomeScreen (phase hero + countdown ring + cards), DayDetail, quick-log entry, partner snapshot. KMM `CycleDataRepository`, `CycleMath`, `CalendarMarker`, `SessionManager`, `SyncStore`.
- **Calendar**: CalendarScreen (month/week/year), markers, SummaryCard. KMM `CalendarMarker.buildMarks`, `CycleMath`, permission filter via `SessionContext.can`.
- **Logging**: LoggingSheet (flow, mood, symptoms, sexual activity, meds, notes). KMM `PeriodLogRepository`, `PeriodLogPolicy` (effective action, same-day edit, flow merge, care-viewer mutation), `ValidationRules`.
- **Care (Be Her Sakhi)**: InviteScreen/CodeSheet, AcceptInvite, LogPermissionSheet, PartnerDetail, partner Home/Calendar. KMM `CareStore` (+ Supabase Realtime via `CareRealtimeCoordinator`), `PartnerCareRepository`, `Permission`, `PartnerHealthSnapshotRepository`, `SyncStore.partnerHealthSnapshot`.
- **AI (Sakhi chat)**: ChatScreen, message bubbles, input bar, suggested chips, rich cards (cycle, health, places, report), safe-place results. KMM `AIRepository.sendMessage/generateWelcomeMessage/generateSuggestionChips/generatePartnerChecklist`, `AIQueryClassifier`, `AICardClassifier`; location + Maps in `:core:platform`.
- **Recommendations**: food eat-more/avoid + phase/condition tips + AI insight, three-layer load. KMM `RecommendationRepository`, `AIRepository`.
- **Profile**: Profile menu, EditProfile, Health data, MyData/export, Notifications, Appearance, PrivacySecurity, CareModeSettings, About/Legal/FAQ, OfflineMode, DataReset. KMM `UserProfileRepository`, `AccountRepository.deleteServerAccount`, feature-access/sync state, `SanityRepository`.
- **Reports**: ReportConfig (date range), ReportPreview, PDF export/share. KMM `ReportDataBuilder.build`; PDF via Android (Compose-to-PDF or `PdfDocument`).
- **App shell / navigation**: RootScaffold observing `AppStateStore.appRoute`; NavHost with type-safe routes; same tab/bottom-nav structure as iOS; a modal sheet lane that mirrors iOS sheet/bottom-sheet presentation exactly (same detents, drag-to-dismiss, backgrounds, transitions); deep links via `DeepLinkParser` (invite, report, profile, care, AI, emergency). Navigation structure, stack behaviour, and every sheet/modal match iOS 1:1.

---

## 6. Platform adapters (`:core:platform`, Android equivalents of iOS native)

- HealthKit -> **Health Connect** (read/write period, symptoms, sleep, temperature; permission UI; background read). Feeds KMM `HealthImportPolicy`.
- Realm -> **Room KMP** cache (in KMM `commonMain`) behind KMM `LocalDatabaseInterface`.
- APNs/UserNotifications -> **FCM** + local notifications; payloads parsed by KMM `NotificationPayloadParser`; token via `deviceRepository`.
- Google Maps SDK + CLLocation -> **Maps SDK for Android** + **FusedLocationProvider** for safe-place search (AI safety intent).
- StoreKit -> **Play Billing** for subscriptions (KMM `PaymentRepository`, Razorpay for India as on iOS; verify the Android payment path).
- BackgroundTasks -> **WorkManager** for durable sync queue and partner-health refresh.
- Keychain -> **EncryptedSharedPreferences / Android Keystore** (already in KMM `androidMain` `PlatformTokenStorage`).
- ConnectivityManager -> `NetworkStatus` actual (replace KMM stub).
- Camera/Photos -> Photo Picker + camera intents for avatar.
- Exotel call -> Android Exotel SDK/HTTP adapter (KMM `PhoneCallRepository`).

---

## 7. Build phases and checklist (full parity before launch)

Order optimises for foundations first, then a working Android shell, then feature fill,
then care/AI, then reports and polish. Android must move low level to high level:
shared store, managers, adapters, DI, navigators, sheet lanes, permission gates,
sync/offline owners, design-system skeleton, reusable UI skeleton, then screens.
"Full parity before launch" means all phases complete before the first public release.

### Phase A, Shared Realm -> Room KMP migration gate
- [ ] Inventory every Realm-backed dataset that Android will share: profile, period logs, cycles, care partnerships, partner invitations, partner checklists, AI messages, health samples, report cache, and sync queue.
- [ ] Define the shared migration contract in KMM first: export shape, import shape, canonical primary key, owner user ID, `updatedAt`, and stable payload fingerprint for each dataset.
- [ ] Implement the shared Room KMP schema in `SakhiCore` `commonMain`, starting from lossless shared records and sync queue support, not Android-only entities.
- [ ] Build the iOS migration path as `Realm export -> shared migration coordinator -> Room import`, dataset by dataset, behind a rollback-safe cutover.
- [ ] Add shared migration audits: per-dataset row count parity, primary-key parity, and payload fingerprint parity before the Realm read path is retired.
- [ ] Run iOS dual-read validation on migrated datasets before any UI or sync path switches product truth from Realm to Room.
- [ ] Freeze the shared schema only after the audit is fully lossless, so Android starts on the final contract instead of a moving one.
- [ ] Acceptance: iOS can boot and render from the shared Room KMP store with no data loss, no duplicate IDs, no missing care or health rows, and no regressions in offline-upgrade or sync-queue behavior.

### Phase B, Foundation and shell
- [ ] Create Gradle project in `02-Platforms/02-Android` (Kotlin, AGP, version catalog, JDK 17, minSdk 26, target latest, Compose BOM).
- [ ] Add `SakhiCore` as an included build (composite) and depend on its `androidTarget`.
- [ ] Set up module skeletons (`:app`, `:core:*`, `:feature:*`) with convention plugins.
- [ ] Build the low-level Android foundation before feature screens: `Application`, Koin bootstrap, platform managers, repository adapters, route owner, sheet owner, permission owner, sync owner, offline/cache owner, notification owner, haptics owner, and shared error/loading owners.
- [ ] Add Koin: a `commonMain` module in KMM constructing stores/repos, plus an Android module for platform bindings (Context, Health Connect, FCM, Maps, location, Room database builder/driver).
- [ ] Implement `NetworkStatus` Android actual (ConnectivityManager) and biometric port actual.
- [ ] Wire `BuildConfigProvider` from `BuildConfig` (secrets via Gradle props/CI), init `PlatformTokenStorage`/`PlatformKeyValueStore` in `Application`.
- [ ] Build `:core:designsystem`: Material 3 theme from KMM `DesignTokens`/`SakhiColorSystem`, General Sans font, light/dark, phase palettes.
- [ ] Build `:core:ui` base components (buttons, text fields, OTP, cards, sheets, calendar, shimmer, empty, alert, toast, offline banner).
- [ ] Build the Android UI skeleton before feature detail: root Compose shell, app background, top/bottom bars, modal sheet lane, loading/error/empty surfaces, keyboard-safe input lane, shared scaffold, and placeholder routes for every major feature.
- [ ] Root shell: Compose NavHost + bottom nav, observe `AppStateStore.appRoute`, splash. The shell is route-driven by KMM and must not contain feature-specific business decisions.
- [ ] Save this plan as `02-Platforms/02-Android/README.md`; mirror the checklist into a dated `01-HQ/05-Product/Engineering/` doc.
- [ ] Android thin-shell audit script (mirror iOS `Scripts/audit_kmm_thin_shell.py`); wire into CI to fail on any forked business logic in Android.
- [ ] CI: Gradle build, KMM `jvmTest`, Android unit tests, ktlint/detekt, screenshot test runner, thin-shell audit.
- [ ] Acceptance: app launches, renders the KMM-driven route (Splash/SignedOut/Home), foundation owners exist, UI skeleton exists, theme matches iOS, no business logic in Android.

### Phase 1, Auth and onboarding
- [ ] Phone + OTP screens; KMM `AuthRepository`, `ValidationRules` for phone/OTP.
- [ ] Account classification + routing via KMM (`AccountClassifier`, `AppStateStore`).
- [ ] Onboarding FlowHost rendering `OnboardingFlowStore.state`; all step screens.
- [ ] All 8 flows wired via `OnboardingFlowKind`/`OnboardingFlowPolicy`; field validation via KMM; setup-loading and completion via `OnboardingFlowStore.completion`.
- [ ] Health Connect import path (optional during onboarding) feeding KMM import policy.
- [ ] Offline (local-only) onboarding path and later upgrade.
- [ ] Acceptance: new user, returning, offline, and partner-invite-accept flows all reach Home via KMM routing; validation parity with iOS.

### Phase 2, Home, calendar, logging, cycle, offline sync
- [ ] Home hero (phase + countdown ring + cards), day detail, quick log entry.
- [ ] Calendar month/week/year with markers from `CalendarMarker`; summary card.
- [ ] Logging sheet (flow, mood, symptoms, sexual activity, meds, notes) via `PeriodLogRepository` + `PeriodLogPolicy`; optimistic write + undo.
- [ ] Phase/predictions strictly from `CycleMath` (no Android cycle math).
- [ ] Room KMP cache + `SyncStore` offline-first: read cache, sync queue, retry, freshness, conflict policy; WorkManager background sync.
- [ ] Acceptance: log offline then sync; Home/Calendar/Reports agree on phase and dates; one device per account enforced.

### Phase 3, Care (Be Her Sakhi) and realtime
- [ ] Invite create + code share; accept invite (code + deep link).
- [ ] Permission configuration via KMM `Permission`; partner view (Home/Calendar) using `PartnerHealthSnapshot` and `SessionContext.can`.
- [ ] Partner logging gated by `PeriodLogPolicy.canCareViewerMutate`.
- [ ] Supabase Realtime via KMM `CareRealtimeCoordinator` (broadcast primary), disconnect notice, revision-safe refresh.
- [ ] Acceptance: full invite/accept/permission/disconnect lifecycle; no health data in realtime payloads/logs; voluntary and private (never prompted as a metric).

### Phase 4, AI chat, safe places, recommendations
- [ ] Chat UI (bubbles, input, suggested chips, typing) consuming `AIRepository`.
- [ ] Intent and card type from KMM `AIQueryClassifier`/`AICardClassifier`; rich cards.
- [ ] Safe-place/location flow: Maps SDK + FusedLocation; permission filtering before context leaves device.
- [ ] Recommendations three-layer load (curated -> enrich -> AI insight).
- [ ] Acceptance: chat parity (intents, cards, partner-mode chips); permission masking enforced; safety flow works offline-degraded.

### Phase 5, Profile, settings, Health Connect, data export
- [ ] Profile menu and all sub-screens (edit, health data, my data/export, notifications, appearance, privacy/security, care-mode settings, about/legal/FAQ).
- [ ] Offline mode toggle (pause/resume sync, disconnect partner), account deletion via `AccountRepository.deleteServerAccount`.
- [ ] Health Connect sync control and status.
- [ ] Acceptance: settings write through shared preference/feature-access contracts; deletion and offline behaviour parity.

### Phase 6, Reports
- [ ] Report config (date range) and preview; data from `ReportDataBuilder.build`.
- [ ] PDF generation and share (Compose-to-PDF or `PdfDocument`).
- [ ] Acceptance: report stats/insights match KMM for regular, irregular, missing, and partner-restricted data.

### Phase 7, Notifications, deep links, widgets
- [ ] FCM register + token upload; payload parsing via KMM; tap routing via `DeepLinkParser`.
- [ ] Reminder scheduling via KMM rules + WorkManager/AlarmManager.
- [ ] Deep links (invite, report, profile, care, AI, emergency).
- [ ] Optional home-screen widget (cycle day/phase) via Glance.
- [ ] Acceptance: notification preview rules respect privacy; deep links resolve via KMM.

### Phase 8, Quality, performance, release
- [ ] Accessibility: TalkBack labels, focus order, touch targets, WCAG AA contrast, dynamic font scaling.
- [ ] Localization: all strings via shared CMS/Sanity content contract; 14+ languages; RTL check; no hardcoded copy.
- [ ] Performance: cold start budget, Baseline Profiles, strong-skipping-friendly stable params, no jank in calendar/chat scroll, image loading (Coil).
- [ ] Dark mode polish across all contexts.
- [ ] Security/privacy: no health data in logs/notifications/realtime; encrypted storage; certificate pinning parity (KMM); Play Data Safety form.
- [ ] Testing: ViewModel unit tests, Compose UI tests, screenshot tests (Roborazzi/Paparazzi), KMM `jvmTest` green, logic parity tests (same KMM input -> same marker/phase/permission/report as iOS), instrumentation on Health Connect/FCM/Maps, manual device matrix.
- [ ] Visual + flow parity gate: side-by-side comparison of every screen, flow, and sheet vs iOS in matching states (incl. flow step order/branching and sheet presentation); each signed off as 1:1 before release.
- [ ] Play Store: listing, screenshots, privacy policy link, data safety, internal -> closed -> production tracks, app signing.
- [ ] Acceptance: full feature parity verified against iOS, quality bar met, store-ready.

---

## 8. Quality bar (exact iOS parity + engineering polish)

- Visual parity: every screen matches its iOS counterpart 1:1 (layout, spacing, colour,
  radius, font, icon, transition, motion, empty/loading/error states). Verified by
  side-by-side screenshot comparison against iOS.
- Behaviour parity: same flows, same gestures, same haptics, same optimistic logging and
  undo, same offline behaviour as iOS.
- Dark mode matches the iOS dark appearance exactly (same `SakhiColorSystem` resolution).
- Localization: same shared CMS/Sanity strings and languages as iOS, no new copy.
- Accessibility: TalkBack labels, focus order, touch targets, WCAG AA contrast, dynamic
  font scaling (without breaking the iOS-matched layout).
- Performance: no layout jank; smooth scroll on calendar and chat; cold-start budget;
  Baseline Profiles; stable params for strong skipping.

---

## 9. Testing and verification (end to end)

- KMM logic: `./gradlew jvmTest` in `SakhiCore` (already green; extends to Room KMP DAOs,
  Koin graph, NetworkStatus).
- Android unit: ViewModel state-machine tests against fake KMM stores.
- Compose UI + screenshot tests per screen and per phase theme.
- **Visual + flow parity gate**: for every screen, flow, and sheet, capture iOS and
  Android in the same state and compare side-by-side. Nothing is "done" until it matches
  iOS 1:1 (layout, spacing, colour, type, icon, motion, sheet presentation, and the full
  flow step order and branching). Maintain an iOS-reference capture set as the baseline.
- Logic parity tests: feed identical KMM inputs and assert Android renders the same
  phase, markers, permissions, and report numbers as iOS (guards against drift).
- Instrumented: Health Connect read/write, FCM token + notification tap routing, Maps
  safe-place search, offline-then-sync, care invite/accept/realtime on two devices.
- Manual matrix: small/large phones, foldable, Android 8 (minSdk 26) to latest, light
  and dark, slow network and offline.
- Build/run: `./gradlew :app:assembleDebug` and run on a Pixel emulator (e.g. Pixel 8,
  API 35); release: `:app:bundleRelease` with signing.

---

## 10. Risks, dependencies, and follow-ups

- **KMM and local-store work to fill first** (Phase A / Phase B): shared Room KMP
  schema, shared migration coordinator, migration audit tooling, `NetworkStatus`
  Android actual, biometric port, Koin `commonMain` module. These touch shared code,
  so add common tests and keep iOS stable while the Room cutover is in progress.
- **Realm -> Room KMP is now the Android gate**, not a side project. Android should
  not start on a temporary Realm contract and then re-migrate later.
- **Secrets management**: Supabase/Claude/Maps/Exotel/Razorpay keys via Gradle
  properties and CI secrets, never committed; `BuildConfigProvider` injection.
- **Payments**: confirm Android billing path (Play Billing vs Razorpay) with the owner
  before Phase 5/6 billing work.
- **AI/safety**: ensure permission masking happens before any context leaves the device,
  matching the server-enforced KMM contract.
- **Privacy**: Be Her Sakhi stays voluntary and private; never a metric or prompt.

---

## 11. Definition of Done (project)

The Android app ships when all are true:
- All phases complete, including the Phase A Realm -> Room migration gate.
- 100% iOS parity verified: every screen, flow, and sheet signed off 1:1 against iOS via
  the visual + flow parity gate.
- Zero forked business logic: the Android thin-shell audit is clean in CI; all rules come
  from KMM; `jvmTest` green and iOS still builds.
- Database is Room KMP behind `LocalDatabaseInterface`; offline logging and sync verified.
- Privacy verified: no health/care data in logs, notifications, or realtime payloads.
- Accessibility (TalkBack, contrast, font scaling), localization (same strings as iOS),
  performance (cold start, no jank, Baseline Profiles) all pass.
- Play Store data-safety, signing, and release tracks configured; closed test passed.

---

## 12. Reference docs

- `01-HQ/05-Engineering/Platform-Cheatsheets/Android-2026-Cheatsheet.md`
- `01-HQ/05-Product/Engineering/2026-06-23-KMM-Shared-Core-Android-iOS-Architecture-Plan.md`
- `01-HQ/05-Product/Engineering/2026-06-23-Supabase-Broadcast-Care-Realtime-Checklist.md`
- `02-Platforms/00-Shared/SakhiCore/KMM-NETWORK-INFRASTRUCTURE-CHECKLIST.md`
- `01-HQ/04-Design/Branding/` (brand, colour palette, typography, voice)
- `02-Platforms/00-Shared/SakhiCore/src/commonMain/kotlin/team/sakhi/design/` (tokens)
- `02-Platforms/01-iOS/SakhiApp/` (feature reference, do not port logic)
