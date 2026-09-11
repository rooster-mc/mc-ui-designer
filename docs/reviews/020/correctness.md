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

## Round 2

### Verdict
Ship. All four round-1 issues are genuinely fixed: a missing `position` now
fails fast for every chest (including a one-element list), the temp file
requests `rw-r--r--` on POSIX, a blank chest name normalises to null, and the
empty-slot rule is scoped to the capture side. The exact schema, ordering,
omission rules, atomic-write mechanics and the no-Bukkit seam all still hold.
The only findings are two low-severity edges: the world-readable guarantee is
umask-dependent while the new test asserts it unconditionally, and blank-name
normalisation is applied to chest names but not slot names.

### Issues

#### 1. The world-readable guarantee is umask-dependent, and the new test asserts it unconditionally (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/export/JsonExporter.kt:17-18,57-62`,
  `src/test/kotlin/dev/cypdashuhn/uidesigner/export/JsonExporterTest.kt:179-191`
- Problem: `createTemp` passes `WORLD_READABLE` (`posix:permissions` = `rw-r--r--`)
  to `Files.createTempFile`. In the JDK 25 source shipped in this toolchain,
  `TempFileHelper.create` forwards that attribute to `Files.createFile`, and
  `UnixChannelFactory.open` passes the resulting mode to `open`/`openat`. The
  kernel applies the process umask to that mode, so the file is only
  world-readable when the umask permits it: with the common `0022` it is `0644`,
  but with `0077` or `0027` it is `0600` or `0640`. The new test asserts
  `GROUP_READ` and `OTHERS_READ` with no umask assumption, so it fails in a
  hardened environment. Round-1's actual defect (the temp file defaulting to
  `0600` regardless of intent) is fixed for a normal umask; this is a caveat on
  the strength of the guarantee, not a regression.
- Reproduction: `umask 077`, then run
  `JsonExporterTest.export writes a world-readable file where the filesystem
  supports posix`; `Files.getPosixFilePermissions(target)` lacks `GROUP_READ`
  and `OTHERS_READ`.
- Suggested fix: decide whether world-readable is a hard requirement. If it is,
  `Files.setPosixFilePermissions(target, WORLD_READABLE.value())` after the move
  (chmod bypasses umask) on the POSIX branch; if respecting umask is intended,
  drop the `OTHERS_READ`/`GROUP_READ` assertions or gate the test on a permissive
  umask. Do not leave an unconditional assertion that depends on the runner's
  umask.

#### 2. Blank-name normalisation is chest-only; a blank slot `name` is still emitted (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/export/JsonExporter.kt:42`,
  `src/main/kotlin/dev/cypdashuhn/uidesigner/model/UiChest.kt:45`,
  `docs/data-format.md:35-37`
- Problem: `name = chest.name?.takeIf { it.isNotBlank() }` fixes the chest-name
  case, but slots are passed through `UiRow.ordered()` untouched, so
  `UiSlot(slot = 1, item = "minecraft:stone", name = "")` serializes with
  `"name": ""`. `docs/data-format.md:35` says a slot `name` is omitted when the
  item "has no custom display name", and an empty string is not a meaningful
  display name, so the code contradicts the general slot rule even though the
  blank-name sentence at `:36-37` is deliberately scoped to chest names. The
  practical risk is low (ticket 050's clear path targets chest names), but the
  same "absence is null" invariant that round 1 established is not enforced for
  slots, and a future item-name clear that yields `""` would leak it.
- Reproduction: `JsonExporter.toJson(listOf(UiChest(name = "Shop", rows = 3,
  content = listOf(UiRow(1, listOf(UiSlot(1, "minecraft:stone", "")))),
  position = BlockPos(0, 0, 0))))` contains `"name": ""` inside the slot.
- Suggested fix: normalise slot names the same way in `UiRow.ordered()`
  (`copy(slots = slots.sortedBy { it.slot }.map { it.copy(name =
  it.name?.takeIf(String::isNotBlank)) })`), or state in `docs/data-format.md`
  that a blank slot `name` is a caller contract and must be `null`.

### Non-issues
- **Round-1 #1 (position default) is resolved and validated for every chest.**
  `UiChest.position` is now `BlockPos? = null` (`UiChest.kt:32`) and
  `normalized` calls `chests.forEach { it.requiredPosition() }` before sorting
  (`JsonExporter.kt:37`). The explicit `forEach` is not redundant: `sortedBy`
  builds a `compareBy` comparator whose selector is only invoked during
  comparisons, so a one-element list would never call it — the `forEach` is the
  only thing that makes the single-element case fail. The regression test
  `a chest without a position fails fast...` (`JsonExporterTest.kt:74-79`)
  covers exactly that one-element input and asserts `IllegalArgumentException`,
  which `requireNotNull` throws.
- **The round-trip test still holds.** `UiChestTest` builds a chest with the
  default `position = null`; `@Transient` keeps it out of the JSON, decode
  restores the null default, and data-class equality compares null to null. No
  change to the test was needed and none is implied by the nullable model.
- **Round-1 #2 (empty slots) is resolved by scoping the doc, matching the
  code.** `docs/data-format.md:33-34` now says the scanner does not emit no-item
  slots and that the exporter drops empty rows, which is exactly
  `JsonExporter.kt:47`. The model still has no empty-slot representation
  (`UiSlot.item` is non-null), so no code could be expected to drop one; code,
  doc and ticket acceptance criteria agree.
- **Round-1 #3 (permissions) is resolved in the normal case.** The POSIX branch
  requests `rw-r--r--` (`JsonExporter.kt:57-59`), the non-POSIX branch falls
  back to `createTempFile(dir, prefix, suffix)` with no attributes
  (`:60-61`), and `PosixFilePermissions.fromString` needs no POSIX filesystem to
  build, so the `WORLD_READABLE` initializer is safe everywhere. The attribute
  is immutable and safely shared across calls. The umask caveat is issue 1.
- **Round-1 #4 (blank chest name) is resolved and correctly scoped.**
  `takeIf { it.isNotBlank() }` returns null for `null` (via `?.`), `""` and
  whitespace-only names, and leaves leading/trailing whitespace on a real name
  intact; `docs/data-format.md:36-37` documents exactly this. The test at
  `JsonExporterTest.kt:98-112` pins whitespace-only to a null name. (A
  non-breaking space `"\u00A0"` is not Java whitespace and is kept, which is an
  acceptable reading of "whitespace-only".)
- **Exact schema and key order unchanged and still pinned.** `UiChest` emits
  `name` (when non-null), `rows`, `content`; `UiSlot` emits `slot`, `item`,
  `name` (when non-null); `UiRow` emits `row`, `slots`. The literal snapshot
  (`JsonExporterTest.kt:200-234`) and the escaped/unicode snapshot
  (`:236-256`) both still match the schema in `docs/data-format.md:9-28`.
- **Ordering is total, deterministic and documented.** `BlockPos.compareTo`
  (`UiChest.kt:18-24`) compares x, then y, then z via `Int.compareTo` (no
  subtraction overflow); chests sort by required position, rows by `row`, slots
  by `slot`. The round-1 tie-break ambiguity is closed by
  `docs/data-format.md:41-43` ("equal positions keep their input order (stable
  sort)"), which matches `sortedBy`.
- **Omission rules are consistent.** Empty rows are filtered after sorting
  (`JsonExporter.kt:44-47`); `content` and `rows` have no defaults so they are
  always emitted (an empty chest emits `"content": []`); null `name`s are
  omitted by `encodeDefaults = false`. `emptyList()` still yields `[]`.
- **Atomic-write mechanics are unchanged and sound.** `target.toAbsolutePath()`
  guarantees a non-null parent for bare filenames; the temp is created in the
  target's directory; `toJson` is evaluated before `writeString` so a missing
  position throws before any bytes are written; the write is followed by
  `REPLACE_EXISTING + ATOMIC_MOVE` with an `AtomicMoveNotSupportedException`
  fallback; any throw deletes the temp and rethrows, leaving the old target
  intact. The overwrite path is exercised by the stale-file test
  (`JsonExporterTest.kt:134-158`).
- **No Bukkit/FAWE imports in `model` or `export`** (grep over both trees finds
  only `kotlinx.serialization` and `java.nio.file`), so the purity seam holds.
- **Double-chest edge cases remain out of scope for this ticket.** The exporter
  consumes already-merged `UiChest` values; orientations, single-half
  selections, trapped chests and shared inventories are grouping/capture
  concerns for later tickets.
- **No new threading concern.** `model`/`export` still touch no Bukkit world or
  inventory state; the file IO is synchronous and caller-driven.
- **Ticket text drift (documentation only, no code impact).** The ticket scope
  still says "empty slots omitted" and its note still claims
  `docs/data-format.md` "currently says `""`"
  (`docs/tasks/020-model-and-json.md:16,35-36`), both stale after the round-1
  doc rewrite. The authoritative format doc and the acceptance criteria are
  consistent with the code, so this does not affect correctness.
