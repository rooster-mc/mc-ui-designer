# Architecture review — 020 (Data model and JSON export)

## Round 1

### Verdict
Ship with fixes. The change lands exactly where `docs/architecture.md` says it
should (`model/UiChest.kt`, `export/JsonExporter.kt`), the `model`/`export`
purity seam is respected, and the exporter API is the right shape for ticket
060 to call. The remaining work is small and mostly documentation: pin down who
owns chest ordering (the doc currently gives it to the grouper, the code gives
it to the exporter), name `BlockPos` in `architecture.md`, and record the
`position` invariant that the origin default hides.

### Issues

#### 1. Ordering has two claimed owners: the grouper (doc) and the exporter (code) (severity: medium) — fix now, verify again in 040
- Location: `docs/architecture.md:51` vs `src/main/kotlin/dev/cypdashuhn/uidesigner/export/JsonExporter.kt:29-31`, `docs/tasks/020-model-and-json.md:24-25`, `docs/data-format.md:38-39`
- Problem: `architecture.md` describes the grouper output as
  `List<UiChest> (double chests merged, ordered)`, but the acceptance criteria
  for this ticket put deterministic position ordering on the exporter, and
  `JsonExporter.ordered` does exactly that (`sortedBy { it.position }`). When
  040 lands there will be two plausible places to sort. If the grouper sorts and
  the exporter sorts, the exporter is redundant; worse, if 040 "owns" ordering
  and the exporter's `position` default (see issue 2) is not populated, the
  exporter silently reshuffles into input order and contradicts
  `data-format.md:38-39`. Ordering is a serialization rule (the format doc
  states it under the schema), so the exporter should be the single authority.
- Suggested fix: make the doc match the ticket. Either drop `(ordered)` from
  `architecture.md:51` and state that `JsonExporter` is the single ordering
  authority, or move sorting entirely into the grouper and have the exporter
  preserve input order. Given ticket 020 already mandates exporter-side sorting,
  updating the architecture line is the smaller change; either way, 040 must not
  re-sort on the assumption that it owns ordering.

#### 2. `position` defaults to `BlockPos(0,0,0)`, which hides a missing canonical position (severity: low) — fix now or carry into 040
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/model/UiChest.kt:32`, `docs/data-format.md:51`
- Problem: `@Transient` forces a default value (kotlinx-serialization requires
  it), so a default is unavoidable. But `(0,0,0)` is a *plausible* coordinate:
  if 040's grouper constructs a `UiChest` without setting `position`, every
  chest compares equal and output order silently falls back to input order
  rather than failing. The invariant "a `UiChest` fed to the exporter must carry
  its canonical position" is real but only implicit.
- Suggested fix: prefer a nullable default
  (`@Transient val position: BlockPos? = null`) and either sort nulls
  deterministically or `requireNotNull(position)` in `ordered`, so the omission
  is loud. If the origin default is kept, add one why-line documenting the
  invariant. Either way this is cheap now and prevents a subtle 040 bug.

#### 3. `BlockPos` is a shared model primitive but is unnamed in `architecture.md` and filed under `UiChest.kt` (severity: low) — doc fix now, file split when 030 lands
- Location: `docs/architecture.md:11`, `src/main/kotlin/dev/cypdashuhn/uidesigner/model/UiChest.kt:13-25`
- Problem: `architecture.md:11` enumerates the contents of `model/UiChest.kt`
  ("UiChest / UiRow / UiSlot + Json config") and does not mention `BlockPos`.
  Yet `BlockPos` is not chest-specific: 030 needs positions for `ChestContent`
  and for region corners, and 040 needs the canonical position. It is the
  natural shared coordinate type for the pure `model` layer, so placing it in
  `model` is right — the doc just has to say so. Keeping it inside `UiChest.kt`
  is acceptable for a single consumer, but once 030 adds a second consumer the
  file name becomes misleading.
- Suggested fix: add `BlockPos` to the `architecture.md:11` line now. When 030
  introduces the second consumer, move it to `model/BlockPos.kt` (no behavioural
  change; import path stays `dev.cypdashuhn.uidesigner.model.BlockPos`).

#### 4. `architecture.md` places `ChestContent` inside the package it declares Bukkit-free (severity: low) — carry over to 030
- Location: `docs/architecture.md:12` vs `docs/architecture.md:34-35`, `docs/tasks/030-selection-capture.md:20-21`
- Problem: The Seams section says "`model` and `export` are pure Kotlin: no
  Bukkit imports", but `model/ChestContent.kt` is described as "chest position +
  inventory" and ticket 030 explicitly allows Bukkit types there. As written,
  the first ticket that adds `ChestContent` will either violate the stated seam
  or have to move the file. This is pre-existing doc drift, not a defect in 020
  (the code added here is pure), but it is the next architectural decision 030
  must make.
- Suggested fix: before 030, decide and update `architecture.md` — most cleanly,
  put `ChestContent` in `capture/` and keep `model` pure, with `ChestScanner`
  mapping Bukkit `ItemStack` to the pure `UiSlot`. If `ChestContent` stays in
  `model`, state the exception explicitly rather than leaving the two lines
  contradictory.

### Non-issues
- **Package layout matches the documented architecture.** `model/UiChest.kt`
  and `export/JsonExporter.kt` are exactly where `docs/architecture.md:11,21`
  puts them; no new package was invented and nothing was placed in
  `capture`/`commands` prematurely.
- **The `model`/`export` purity seam holds.** Neither file imports anything
  from `org.bukkit` or FAWE. `JsonExporter` only pulls `java.nio.file` and the
  model; `UiChest.kt` only pulls `kotlinx.serialization`. The direction is
  `export → model`, no cycle.
- **`JsonExporter` is a sensible seam for 060.** `export(chests, target: Path)`
  is a single stateless call the command can make after grouping, and
  `toJson(chests)` is a pure helper for the snapshot/round-trip tests. It needs
  no interface, no plugin handle and no config; 060 can pass
  `config.outputFile` directly (a `File` would need a `.toPath()`, which is
  trivial). Atomic-write behaviour (temp file in the target directory, move with
  `ATOMIC_MOVE`, fall back on `AtomicMoveNotSupportedException`, delete the temp
  on failure) satisfies the 060 criterion "a failed write leaves the previous
  file intact" without any caller involvement.
- **`BlockPos : Comparable<BlockPos>` is justified, not over-generalised.**
  The exporter genuinely sorts on it, and the x→y→z order matches the
  canonical-position rule in `data-format.md:38-39`. A data class with
  `equals`/`hashCode` is appropriate for a value type; no extra geometry API was
  invented.
- **No premature generalisation for the backlog.** `UiChest` stays
  chest-specific rather than being generalised to a container/`UiContainer`
  abstraction; `docs/design.md:30-35` keeps barrels/shulkers and import out of
  scope, so generalising now would be speculative. `@Serializable` already makes
  a future reader possible without building one.
- **Keeping the serialization config in `model` is documented, and the
  `Json` shadow is tolerable.** `architecture.md:11` explicitly says
  `UiChest.kt` carries the "Json config", so `Json` (`UiChest.kt:7-11`) is not
  an undocumented layering leak. The name shadows
  `kotlinx.serialization.json.Json` for readers, which is a readability nit at
  most; it is not worth moving while there is a single serialization policy.
- **No drift-prone duplicated logic.** The only ordering logic is the private
  `ordered()`/`UiRow.ordered()` pair in the exporter; `BlockPos.compareTo` and
  `sortedBy { it.position }` are complementary, not duplicates. Empty-row
  filtering lives only in the exporter, matching `data-format.md:33`.
- **Empty input and stability are covered by the seam, not by special cases.**
  `Json.encodeToString(emptyList())` naturally yields `[]`, so no branch was
  added for it; `sortedBy` is stable, so equal positions keep input order, which
  is only a hazard if issue 2's default is relied upon.
