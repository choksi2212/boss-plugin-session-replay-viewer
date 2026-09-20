# Session Replay Viewer

A visual, step-by-step playback for recorded browser sessions. Built as a BOSS
desktop plugin.

## Why this exists

[BOSS RPA Recorder](https://github.com/risa-labs-inc/boss-plugin-rparecorder)
captures browser interactions into JSON. [RPA Engine](https://github.com/risa-labs-inc/boss-plugin-rpaengine)
replays them headlessly. Neither gives you a way to **read** a recorded session
without running it - you only see "action 3 of 12 succeeded, action 4 failed"
as scrollback. For a long workflow this is unreadable, and for a recorded
session the user wants to share with a teammate there is no visual at all.

This plugin fills the gap. Pick an rparecorder session file (or a directory of
sessions), and it walks the workflow one action at a time:

- a horizontal **timeline** with one tick per action, click to jump
- a **step view** with the action type, the recorded selector, the typed
  value, the URL at the moment of capture, and timing
- a one-paragraph **narrative** per step that explains what the recorder did
  in plain English ("Clicked the 'Submit' button by CSS selector
  `.btn-primary`, 1.2s after the previous step. URL changed to `/thank-you`.")
- **play, pause, step, jump** controls and a 0.5x / 1x / 2x / 5x speed dial
- **export to clipboard** as Markdown so the narrative can be pasted into a
  bug report or a handoff doc

It is the first time a recorded session can be reviewed as a document rather
than as a process.

## Session format

The plugin accepts both shapes the recorder emits:

```jsonc
// 1. Raw action list:
[
  {
    "type": "navigate",
    "selector": { "type": "none" },
    "value": "https://example.com/login",
    "timestamp": 1737000000000,
    "url": "https://example.com/login"
  },
  {
    "type": "click",
    "selector": { "type": "css", "value": "button.btn-primary", "isUnique": true },
    "elementText": "Submit",
    "elementType": "button",
    "timestamp": 1737000001200,
    "url": "https://example.com/login"
  }
]

// 2. Configuration form (with name + description):
{
  "name": "Quarterly Login Flow",
  "description": "Recorded for the auth-team handoff",
  "actions": [ /* ...same shape as above... */ ]
}
```

Unknown fields are ignored. Missing fields default to empty / null so a partial
session still parses.

A single file over 32 MiB is refused without reading further, to keep the
host UI responsive when an agent asks for a runaway recording.

## MCP tools

The plugin contributes three read-only tools to the `boss` MCP server, for
in-terminal agents that want the same view headlessly.

| Tool | Purpose |
|---|---|
| `replay_session_summary(path)` | Action count, duration, first/last timestamps, action-type breakdown |
| `replay_step_narrative(path, stepIndex)` | The narrative paragraph for one step |
| `replay_open_session(path)` | Overview block: name, description, source, action count, duration, breakdown |

All three reuse the same parser as the panel UI.

## Install

```bash
./gradlew buildPluginJar
cp build/libs/boss-plugin-session-replay-viewer-*.jar ~/.boss/plugins/
```

The plugin appears in the left sidebar's bottom slot under the name
"Session Replay".

## Requirements

- BOSS >= 9.4.2
- boss-plugin-api >= 1.0.93
- `fileSystemDataProvider` and `clipboardProvider` on the host. Both are
  nullable - the plugin falls back to direct `File` I/O and `java.awt`
  clipboard if either is missing, and logs the fallback so it is never silent.

## Build

```bash
./gradlew buildPluginJar       # produces the jar in build/libs/
./gradlew build                # full build
./gradlew processResources     # syncs the version into plugin.json
```

## License

Proprietary - Risa Labs Inc.
