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
| MT-004 | 030 | Confirm a real FAWE selection (including a non-cuboid one) scans the expected chests and that large selections do not stall the server noticeably. | unverified |

## How to run

```sh
just run      # Paper 26.2 on localhost:25000 with FAWE + UiDesigner
```

Join, perform the check in-game, then update the row's status here and commit.
