# ux review — 140 (Abort export on a double chest clipped by the selection)

## Round 1

### Verdict

The fail-closed loop is tight and truthful: `/uidesigner save` aborts before the
exporter, names the captured half and its missing partner with coordinates, and
gives a fix hint in the same red/`[UiDesigner]` style as the other errors. One
gap: the unloaded-chunk message assumes the player can load a chunk and offers no
way out when they cannot, even though `expand` is not the fix there.

### Findings

#### 1. Unloaded-chunk hint assumes the player can load the chunk and gives no fallback

- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:186-190`
- Problem: the unloaded branch tells the player "…is inside your selection but was
  not captured (its chunk is not loaded). Load the chunk, then try again." This is
  the correct diagnosis, but "load the chunk" is server-admin jargon and, more
  importantly, it is the *only* remediation offered. A player whose selection
  reaches a chunk they cannot or do not want to load (a stray chest far outside the
  design, a region larger than their view distance) is stuck re-reading the same
  message. Note that the intuitive follow-up to the other error — expanding the
  selection — cannot help here, so the player has no discoverable second move; the
  task's own focus calls out the "load the chunk / shrink" pair.
- Suggested fix: make the loaded action concrete and add the exclusion escape,
  e.g. "Move near (x2, y2, z2) to load that chunk and try again, or shrink the
  selection so neither half of this double chest is selected." (Shrinking must
  exclude the captured half too; excluding only the partner just flips the
  classification back to the outside-selection error.) Keep the message a single
  sentence so it stays as compact as the current one.

### Non-findings

- **Outside-selection hint is correct.** "…whose other half at (x2, y2, z2) is
  outside your selection. Expand the selection to include both halves." matches
  `ClippedHalf.partnerInsideSelection == false` (partner outside the region bounds)
  and the cuboid-selection model; expanding the bounding box does capture the
  partner and resolve it.
- **Coordinates are readable and unambiguous.** `BlockPos.coords()` renders
  `(x, y, z)` (block coords, matching `BlockPos`), and the sentence structure
  "the chest at A … whose other half at B" makes clear A is the captured half and
  B the missing partner. No leading/duplicate punctuation issues.
- **Multiple clipped chests are handled clearly.** The first is fully named and
  the rest counted ("1 more chest is also clipped." / "N more chests are also
  clipped."). Each `ClippedHalf` necessarily belongs to a distinct double chest
  (a matched pair is consumed/merged, and a partner present in `byPosition` makes
  `clippedHalf` return `null`), so the count is an accurate count of additional
  affected double chests, not a doubled count of halves. Naming the first and
  counting the rest matches the ticket's chosen UX.
- **No false success.** `save` returns `SaveOutcome.ClippedChests` before
  `exporter(...)` is reached (`UiDesignerCommand.kt:89-90`), and the success
  message is only built for `Exported`; a player who sees "Cannot export:" knows
  the run produced nothing and won't look for a stale/updated file from the green
  path.
- **Presentation is consistent.** `Messages.styled(Messages.errorColor, …)` gives
  the same aqua prefix + red body as the other failures; wording ("Cannot
  export:") is in the same register as "Could not write the export…". Length is
  justified by the actionable payload, not spammy, and the message is not silent.
- **Discoverability is unchanged and adequate.** No new command, alias, or
  argument was added, so tab completion and `/uidesigner help` need no update; the
  new failure is surfaced on the command the player already ran. The bare
  `/uidesigner save` path is the only entry point and it reports.
- **Other failure modes untouched and fine.** No selection ("No WorldEdit
  selection. Select a region first."), no chests ("The selection contains no
  chests. Place chests inside the selected region (loaded chunks only)."), and
  unwritable output ("Could not write the export to <path>: …") still read
  correctly; the clipped path short-circuits cleanly before them.

## Round 2

### Verdict

Concur with the round-1 fix. The unloaded-chunk branch now diagnoses the cause,
names the concrete action, and adds the exclusion escape I asked for, and the
copper-chest extension only widens which blocks reach the same fail-closed path.
No new in-scope findings; ship.

### Findings

None. My one round-1 finding is resolved by commit `5a21a34`
(`UiDesignerCommand.kt:187-192`), and I found no new player-facing problem in the
reworded text or the copper support.

### Non-findings

- **Concur with the round-1 fix.** The reworded branch reads "…is inside your
  selection but was not captured because its chunk is not loaded; move near
  (x2, y2, z2) to load that chunk and try again, or shrink the selection so
  neither half of this double chest is selected." It keeps the accurate
  diagnosis (bounds-inside + absent from `byPosition` means the chunk was
  skipped), replaces the jargon-y "Load the chunk" with a concrete "move near
  (x2, y2, z2)" action plus "try again", and adds the fallback I requested. It is
  one sentence, no more verbose than the two-remedy information requires, and
  still front-loads "Cannot export:" and both coordinates.
- **The shrink escape works.** Shrinking the selection until neither half of the
  named double is selected removes the captured half from `contents`, so
  `DoubleChestGrouper.group` no longer sees a `LEFT`/`RIGHT` block with an absent
  partner and `clipped` stays empty; any other chests still export. If it was the
  only chest, the player gets the ordinary "The selection contains no chests."
  message instead of a clip error, which is a truthful, non-scary end state. The
  message scopes the advice to "this double chest", so it does not imply the
  player must shrink away unrelated chests.
- **The advice is correctly branch-specific and not self-contradictory.** The
  outside-selection branch still says "Expand the selection to include both
  halves" and the unloaded branch says move-near-or-shrink; expanding genuinely
  cannot help the unloaded case, so the two messages giving opposite directions
  is correct, not confusing.
- **No new player-facing consequence from copper support.** Copper chests
  (`Tag.COPPER_CHESTS`, eight variants, confirmed present in the pinned
  `paper-api-26.2.build.111-stable`) now scan and group like normal chests, so a
  clipped copper double reaches the same `ClippedChests` abort instead of being
  silently dropped or reported as "no chests" (the correctness gap from round 1).
  The message wording stays generic ("double chest", "the chest at …"), which is
  accurate for copper and needs no special case. `/chest-edit` now also
  recognises copper chests, so naming/clearing them no longer reports
  "not a chest"; no new command, alias, argument, or help entry is involved.
- **Loop and feedback unchanged elsewhere.** `save` still returns
  `ClippedChests` before the exporter, so no file is written, and the success
  path, no-selection, no-chests, and write-failure messages are untouched. The
  message remains a single red `[UiDesigner]` component consistent with the other
  errors, and no success text can be reached from the clipped path.
- **The round-1 test-coverage fix keeps the message honest.** The unloaded-case
  command test now places the half at `(15,0,0)` with the partner at `(16,0,0)`
  in the unloaded chunk `(1,0)`, so the asserted "chunk is not loaded" /
  "shrink the selection" wording is exercised for the real production cause, not
  an air block in a loaded chunk. That closes the only way this message could
  have been truthful-looking but wrong.
