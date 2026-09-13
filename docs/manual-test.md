# Manual test gate

Acceptance criteria that cannot be automated live here, each owned by the ticket
that introduced it. A ticket may be `done` while its entry is `unverified`, but
the entry must exist so the gap is tracked (see `docs/workflow.md` → Manual
gate).

Status: `unverified` | `passed` | `failed`.

| Id | Ticket | Check | Status |
|---|---|---|---|
| MT-001 | 060 | Place two single chests and one double, fill them, name one with `/chest-edit`, select the region with FAWE, run `/uidesigner save`, and confirm the JSON matches `docs/data-format.md` (double counts once, 6 rows; names and slots correct). | unverified |
| MT-002 | 050 | Name a chest, restart the server, and confirm the name survives and appears in a subsequent export. | unverified |
| MT-003 | 040 | With a real (non-MockBukkit) server, confirm the `DoubleChest` holder path merges both halves across all four orientations, not just the geometry fallback. | unverified |
| MT-004 | 030 | Confirm a real FAWE selection scans the expected chests and that a large selection does not stall the server noticeably. | unverified |
| MT-005 | 080 | Cross-world stale selection (not automatable: needs a live FAWE session): select a region in world A, teleport to world B without re-selecting, run `/uidesigner save`, and confirm it reports no selection instead of scanning B at A's coordinates. | unverified |
| MT-006 | 080 | Dev-server end-to-end after the rooster-region swap (not automatable: needs a live FAWE session and Paper): `just run`, place/fill/name chests, select the region with FAWE, run `/uidesigner save`, and confirm the server boots with the library-backed selection and the JSON matches `docs/data-format.md`. | unverified |
| MT-007 | 100 | Dev-server check after the rooster-commands swap (not automatable: needs a live CommandAPI/Brigadier tree): confirm both commands behave as before — `/chest-edit <name>` names, bare `/chest-edit` sends usage (and is a silent no-op from console), `/uidesigner` and `uid` alias respond, suggestions offer the subcommands, and no permission checks exist. | unverified |
| MT-008 | 110 | Export on a non-POSIX filesystem (not automatable on the Linux dev box): run `/uidesigner save` with the output file on a filesystem without POSIX support (e.g. FAT/exFAT mount or WSL `/mnt/c`), and confirm the export succeeds (temp file keeps default permissions) instead of throwing `UnsupportedOperationException`. | unverified |
| MT-009 | 130, 150 | Live-server suggestion check after `clear` was removed (150): `just run`, then type `/chest-edit ` and `/chest-edit cl` and confirm `clear` is not suggested, and that `/uidesigner`/`uid` subcommand suggestions still appear. | unverified |
| MT-010 | 120 | Live-server scan parity after the rooster-region enumeration swap: `just run`, place a real double chest across a chunk border plus a chest in an adjacent unloaded chunk, select the region with FAWE, run `/uidesigner save`, and confirm each chest block is enumerated exactly once with its own cloned 27-slot contents (the double chest's halves stay separate until the grouper merges them). | unverified |
| MT-011 | 140 | Clipped double chest fails closed (not automatable: needs a real FAWE selection and a linked double chest): `just run`, place and fill a real double chest, select only one half (or a region clipping it), run `/uidesigner save`, and confirm no file is written and the error names both halves and the fix hint; then expand the selection to include both halves and confirm one 6-row chest exports. | unverified |
| MT-012 | 150 | Required unique names on a live FAWE selection (not automatable: the save path needs a real selection): `just run`, leave a chest unnamed, run `/uidesigner save`, and confirm no file is written and the error names that chest's position; name two chests `Shop` and `  shop  `, save, and confirm no file is written and the error names the name and both positions; then rename one and confirm every name in the written JSON. Also confirm `/chest-edit clear` names the chest `clear` and a blank argument shows usage without changing an existing name. | unverified |

## How to run

```sh
just run      # Paper 26.2 on localhost:25000 with FAWE + UiDesigner
```

Join, perform the check in-game, then update the row's status here and commit.
