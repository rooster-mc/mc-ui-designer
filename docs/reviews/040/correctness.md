# Correctness review — 040 (Double-chest grouping)

## Round 1
### Verdict
Ship with fixes. The primary holder route is sound in production and the
row/slot math is correct, but the geometry fallback is not safe against
unlinked/mismatched halves: it can emit `rows = 6` from a 27-slot inventory and,
when a half was already emitted as a single, it can emit that chest twice,
violating an explicit acceptance criterion.

### Issues
#### 1. Geometry fallback hardcodes 6 rows while reading a 27-slot inventory (severity: medium)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/DoubleChestGrouper.kt:62-73`
  (`geometryMatch` returns `chest.inventory.contents`) and `:31` (`rows = DOUBLE_ROWS`).
- Problem: the fallback returns `chest.inventory.contents` for the *current*
  half. `Chest.getInventory()` is only the shared 54-slot
  `DoubleChestInventory` when the two halves are actually linked (holder is a
  `DoubleChest`); for an unlinked chest it is the block's own 27-slot
  inventory (`CraftChest.getInventory()` returns `getBlockInventory()` unless
  `ChestBlock.getMenuProvider` yields a double provider — verified against
  `run/versions/26.2/paper-26.2.jar`). So the fallback can return a 27-item
  list while `group` stamps `rows = 6`, producing a `UiChest` whose `rows` (6)
  contradicts its `content` (only rows 1..3), and any item in the *partner*
  half is silently lost. This is exactly the `docs/data-format.md:45-46` rule
  ("double chests have 54 [slots]") being violated.
  Reproduction: adjacent blocks where one has `ChestData.Type.LEFT` (or RIGHT)
  pointing at the other but the pair is not linked, both inside the selection
  and both in `contents` (holder is `null`). The scanner reads 27 per half, the
  fallback merges, and the result is a 6-row chest with 27 items. This is
  reachable after a FAWE paste / `/setblock` of inconsistent chest block data,
  and is the only way the fallback's double branch runs in production (a
  genuinely linked pair takes the holder route).
  The `geometryChest` test helper (`DoubleChestGrouperTest.kt:241-258`) injects
  a 54-slot `state.provided` for both halves, so the test can never observe
  this divergence — it pins the fallback's plumbing, not its inventory
  contract.
- Suggested fix: don't hardcode the row count from the detection branch. Derive
  `rows` from the list actually being serialized (e.g. `items.size / 9`, with a
  guard that the size is a multiple of 9), so a 27-item fallback yields 3 rows.
  Optionally, in `geometryMatch` also require the current half's inventory to be
  the 54-slot shared one before treating it as a double, otherwise fall through
  to the single route. Add a test that drives the fallback with a 27-slot
  `provided` and asserts a 3-row result (or, if you keep 6 rows, that both
  halves' items are combined).

#### 2. A partner already emitted as a single can be merged again, emitting it twice (severity: medium)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/DoubleChestGrouper.kt:24-43`
  (`consumed` handling) and `:62-73` (`geometryMatch` only checks
  `partner in byPosition`).
- Problem: `consumed` is only consulted for the chest currently being iterated
  (`:25`); a later chest may still claim a partner that was already emitted.
  With the geometry route, `partner in byPosition` is the *only* check — the
  partner's own block data is never validated, and `consumed` is not consulted.
  Reproduction (inconsistent block data, e.g. a pasted structure): chest `B`
  at `(0,0)` is `SINGLE`; chest `A` at `(1,0)` is `LEFT` facing `SOUTH` so
  `partnerOffset` points at `B`. Scanner order is `x`-major, so `B` is processed
  first: holder is `null`, `partnerOffset(SINGLE, …)` is `null`, `B` is emitted
  as a 3-row single. Then `A` is processed: `partner = (0,0) in byPosition`, so
  the fallback emits a double containing `B`. `B` now appears both as its own
  `UiChest` and inside `A`'s — the "No chest is emitted twice" acceptance
  criterion (`docs/tasks/040-double-chest-grouping.md:28`) fails. The holder
  route is symmetric and does not have this asymmetry, but the fallback does.
  Note this repro also triggers issue 1 (A's inventory is 27, rows = 6).
- Suggested fix: in the fallback, require the partner to be the complementary
  half — same `facing`, `partner`'s `blockData.type` the opposite of the current
  one — before merging; and/or pass `consumed` into `match`/`geometryMatch` and
  reject a partner already consumed. A cleaner alternative is a first pass that
  resolves all doubles before emitting any single, so an emitted single can
  never be re-claimed.

### Non-issues
- **Holder route in production.** `CraftChest.getInventory()` builds a
  `CraftInventoryDoubleChest` when the pair is linked, whose `getHolder()` is a
  `DoubleChest`; `DoubleChest.getLeftSide()/getRightSide()` resolve to `Chest`
  block states (`BlockEntity.getOwner` → `CraftBlock.at(...).getState()`), so
  `halvesIn` works as written. Reading `holder.inventory.contents` yields the
  combined 54 slots.
- **`partnerOffset` matches vanilla.** LEFT = clockwise (`N→E`, `E→S`, `S→W`,
  `W→N`) and RIGHT = counter-clockwise, checked against the four table entries
  at `:81-100`; the orientation tests' hand-written `(dx, dz)` agree with the
  table (their lack of an independent vanilla anchor is the tester's issue 4).
- **One-half selection.** `halvesIn` (`:75-79`) filters to `byPosition` and
  `takeIf { it.size == 2 }` falls through when the partner is unselected; the
  geometry route then finds the partner absent and returns `null`, so the
  single route uses the selected half's own `content.items` (27) and never
  reads the unselected half's inventory. Matches the documented decision.
- **No double emission with consistent block data.** The `consumed.add` at
  `:25` plus `consumed += match.positions` at `:28` make the reversed-input
  case idempotent (both halves are consumed on the first encounter).
- **Row/slot math.** `index / 9 + 1` and `index % 9 + 1` (`:107`, `:114`) give
  1-based row/slot correctly for 27- and 54-slot lists; `groupBy` preserves
  ascending row order and `mapNotNull` preserves ascending slot order, and the
  exporter re-sorts anyway.
- **Empty/air slots.** `stack?.takeUnless { it.isEmpty }` (`:106`) drops nulls,
  air and zero-amount stacks, and `rows()` never emits a row with no slots.
- **Slot names.** `plainDisplayName` (`:122-126`) uses the custom display name,
  strips it to plain text and maps blank/whitespace to `null`; the exporter
  also omits blanks. No material name leaks in.
- **`name = null` on the chest.** Correct for 040; naming is 060
  (`docs/tasks/040-double-chest-grouping.md:30-31`).
- **Canonical position.** `match.positions.min()` uses `BlockPos`'s
  x→y→z ordering, matching `docs/data-format.md:41-43`; the single route keeps
  its own position. Grouper output order is not the schema's concern — the
  exporter is the ordering authority.
- **Empty input / unloaded chunks.** `group(region, emptyList())` returns an
  empty list; a chest in a chunk that unloads between scan and grouping yields
  a non-`Chest` state and degrades to the single route instead of throwing.
- **Docs nit (not filed as an issue).** `docs/architecture.md:77-79` says both
  routes read the shared 54-slot inventory; the fallback only does so when the
  halves are linked. Worth a wording tweak alongside issue 1's fix.
