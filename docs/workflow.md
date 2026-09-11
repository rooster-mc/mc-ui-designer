# Workflow

How work is planned, executed, reviewed and committed in this repository.

## Roles

Two orchestrator tiers, then workers. Nesting is two levels deep; opencode's
`subagent_depth` defaults to `1` (no subagents of subagents), so this repo sets
it to `2` in `.opencode/opencode.json`.

| Agent | Tier | Job |
|---|---|---|
| `orchestrator` | primary | Owns the queue, picks unblocked tickets, manages branches/worktrees, launches one ticket-orchestrator per ticket. Never implements or reviews. |
| `ticket-orchestrator` | subagent | Runs one ticket end to end by delegating to the workers. The only agent that may spawn them. |
| `implementor` | worker | Implements the ticket and its tests. The only agent that edits source. |
| `tester` | worker | Reviews test coverage: missing, excessive, brittle. |
| `correctness` | worker | Hunts technical bugs, wrong behaviour, spec mismatches. |
| `architecture` | worker | Checks fit with existing structure, extendability, over-generalisation. |
| `readability` | worker | Checks clarity, file hygiene, formatting, followability. |
| `ux` | worker | Checks the player-facing loop: actions, feedback, presentation. |

Subagents cannot spawn subagents unless their agent config declares a `task`
permission; `ticket-orchestrator` does, scoped to the workers. Every reviewer
owns exactly one report file, bound to its role (see
[Review reports](#review-reports)); the ticket-orchestrator hands those reports
back to `implementor`.

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
3. Run the relevant reviewers in order: `tester`, `correctness`,
   `architecture`, `readability`, `ux`.
4. Hand all reports to `implementor` for fixes.
5. **Commit** the resulting state.
6. Repeat steps 3–5 for a **second round**, so fixes from later reviewers do
   not invalidate earlier ones.
7. Mark the ticket `done` and commit.

Two rounds total per ticket. Omit stages that do not apply, but keep the order
of those that remain.

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

### Issues
#### 1. <title> (severity: low|medium|high)
- Location: path:line
- Problem: what is wrong and why it matters
- Suggested fix: concrete direction

### Non-issues
Things deliberately checked and found fine (keeps the implementor from
re-litigating them).
```

The reviewer also returns a one-paragraph summary in its reply so the
ticket-orchestrator can hand feedback to `implementor` without re-reading files.

## Definition of done for a ticket

- Acceptance criteria met.
- Build, tests and lint pass.
- Both review rounds completed and feedback resolved or explicitly deferred.
- Ticket status set to `done` and committed.
