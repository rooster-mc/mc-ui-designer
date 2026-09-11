# Correctness review — 020 (Data model and JSON export)

## Round 1

### Verdict
Ship with fixes. The emitted JSON matches `docs/data-format.md` and every
acceptance criterion passes for well-formed input, but the `position` default
lets the exporter silently fall back to input order (breaking the "deterministic
regardless of input order" guarantee), and there are a few low-severity
robustness gaps around empty slots, file permissions, and empty-string names.

### Issues

#### 1. Default `position` silently defeats deterministic position ordering (severity: medium)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/model/UiChest.kt:32`,
  `src/main/kotlin/dev/cypdashuhn/uidesigner/export/JsonExporter.kt:29-31`
- Problem: `@Transient val position: BlockPos = BlockPos(0, 0, 0)` gives every
  `UiChest` constructed without an explicit position the same sort key.
  `ordered` sorts with `sortedBy { it.position }`, and Kotlin's sort is stable,
  so those chests keep input order. That is the exact failure mode the ticket's
  criterion "Ordering is deterministic regardless of input order" forbids: two
  different input orders produce two different JSON documents, and
  `docs/data-format.md:38-39` ("ordered ... by the world position of their
  canonical ... block") is silently violated. The origin default is a plausible
  coordinate, so a caller that forgets to set it does not fail loudly. Every
  current test sets `position`, so nothing catches this; ticket 040's grouper is
  the likely first real caller.
- Reproduction: build `a = UiChest(name = "a", rows = 3, content = ..., position
  = BlockPos(0,0,0))` and `b = ...("b", ..., BlockPos(0,0,0))` (i.e. both left at
  the default), then compare `JsonExporter.toJson(listOf(a, b))` with
  `JsonExporter.toJson(listOf(b, a))`; the `name` order differs.
- Suggested fix: make a missing position loud rather than silent — e.g.
  `@Transient val position: BlockPos? = null` and `requireNotNull(it.position)`
  (or a documented nulls-last order) in `ordered`, or move ordering to a
  `(position, chest)` pair built at the call site. If the origin default is kept,
  document the "must set position" invariant and add a test with equal positions
  pinning the tie-break.

#### 2. "Empty slots are omitted" has no code counterpart; only empty rows are dropped (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/export/JsonExporter.kt:38`,
  `docs/data-format.md:33`
- Problem: `ordered` filters rows with `it.slots.isNotEmpty()` but never inspects
  individual slots. `UiSlot.item` is a non-null `String` (`UiChest.kt:44`), so the
  only way to represent an empty slot is a sentinel such as `""` or
  `minecraft:air`, and the exporter emits it verbatim. The ticket scope explicitly
  assigns "empty slots omitted" to `JsonExporter`, yet no code implements it and
  no test pins it. If the future capture layer walks all 27/54 inventory slots
  and emits air, the output will contain slots the schema says to omit.
- Suggested fix: either filter slots whose `item` is blank / `minecraft:air` in
  `ordered`, or — preferred, since the model has no empty-slot concept — reword
  `docs/data-format.md:33` and the ticket to state that omission is a
  capture-side rule ("slots with no item are not emitted by the scanner").

#### 3. Atomic write changes the target file's permissions to owner-only (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/export/JsonExporter.kt:19-22`
- Problem: on POSIX, `Files.createTempFile` creates the temp file with
  `rw-------` (verified in `java.nio.file.TempFileHelper$PosixPermissions`:
  `OWNER_READ` + `OWNER_WRITE` only), and `Files.move` preserves the inode's
  permissions. The exported `design.json` therefore becomes `0600` regardless of
  the previous target mode or the process umask, whereas a direct
  `Files.writeString(target, ...)` would have created `0644`. In the "JSON handed
  to an agent/user" workflow a different reader account cannot read the file, and
  overwriting an existing world-readable output silently tightens it.
- Reproduction: on Linux run `JsonExporter.export(chests, dir.resolve("design.json"))`;
  `stat -c '%a' design.json` reports `600`.
- Suggested fix: pass a POSIX file attribute
  (`PosixFilePermissions.asFileAttribute(fromString("rw-r--r--"))`) to
  `createTempFile` when the filesystem supports POSIX, or copy the existing
  target's permissions onto the temp before the move, keeping a non-POSIX
  fallback.

#### 4. Empty-string chest name is emitted, not omitted (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/export/JsonExporter.kt:29-40`,
  `src/main/kotlin/dev/cypdashuhn/uidesigner/model/UiChest.kt:29`
- Problem: the unnamed-chest decision is "omit when unnamed", implemented as
  `name == null` via `encodeDefaults = false`. A caller that represents a cleared
  name as `""` (a plausible result of ticket 050's `/chest-edit clear`) emits
  `"name": ""`, contradicting `docs/data-format.md:34`. Code, tests and doc agree
  for `null`, but the "unnamed means null" invariant is implicit and unenforced.
- Suggested fix: normalize blank to null in `ordered` (or at the naming seam), or
  state explicitly in `docs/data-format.md` that `""` is not a valid unnamed
  representation and that callers must pass `null`.

### Non-issues
- **Exact schema match.** Generated serializers expose exactly `name`/`rows`/
  `content` (`UiChest`) and `slot`/`item`/`name` (`UiSlot`); key order matches the
  declaration order in `docs/data-format.md:9-28`, and the pretty-print snapshot
  test passes.
- **`name` omission works as intended.** Bytecode for
  `UiChest.write$Self` gates the name on
  `shouldEncodeElementDefault || name != null`, so with `encodeDefaults = false`
  a null name is skipped; the unnamed chest and unnamed slot are both covered by
  the literal snapshot (`JsonExporterTest.kt:122-158`).
- **1-based `row`/`slot` and `rows` semantics are pass-through.** The model stores
  what the capture layer provides; deriving 3/6 rows and 1-based indices is out of
  scope for this ticket.
- **Ordering comparator is correct and total.** `BlockPos.compareTo` uses
  `Int.compareTo` per axis (no subtraction, so no overflow), x then y then z, and
  matches the canonical-position rule. `sortedBy` is stable; rows are sorted
  before the empty-row filter, and slots before serialization.
- **Atomic-write mechanics are sound.** Temp file is created in the target's
  parent directory, moved with `REPLACE_EXISTING` + `ATOMIC_MOVE`, falls back on
  `AtomicMoveNotSupportedException`, the temp is deleted on any failure, and
  `createDirectories` creates the parent. The "no leftover temp" assertion
  (`JsonExporterTest.kt:108-113`) covers the observable end state.
- **No Bukkit/FAWE imports in `model` or `export`**; the purity seam holds.
- **Unnamed-chest decision is consistent** across `UiChest.kt` (nullable default),
  the tests (snapshot omits the key) and `docs/data-format.md` (omit when
  unnamed), including the delta table (`:76`).
- **`@Transient`/round-trip.** `position` is excluded from the descriptor (3
  elements, not 4) and resets to its default on decode; reading JSON back in is
  explicitly out of scope, and the round-trip test only asserts equality for the
  default position, so this is expected rather than a defect.
- **Double-chest edge cases** (orientations, one half selected, trapped chests,
  overlapping selections, shared inventories) are grouping/capture concerns owned
  by later tickets; the exporter receives already-merged `UiChest` values.
- **Threading.** `model`/`export` contain no Bukkit world/inventory access; the
  file IO is synchronous but caller-driven, and the ticket sets no async
  requirement.
