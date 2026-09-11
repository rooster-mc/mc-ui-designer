---
name: Project setup and dev server
status: done
parent: MVP
depends-on: []
reviewers: [tester, correctness, architecture, readability]
---

## Goal
A bootable Paper dev server on `localhost:25000` with Fast Async WorldEdit and
the (empty) UiDesigner plugin loaded, plus a working build/test/run loop.

## Scope
- Gradle Kotlin DSL single module. Kotlin `2.4.10`, Java `25`, Paper `26.2`.
- Plugins: `xyz.jpenilla.run-paper` `3.0.2`, `com.gradleup.shadow` `8.3.6`,
  `net.minecrell.plugin-yml.bukkit` `0.6.0`.
- Dependencies: `paper-api` (`compileOnly`), `commandapi-paper-shade` `11.2.0`,
  `kotlinx-serialization-json`, FAWE Core/Bukkit via the IntellectualSites BOM
  (`compileOnly`), MockBukkit `mockbukkit-v26.2` + JUnit 5 (`test`).
- `UiDesignerPlugin` main class, `onEnable`/`onDisable`, plugin-yml metadata
  (`name: UiDesigner`, `apiVersion: 26.2`, command declarations).
- `runServer` configured for port `25000`, `online-mode=false`, and FAWE
  downloaded automatically (run-paper `downloadPlugins`, Hangar) so the use
  case works out of the box.
- `config.yml` resource with `output-file` defaulted from the Gradle property
  `uiDesigner.defaultOutput` (resource filtering).
- `justfile`: `build`, `run`, `test`, `format` (ktlint).
- Test source set with JUnit 5 + MockBukkit and one smoke test.
- `.editorconfig` and `.gitignore` (already present at repo root; extend if
  needed).

## Acceptance criteria
- `just build` produces a shaded plugin jar.
- `just run` boots a server reachable at `localhost:25000`; console shows
  `UiDesigner` enabled and FAWE present.
- `just test` runs the JUnit suite green.
- Plugin jar loads with no errors in the server log.

## Out of scope
- Any chest logic (later tickets).
- Publishing / CI.

## Notes
- `run-paper` has no direct port option; set `server-port=25000` via a
  `run/server.properties` written by the run task (or a committed template).
  Do not commit the whole `run/` directory.
- FAWE Hangar project id is `FastAsyncWorldEdit`; if auto-download is fragile,
  document the manual jar drop as a fallback.
- Reference: `/home/cyp/repos/extended-inventory/build.gradle.kts` for the
  exact plugin/dependency versions and `runServer` block.

### Deviations from this ticket

- `run-paper` `3.0.2` → `3.1.0`: `3.0.2` fails Gradle 9.7 task validation
  (`downloadPlugins.elements` is missing an input/output annotation); `3.1.0`
  fixes it and uses the current Paper Fill API v3.
- JUnit 5 → `6.1.3`: MockBukkit `4.116.1` declares `junit-jupiter-api:6.1.3`.
- FAWE is downloaded from the GitHub release instead of Hangar: Hangar's
  `FastAsyncWorldEdit` versions have null `downloadUrl`s, and `2.15.4` has no
  release assets, so `2.15.3` is the newest downloadable. The same `2.15.3` is
  used for the `compileOnly` FAWE dependencies (the BOM still supplies their
  transitive deps).
- foojay toolchain resolver `0.8.0` → `1.0.0`.
- The `plugin.yml` command declarations were removed: CommandAPI owns command
  registration (tickets 050/060), so declaring them here would create a second
  Brigadier node that shadows the CommandAPI command.
