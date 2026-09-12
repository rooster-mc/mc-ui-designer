# Workflow

How work is planned, executed, reviewed and committed in this repository.

## Roles

Two orchestrator tiers, then workers. Nesting is two levels deep; opencode's
`subagent_depth` defaults to `1` (no subagents of subagents), so this repo sets
it to `2` in `.opencode/opencode.json`.

| Agent | Tier | Job | Reports only on |
|---|---|---|---|
| `orchestrator` | primary | Owns the queue, picks unblocked tickets, manages branches/worktrees, launches one ticket-orchestrator per ticket. Never implements or reviews. | — |
| `ticket-orchestrator` | subagent | Runs one ticket end to end by delegating to the workers. The only agent that may spawn them. | — |
| `implementor` | worker | Implements the ticket and its tests. The only agent that edits source. | — |
| `tester` | worker | Test quality (missing, excessive, brittle) and test-environment fidelity. | test files and harness limits only |
| `correctness` | worker | Bugs, wrong behaviour, spec mismatches, data/integration contracts. | logic, state, data, integration only |
| `architecture` | worker | Fit, extendability, seams, and doc staleness. | package structure and docs only |
| `readability` | worker | Clarity, file hygiene, formatting, followability. | source structure/naming/format only |
| `ux` | worker | The player-facing loop and feedback. | loop, feedback truthfulness, discoverability only |

Scopes are **exclusive**: a reviewer reports only within its column. If a
finding belongs to another role, leave it for that role — do not pad its own
report with out-of-scope trivia.

Subagents cannot spawn subagents unless their agent config declares a `task`
permission; `ticket-orchestrator` does, scoped to the workers. Every reviewer
owns exactly one report file, bound to its role (see
[Review reports](#review-reports)).

## Meta-orchestration

Run by `orchestrator` (primary):

- Pick unblocked tickets: `status: todo` whose `depends-on` are all `done`.
- Launch one `ticket-orchestrator` subagent per ticket. Run several in parallel
  when they are file-disjoint; serialize them or isolate each in its own
  worktree (`../mc-ui-designer--<id>` on branch `ticket/<id>-<slug>`) otherwise.
- Merge finished ticket branches back in dependency order and update the queue.

## Per-ticket pipeline

Run by `ticket-orchestrator`:

1. Read the ticket and its `reviewers` list.
2. `implementor` implements it on the ticket's branch/worktree.
3. Spawn every reviewer in the ticket's `reviewers` list **concurrently** with
   the ticket and round number. They run in parallel and write only their own
   report; they do not see same-round peers (their scopes are exclusive, so
   overlap is not expected).
4. Hand **all** reports (not just the last) to `implementor`. Every finding must
   be fixed, or explicitly deferred to a named ticket/gate with a reason.
5. **Commit** the resulting state.
6. Round 2: **resume** the same reviewer and implementor sessions via `task_id`
   and repeat steps 3–5 — again with the reviewers in parallel — this time
   seeding each reviewer with the **round-1 reports**, so later fixes cannot
   invalidate earlier reviews and nothing fixed there is re-reported.
7. Record any acceptance criterion that cannot be automated in
   `docs/manual-test.md` (see [Manual gate](#manual-gate)).
8. Mark the ticket `done` and commit.

Two rounds total per ticket. Omit stages that do not apply.

## Reviewer handoff (statefulness)

Same-round reviewers run in **parallel**, so they cannot read each other's
reports. That is fine: scopes are exclusive, so they should not collide.

- Round 1 cannot seed prior reports; each reviewer works only from the ticket
  and the diff.
- Round 2 seeds each reviewer with the full set of **round-1 reports** and the
  implementor's fixes. A reviewer must **not** re-report a finding already
  addressed or accepted there. It may **concur** (state agreement, add nothing)
  or **dissent** (explain why the prior finding is wrong), and may add findings
  only within its own scope.
- Findings carry **no severity labels**. If a reviewer flags it, it is treated as
  work: it gets fixed, or deferred with a named target and a reason. Reviewers
  therefore report only what they would actually stand behind.

## Manual gate

Some acceptance criteria cannot be automated (a real FAWE selection, an in-game
click loop). Those go in `docs/manual-test.md`, each with a status and, when
unverified, a reason.

- A ticket may be `done` while it has an unverified manual entry, but the entry
  must exist and name the ticket, so the gap is tracked rather than lost.
- `tester` flags paths the test harness cannot exercise (e.g. MockBukkit cannot
  form a real `DoubleChest`); those become manual-test entries.
- The `orchestrator` surfaces open manual entries when reporting a phase done.

## Verification ownership

- The **implementor** is the only agent that runs the build, the test suite, and
  the formatter/lint. It must leave the tree green before a ticket goes to
  review, and report the exact commands and results.
- Reviewers do **not** run long or slow tools (Gradle, `just test`, the dev
  server, ktlint, ...). They assume a green tree: if tests were not green, the
  ticket would not have reached review. Reviewing is reading and reasoning, not
  re-executing.
- If a reviewer suspects a failure, it states the suspicion and the scenario in
  its report; the ticket-orchestrator sends it to the implementor to reproduce.
  This keeps slow verification in one place instead of repeating it per stage.
- Quick read-only inspection (reading files, `git diff`, `fcp query`) is fine.

## Commits

- Conventional commits: `type(domain): description`, imperative, lowercase,
  ≤72 chars, no trailing period.
- Types: `feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `perf`, `revert`.
- One commit per unit of completed work; stage precisely, never `git add -A`.
- No authorship or tool trailers.
- Never push unless asked.

## Ticket format

Tickets live in `docs/tasks/` as `NNN-slug.md` with YAML frontmatter:

```markdown
---
name: Human readable title
status: todo | in-progress | review | done | blocked | backlog
parent: MVP
depends-on: [000]
reviewers: [tester, correctness, architecture, readability, ux]
---

Body: goal, scope, acceptance criteria, out of scope, notes.
```

- `depends-on` lists ticket ids that must be `done` first.
- `reviewers` lists which pipeline stages apply, in pipeline order.
- Status is owned by the ticket-orchestrator; update it in place.

## Review reports

Each reviewer writes to exactly one file, bound to its role:

```
docs/reviews/<ticket-id>/<role>.md
```

- The reviewer creates the ticket directory if needed and appends a
  `## Round <n>` section; it never touches any other file.
- This is the only write a reviewer may perform. Source stays untouched; the
  ticket-orchestrator commits the report alongside the ticket's work.
- The ticket-orchestrator tells each reviewer the ticket id and round number.

Report body per round:

```markdown
# <Role> review — <ticket id> (<short title>)

## Round 1
### Verdict
One or two sentences: ship / ship with fixes / needs rework.

### Findings
#### 1. <title>
- Location: path:line
- Problem: what is wrong and why it matters
- Suggested fix: concrete direction

### Non-findings
Things deliberately checked and found fine, and any prior-round finding you
concur with (keeps the implementor from re-litigating them).
```

No severity labels: a finding is work. If you would not stand behind fixing it,
do not report it, or say why it should be deferred to a named target.

The reviewer also returns a one-paragraph summary in its reply so the
ticket-orchestrator can hand feedback to `implementor` without re-reading files.

## Definition of done for a ticket

- Acceptance criteria met.
- Build, tests and lint pass.
- Both review rounds completed; every finding fixed or explicitly deferred to a
  named ticket/gate with a reason.
- Every non-automatable acceptance criterion recorded in `docs/manual-test.md`.
- Ticket status set to `done` and committed.
