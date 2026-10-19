# Smart Faculty Attendance: build and verification report

Prepared 2026-10-18 in an authoring sandbox. **Read the verdict first.**

## Verdict

| Area | Result |
|---|---|
| Pure-Java core (attendance state machine, campus policy, matching, CSV, auth logic) | **Compiled and tested: 58 of 58 tests pass.** |
| Android application layer (screens, camera, Room, Keystore, manifest, Gradle) | **Written and statically cross-checked only. Never compiled, never run.** |
| Gradle sync / `assembleDebug` / instrumentation tests | **Not run.** The sandbox has no Android SDK, no Gradle and no network. |
| Debug APK | **Not produced. No `app-debug.apk` is delivered.** |
| Recognition model | **Not bundled.** Real scanning is blocked until you add one. |
| Gradle wrapper | `gradlew`, `gradlew.bat`, properties and version catalog present. **`gradle-wrapper.jar` is missing** (binary; cannot be created offline). |

Nothing in this report claims a successful Android build, because none happened.

## Authoring environment

OpenJDK 21.0.10 runtime with the `jdk.compiler` module (no `javac` launcher; `--release` is unsupported), Python 3, Node, `zip`.
No `ANDROID_HOME`, no Gradle, `curl` could not resolve any host. Web search/fetch (official docs) was available and used
for version checks only.

## What was actually run, and the result

| # | What | How | Result |
|---|---|---|---|
| 1 | Compile the pure-Java core (domain, config, location policy, attendance, auth, matching, util) | `java -m jdk.compiler/com.sun.tools.javac.Main -source 8 -target 8 -Xlint:all` | **0 errors, 0 warnings** (after adding `serialVersionUID` to two exception classes) |
| 2 | Run the 9 unit-test classes | Small JUnit-4-compatible shim and runner (the sandbox has no JUnit jar), **not** Gradle | **58 passed, 0 failed** (AttendanceService 17, CampusPolicyEvaluator 11, RecognitionLogic 10, AuthAndMode 9, GeoMath 5, CsvWriter 4, AdminCorrectionService 2; `FakeAttendanceStore` and `MutableClock` are helpers) |
| 3 | Java 9+ API audit (device minSdk 26 only has the Java 8 `java.time` API) | `grep` for `List.of`, `isBlank`, `LocalDate.ofInstant`, `String.repeat`, `toMinutesPart`, `Stream.toList`, `var` over **all** Java sources | none found. (`--release 8` could not be used: the JRE has no `ct.sym`, so this is a grep audit, not a compiler guarantee.) |
| 4 | Syntax parse of **all 90 Java files** (main, debug, release, test, androidTest) | `javax.tools` `JavacTask.parse()` | **0 syntax errors.** (Parse only: symbols are not resolved, so Android API misuse is not detected.) |
| 5 | XML well-formedness of all 28 XML files | Python `ElementTree` | all well-formed |
| 6 | Resource cross-check | script: every `@string/@color/@drawable/@style/@mipmap/@xml/@id` in XML and every `R.*` in Java resolves to a definition | all resolve |
| 7 | ViewBinding cross-check | script: all 83 `binding.field` uses exist as ids in the matching layout | all exist |
| 8 | Project-import and manifest cross-check | script: every project import resolves; all 12 manifest activities + the `Application` class exist; every Activity class is declared | consistent |
| 9 | Secrets / forbidden files / invented values | grep scans | no keystores, keys, `local.properties`, models, APKs, databases or CSVs; no hard-coded admin password; no invented SSIDs or coordinates; `INTERNET` removed in manifest |

### Defect found by the tests and fixed

`PasswordHasher` zeroed the salt array inside the key-derivation helper, then stored the zeroed salt, so no password could ever
verify. The password test caught it (57 of 58 passed). Fixed (the salt is not secret and is no longer wiped). This is the kind of bug
the Android layer may also hide, which is why a first on-device pass matters.

## What could NOT be verified, and why

| Item | Why not |
|---|---|
| Gradle sync, AGP 9.1.1 + Kotlin-DSL build files | no Gradle / SDK / network |
| Compilation of the Android-dependent classes against the real Android, AndroidX, CameraX, ML Kit, Room and LiteRT APIs | no SDK, no dependency jars |
| Room annotation processing and query validation (e.g. the `LIKE ... ESCAPE '\'` search query) | needs the Room compiler |
| Lint, R8, release build | no toolchain; R8 is disabled for release for that reason |
| Instrumentation tests (Room integration with 16-thread concurrency, admin setup screen) | need a device/emulator |
| Camera behaviour, permission dialogs, GPS and Wi-Fi readings, Keystore encryption, face quality thresholds | need a real device |
| Face recognition accuracy | no model, no faces (and no real faces may be used in tests) |
| Portfolio card rendering | only an old WebKit (`wkhtmltoimage`, no CSS grid/variables) was available; HTML fragment was checked for well-formedness only |
| `androidx.test`, JUnit versions | not re-verified against official pages (listed as such in `libs.versions.toml`) |

## Where I expect the first build to fail or warn (check these first)

1. `TfliteFaceEmbedder`: `import com.google.ai.edge.litert.Interpreter;` (LiteRT 1.4.2). The package is from the LiteRT migration notes
   ("update the package name"); the exact class path was not compile-checked. This is the only file that touches that API.
2. `app/build.gradle.kts`: `compileSdk = 37` needs the API 37 platform and Studio Panda 3 **Patch 1** or newer. If sync complains
   about the Studio/AGP combination, update Android Studio.
3. `FaceAnalyzer`: `com.google.android.gms.tasks.Tasks` should arrive transitively with ML Kit; if not, add `play-services-tasks`.
4. `VerifyActivity`: `com.google.common.util.concurrent.ListenableFuture` should arrive transitively with CameraX.
5. Room: schema export writes JSON to `app/schemas/` on first build; commit those files.
6. Lint warnings (not errors): deprecated `WifiManager.getConnectionInfo`, UI strings that live in Java instead of `strings.xml`, unused imports.
7. Any library that merges an `INTERNET` permission is overridden by `tools:node="remove"`. Check the merged manifest (Studio > Merged Manifest).
8. Add `gradle-wrapper.jar` only if you want command-line builds (`docs/BUILD_AND_VERSIONS.md`).

## Dependency verification

Official-page checks and rationale are in `docs/BUILD_AND_VERSIONS.md`. In short: AGP **9.1.1** (the newest your Panda 3 IDE supports; AGP 9.4 would
not open), Gradle **9.3.1**, compileSdk **37** (forced by `androidx.core` 1.19.x), Room 2.8.5, CameraX 1.6.2, Material 1.14.0,
ML Kit face-detection 16.1.7, LiteRT 1.4.2. No alpha/beta/snapshot libraries.

## Deviations and decisions to be aware of

* Re-login cooldown (60 s default, configurable to 0) added to the specified timing rule.
* No device-biometric fallback (cannot be bound to a faculty UID); see README "Design decisions".
* All-faculty history and export are admin-only; the faculty dashboard appears only after face verification.
* targetSdk 36 with compileSdk 37; `minSdk` 26.
* `WifiManager.getConnectionInfo()` (deprecated, works) is used for the synchronous Wi-Fi read.
* DEMO mode exists only in the debug source set; the release source set has no demo embedder.

## Checklist: before the first build

- [ ] Android Studio Panda 3 **Patch 1 or newer** (Help > Check for Updates)
- [ ] SDK Manager: Android 17 (API 37) platform and Build-Tools
- [ ] Open the folder; let Gradle download Gradle 9.3.1 and the libraries
- [ ] Fix compile errors (see list above), then run `testDebugUnitTest` and the instrumentation tests on a phone
- [ ] Commit `app/schemas/*.json` once generated

## Checklist: configuration required before real campus use

- [ ] Create the administrator password (first run)
- [ ] Add a **licence-checked** recognition model and calibrate the threshold (`docs/MODEL_SETUP.md`)
- [ ] Enter the real campus **SSID(s)** and, preferably, **BSSIDs**; capture the GPS centre; confirm radius/accuracy (`docs/ADMIN_CONFIG_GUIDE.md`)
- [ ] Decide the attendance policy with the university (window, cooldown, time zone) (`docs/ATTENDANCE_POLICY.md`)
- [ ] Run the acceptance-test table in the admin guide on real devices, including spoofing attempts
- [ ] Decide on consent, retention and a non-biometric alternative (`docs/SECURITY_PRIVACY.md`)
- [ ] Choose a software licence and replace the placeholder `LICENSE`
- [ ] Before **official** use: server of record, device attestation, validated anti-spoofing, legal review (see the security document)

## Archive verification (`SmartFacultyAttendance_Project.zip`)

* `unzip -t`: no errors in the compressed data.
* Contains the complete source tree: 142 files, including 76 main + 3 debug/release + 9 unit-test + 2 instrumentation-test Java files,
  28 XML files, Gradle build files, version catalog, `gradlew`, `gradlew.bat`, `gradle-wrapper.properties`, `.gitignore`, `LICENSE`
  placeholder, README, six docs plus this report, and the portfolio-integration folder.
* **Intentionally absent:** `gradle/wrapper/gradle-wrapper.jar` (binary, cannot be created offline), any `.tflite` model, any APK.
* No keystores, API keys, `local.properties`, databases, CSV files or build output are inside.
* After extracting the archive to a fresh folder: 90 Java files parsed with 0 syntax errors, all static cross-checks passed, the pure-Java core
  compiled, and **58 of 58 unit tests passed again** from the extracted copy.

These checks prove the archive is complete and internally consistent. They do **not** prove the Android project builds.
