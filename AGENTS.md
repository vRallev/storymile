# Development guide

## Pull requests

Create new PRs as ready for review, never as drafts, even when a skill says otherwise.
Only an explicit instruction in the user's prompt can override this rule.

## Structure

- `app`: Android, Desktop, iOS, and Wasm launchers.
- `app-framework`: application lifecycle, dependency graphs, and integration-test fixtures.
- `templates`, `theme`, `presenter-navigation`: shared UI infrastructure.
- `buildSrc`: Gradle conventions, platform targets, module checks, and test setup.

`app-framework:impl` assembles implementations and produces the iOS framework used by
Xcode; preserve that integration when changing it. Put reusable build conventions in `buildSrc`.

## Architecture skills

Use the matching skill for detailed guidance:

- [Module structure](.agents/skills/app-platform-module-structure/SKILL.md): module boundaries and dependencies.
- [Presenters](.agents/skills/app-platform-presenters/SKILL.md): models, state, and presentation logic.
- [Renderers](.agents/skills/app-platform-renderers/SKILL.md): UI rendering and templates.
- [Scopes](.agents/skills/app-platform-scope/SKILL.md): lifetimes, coroutines, and Metro graphs.
- [Testing](.agents/skills/app-platform-testing/SKILL.md): fakes, unit tests, robots, and integration tests.

## Development

- Use Desktop for most development and testing because it is the fastest.
- Use other platforms only when requested or to verify platform integration.
- Add `--quiet` to Gradle commands by default to reduce output; examples omit it for brevity.
- Prefer shared KMP implementations; keep app shells focused on startup and platform integration.
- Preserve compatibility with Android, Desktop, iOS, and Wasm.

### Gradle build files

Module `build.gradle` files should only apply one Storymile plugin, configure `storymile { ... }`,
and declare dependencies. Put other configuration in `buildSrc`, exposing DSL options when needed.
Configuration used only once may stay in its build file.

### Dependencies

- Reusable modules depend on public APIs.
- Put dependency and SDK versions in `gradle/libs.versions.toml`.
- Prefer declaring KMP dependencies in `commonMain` or `commonTest`, even when only one
  platform uses them.
- After editing dependencies, run `./gradlew sortDependencies` and
  `./gradlew -p buildSrc sortDependencies`. CI verifies both with `checkSortDependencies`.

### Dependency injection

- Put Metro bindings in the owning library's `:impl` module; create it if needed.
- Use injected abstract APIs instead of `expect`/`actual` functions or classes.
- Contribute platform implementations to Metro from the matching platform source sets in `:impl`.

### Testing

Evaluate each unit test for usefulness; 100% coverage is not the goal. Test meaningful
behavior and regressions. Avoid trivial tests that duplicate or are tightly coupled to
their implementation.

### Icons

After changing `images/icon.png`, run `./scripts/app-icon/generate-icons.sh` on macOS to update
all app icons, including rounded macOS icons.

## Run

Use Azul Zulu JDK 25, the Android SDK, and Xcode for iOS.

Run `./run.sh` to choose a platform, device, or Desktop window size. Desktop uses hot
reload. Android and iOS device selection also requires Python 3.

```sh
./gradlew :app:android:installDebug
./gradlew :app:desktop:run
./gradlew :app:desktop:hotRunDesktop --auto
./gradlew :app:web:wasmJsBrowserDevelopmentRun
open app/ios/iosApp.xcodeproj
```

In Xcode, select the `iosApp` scheme and an ARM64 iOS simulator. Device builds need your
team ID in `app/ios/Configuration/Config.xcconfig`.

## Verify

Run `./scripts/ktfmt.sh` to format tracked Kotlin files, including `buildSrc`. CI checks formatting
with `./scripts/ktfmt.sh --dry-run --set-exit-if-changed`.

Every public API in a `:public` module must have KDoc, including classes, functions,
and properties. Detekt checks production sources for missing KDoc.

Put shared behavior tests in shared modules' `commonTest`; use app-shell tests for
platform integration. Start with the affected module's `desktopTest`, for example:

```sh
./gradlew :app-framework:impl:desktopTest
./gradlew detekt checkModuleStructureDependencies checkModuleStructureNesting
```

For graph assembly or app integration changes, run the Desktop smoke tests:

```sh
./gradlew :app-framework:impl-ui-test-robots:desktopTest :app:desktop:desktopTest
```

Run `./gradlew -p buildSrc release` when changing build conventions.

### Platform integration

Run only the checks needed for the change or requested platform. Android KMP libraries
use `testAndroidHostTest`; the Android app shell uses `testDebugUnitTest`.
Android Lint checks the app and shared KMP code.

```sh
./gradlew lint lintAndroidMain
./gradlew :app-framework:impl:testAndroidHostTest
./gradlew :app:android:testDebugUnitTest
./gradlew wasmJsTest iosSimulatorArm64Test
./gradlew :app:android:emulatorCheck
./gradlew :app:android:assembleDebug :app:desktop:createDistributable
./gradlew :app:web:wasmJsBrowserDistribution
./gradlew :app-framework:impl:linkDebugFrameworkIosSimulatorArm64
```

Desktop tests include rendered and headless application smoke tests. Android UI
tests use the blueprint's managed Pixel 3 emulator.

## CI

`.github/workflows/ci.yml` runs checks, tests, and platform builds on pushes and pull requests.
Every push to `main` runs CI without canceling earlier runs. New PR pushes cancel older
runs for that PR. When CI fails, verify and push the fix promptly without waiting for
unrelated jobs to finish, so cancellation frees workers for the updated run.

The optional `GRADLE_ENCRYPTION_KEY` repository secret enables Gradle configuration-cache caching.

The source code is public. Store secrets and sensitive keys in GitHub Actions secrets;
never commit them or expose them in logs or artifacts.
