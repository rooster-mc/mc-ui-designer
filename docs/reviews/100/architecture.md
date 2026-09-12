# Architecture review — 100 (Adopt rooster-commands and drop command-layer accidents)

## Round 1

### Verdict

The architecture doc is in good shape — package tree, seams, data-flow diagram,
and the `ChestCapture.capture` signature all match the code, and the
composite-build wiring follows the established `rooster-region` pattern with
correct substitution coordinates. The gap is `docs/design.md`: it still
describes the deleted `SelectionSource` seam and the removed permission
concept, and still lists `rooster-commands` as "not required", which the ticket
just made false. Layering itself is clean.

### Findings

1. **`docs/design.md` still documents the deleted `SelectionSource` seam.**
   - Location: `docs/design.md:62-63` ("the lazy delegating `SelectionSource`
     only exists to keep `FaweSelectionSource` from class-loading under
     MockBukkit").
   - Problem: Both types were removed in this ticket
     (`capture/SelectionSource.kt`, `capture/FaweSelectionSource.kt` deleted);
     the containment is now the `(Player) -> Region?` lambda in
     `UiDesignerPlugin.worldEditSelectionOf`. `docs/architecture.md` was
     updated for this, but `design.md` — which AGENTS.md lists as a
     must-read — still names the old seam as the mechanism behind the FAWE
     `depend` decision.
   - Suggested fix: rewrite the sentence to say FAWE containment is by lazy
     lambda invocation (or simply point at `docs/architecture.md`'s FAWE seam),
     in the same commit as the change that invalidated it.

2. **`docs/design.md` still documents the permission concept.**
   - Location: `docs/design.md:74-77` ("**Permissions:** `uidesigner.save`,
     `uidesigner.reload`, and `uidesigner.chest-edit` default to op and are
     declared in `build.gradle.kts` ... replies with a prefixed denial
     message").
   - Problem: The permission concept was dropped entirely this ticket — the
     `bukkit { permissions { ... } }` block is gone from `build.gradle.kts`,
     `Messages.noPermission` is gone, and the `(op)` markers were removed from
     the help text. `design.md`'s stack/decisions section now contradicts both
     the build file and `architecture.md` ("This is a local tool, so there are
     no permission checks").
   - Suggested fix: replace the bullet with the local-tool decision (no
     permissions; player-only executors silently no-op for non-players), or
     delete it and let `architecture.md` carry the sender semantics.

3. **`docs/design.md` still classifies `rooster-commands` as an unused
   candidate.**
   - Location: `docs/design.md:46-48` ("`rooster-core` (config helpers) and
     `rooster-commands` (CommandAPI wrapper) remain candidates to revisit
     before writing more commands").
   - Problem: This ticket adopts `rooster-commands` (and its `command-api`
     backend) as a hard dependency; the "Commands" stack bullet at
     `docs/design.md:56` also still lists only raw
     `dev.jorel:commandapi-paper-shade:11.2.0` with no mention of the
     `rooster-commands` layer now sitting between the plugin and CommandAPI.
     A reader of `design.md` alone would not know the DSL is adopted or that
     `../rooster-commands` must be checked out beside this repo (the
     `rooster-region` bullet documents its own checkout requirement; the new
     build has the same requirement via the `check(...)` in
     `settings.gradle.kts:22-24` and deserves the same note).
   - Suggested fix: update the `rooster-*` decision bullet to record the
     adoption (composite build, checkout requirement, `command-api` backend),
     and note in the Commands stack bullet that the DSL comes from
     `rooster-commands` with CommandAPI shaded beneath it.

4. **`docs/architecture.md` no longer records the composite-build substitution
   mapping for either sibling.**
   - Location: `docs/architecture.md:3-6` (intro) and the FAWE seam paragraph
     (`docs/architecture.md:33-44`).
   - Problem: The pre-ticket text documented that `settings.gradle.kts` maps
     `dev.rooster.region:rooster-region` → `:core` and
     `...-worldedit` → `:worldedit`; the rewrite dropped that detail and did
     not add the new mappings (`dev.rooster:rooster-commands` → `:`,
     `dev.rooster:command-api` → `:command-api`). The intro now says only
     "composite build (`:`, `:command-api`)", which names the included project
     paths but not the GAV coordinates they substitute, and says nothing about
     the sibling-checkout requirement. Someone cloning the repo without the
     siblings gets a `check(...)` failure with no doc explaining why.
   - Suggested fix: one sentence in the intro (or the seams section) listing
     both mappings and the `../rooster-region` + `../rooster-commands`
     checkout requirement, mirroring what `design.md` does for `rooster-region`.

5. **Transitive sibling-checkout requirement (`rooster-core`) is implicit.**
   - Location: `settings.gradle.kts:21-33`; `/home/cyp/repos/rooster-commands/settings.gradle.kts`
     (`includeBuild("../rooster-core")`).
   - Problem: `rooster-commands` itself composite-includes `../rooster-core`,
     so building mc-ui-designer now silently requires *three* sibling
     checkouts (`rooster-region`, `rooster-commands`, `rooster-core`), but the
     `check(...)` guards only verify the first two. A missing `rooster-core`
     surfaces as a resolution failure deep inside the included build rather
     than the clear message the first two produce. This mirrors how
     `rooster-region` presumably chains, so it may be accepted as the
     composite-build status quo — but it should be a conscious acceptance, and
     the checkout requirement belongs in the docs (see finding 4).
   - Suggested fix: either accept and document the three-sibling requirement,
     or add a matching `check(...)`-style guard/comment for the transitive
     dependency. Deferring to a `rooster-commands`-side fix (publishing to a
     maven repo so the composite chain stops at one hop) is also reasonable —
     record it rather than leave it implicit.

### Non-findings

- **`docs/architecture.md` is otherwise accurate.** Package tree has no
  `SelectionSource.kt`/`FaweSelectionSource.kt` and describes `ChestCapture.kt`
  as "selection lookup + player -> CapturedSelection?"; the seams section's
  `ChestCapture.capture(selectionOf, player)` matches the actual signature
  (`ChestCapture.kt:12`); the "injects a `(Player) -> Region?` selection
  provider" paragraph matches `UiDesignerCommand.kt:25`; the permission
  paragraph is replaced by the local-tool sender-semantics description that
  matches the `playerOrNull ?: return@onExecute` guards; the data-flow diagram's
  "selection lookup lambda (UiDesignerPlugin)" matches
  `UiDesignerPlugin.kt:37-39`. No stale `(op)` markers, permission nodes, or
  ticket-number cargo cult remain in `architecture.md` (`data-format.md:4`'s
  `TODO-QUEUE.md` mention is a historical provenance note, not a stale marker).
- **Composite-build wiring is correct and idiomatic.** The substitution
  aliases match `rooster-commands`' own coordinates exactly (root and
  `command-api` both declare `group = "dev.rooster"`, `version = "1.0.0"` in
  their `build.gradle.kts`), and the block mirrors the `rooster-region`
  pattern in `settings.gradle.kts:12-19` including the `check(...)` guard.
  Declaring both `dev.rooster:rooster-commands` and `dev.rooster:command-api`
  explicitly in `build.gradle.kts` is necessary, not redundant: the
  `rooster-commands` root module does not depend on `:command-api` (the
  dependency points the other way), and the command classes import both
  namespaces (`dev.rooster.commands.commandapi.command` and
  `dev.rooster.commands.*`).
- **Layering holds.** `export/` remains Bukkit-free (untouched by this
  ticket); FAWE containment moved from an interface to a lazy lambda but stays
  inside `UiDesignerPlugin` with no FAWE-typed imports beyond the
  rooster-region worldedit extensions, and `capture/` still only sees
  `dev.rooster.region` types; both command classes stay thin — they own only
  outcome→message mapping and delegate to `ChestCapture`/`DoubleChestGrouper`/
  `ChestNamer`/`JsonExporter`, with the injected providers unchanged in shape.
  The new `worldEditSelectionOf` helper is a faithful rename of the old
  `faweSelectionSource()` laziness role, and the explanatory comment it carries
  is a legitimate non-obvious-*why* comment.

## Round 2

### Verdict

Ship. All four round-1 findings are resolved accurately: `docs/design.md` now
matches the code on FAWE containment, the no-permission decision (including the
console no-op convention the round-1 correctness discussion settled on), and
the `rooster-commands` adoption record; `docs/architecture.md` documents the
substitution mappings and the three-sibling checkout requirement; and the
`rooster-core` `check(...)` follows the existing settings conventions with a
truthful why-comment. No new findings in my scope.

### Findings

None.

### Non-findings

- **Round-1 findings 1–3 (design.md staleness) resolved.** No
  `SelectionSource`/`FaweSelectionSource`/`noPermission`/permission-node
  references remain in `docs/design.md` or `docs/architecture.md`. The FAWE
  bullet now describes the `(Player) -> Region?` lambda and names
  `UiDesignerPlugin.worldEditSelectionOf`, matching the code. The
  "**No permissions.**" bullet states the convention the code now implements:
  `save` and both `/chest-edit` executors are player-only with silent console
  no-ops — confirmed against `UiDesignerCommand.kt:70-73` (`playerOrNull ?:
  return@onExecute`) and the round-2 `usageExecutor` guard in
  `ChestEditCommand.kt:37-41` (`sender as? Player ?: return@CommandExecutor`),
  with the bare-root `/chest-edit` console case now uniformly silent and pinned
  by the new test; `reload`, `help`, and the bare `/uidesigner` root accept any
  sender, as documented. The adoption record is accurate: sibling composite
  build, `../rooster-commands` checkout, transitive `../rooster-core`,
  `command-api` backend compiling to CommandAPI `CommandTree`s, CommandAPI
  11.2.0 shaded beneath, and the correct note that `rooster-core` is only
  transitive (this plugin does not use its service hooks — the imports in both
  command classes and `UiDesignerPlugin` span only `dev.rooster.commands`,
  `dev.rooster.commands.commandapi`, and `dev.rooster.region`).
- **Round-1 finding 4 (substitution mappings) resolved.** The new
  `docs/architecture.md` sentence lists all four mappings exactly as
  `settings.gradle.kts` declares them (`rooster-region` → `:core`,
  `rooster-region-worldedit` → `:worldedit`, `rooster-commands` → root,
  `command-api` → `:command-api`) and states the three-sibling checkout
  requirement with the `check(...)` guard. Package tree, seams, and data-flow
  sections are unaffected by the executor hoists in 8696937 — those changed no
  names, signatures, or layer boundaries the docs reference.
- **Round-1 finding 5 (rooster-core checkout) resolved appropriately.** The
  `check(...)` in `settings.gradle.kts:26-31` mirrors the two existing guards
  in message format and placement (settings-configuration time, before any
  includeBuild), and its why-comment explains the transitive nature — a
  legitimate non-obvious-*why* comment. The wording is truthful against the
  sibling: `rooster-commands/settings.gradle.kts` does
  `includeBuild("../rooster-core")` and its modules depend on
  `dev.rooster.core:rooster-core:1.0-SNAPSHOT` (group/version confirmed in
  `rooster-core/build.gradle.kts`). No substitution alias for
  `dev.rooster.core:rooster-core` is needed in this repo's
  `dependencySubstitution` block: `rooster-commands`' own settings already
  substitutes it inside its included graph, so the alias would be dead
  configuration here. The docs now say exactly this ("only pulled in
  transitively").
- **Executor hoists (8696937) introduce no structural drift.** The hoisted
  `usageExecutor`/`helpExecutor` locals stay inside `register()`; no new
  package, class, or seam appeared, so the architecture doc's package tree and
  integration-point description remain accurate. The `argOrNull<String>("name")
  ?: return@onExecute` early return and the `CONFIG_UNREADABLE_WARNING`
  const-templating change no documented contracts (correctness round 2 traced
  both as behaviour-preserving; I concur for the structure/docs angle).
