---
name: TODO-sweep hygiene and exporter robustness
status: done
parent: Polish
depends-on: ["100"]
reviewers: [correctness, architecture, readability]
---

## Goal
Resolve the hygiene TODOs from the `2dd20a7` sweep that are safe, local
changes independent of the command-layer rework.

## Scope
- `util/Messages.kt`: keep the shared constants (prefix, colors), move
  per-feature message bodies (chest-edit outcomes, save/export feedback) to
  the call sites, keeping adventure `Component` construction where it already
  lives if the split is cleaner that way. The file may shrink to just shared
  constants or disappear if nothing remains.
- `JsonExporter.kt`: rename `normalized` to something that says what it
  normalizes by (it orders chests by their position); add a doc/kdoc only if
  naming cannot capture it.
- `JsonExporter.kt` **windows-safety bug**: `PosixFilePermission` /
  `PosixFilePermissions.fromPermissions` throws
  `UnsupportedOperationException` on filesystems without POSIX support. Guard
  the attribute application: attempt POSIX attrs, fall back to default file
  permissions when unsupported. This is a latent crash, not just a TODO.
- `DoubleChestGrouper.kt`: investigate whether the Chest `Type` block state
  directly exposes its other half (e.g. type + facing implies the neighbor) and
  simplify if yes; if not, write the conclusion as a short note where the old
  TODO was or in the ticket, then remove the TODO.
- Remove the resolved TODO markers.

## Out of scope
- The command/command layer (ticket 100).
- Any `rooster-region` API additions (ChestScanner TODOs).

## Acceptance criteria
- `just build`, `just test`, `just format` pass.
- Export still produces identical JSON; tests unchanged in behavior.
- `JsonExporter` no longer throws on non-POSIX filesystems.
- No `Messages` behavior change: identical player-visible strings.
- Resolved TODO markers gone.
