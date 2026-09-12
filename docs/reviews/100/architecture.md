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
