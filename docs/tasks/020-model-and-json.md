---
name: Data model and JSON export
status: done
parent: MVP
depends-on: [000]
reviewers: [tester, correctness, architecture, readability]
---

## Goal
The serializable chest model and a JSON exporter that produces exactly the
schema in `docs/data-format.md`.

## Scope
- `UiChest`, `UiRow`, `UiSlot` as `@Serializable` data classes.
- `JsonExporter` producing a pretty-printed JSON array, stable ordering, empty
  rows omitted, `name` omitted when absent.
- Atomic write to a target `Path` (write temp then move).
- No Bukkit imports in `model` or `export`.

## Acceptance criteria
- Round-trip serialization test passes.
- A fixture with a named and unnamed chest serializes to an exact expected
  JSON string (snapshot) matching `docs/data-format.md`.
- Ordering is deterministic regardless of input order (chests sorted by
  position; rows and slots sorted).
- Empty input produces `[]`.

## Out of scope
- Reading JSON back in.
- Capture from the world (later tickets).

## Notes
- `DesignJson { prettyPrint = true; encodeDefaults = false }` so omitted nullable
  fields stay omitted.
- Unnamed chests and slots omit `name` (null, or blank normalised to null);
  `docs/data-format.md` documents this.
