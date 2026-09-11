---
name: Config loading and default output path
status: todo
parent: MVP
depends-on: [000]
reviewers: [tester, correctness, readability]
---

## Goal
A typed configuration read from `config.yml`, with the output file path
overridable and a sane default baked in at build time.

## Scope
- `UiDesignerConfig` wrapping Bukkit `FileConfiguration`.
- Keys:
  - `output-file` — path of the exported JSON. Relative paths resolve against
    the plugin data folder. Default from Gradle property
    `uiDesigner.defaultOutput`.
- Load on enable; `/uidesigner reload` re-reads the file and rebuilds the
  config.
- Missing file / missing key falls back to defaults and writes them back.

## Acceptance criteria
- Default `config.yml` is written to the data folder on first enable with the
  Gradle-provided default.
- Editing `output-file` and running `/uidesigner reload` changes the path used
  by the exporter.
- Unit tests cover default resolution, relative/absolute path resolution, and
  reload.

## Out of scope
- Any other config keys (add them when a ticket needs them).

## Notes
- Keep config access behind the typed class; do not scatter
  `plugin.config.getString(...)` calls.
- Consider exposing the default via a small generated `BuildConfig` object if
  resource filtering proves awkward.
