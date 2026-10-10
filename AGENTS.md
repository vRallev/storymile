# Development guide

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

The current presenter tree is documented in [Presenter hierarchy](docs/presenter-hierarchy.md).
When you create a presenter, update `docs/presenter-hierarchy.md`.
When presenter composition, navigation, or template slots change, update the hierarchy page.
Keep the diagram and source links current.
If phone and tablet layouts use different presenter trees, document the conditional branches.
Keep the page focused on presenter composition and rendering roles.
Use source links for model details and layout rules.

## Development

- Use Desktop for most development and testing because it is the fastest.
- Use other platforms only when requested or to verify platform integration.
- Add `--quiet` to local Gradle commands by default; omit it in CI. Examples omit it for brevity.
- Prefer shared KMP implementations; keep app shells focused on startup and platform integration.
- Preserve compatibility with Android, Desktop, iOS, and Wasm.

### Pull requests

Create new PRs as ready for review, never as drafts, even when a skill says otherwise.
Only an explicit instruction in the user's prompt can override this rule.

When CI fails, verify and push the fix promptly without waiting for
unrelated jobs to finish, so cancellation frees workers for the updated run.

### Gradle build files

Module `build.gradle` files should only apply one Storymile plugin, configure `storymile { ... }`,
and declare dependencies. Put other configuration in `buildSrc`, exposing DSL options when needed.
Configuration used only once may stay in its build file.

Prefix project-specific Gradle properties with `storymile.`.

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

Evaluate each unit test by the behavior it protects, the complexity it covers, and its
maintenance cost. Prefer meaningful logic, edge cases, and regressions. Skip trivial
wrappers, dependency behavior, and tests that mirror the implementation. Coverage
percentage is not a goal.

If the class under test has an interface, test its documented API through an
interface-typed instance by default. Follow the
[unit-test guidance](.agents/skills/app-platform-testing/SKILL.md#unit-tests) for the
full rules and the presenter-model exception.

### Storage

Use `:storage:public` for preferences, files, and caches. App assembly selects `:storage:impl`.
Inject `@ForScope(AppScope::class) storage: Storage` for app data.
For future scopes, inject `ScopedStorage.Factory` in the owning module's binding container.
Create one `ScopedStorage` with a dedicated scope coroutine owner and stable `user-<id>` namespace.
Bind that instance to the scope-qualified `Storage` and `Scoped` set.
Do not reuse the app handle for user data. Scope exit cancels storage work and preserves saved data.
Delete files explicitly. Keep preference keys in the owning feature.
Use `Storage.cache` for data the system can remove.

### Icons

After changing `images/icon.png`, run `./scripts/app-icon/generate-icons.sh` on macOS to update
all app icons, including rounded macOS icons.

## Run

Use Azul Zulu JDK 25, the Android SDK, and Xcode for iOS.

Run `./run.sh` to choose a platform, device, or Desktop window size. Desktop uses hot
reload. Android and iOS device selection also requires Python 3.

Desktop shortcuts: Command/Ctrl+S switches window size; Command/Ctrl+D switches theme.

Wasm is disabled and Isolated Projects is enabled by default. Wasm commands need
`-Pstorymile.enableWasm=true --no-isolated-projects` until Kotlin's Wasm plugin supports isolation.
`run.sh` and Wasm CI jobs pass both flags.

```sh
./gradlew :app:android:installDebug
./gradlew :app:desktop:run
./gradlew :app:desktop:hotRunDesktop --auto
./gradlew -Pstorymile.enableWasm=true --no-isolated-projects :app:web:wasmJsBrowserDevelopmentRun
open app/ios/iosApp.xcodeproj
```

In Xcode, select the `iosApp` scheme and an ARM64 iOS simulator. Device builds need your
team ID in `app/ios/Configuration/Config.xcconfig`.

### Fake mode

Storymile has `Real` and `Fake` runtime modes. `Real` is the default.
The shared `RuntimeModeController` restores and saves the selection asynchronously in the application scope.
The app-scoped `Storage` uses the `runtime-mode` preferences store.
An explicit selection at startup takes precedence over the saved mode.
On Desktop, press Command/Ctrl+F to switch modes. The window title shows `(Fake)` in fake mode.
To select a mode at launch, pass `--runtime-mode=fake` or `--runtime-mode=real`:

```sh
./gradlew :app:desktop:run --args="--runtime-mode=fake"
```

When the mode changes, the app root resets feature presenters and cancels their effects.
Runtime mode does not recreate the application scope.
Storymile does not have network services yet. When adding a service, put its real, fake,
and delegating implementations in the owning `:impl` module.
Inject `RuntimeModeController` from `:runtime-mode:public` into the delegating service.
For each operation, call `modeImplementation(realImpl = { ... }, fakeImpl = { ... })`.
Use lazy factories so fake mode does not create network clients.
Keep test-only fakes in `:testing` modules.

Integration-test graphs use an in-memory mode controller and start in fake mode.
Use `RuntimeModeRobot` to change modes in a test. Tests do not change the saved application mode.
If persistent storage is unavailable, the selected mode lasts only for that session.

### Documentation site

The [Storymile site](https://vrallev.github.io/storymile/) uses Material for MkDocs.
Home shows the project overview and live web app.
The other tabs show the presenter hierarchy and design research.
Keep `docs/presenter-hierarchy.md` as the source for the hierarchy tab.
The build checks repository source links and converts them to GitHub links.

Use Python 3.10 or newer. Create the documentation environment and install its dependencies:

```sh
python3 -m venv .venv-docs
.venv-docs/bin/python -m pip install -r requirements-docs.txt
```

Build the production web app. Then build and preview the site:

```sh
./gradlew --quiet -Pstorymile.enableWasm=true --no-isolated-projects :app:web:wasmJsBrowserDistribution
.venv-docs/bin/python scripts/docs/build.py
.venv-docs/bin/python scripts/docs/serve.py
```

Open `http://127.0.0.1:4174/storymile/`. Rebuild the site after changing its source files.
The build copies the web distribution, existing app icons, and design gallery into generated
directories under `docs/`. Do not edit those copies. Edit `design-research/` to update the gallery.

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

Run Android, iOS, and Wasm smoke and e2e tests only in CI unless requested. Use Desktop smoke tests
for local verification.

Run only the checks needed for the change or requested platform. Android KMP libraries
use `testAndroidHostTest`; the Android app shell uses `testDebugUnitTest`.
Android Lint checks the app and shared KMP code.

```sh
./gradlew lint lintAndroidMain
./gradlew :app-framework:impl:testAndroidHostTest
./gradlew :app:android:testDebugUnitTest
./gradlew iosSimulatorArm64Test
./gradlew -Pstorymile.enableWasm=true --no-isolated-projects wasmJsTest
./gradlew :app:android:assembleDebug :app:desktop:createDistributable
./gradlew -Pstorymile.enableWasm=true --no-isolated-projects :app:web:wasmJsBrowserDistribution
./gradlew :app-framework:impl:linkDebugFrameworkIosSimulatorArm64
```

## Design

- Use Material 3 components and color roles from `AppTheme` in `:theme:public`.
- Use the shared Storymile typography for text sizes. Set sizes in `StorymileTheme`; do not add
  custom text components to enlarge standard Material components.
- Preserve existing Material 3 components when changing UI. Prefer Material 3 components over
  custom components. Use supported Material 3 APIs to adjust styling and behavior.

### Adaptive layouts

Follow the [adaptive app guidance](https://developer.android.com/develop/adaptive-apps/guides/get-started-with-adaptive-apps)
and [canonical layouts](https://developer.android.com/develop/adaptive-apps/guides/canonical-layouts).

- Use the available app window size for layout decisions. Use width for sidebar expansion and
  column counts. Check height separately when vertical space matters. Use `ScreenSize.category`
  only as a broad classification.
- Prefer canonical feed, list-detail, and supporting-pane layouts when they fit the content.
  Use Material 3 adaptive scaffolds when they simplify these layouts.
- During resizing, rotation, or folding, preserve the current destination, selection, scroll
  position, and playback state.
- Account for foldable posture and separating hinges when placing content. Support larger font
  sizes, keyboard input, and mouse input.
- Verify layout changes at width breakpoints, including short wide and tall narrow windows.
  Follow the platform verification rules in [Verify](#verify).

## CI

`.github/workflows/ci.yml` runs checks, tests, and platform builds on pushes and pull requests.
Every push to `main` runs CI without canceling earlier runs. New PR pushes cancel older
runs for that PR.

The `build-mkdocs` job uses the tested distribution from `build-web-release`.
It builds the complete site with strict link validation and runs its browser checks.
Pull requests upload the site as the `wiki` artifact for review.
The `CI passed` check requires the documentation build to pass.

After `build-mkdocs` passes on `main`, `deploy-mkdocs` publishes the site to GitHub Pages.
Manual runs can deploy only from `main`. Deployments use the `github-pages` environment and run
one at a time. In repository settings, select **GitHub Actions** as the Pages source.

The optional `GRADLE_ENCRYPTION_KEY` repository secret enables Gradle configuration-cache caching.

The source code is public. Store secrets and sensitive keys in GitHub Actions secrets;
never commit them or expose them in logs or artifacts.

### Smoke tests

#### Desktop

Desktop tests include rendered and headless application smoke tests.

#### Android

Android UI tests use a managed Pixel 3 emulator. The blackbox test checks the initial screen
of the release APK, signed with the debug key and shrunk by R8 without obfuscation.

#### Web

Use the `build-web-release` job in [CI](.github/workflows/ci.yml) as the source of truth
for setup and test commands.

The `build-mkdocs` job checks the published project path, app embedding, preview presets,
resizing, themes, Mermaid diagrams, and source links. Use its steps as the source of truth
for documentation browser checks.

#### iOS

The iOS smoke test launches the Release app on a fresh iPhone simulator with iOS 26.
