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
