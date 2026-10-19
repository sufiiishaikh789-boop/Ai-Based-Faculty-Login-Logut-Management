# Architecture

## Layers

```
 ui/            Activities, adapters, ScanOverlayView          (Android, thin)
   |
 camera/        FaceAnalyzer, FaceCropper, TfliteFaceEmbedder  (Android)  +  FaceMatcher, LivenessTracker,
   |            FrameStabilizer, EnrollmentCollector, FaceEmbedder (pure Java, unit tested)
 location/      AndroidWifiReader, AndroidLocationSource       (Android)  +  CampusPolicyEvaluator, GeoMath (pure)
   |
 attendance/    AttendanceService, AdminCorrectionService, CsvWriter   (pure Java, unit tested)
   |            depends only on the AttendanceStore interface
 data/          Room entities/DAOs, RoomAttendanceStore, repositories  (Android)
 authentication/ PasswordHasher, LoginThrottle, AdminSession (pure) + KeystoreTemplateCipher, AdminAuthManager
 config/        CampusConfig, AttendancePolicy, RecognitionPolicy (pure) + SettingsRepository
 domain/        immutable result/value types (pure)
 app/           AppContainer (hand-written service locator), Application class
```

**Rule:** business decisions live in pure-Java classes that have no Android imports. That is what lets
the attendance rules, campus policy, matching, CSV and password logic be unit tested on a plain JVM.
Android classes only collect inputs (camera frames, Wi-Fi/GPS readings) and display results.

**Replaceable recognition module:** `FaceEmbedder` is the only thing the rest of the app knows about
recognition. Swap `TfliteFaceEmbedder` for another implementation and nothing in the database,
attendance service or UI changes. Templates carry a `templateModelId`, and only templates from the
same model are ever compared.

**Optional backend later:** `AttendanceStore` (write side) and `AttendanceRepository` (read side) are
the seams. A remote implementation can be added behind them. No Firebase or REST code is included,
because an untested integration would be misleading.

## Scan flow

```
camera permission -> front camera preview
   -> ModeResolver: CONFIGURED | DEMO | NOT_READY            (blocks scanning when not ready)
   -> CampusPolicyEvaluator: trusted Wi-Fi? else GPS within radius, accuracy, freshness, not mock
   -> FaceAnalyzer: detect -> quality gate -> align -> embed -> match (1:N) -> N stable frames (+ blink)
   -> exactly one onVerified() callback  (the analyzer latches)
   -> AttendanceService.recordScan(identity, eligibility)     (one DB transaction)
   -> result card -> dashboard
```

`AttendanceService` itself re-checks that identity is VERIFIED, that eligibility is positive and fresh
(max age 120 s), and that demo results are only accepted when demo records are allowed. A caller
cannot write attendance by skipping those steps.

## Database (Room, version 1, schema exported to `app/schemas/`)

**faculty**: `uid` PK (validated 3-32 chars), `name`, `department`, `active`, `createdAtMillis`,
`updatedAtMillis`, `enrolledAtMillis` (0 = none), `encryptedTemplate` BLOB (AES-256-GCM, Keystore key,
UID bound as associated data), `templateModelId`, `templateDemo`.

**attendance**: `id` PK autogenerate, `facultyUid` FK -> faculty (RESTRICT), `facultyName`,
`localDate` (yyyy-MM-dd, configured zone), `loginAtMillis` (UTC epoch), `logoutAtMillis` (nullable),
`status` (LOGGED_IN / LOGGED_OUT), `openMarker` (1 while open, NULL when closed), `loginEvidence`,
`logoutEvidence`, `demo`, `corrected`.
Indexes: **UNIQUE (facultyUid, openMarker)** (at most one open session per person, enforced by SQLite itself),
(facultyUid, loginAtMillis), (localDate).

**audit**: `id` PK, `action`, `recordId`, `targetUid`, `atMillis`, `actor`, `reason`, `details`. Index on `atMillis`.

Evidence strings contain method and distance/accuracy only (for example `GPS:dist=4.2m;acc=6.0m` or
`WIFI:ssid_match`), never raw coordinates, SSIDs or BSSIDs.

Non-secret settings (campus config, time rules, thresholds) are in app-private SharedPreferences,
excluded from backup. Migrations: `Migrations.ALL` is empty for v1; there is no destructive fallback.

## Threading

Database, Keystore and model loading run on `AppContainer.io` (2 threads). Camera analysis runs on its
own single-thread executor, throttled to one frame per 250 ms, on `STRATEGY_KEEP_ONLY_LATEST`.
UI callbacks are marshalled with `ui()`, which drops results if the screen is gone. A `runId` counter
invalidates callbacks that arrive after a retry or `onStop`.
