# Build environment and dependency versions

Versions were checked against official pages on **2026-10-18**. Nothing here was invented; where a
version could **not** be re-verified in the authoring session it is marked.

| Component | Version | Why / source |
|---|---|---|
| Android Studio | Panda 3 (2025.3.3) | Your IDE. Per the official "About Android Gradle plugin" page it supports AGP 7.0 to **9.1**. |
| Android Gradle Plugin | **9.1.1** | Newest AGP your IDE can open. The newest stable AGP (9.4.0, Sept 2026) needs a newer Studio, so it is deliberately **not** used. 9.1.1 is also the first AGP that supports API 37. |
| Gradle | **9.3.1** | Minimum Gradle for AGP 9.1 (`gradle-wrapper.properties`). Android Studio downloads it by itself. |
| JDK running Gradle | 21 (yours) | AGP 9.1 needs JDK 17 or newer. |
| Java language level | 17 | Supported by AGP 9.1 and D8 desugaring. |
| compileSdk | **37** | `androidx.core` 1.19.x requires compileSdk 37 (AGP's AAR metadata check fails otherwise). Install "Android 17 (API 37)" in SDK Manager. Per the AGP notes, API 37 needs Studio Panda 3 **Patch 1** or newer: update Studio if Gradle sync complains. |
| targetSdk | 36 | Behaviour changes of API 37 were not tested, so the app does not opt in. |
| minSdk | 26 (Android 8.0) | `java.time`, adaptive icons, PBKDF2-SHA256 are all available without desugaring. |
| androidx.core | 1.19.1 | AndroidX releases page (stable). |
| androidx.appcompat | 1.8.0 | AndroidX releases page (stable). |
| androidx.activity | 1.13.0 | AndroidX releases page (stable). |
| androidx.constraintlayout | 2.2.2 | AndroidX releases page (stable). |
| androidx.recyclerview | 1.4.0 | AndroidX releases page (stable). |
| androidx.room | 2.8.5 | Room 2.x is the Java-compatible line (annotation processor). |
| androidx.camera | 1.6.2 | AndroidX releases page (stable). `ImageProxy.toBitmap()` is used. |
| Material Components | 1.14.0 | Maven listing. Views-based Material is in maintenance mode but still supported and matches the XML requirement. |
| ML Kit face-detection | 16.1.7 | ML Kit release notes. Detection, landmarks, eye-open probability only. |
| LiteRT (Interpreter API) | 1.4.2 | LiteRT Android docs list 1.4.2 as latest of the v1 line; a v2.x line exists with a different (CompiledModel) API. `import com.google.ai.edge.litert.Interpreter` could **not** be compile-checked offline: see `TfliteFaceEmbedder`. |
| JUnit | 4.13.2 | Long-standing stable release. **Not re-verified** in the session. |
| androidx.test ext-junit / runner / core | 1.3.0 / 1.7.0 / 1.7.0 | **Not re-verified** in the session; used by instrumentation tests only. |

No alpha, beta, RC or snapshot dependency is used. R8 shrinking is **off** for release because a release
build could not be tested here.

## Why the Gradle wrapper JAR is missing

`gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.properties` are included, but
`gradle/wrapper/gradle-wrapper.jar` is a **binary** that could not be produced or downloaded in the
offline authoring environment, and fabricating one is not possible. Options (any one):

1. **Just open the project in Android Studio.** It reads `gradle-wrapper.properties`, downloads Gradle
   9.3.1 and syncs. The command-line scripts are then optional.
2. Install any recent Gradle, then run `gradle wrapper --gradle-version 9.3.1` in the project root. This
   writes the official `gradle-wrapper.jar` (and refreshes the scripts).
3. Copy `gradle/wrapper/gradle-wrapper.jar` from any other Android Studio project.

Until the JAR exists, `gradlew` prints a clear message instead of failing obscurely.
