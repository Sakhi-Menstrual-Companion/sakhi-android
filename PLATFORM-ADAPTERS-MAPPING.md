# Sakhi Android Platform Adapters Mapping

This document maps the current iOS native platform integrations to the planned Android `:core:platform` adapters.

It is based on the actual iOS source, not on generic platform assumptions.

If something is not implemented or not found in the iOS source, it is called out clearly.

## Sources reviewed

Primary Android planning source:

- `02-Platforms/02-Android/Android-Developent-Final-Plan.md`, Section 5 and Section 6

Primary iOS native adapter sources:

- `02-Platforms/01-iOS/SakhiApp/Platform/Device/HealthKitManager.swift`
- `02-Platforms/01-iOS/SakhiApp/Features/Onboarding/ViewModels/OnboardingViewModel.swift`
- `02-Platforms/01-iOS/SakhiApp/App/Lifecycle/AppDelegate.swift`
- `02-Platforms/01-iOS/SakhiApp/Platform/Notifications/NotificationManager.swift`
- `02-Platforms/01-iOS/SakhiApp/Platform/Permissions/PermissionsManager.swift`
- `02-Platforms/01-iOS/SakhiApp/App/Lifecycle/SakhiApp.swift`
- `02-Platforms/01-iOS/SakhiApp/Features/AI/Services/SakhiAIManager+Context.swift`
- `02-Platforms/01-iOS/SakhiApp/Platform/Background/BackgroundLocationManager.swift`
- `02-Platforms/01-iOS/SakhiApp/Platform/Background/BackgroundTaskManager.swift`
- `02-Platforms/01-iOS/SakhiApp/Core/Data/Infrastructure/KeychainKey.swift`
- `02-Platforms/01-iOS/SakhiApp/Platform/Device/PhotoPickerManager.swift`
- `02-Platforms/01-iOS/SakhiApp/Platform/Services/ExotelManager.swift`
- `02-Platforms/01-iOS/SakhiApp/App/Update/Update/UpdateView.swift`
- `02-Platforms/01-iOS/SakhiApp/Platform/Services/RazorpayManager.swift`

Supporting references:

- `02-Platforms/01-iOS/SakhiApp/Core/Configuration/EnvironmentManager.swift`
- `02-Platforms/01-iOS/SakhiApp/Core/KMM/SakhiCoreSDK.swift`
- `02-Platforms/01-iOS/SakhiApp/Core/Network/APIEndPoint.swift`
- `02-Platforms/01-iOS/SakhiApp/SupportingFiles/Info.plist`

## Adapter mapping table

| Capability | iOS implementation today | Android target | Parity notes for `:core:platform` |
| --- | --- | --- | --- |
| Health data import | `HealthKitManager` uses `HKHealthStore`, `HKSampleQuery`, `HKAnchoredObjectQuery`, `HKObserverQuery`, and `enableBackgroundDelivery(..., frequency: .immediate)`. Onboarding also does direct import through `OnboardingViewModel.importFromHealthKit()`. | Health Connect | Android needs the same two flows: onboarding prefill import and ongoing background sync. Match read scope, conflict handling, and incremental sync behavior. |
| Push notifications, remote token, local reminders | `AppDelegate` registers APNs, uploads token through `deviceRepo.registerToken(userId:token:platform:)`, handles background pushes. `NotificationManager` requests `.alert/.sound/.badge`, schedules local notifications, and parses payloads through KMM `NotificationPayloadParser`. | FCM + local notifications | Android should mirror token registration timing, KMM payload parsing, privacy-safe local reminder copy, and background sync push handling. |
| Maps and safe places | `SakhiApp` configures `GMSServices.provideAPIKey(...)`. `SakhiAIManager+Context` uses Google Places first, then falls back to `MKLocalSearch`. `BackgroundLocationManager` and `PermissionsManager` use `CLLocationManager`. | Maps SDK for Android + FusedLocationProvider | Android should keep the same layered search behavior: Google Places first, platform fallback second if that still makes sense, and shared KMM safe-place intent logic above it. |
| Billing and App Store product surfacing | Real in-app subscription flow was not located in iOS source. `UpdateView` uses `SKStoreProductViewController` only for forced-update App Store sheet. `RazorpayManager` is stubbed and only creates orders through KMM `paymentRepo`. | Play Billing + Razorpay path as needed | Do not invent a subscription adapter from thin air. Current iOS source shows update-sheet StoreKit usage and a not-live payment path. Verify final Android purchase scope with Karan before building. |
| Background work | `BackgroundTaskManager` registers `BGProcessingTask` and `BGAppRefreshTask` entries for sync, location update, session check, and data refresh. `AppDelegate` also supports `performFetchWithCompletionHandler`. | WorkManager | Android needs named workers for the same responsibilities, with real success or failure reporting, re-scheduling, and network constraints where needed. |
| Secure storage | `KeychainManager` uses `kSecClassGenericPassword` with `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`. Used for auth tokens, refresh token, password, app lock, and older Realm encryption key. | EncryptedSharedPreferences + Android Keystore | Mirror the split between string and binary secrets, device-only storage intent, and auth-token fast path expected by app bootstrap. |
| Camera and photos | `PermissionsManager` handles `AVCaptureDevice` video permission and `PHPhotoLibrary` read/write permission. `PhotoPicker` uses `PHPickerViewController` with `.images` and `selectionLimit = 1`. | Photo Picker + camera intents | Current iOS source clearly supports photo picking and camera permission checks. A real camera capture flow was not located in the reviewed iOS source, verify with Karan if Android needs capture on day one or only gallery pick. |
| Exotel SMS and calls | `ExotelManager.sendSMS()` builds Basic auth + form body and hits `APIClient` Exotel endpoints. `ExotelManager.makeCall()` delegates to KMM `phoneCallRepo.initiateCall(...)`. | Exotel Android SDK or HTTP adapter | Android should preserve the same credentials model, masking/privacy behavior, and KMM-owned call initiation path where possible. |

## Detailed behavior notes

## 1. HealthKit to Health Connect

Current iOS behavior:

- Availability gate: `HKHealthStore.isHealthDataAvailable()`
- Permission request: `requestAuthorization(toShare: [], read: HealthKitManager.healthKitReadTypes(...))`
- Read scope includes:
  - menstrual flow
  - intermenstrual bleeding
  - persistent intermenstrual bleeding
  - cervical mucus quality
  - ovulation test result
  - progesterone test result
  - contraceptive
  - sexual activity
  - irregular, infrequent, and prolonged menstrual cycle categories
  - symptom categories like cramps, back pain, pelvic pain, breast pain, headache, bloating, nausea, diarrhea, constipation, vomiting, fatigue, dizziness, fever, chills, acne, mood changes, and sleep changes
  - date of birth
  - height
  - body mass
  - for insights, sleep analysis, step count, and Apple sleeping wrist temperature
- Incremental sync uses `HKAnchoredObjectQuery`
- Realtime follow-up import uses `HKObserverQuery`
- Background delivery is enabled for all tracked menstrual and symptom categories with `.immediate`
- Import writes go through shared or app-owned persistence flows:
  - `PeriodLogObject.upsertDailyLog(...)`
  - `CycleDetectionEngine.shared.processLogChange(...)`
  - `dataManager.queueForSync(...)`
  - widget refresh and `dailyLogUpdated` notification
- Conflict rule is shared, iOS defers the final skip decision to `SakhiCore.HealthImportPolicy.shouldSkipImportDate(...)`
- Onboarding has a separate prefill path in `OnboardingViewModel.importFromHealthKit()`
  - imports DOB, height, weight, last period, observed period length, observed cycle length
  - groups HealthKit menstrual samples by date
  - computes period blocks from contiguous dates
  - validates imported values through KMM `ValidationRules`

Android parity target:

- `Health Connect` should support the same two entry points:
  - onboarding prefill import
  - ongoing integration after onboarding
- Keep incremental sync, not just full re-import every time
- Keep KMM as the owner of final conflict resolution and validation
- Preserve background refresh behavior if Health Connect permissions and platform rules allow it
- Match the imported domains, especially menstrual flow, spotting, symptoms, reproductive context, sleep, steps, and temperature

## 2. APNs and UserNotifications to FCM

Current iOS behavior:

- App launch sets `UNUserNotificationCenter.current().delegate = self`
- App launch calls `application.registerForRemoteNotifications()`
- `didRegisterForRemoteNotificationsWithDeviceToken` stores the APNs token, then registers it against the current authenticated user via `SakhiCoreSDK.shared.deviceRepo.registerToken(..., platform: "ios")`
- If auth is not ready yet, token registration waits until `.userDidSignIn`
- Background push path:
  - `application(_:didReceiveRemoteNotification:fetchCompletionHandler:)`
  - if payload contains `sync = true`, it runs `SyncManager.shared.syncAllPending()`
  - otherwise it parses the payload through `NotificationManager.shared.parseNotificationPayload(...)`
  - partner period log push refreshes care snapshots, syncs partner data, posts `dailyLogUpdated`, and reloads widget timelines
- Local notification path:
  - permission request uses `.alert`, `.sound`, `.badge`
  - local reminders use `UNMutableNotificationContent`, `UNTimeIntervalNotificationTrigger`, and `UNNotificationRequest`
  - cycle reminders are built by KMM `NotificationScheduleBuilder`
  - lock-screen body stays privacy-safe: `"Open Sakhi for today's update"`
- Tap handling:
  - foreground presentation returns `[.banner, .sound, .badge]`
  - tap routing includes KMM `NotificationPayloadParser`
  - SOS payload routes to `.navigateToSession`
  - some care permission requests still branch on explicit payload fields

Android parity target:

- Use FCM for remote token and remote payload delivery
- Register token against the authenticated user only when identity is ready
- Keep KMM as the source of truth for payload parsing semantics
- Preserve privacy-safe local reminder copy and lock-screen behavior
- Mirror the background push-triggered sync and care refresh behaviors

## 3. Maps, location, and safe-place search

Current iOS behavior:

- `SakhiApp.configureGoogleMaps()` reads the Google Maps API key and calls `GMSServices.provideAPIKey(...)`
- Safe-place lookup is in `SakhiAIManager+Context`
  - first layer: Google Places Nearby Search through APIClient
  - second layer: `MKLocalSearch` fallback when Google is missing or empty
  - search radius comes from app constants, not hardcoded in the feature surface
- Device location:
  - `CLLocationManager` is used in AI view model and background location manager
  - location permission is checked through `PermissionsManager`
  - background location upgrade uses `requestAlwaysAuthorization()`
  - `BackgroundLocationManager` enables `allowsBackgroundLocationUpdates = true`
  - starts both `startUpdatingLocation()` and `startMonitoringSignificantLocationChanges()`
  - throttles updates to at most once per 60 seconds
- Map rendering:
  - `SakhiAIPlacesCard` uses SwiftUI `Map`
  - not Google Maps UI, Google is used for API search, MapKit is used for fallback search and map presentation

Android parity target:

- Use `FusedLocationProvider` for device location
- Use Maps SDK for Android for visible map UI
- Keep the same search layering logic if business wants Google-first plus platform fallback
- Keep location permission upgrade behavior explicit, especially background access if safe-place or care flows depend on it
- Keep throttling and background update logic out of feature screens

## 4. StoreKit and payments

Current iOS behavior found in source:

- `UpdateView` uses `SKStoreProductViewController` to open the App Store product sheet inside the app for forced updates
- `LoggingViewModel` imports `StoreKit` for `AppStore.requestReview(...)`
- A live StoreKit subscription or in-app purchase flow was not located in the reviewed iOS source
- `RazorpayManager` is intentionally stubbed
  - comment says payments are not live in this release
  - it currently only creates an order via `SakhiCoreSDK.shared.paymentRepo.createOrder(...)`
  - no real SDK checkout flow is wired in the reviewed source

Android parity target:

- Build a Play Billing adapter only if Karan confirms that Android launch scope includes real subscription purchase
- Do not assume iOS already has a working purchase UX, it does not in the reviewed source
- If Android needs an update-sheet equivalent, that is a separate Play Store flow from real billing

Status to record:

- Real iOS subscription purchase source not located, verify with Karan

## 5. BackgroundTasks to WorkManager

Current iOS behavior:

- `BackgroundTaskManager` registers four tasks:
  - `com.sakhi.sync`, `BGProcessingTask`
  - `com.sakhi.locationUpdate`, `BGAppRefreshTask`
  - `com.sakhi.sessionCheck`, `BGAppRefreshTask`
  - `in.sakhi.app.refresh`, `BGAppRefreshTask`
- Scheduling windows:
  - sync after 15 minutes
  - location update after 5 minutes
  - session check after 3 minutes
  - data refresh after 15 minutes
- Each handler re-schedules itself before doing work
- Sync task requires network connectivity
- Data refresh explicitly reports real success or failure to the system
- `AppDelegate.performFetchWithCompletionHandler` also triggers `SyncManager.shared.syncAllPending()`

Android parity target:

- Use separate WorkManager workers, not one catch-all worker
- Keep re-scheduling policy and network constraints explicit
- Report success or retry based on real work result
- Maintain distinct jobs for sync, location refresh, session check, and data refresh if they still matter in Android architecture

## 6. Keychain to Android secure storage

Current iOS behavior:

- `KeychainManager` uses `kSecClassGenericPassword`
- Accessibility is `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`
- Stored keys include:
  - `auth_token`
  - `refresh_token`
  - `user_password`
  - `sakhi.realm.encryption_key`
  - app lock PIN entries
- Supports both string and binary payloads
- `FirstRunDetector` clears stored secrets on fresh install
- Auth bootstrap assumes fast local reads are available from secure storage

Android parity target:

- Use Android Keystore-backed storage for the same sensitive classes of data
- Support both string and binary storage
- Preserve fast auth-token restoration for app bootstrap
- Keep device-local semantics as close as platform allows

## 7. Camera and photos

Current iOS behavior found in source:

- Camera permission:
  - `AVCaptureDevice.authorizationStatus(for: .video)`
  - `AVCaptureDevice.requestAccess(for: .video)`
- Photo permission:
  - `PHPhotoLibrary.authorizationStatus(for: .readWrite)`
  - `PHPhotoLibrary.requestAuthorization(for: .readWrite)`
  - `.limited` is treated as granted
- Picker:
  - `PHPickerViewController`
  - filter `.images`
  - `selectionLimit = 1`
  - selected object loaded as `UIImage`
- Info.plist camera purpose string exists

Not located in reviewed iOS source:

- a real `UIImagePickerController` camera capture flow
- a custom AVCapture session camera screen

Android parity target:

- Support system photo picking with one-image selection
- Support camera permission plumbing
- Verify with Karan whether Android needs actual camera capture on the first pass, or only avatar/library selection

Status to record:

- Real iOS camera capture flow not located in reviewed source, verify with Karan

## 8. Exotel SMS and phone calls

Current iOS behavior:

- Credentials come from `EnvironmentManager`
  - `EXOTEL_ACCOUNT_SID`
  - `EXOTEL_API_KEY`
  - `EXOTEL_API_TOKEN`
  - `EXOTEL_FROM_NUMBER`
  - `EXOTEL_CALLER_ID`
- SMS path:
  - `ExotelManager.sendSMS(to:message:)`
  - builds Basic auth from `apiKey:apiToken`
  - URL-encodes message body
  - sends through `APIClient` endpoint `.exotelSendSms(...)`
- Call path:
  - `ExotelManager.makeCall(from:to:)`
  - comment says KMM migration owns this path
  - delegates to `SakhiCoreSDK.shared.phoneCallRepo.initiateCall(...)`
- Logging masks phone numbers down to the last 4 digits on SMS success

Android parity target:

- Keep SMS and voice-call handling behind one adapter boundary
- Prefer KMM-owned repository semantics for the call initiation path
- Preserve credential handling and phone-number redaction in logs

## Android implementation checklist

- Build adapters around the real iOS responsibilities, not just Android SDK equivalents.
- Keep business decisions in KMM, especially:
  - Health import conflict rules
  - notification payload semantics
  - reminder schedule building
  - payment order creation
  - Exotel call initiation if KMM already owns it
- For anything marked below, stop and verify before coding:
  - real subscription purchase flow
  - real camera capture flow

## Verify with Karan

- Real iOS subscription purchase implementation was not located in reviewed source, verify Android launch scope before building Play Billing.
- Real iOS camera capture flow was not located in reviewed source, verify whether Android needs camera capture or only picker-based image selection.
