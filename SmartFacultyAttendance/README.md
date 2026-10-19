# Smart Faculty Attendance Management System

**Forwarding With Lego.**

An offline-first Android app (Java + XML) that records faculty login and logout automatically after two
checks: a **face check** against an enrolled identity and a **campus check** (trusted Wi-Fi, or GPS within
10 m as a fallback). It includes a protected administration area, an audit trail, CSV export and diagnostics.

> ### Read this first: what is and is not done
>
> | | Status |
> |---|---|
> | Attendance state machine, campus policy, matching, CSV, password logic | **Implemented and unit-tested** (58 tests pass on a plain JVM) |
> | Android app (screens, camera, Room, Keystore, permissions) | **Written and statically cross-checked, but never compiled or run.** The authoring environment had no Android SDK, Gradle or network. Expect to fix a few compile errors on first build. |
> | Face recognition | **The recognition model is NOT included.** You must add one (`docs/MODEL_SETUP.md`). Until then real scanning is blocked. |
> | Debug APK | **Not produced** (no SDK). Build it yourself, see below. |
> | `gradle-wrapper.jar` | **Missing** (binary, cannot be created offline). Android Studio does not need it. See `docs/BUILD_AND_VERSIONS.md`. |
> | Campus Wi-Fi/GPS values | **Placeholders.** Attendance stays blocked until an administrator fills them in. |
> | Official attendance use | **Not ready.** Local-only storage and device-side checks can be bypassed. See `docs/SECURITY_PRIVACY.md`. |

## Features

* Branded splash, then an automatic front-camera scan with an alignment guide and a scanning animation.
* States: initialising, permission required, locating campus, scanning, blink, verifying, success, duplicate,
  no face, multiple faces, low quality, mismatch, not ready, error, each with a retry path.
* On-device recognition through a replaceable `FaceEmbedder` (1:N cosine matching with an ambiguity margin and
  N-consecutive-frame stability). Basic blink check, clearly **not** validated anti-spoofing.
* Campus check: configured SSID (+ optional BSSID allowlist) first; GPS fallback with radius, accuracy, freshness
  and mock-location checks. Missing or invalid configuration fails safely.
* Configurable timing rule (duplicate window default 15 min) in one transactional service. See `docs/ATTENDANCE_POLICY.md`.
* Admin: first-run password, faculty add/edit/deactivate, face enrolment with a biometric notice, template removal,
  open-session correction with reasons, audit history, configuration validation, diagnostics, CSV export.
* Face templates encrypted with an Android Keystore key. No INTERNET permission. Backups disabled.
* Light and dark themes, deep-purple Material 3 design, screen-reader live regions, 48 dp targets.

## Technology stack

Java 17, native XML + ViewBinding, Material Components 1.14.0, CameraX 1.6.2, ML Kit face detection 16.1.7,
LiteRT 1.4.2 (embedding model supplied by you), Room 2.8.5, Android Keystore, AGP 9.1.1, Gradle 9.3.1,
compileSdk 37 / targetSdk 36 / minSdk 26. Versions and the reasoning for each are in `docs/BUILD_AND_VERSIONS.md`.

## Architecture overview

Pure-Java business logic (`attendance/`, `location/CampusPolicyEvaluator`, `camera/FaceMatcher`, `authentication/PasswordHasher`,
`config/`, `domain/`) with **no Android imports**, so it is unit-testable; Android classes only gather inputs and show
results. `AttendanceStore` and `FaceEmbedder` are the two seams (database and recognition). Details, schema and the scan
flow: `docs/ARCHITECTURE.md`.

## Directory structure

```
SmartFacultyAttendance/
├── app/
│   ├── schemas/                          Room schema history (commit it)
│   └── src/
│       ├── main/java/com/example/facultyattendance/
│       │   ├── app/  config/  domain/  attendance/  authentication/
│       │   ├── camera/  location/  data/local/  data/repository/  ui/  util/
│       │   └── (see docs/ARCHITECTURE.md)
│       ├── main/res/ {layout, drawable, values, values-night, xml, mipmap-anydpi-v26}
│       ├── main/assets/models/           put face_embedding.tflite here
│       ├── debug/   DemoSupport + DemoFaceEmbedder (debug builds only)
│       ├── release/ DemoSupport stub (no demo class exists)
│       ├── test/         JUnit 4 unit tests (JVM)
│       └── androidTest/  Room integration + UI smoke tests (device/emulator)
├── docs/                 architecture, policy, admin guide, model setup, security, versions
├── portfolio-integration/  HTML + React project card and instructions
├── gradle/ (libs.versions.toml, wrapper/gradle-wrapper.properties)
├── gradlew, gradlew.bat  (need gradle-wrapper.jar, see above)
├── build.gradle.kts, settings.gradle.kts, gradle.properties
├── .gitignore, LICENSE (placeholder), README.md
```

## Screenshots

Screenshots are placeholders until you run the app. Suggested filenames (create `docs/screenshots/`):
`verify.png`, `dashboard.png`, `admin-home.png`, `history.png`, `config.png`. Reference them here once they exist:
`![Face verification](docs/screenshots/verify.png)`.

## Prerequisites

* Windows 11, Android Studio **Panda 3** (2025.3.3). For compileSdk 37 the AGP notes require **Panda 3 Patch 1 or newer**,
  so run Help > Check for Updates first.
* JDK 21 (Android Studio's bundled JBR is fine; your installed JDK 21 also works).
* In SDK Manager install **Android 17 (API 37)** platform and the suggested Build-Tools.
* A physical Android 8.0+ phone with a front camera is strongly recommended (emulator cameras and GPS are poor tests).

## Open, build and run (Android Studio Panda 3, Windows 11)

1. Extract `SmartFacultyAttendance_Project.zip` to a short path, for example `C:\dev\SmartFacultyAttendance`.
2. Android Studio > **Open** > select the `SmartFacultyAttendance` folder. Accept "Trust project".
3. Let Gradle sync (it downloads Gradle 9.3.1, AGP 9.1.1 and libraries; needs internet once).
4. Fix anything the first build reports. The Android sources have **not** been compiled before. Likely spots are listed in
   `SmartFacultyAttendance_Build_Report.md`.
5. Enable USB debugging on the phone, select it, press **Run**.
6. First launch: create the administrator password, then follow `docs/ADMIN_CONFIG_GUIDE.md`.

Command line (after the wrapper JAR exists, see `docs/BUILD_AND_VERSIONS.md`):

```
gradlew.bat assembleDebug           # APK: app\build\outputs\apk\debug\app-debug.apk
gradlew.bat testDebugUnitTest       # JVM unit tests
gradlew.bat connectedDebugAndroidTest   # instrumentation tests (device/emulator attached)
```

No APK was generated in the authoring environment, so no APK is shipped.

## Configure campus Wi-Fi and GPS

Admin > Configuration. Full steps, including how to capture the centre point and an acceptance-test table:
`docs/ADMIN_CONFIG_GUIDE.md`. Defaults: radius 10 m, accuracy 10 m, fix age 15 s. SSIDs and coordinates start **empty**.

## Configure the face-recognition model

Copy your model to `app/src/main/assets/models/face_embedding.tflite`. The contract, the licence checklist and the threshold
calibration procedure are in `docs/MODEL_SETUP.md`. The similarity threshold shipped (0.70) is a placeholder.

## Database schema and attendance flow

Tables `faculty`, `attendance`, `audit` with a UNIQUE index guaranteeing one open session per person; schema in
`docs/ARCHITECTURE.md`. Flow: verify identity + campus, then in one transaction: no open session -> login; open and inside
the window -> ignore; open and past the window -> logout; shortly after a logout -> ignore; later -> new session.

## Testing

| Suite | What it covers | Status |
|---|---|---|
| `app/src/test` (JUnit 4) | first login; duplicate ignored; logout after window; new session; open session stays null; unknown/inactive UID; mismatch never writes; campus failure never writes; missing config; accuracy/stale/mock; concurrency (24 threads -> 1 login); DB exception; CSV escaping; matching, enrolment, liveness, password, lock-out, mode resolution | **58 pass** (run with a JUnit-compatible shim on the JVM, not via Gradle) |
| `app/src/androidTest` | real Room: unique UID, one-open-session index, lifecycle, 16-thread concurrency, search filters; admin setup screen | **Written, not run** |

Permission dialogs, camera behaviour and navigation under real permission states need manual or UI-Automator testing on a device.
All test data is synthetic: no real faces or faculty data.

## Demo mode versus real verification

| | Release build | Debug build, demo off | Debug build, demo on |
|---|---|---|---|
| Demo embedder class | **not compiled in** | present, unused | used if no real model |
| Campus check | enforced | enforced | skipped (flagged) |
| Records | real only | real only | flagged `DEMO` (banner, CSV column) |
| Real model + valid config required | yes | yes | no |

`AttendanceService` additionally refuses demo-flagged identity or eligibility unless demo records are allowed, so a fake
verifier can never produce a real record.

## Known limitations

* Android code never compiled or run (see the status table). The LiteRT import in `TfliteFaceEmbedder` is unverified.
* No bundled model; recognition accuracy depends on the model and calibration.
* Blink liveness is basic. No validated anti-spoofing. Location/Wi-Fi checks run on the device and can be spoofed.
* Single administrator, no password recovery, local-only data, device clock as time source.
* `WifiManager.getConnectionInfo()` is deprecated since API 31 (still works); documented trade-off.
* R8 shrinking is off for release (untested). No release signing configured.
* Indoor GPS often cannot meet a 10 m / 10 m-accuracy rule. Wi-Fi is the realistic primary check.
* The portfolio card was not rendered in a modern browser during authoring.

## Security and privacy notes

See `docs/SECURITY_PRIVACY.md` (what is protected, what is not, what is required for official use, retention guidance).

## Design decisions worth knowing

* **No device-biometric (fingerprint/phone face) fallback.** The brief allows one only if it is securely bound to a faculty account.
  A phone's enrolled fingerprint proves nothing about which faculty UID is holding the phone, so it was left out rather than weakened.
  The `FaceEmbedder` seam and `AdminAuthManager` are where an explicitly enrolled alternative could be added.
* **The faculty dashboard opens only after a successful face verification**, and the all-faculty history/search/export lives behind the
  administrator sign-in, so one faculty member cannot browse another's attendance.
* **Re-login cooldown (default 60 s)** is an addition to the specified timing rule; set it to 0 for the literal rule.
* **targetSdk 36 with compileSdk 37**: compileSdk is forced by `androidx.core` 1.19; API 37 behaviour changes are not adopted untested.

## Future improvements

Server of record with authenticated sync, device attestation, validated presentation-attack detection, multi-admin roles,
shift-aware attendance rules, localisation, screenshots and UI automation, per-model calibration tooling.

## Contributing

Keep business rules in the pure-Java packages with tests. Do not add real faces, faculty data, credentials, keystores or models
without a redistributable licence to the repository. Open an issue before large changes.

## License

**No licence has been chosen**, and `LICENSE` is a placeholder (so, legally, nobody may reuse the code yet). Common choices:

* **MIT**: very permissive; anyone may use, change and sell it if they keep your copyright notice. Little protection beyond that.
* **Apache-2.0**: permissive like MIT, plus an explicit patent grant and a requirement to state changes. Good for a project others may build on.
* **GPL-3.0**: copyleft; anyone who distributes a modified version must also publish it under GPL-3.0. Keeps derivatives open, but companies and
  some universities may avoid it.

Pick deliberately, then replace `LICENSE` with the official text. Third-party libraries and any face model keep their own licences.
Branding note: the name "Lego" is a trademark of the LEGO Group; the logo here is an original abstract mark. See `docs/SECURITY_PRIVACY.md`.
