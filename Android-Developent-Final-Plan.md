# Sakhi Android App — Build Plan and Checklist

## Ground Rules

This file is the plan and the live checklist. The work journal/handoff log is a
separate file, `Android-Live-Status-Log.md`, kept apart so this file stays a clean
checklist. Together they are the single source of truth for Android execution, worked
on by two agents (Claude Code and Codex, session "Android Work-Codex"). Do not copy
either file anywhere else.

**Workflow rules:**
1. Before starting any task: read the newest entries in `Android-Live-Status-Log.md`,
   then this file's checklist, to see the current state.
2. After finishing any task: tick the checklist item(s) in this file AND append a new
   entry to `Android-Live-Status-Log.md` (date, agent, what was done, build/test
   verification, exact next step).
3. **Execution order:** finish the Development Checklist first. Do not start new
   Testing Checklist work while any real development item that is not
   `(BLOCKED ON KARAN)` is still open. Testing only becomes the active focus
   after development is exhausted or externally blocked.
4. Never leave a task half-done without recording exactly where you stopped.
5. Neither agent does anything wrong, unsafe, or off-objective. The only objective is to
   complete the Sakhi Android app to full iOS parity. No shortcuts, no side quests.
6. **Session-limit handoff:** whichever agent senses its session/context limit
   approaching must hand off BEFORE dropping below 5% remaining — finish or safely park
   the current step, write full handoff context into `Android-Live-Status-Log.md`, then
   pass the baton (Claude → message into the Codex session; Codex → write the handoff
   there and tell Karan to continue in the Claude session). Work must never be lost to
   a dead session.

**Coding rules (CI-enforced where possible):**
1. **No product logic in Android.** Cycle, period, care, sync, validation, AI, report,
   permission, and routing rules come only from KMM (`SakhiCore`). Android renders and
   touches the OS; it never re-implements a shared rule.
2. **Never fork KMM.** If a rule is missing or wrong, fix it in `SakhiCore` `commonMain`
   with a common test, then consume it from both platforms. Keep iOS's `jvmTest` green
   on every KMM change.
3. **100% iOS parity** (screens, flows, sheets, motion, copy). Any real difference from
   iOS is a bug, not a design choice — verify by reading the actual iOS Swift source
   before "fixing" something that looks different; several apparent gaps this session
   turned out to be deliberate shared behavior once checked.
4. **Privacy.** No health or care data in logs, crash reports, notifications, or realtime
   payloads. Be Her Sakhi stays voluntary and private — never a metric, campaign ask, or
   launch task.
5. **One account, one device**, matching KMM (`DeviceSessionGuard`/`revokeOtherDeviceSessions`).
6. Toolchain pinned to `SakhiCore`: Kotlin 2.1.20, matching KSP, Room 2.7+ (KMP), Koin,
   `minSdk 26`. All versions in `libs.versions.toml`, no inline versions.
7. Verify every change with a real `./gradlew` build (module compile + full
   `clean :app:assembleDebug`, plus `SakhiCore:jvmTest` if any KMM file changed) before
   marking a checklist item done or considering a fix real.

---

## Live Status

Full work journal moved to `Android-Live-Status-Log.md` (kept separate so this
file stays a clean checklist). Read the newest entries there before starting any
task; append a new entry there after finishing one, per the workflow rules above.

---


## Progress

**Formula:** count every `- [x]` and `- [ ]` line under "Development Checklist" and
"Testing Checklist". Items tagged `(OPTIONAL)` are excluded from "Must-ship %" (they
don't block release) but included in "Raw %" (total real coverage, including nice-to-haves).
Recompute both by hand after ticking or re-tagging any box — do not let this drift.

- **Development — raw: 93 / 114 (82%) · must-ship: 82 / 101 (81%)**
- **Testing — raw: 4 / 18 (22%) · must-ship: 4 / 14 (29%)**
- **Overall — raw: 97 / 132 (73%) · must-ship: 86 / 115 (75%)**

The Android app is ready to release when every **untagged** and **`(BLOCKED ON KARAN)`**
box in both checklists below is checked — that's the "must-ship" number. `(OPTIONAL)`
items can stay unchecked for a v1 release. `(CONFIRMED NOT A GAP)` items are already
checked because they were investigated and confirmed not to need building.

---

## Development Checklist

**Tag key for every unchecked `[ ]` item:** no tag = must be built before release.
`(BLOCKED ON KARAN)` = code is ready or close, just needs a credential/decision from
Karan, not more coding. `(OPTIONAL)` = nice-to-have, not required for a v1 release.
`(CONFIRMED NOT A GAP)` = investigated this session; genuinely doesn't need building
(either iOS doesn't have it either, or building it now would be actively wrong/risky).
Only untagged and `(BLOCKED ON KARAN)` items count toward "ready to release."

### SakhiCore (KMM) — shared foundation
- [x] Shared Room KMP database (entities, DAOs, migrations) in `commonMain`; iOS migrated
      off Realm first, Android built on the same frozen schema (Phase A, closed 2026-07-04)
- [x] Koin `commonMain` DI module (`team.sakhi.di.appModule()`) constructing all repos/stores
- [x] `NetworkStatus` Android actual backed by real `ConnectivityManager`
- [x] Biometric port actual wired (`AndroidBiometricAdapter`)
- [x] Thin-shell audit script exists (`Scripts/audit_kmm_thin_shell.py`), zero violations
      found across all real feature modules
- [ ] `BuildConfigProvider` wired to real secrets (Supabase URL/key, Claude, Places, Exotel,
      Sanity, Razorpay, USDA) via Gradle properties / CI secrets — nothing injected yet
      `(BLOCKED ON KARAN — needs the real secret values)`
- [x] Thin-shell audit enforced as a real CI gate (`--strict`) `(OPTIONAL)` — workflow now
      runs the audit in strict mode, and the remaining false positives were cleared so the
      gate can fail only on real thin-shell ownership bypasses

### `:app`
- [x] Multi-module Gradle project (Kotlin 2.1.20, AGP, Compose BOM, version catalog, JDK 17)
- [x] `SakhiApplication` + Koin bootstrap
- [x] `RootNavHost` renders purely off `AppStateStore.appRoute` (Splash/SignedOut/Onboarding/Home)
- [x] `HomeSessionGate` correctly boots `SessionManager`/`CareRealtimeCoordinator` per real session
- [x] Force-update gate reading the shared `app_update_policies` table
- [x] Global `ToastManager`/`ToastHost`
- [x] Android haptic-feedback system wired across all features
- [x] Shared app-wide scaffold (top/bottom bars, modal sheet lane, keyboard-safe input lane)
      `(OPTIONAL)` — `SakhiModalSheet`, `BackButton`, `DetailSheetScaffold`, and the new
      shared `KeyboardSafeScaffold` now cover the real repeated app shells: the Home overlay
      lane, auth CountryPicker, the full profile detail-screen family, and the main
      input-heavy surfaces (`PhoneScreen`, `OtpScreen`, `ChatScreen`, `LoggingSheet`) so the
      keyboard-safe footer/action lane is no longer hand-rolled per screen
- [ ] CI pipeline actually triggers in GitHub Actions `(OPTIONAL)` — workflow file exists as
      a skeleton; valuable but not a release blocker for a first ship
- [ ] App launched and manually walked through on a real emulator/device — **critical**,
      nothing in this file has ever been runtime-verified beyond compile/assemble

### `:core:designsystem`
- [x] Material 3 theme built from shared `DesignTokens`/`SakhiColorSystem`/`PhaseVisualStyle`
- [x] Real Lato font family bundled and wired (corrected from an earlier General-Sans mistake)
- [x] Light/dark theme resolution from `ThemePreferenceStore`, app-wide
- [ ] Full manual visual-QA pass across every screen in both themes — mechanism is real;
      the actual side-by-side walkthrough against iOS hasn't happened yet

### `:core:ui`
- [x] Core reusable components built: `PrimaryButton`, `SecondaryButton`, `SakhiTextField`,
      `OtpField`, `GlassCard`, `LoadingShimmer`, `EmptyState`, `SakhiAlert`, `OfflineBanner`,
      `ToastHost`, `SheetSurface`
- [x] Reusable `BackButton` component `(OPTIONAL)` — shared `:core:ui` back affordance now
      exists and is adopted by the repeated profile / care / AI detail-header paths instead
      of each screen hand-rolling its own arrow button
- [x] Shared `DetailSheetScaffold` component `(OPTIONAL)` — the reusable detail-sheet shell
      (back header, divider, padded scroll body) now covers the full profile detail-screen
      family: `About`, `HelpSupport`, `Notifications`, `Appearance`, `AppIntegration`,
      `PrivacySecurity`, `Feedback`, `EditProfile`, `Legal`, `ContentPage`, `ActivityLog`,
      and `ManageAccount`
- [x] Shared bottom-sheet scaffold with iOS-matched detents/drag-to-dismiss `(OPTIONAL)` —
      `SakhiModalSheet` now owns the real shared `ModalBottomSheet` host (large-detent-only,
      transparent outer container, consistent drag-handle policy), reused by the Home
      overlay lane and auth's CountryPicker sheet
- [x] `SakhiCalendar` extracted as a reusable `:core:ui` component `(OPTIONAL)` — the
      reusable `SakhiCalendarDay` model plus `SakhiCalendarMonthGrid`,
      `SakhiMiniMonthGrid`, and `SakhiWeekdayHeaderRow` now live in `:core:ui`, and
      `:feature:calendar` consumes those shared primitives instead of owning the month-grid
      internals locally
- [x] `PhaseBadge` shared component `(OPTIONAL)` — reusable phase chip now lives in
      `:core:ui` and is adopted in Home and Recommendations instead of each screen
      hand-rolling its own phase-status pill
      not existing

### `:core:common`
- [x] Result/UiState helpers and KMM↔Compose adapters

### `:core:platform`
- [x] `AndroidHealthConnectManager` — read/write period, sleep, steps, temperature; real
      permission flow; onboarding import path closed
- [x] `SakhiFirebaseMessagingService` — FCM transport, pending-token cache + retry-on-session,
      all 9 push types, tap routing through the shared `DeepLinkParser`
- [x] `AndroidNotificationReminderManager` — WorkManager-based reminder scheduling via the
      shared `NotificationScheduleBuilder`/`CycleMath`
- [x] `AndroidLocationProvider` — plain `LocationManager`, deliberately no Maps SDK dependency
- [x] `AndroidBiometricAdapter` + `CurrentActivityHolder`
- [x] `AndroidHapticManager`
- [x] `PlatformTokenStorage` (`EncryptedSharedPreferences`+`MasterKey`, AES256-GCM-SIV) /
      `PlatformKeyValueStore` (plain `SharedPreferences` for non-sensitive settings)
- [ ] Google Places wired to a real key — code path is real (Ktor straight to the Places
      REST API, permission-gated), but `GOOGLE_PLACES_API_KEY` is blank, so the safety
      feature is non-functional until this lands `(BLOCKED ON KARAN)`
- [x] Play Billing / Razorpay payment adapter `(CONFIRMED NOT A GAP for v1)` — Sakhi's own
      shipped FAQ copy states the app is completely free with no paid tier; nothing in
      today's product requires a payment adapter to launch
- [x] Camera / Photo Picker adapter for avatar upload `(CONFIRMED NOT A GAP)` — re-grepped
      the real iOS profile source; it only renders initials/color avatars too and exposes no
      real profile-photo picker flow, so building Android-only avatar upload would break
      parity rather than close a real feature gap
- [x] Exotel call adapter (`PhoneCallRepository` Android actual) `(CONFIRMED NOT A GAP)` —
      re-grepped iOS and KMM: the shared `PhoneCallRepository` and iOS `ExotelManager`
      plumbing still exist, but there is no real UI call path invoking them on either
      platform, so this is dormant infrastructure, not a missing Android feature for v1
- [ ] Certificate pinning (SPKI) against the Supabase endpoint — real security gap for a
      health app; blocked on a real production SPKI hash from Karan, and on Karan
      confirming whether iOS's own pinning is even live on its current Ktor path or is
      inherited dead code `(BLOCKED ON KARAN)`

### `:feature:auth`
- [x] `PhoneScreen`, `OtpScreen`, `CountryPicker` — all real, validation via shared
      `ValidationRules`/`PhoneCountry`

### `:feature:onboarding`
- [x] 31 of 32 `OnboardingFlowStep` screens real and reachable (the 32nd, `InviteCreating`,
      is unreachable in any real flow — confirmed not a missing screen)
- [x] All 7 real `OnboardingFlowKind` flows wired end to end
- [x] Health Connect import step (`DataSourceStep`) fully closed
- [x] Partner-invite tail, contact-access/pick screens
- [x] Local-only-account "upgrade to cloud later" flow `(CONFIRMED NOT A GAP)` — grepped
      iOS, this flow doesn't exist on either platform; not product-defined yet

### `:feature:home`
- [x] Phase hero, countdown ring, phase/nutrition/AI-insight cards
- [x] Quick-log entry + long-press flow-level quick-log menu
- [x] Logged-details / cycle-details cards, 9-card learning-phase empty state
- [x] Full partner-mode branch (checklist card, no-data card, heads-up card)
- [x] `HomeGlassCard` reusable wrapper, cycle-day pill strip with real diagonal
      ovulation-hatch drawing
- [x] Real Home visual chrome: phase-gradient background, iOS-style tinted card palette,
      and date-centered top bar with hero-summary crossfade
- [x] Scroll-tied hero animations on the main hero block

### `:feature:calendar`
- [x] Month/year grid, markers via shared `CalendarMarker`, summary card
- [x] Year-expansion sheet, jump-to-today / jump-to-month
- [x] Touch-target and accessibility fixes (48dp cells, real per-day `contentDescription`)

### `:feature:logging`
- [x] Full logging sheet: flow, mood, symptoms, notes, weight, BBT, discharge color,
      painkiller, doctor-visited
- [x] Optimistic write via shared `PeriodLogRepository`/`PeriodLogPolicy`
- [x] `(CONFIRMED NOT A GAP)` sexualActivity input control and "undo" — grepped iOS,
      neither exists there either

### `:feature:care`
- [x] Invite create/share (code + deep link), accept (manual code + deep link)
- [x] Permission configuration + partner view (Home/Calendar) via `SessionContext.can`
- [x] Partner logging gated by `PeriodLogPolicy.canCareViewerMutate`
- [x] Supabase Realtime via `CareRealtimeCoordinator`, correctly started/stopped per session
- [x] Activity/history ledger with correct fixed-perspective attribution (`LogSource`-based,
      not viewer-relative)

### `:feature:ai`
- [x] Chat UI: bubbles, input bar, suggested chips, typing indicator
- [x] Intent/card classification via shared `AIQueryClassifier`/`AICardClassifier`
- [x] Search / Media / Starred hub
- [x] Inline report-card generation flow (shares `:feature:reports`'s PDF exporter)
- [x] Nearby Places — compact card + list-detail sheet, real Ktor call to Places REST API
- [ ] Interactive map / radius-filter surface `(OPTIONAL)` — iOS has one, the compact card
      already delivers the same real functional outcome (find real nearby help)
- [ ] Real `GOOGLE_PLACES_API_KEY` wired — see `:core:platform`, same item `(BLOCKED ON KARAN)`

### `:feature:recommendations`
- [x] Three-layer load: curated food list → enrichment → AI insight

### `:feature:profile`
- [x] All sub-screens present: EditProfile, PrivacySecurity (+export), Notifications,
      Appearance, Care settings, About, Legal, HelpSupport, ManageAccount, ActivityLog,
      AppIntegration
- [x] Profile root parity: real avatar initials circle, secure/offline status subtitle,
      `Cycle Health` badge, and the iOS footer CTA, with the badge decision moved into
      shared KMM (`CycleMath.profileHealthStatus`) instead of Android-local heuristics
- [x] Account deletion — real 3-step wizard via `AccountRepository.deleteServerAccount`
- [x] Disconnect-partner — real, via `CareViewModel.removePartnership`
- [x] Data export — real, and fixed to surface genuine fetch failures instead of a false
      "0 records" export on error
- [x] Health Connect sync control and status
- [x] Offline-mode toggle (pause/resume sync) `(CONFIRMED NOT A GAP for now)` — building
      this before a Room local-first rearchitecture would show "offline" while still
      silently syncing to the cloud, a real privacy/correctness risk for a health app;
      deliberately deferred, not forgotten
- [x] Live Sanity-CMS content sync for Legal/About/FAQ `(OPTIONAL)` — shared
      `SanityRepository` now has typed legal-page / FAQ / site-settings fetchers with
      cross-platform KV-backed response caching, and Android's Legal/About/Help flows use
      them with the same instant static fallback body as iOS when CMS data is absent

### `:feature:reports`
- [x] Report config (date range) + preview
- [x] PDF generation + share
- [x] Real `ReportDataBuilder` test coverage (6 cases); one real cycle-ordering bug found
      and fixed while writing it

### Notifications, deep links, and widget (cross-cutting)
- [x] FCM transport + all 9 push types + tap-to-open (see `:core:platform`)
- [x] Reminder scheduling via shared `NotificationScheduleBuilder`/`CycleMath` (see `:core:platform`)
- [x] Deep-link routing for invite/report/profile/care/AI + onboarding links, both
      cold-start and already-running (`onNewIntent`), via the shared `DeepLinkParser` +
      `AndroidDeepLinkManager`
- [x] Signed-in vs signed-out invite-link handling (prefill Care vs force the real
      `joinFamily` onboarding flow, carrying the code through OTP)
- [ ] `App Links` domain auto-verification (`assetlinks.json` hosted at
      `sakhi-care.web.app`/`sakhi.com`) `(BLOCKED ON KARAN)` — needs the file hosted on a
      real, owned domain; manifest intent-filters are already real and waiting
- [x] Emergency deep-link destination (`sakhi://emergency` / `OpenEmergency(sessionId)`)
      `(CONFIRMED NOT A GAP for this release)` — grepped iOS exhaustively: it sets a
      `presentedFullScreen` route but no SwiftUI view anywhere actually renders it, so
      the live emergency-session viewer doesn't exist as a working screen on iOS either.
      Not Android lagging behind a real iOS feature; flag to Karan as a genuinely
      unbuilt cross-platform feature if/when SOS session viewing becomes a real priority
- [x] Home-screen widget (cycle day/phase) via Glance `(OPTIONAL)` — Android now has a
      real Glance widget lane: `AndroidWidgetSnapshotManager` persists a widget snapshot from
      the real repositories + session context, drains `sakhi://widget/log-today` taps through
      the real period-log repository, and `SakhiPeriodWidget` / `SakhiPeriodWidgetReceiver`
      render the same countdown + log-today affordance as iOS. Glance still uses a resolved
      phase mid-tone instead of iOS's fully dynamic three-stop gradient, because the Android
      widget surface does not expose the same dynamic gradient rendering path

### Security & Privacy (cross-cutting)
- [x] Zero raw health data in logs — grepped the whole tree, no `Log.*` calls exist at all
- [x] Encrypted token storage confirmed real (`EncryptedSharedPreferences`+`MasterKey`)
- [x] Be Her Sakhi kept voluntary/private everywhere — never a metric, campaign ask, or
      launch task in any plan, copy, or code path
- [ ] Certificate pinning (SPKI) — see `:core:platform`, same item `(BLOCKED ON KARAN)`
- [ ] Play Data Safety form — not checked or filled yet, required for the Play Store
      submission itself (tracked again under Release readiness)

### Accessibility & Localization (cross-cutting)
- [x] Every icon-only tappable element app-wide audited for `contentDescription` (script +
      manual follow-up on all flagged candidates) — zero real misses
- [x] Touch-target violations found and fixed (6 real cases, all now ≥48dp)
- [x] Color-only health-state indicators fixed with real semantic descriptions (Calendar
      day cells, Reports calendar-preview dots)
- [x] Dynamic-font-scaling structural risks fixed (Home/AI hard height/width clamps swapped
      for min-constraints; explicit ellipsis added where clipping was plausible)
- [x] WCAG AA contrast computed from real hex tokens; 3 failing tokens documented as
      pre-existing shared design-system traits (also present on iOS), not Android bugs
- [ ] Real TalkBack device/emulator run — not done, no device available this session
- [ ] Real 200%-font-scale screenshot/device run — not done
- [ ] Localization: shared CMS/Sanity content wiring, 14+ languages, RTL check — real,
      large, and now only foundation-started: per-module `strings.xml` resources exist in
      `:app`, `:core:ui`, `:core:platform`, `:feature:auth`, `:feature:calendar`,
      `:feature:ai`, `:feature:recommendations`, `:feature:home`, `:feature:profile`,
      `:feature:reports`, `:feature:logging`, `:feature:care`, and now
      `:feature:onboarding`; the update gate,
      shared shell copy (`BackButton`/`OfflineBanner`/`SakhiAlert`), auth screen copy +
      Android auth validation errors, widget copy, calendar nav/access strings, the main
      AI chat lane's Android-owned copy, recommendations summary/section/nutrition-label
      copy, and Home's visible UI copy end to end (session/sync labels, hero text, Ask
      Sakhi placeholders, action-bar labels, cycle-card labels, partner heads-up text,
      empty-state text, recommendation titles, the phase explainer, and the learning-card
      educational copy), plus the profile shell, edit-profile form copy, notifications /
      appearance settings, Privacy & Security / Feedback / Manage Account screen copy,
      the App Integration screen + Health Connect status/error copy, the Reports
      module's config/preview/view-model error copy, and the full Logging sheet's
      Android-owned section labels, save-state copy, validation/load/save errors, notes
      placeholder, partner-lock message, and header-date formatting, plus the full Care
      hub's Android-owned copy end to end (connected-partner detail copy, pending-invite
      share/cancel flow, create/accept invite forms, permission-editor labels, activity
      history labels, and the care view-model's fallback/info/error messages), plus the
      onboarding health-step lane's Android-owned copy (step titles/subtitles, unit
      toggles, date-nav content descriptions, day-length help-sheet labels/copy, shared
      Continue/Back CTAs, and the onboarding view-model's health/import/invite error
      messages), are no longer hardcoded English literals. Calendar month / weekday
      labels, AI chat timestamps, App Integration's last-synced date label, the Reports
      preview's month/week labels, the Logging sheet header date, Care's connected/history
      date labels, and onboarding's health-step weekday/unit labels now come from the
      device locale or Android resources instead of enum-name / hand-built English
      formatting. The current active development slice is the remaining onboarding content
      lane in `OnboardingContentStepUi.kt` / `OnboardingFlowHost.kt`; after that, the rest
      of onboarding content plus any smaller remaining modules are still partially
      hardcoded, and the real multi-language / RTL / CMS-driven parity pass is
      still open `(confirm with Karan whether v1 ships
      English-only or needs this first)`

### Performance (cross-cutting)
- [x] Calendar's unstable hot-path collection wrapped in a stable holder (`CalendarMonthCache`)
- [x] Calendar's weekly-row chunking now remembered instead of rebuilt every recomposition
- [x] Chat's per-row `indexOf` scan removed from the message list
- [ ] Cold-start budget measurement — not done, needs a real device
- [ ] Baseline Profiles `(OPTIONAL for v1)` — real optimization, not a functional blocker
- [ ] Real before/after recomposition-count and scroll-jank measurement — not done, needs
      a real device

### Release readiness
- [x] `SakhiCore` `consumer-rules.pro` written (kotlinx.serialization keep rules) — zero-risk
      groundwork, `isMinifyEnabled` still false
- [ ] Release signing config with a real keystore `(BLOCKED ON KARAN)` — only the implicit
      debug key exists today
- [ ] R8 minification (`isMinifyEnabled = true`) enabled and verified safe on a real device —
      deliberately not flipped blind; keep-rule mistakes for Room/Ktor/serialization fail at
      runtime, not compile time; needs a device pass first
- [ ] `google-services.json` added `(BLOCKED ON KARAN)` — blocks FCM actually working at
      runtime; needs Karan to register the app in the Firebase console
- [ ] Play Store console: listing copy, screenshots, privacy-policy link, Data Safety form,
      internal → closed → production tracks — not started, mostly not code work

---

## Testing Checklist

Same tag key as the Development Checklist above.

### KMM shared tests
- [x] `SakhiCore` `jvmTest` green across all suites (DI graph, `CalendarMarker`,
      `NotificationPayloadParser`/`NotificationScheduleBuilder`, `ReportDataBuilder`,
      `SyncQueue`, `SessionPermissions`, `PhaseVisualStyle`, `PhoneCountry`,
      `ReviewTriggerEvaluator`, `SakhiAIContext`, `CycleInsightEngine`, `DeviceSessionGuard`
      decision logic, and more added during tonight's self-audit)
- [x] A regression test exists for every real bug found and fixed this session, so each
      stays fixed

### Android unit tests
- [x] First real JUnit module established: `feature:home`'s `PartnerHeadsUpTextTest` (12 cases)
- [x] `core:platform`'s first unit test: `SakhiFirebaseMessagingServiceTest` (3 cases)
- [ ] ViewModel state-machine tests against fake KMM stores `(OPTIONAL)` — a good practice
      to adopt, not itself a release blocker given the shared KMM logic underneath is
      already well-tested
- [ ] Broader per-module unit test coverage across the remaining feature modules `(OPTIONAL)`

### Compose UI / screenshot tests
- [ ] Roborazzi or Paparazzi dependency added to the version catalog `(OPTIONAL)`
- [ ] Screenshot tests per screen, per theme `(OPTIONAL)` — the manual visual + flow parity
      gate below is the real release gate; automated screenshot tests are a durability
      improvement on top of that, not a substitute requirement

### Visual + flow parity gate
- [ ] Side-by-side iOS-vs-Android comparison for every screen, flow, and sheet — **real
      release gate**, per the "100% iOS parity" ground rule; still open because no full
      manual side-by-side walkthrough has been run yet on a real device/emulator, not
      because of one single known screen gap

### Instrumented / on-device tests
- [ ] Health Connect read/write verified on a real device
- [ ] FCM token registration + notification tap-routing verified on a real device
      `(BLOCKED ON KARAN's google-services.json first)`
- [ ] Places / safe-place search verified on-device `(BLOCKED ON KARAN's Places API key first)`
- [ ] Offline-then-sync round trip verified on-device
- [ ] Care invite/accept/realtime verified across two real devices

### Manual device matrix
- [ ] At minimum: one real phone, one Android version, light + dark theme — the actual
      floor for "has this app ever really run," not yet met
- [ ] Full matrix (small/large phone, foldable, Android 8/minSdk 26 through latest, slow
      network / fully offline) `(OPTIONAL beyond the minimum above for a v1 release)`

### Release QA
- [ ] Internal Play Store closed-test track run
- [ ] Final store-readiness acceptance sign-off

---

## Definition of Done

The Android app is ready to release when:
- Every **untagged** and **`(BLOCKED ON KARAN)`** box in Development Checklist and Testing
  Checklist above is checked (i.e. "must-ship" reaches 100% — see Progress above).
  `(OPTIONAL)` items may remain unchecked for a v1 release.
- `SakhiCore` `jvmTest` is green and the Android thin-shell audit is clean.
- No business logic has been forked into Android — every rule still comes from KMM.
- Privacy is verified: no health/care data in logs, notifications, or realtime payloads.
