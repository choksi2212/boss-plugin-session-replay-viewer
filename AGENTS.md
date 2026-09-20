# AGENTS.md

## Project Overview

**Session Replay Viewer (Dynamic)** (`ai.rever.boss.plugin.dynamic.replay`) is a dynamic plugin for the BOSS desktop application.

Visual, step-by-step playback of rparecorder session files - timeline + per-step narrative so a recorded workflow is readable without running it.

- **Plugin ID**: `ai.rever.boss.plugin.dynamic.replay`
- **Main Class**: `ai.rever.boss.plugin.dynamic.replay.ReplayDynamicPlugin`
- **API Version**: 1.0.93
- **Slot**: left sidebar, bottom (`Panel.left.bottom`), priority 66

## Essential Commands

```bash
./gradlew buildPluginJar    # Build plugin JAR (output: build/libs/)
./gradlew build              # Full build (compile + buildPluginJar)
./gradlew processResources   # Process resources (syncs version)
```

## Workflow Rules

- Do NOT run the BOSS application to test. The user tests manually.
- After building, copy the JAR to `~/.boss/plugins/` for local testing.

## Architecture

### Plugin Structure
```
src/main/kotlin/   -> Plugin source (package: ai.rever.boss.plugin.dynamic.replay)
src/main/resources/META-INF/boss-plugin/plugin.json -> Plugin manifest
build.gradle.kts   -> Build config + version (single source of truth)
```

### Source map
- `ReplayDynamicPlugin.kt` - entry point; registers panel + MCP provider.
- `ReplayInfo.kt` - `PanelInfo` (id, slot, icon).
- `ReplayComponent.kt` - `PanelComponentWithUI`; owns the view model.
- `ReplayViewModel.kt` - session state, transport, copy-to-clipboard actions.
- `ReplayContent.kt` - `@Composable` UI: picker, list, timeline, step view.
- `RecordedSession.kt` - `@Serializable` model for one session file.
- `SessionParser.kt` - bounded-size read; accepts both shapes the recorder writes.
- `Narrative.kt` - one-paragraph generator + Markdown export + breakdown helper.
- `Clipboard.kt` - prefers `ClipboardProvider`, falls back to AWT with a warning.
- `ReplayMcpTools.kt` - the three `replay_*` MCP tool definitions.

### Key Patterns
- Entry point: `DynamicPlugin` interface with `register(context)` and `dispose()`.
- UI: `PanelComponentWithUI` with `@Composable Content()`.
- State: ViewModel pattern with `StateFlow`; auto-advance is a `Job` in a
  `SupervisorJob + Dispatchers.Default` scope, cancelled on pause / new session /
  jump past end.
- Providers from `PluginContext`: `fileSystemDataProvider`, `clipboardProvider`.
  Both nullable; UI must handle a null provider.
- Classloader isolation: AWT `Toolkit` is the documented fail mode for the
  clipboard, but the host's `ClipboardProvider` is always preferred.

### Provider fallbacks

`fileSystemDataProvider` is null on hosts that predate the codebase panel, and
`clipboardProvider` is null on hosts where the bridge to the system clipboard
is not wired. Both providers are documented as nullable in the API. The
plugin falls back to direct `java.io.File` I/O and `java.awt.Toolkit`
respectively when the provider is null, and `BossLogger` records the fallback
so it is never silent.

### Dependencies
- **boss-plugin-api**: compileOnly (provided by host at runtime).
- **Compose Desktop**: UI framework.
- **Decompose**: navigation + component lifecycle.
- **kotlinx-coroutines**, **kotlinx-serialization**.

## Version Management

**`build.gradle.kts` is the single source of truth for version.**

The `processResources` task automatically syncs the version into `plugin.json`
at build time. Never manually edit the version in `plugin.json` - only change
it in `build.gradle.kts`.

## Code Quality

- Compose Multiplatform APIs only (no Android-specific calls).
- All Kotlin files end with a newline.
- Handle null providers gracefully; log + fall back, never crash.
- Bound every read the user can trigger: `SessionParser.MAX_SESSION_BYTES` is
  the cap on a single session file.
- Spaced hyphens (` - `) only in prose; never em-dashes (U+2014).

## CI/CD

- `.github/workflows/test.yml` runs on every pull request and is the required
  status check. It downloads the latest `boss-plugin-api` jar and runs
  `./gradlew build`.
- `.github/workflows/build.yml` runs on push to main (and on
  `workflow_dispatch`) and delegates to
  `risa-labs-inc/BossConsole-Releases/.github/workflows/plugin-release.yml@main`
  to publish a GitHub release and push the jar to the BOSS Plugin Store. The
  Release workflow needs `permissions: contents: write` - without it the
  shared workflow 403s trying to create a release.
