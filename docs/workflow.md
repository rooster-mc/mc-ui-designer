# Workflow

How work is planned, executed, reviewed and committed in this repository.

## Roles

The primary agent is an **orchestrator**. It does not implement. It plans,
writes tickets, delegates to subagents, and administers the git repository.

Subagents (defined in `.opencode/agent/`):

| Agent | Job |
|---|---|
| `implementor` | Implements the ticket and its tests. The only agent that edits source. |
| `tester` | Reviews test coverage: missing, excessive, brittle. Can add/remove tests. |
| `correctness` | Hunts technical bugs, wrong behaviour, spec mismatches. |
| `architecture` | Checks fit with existing structure, extendability, over-generalisation. |
| `readability` | Checks clarity, file hygiene, formatting, followability. |
| `ux` | Checks the player-facing loop: actions, feedback, presentation. |

Omit a reviewer when it is genuinely irrelevant (e.g. `ux` for pure backend).
Every reviewer writes a report; the orchestrator hands reports back to
`implementor`.

## Per-ticket pipeline

1. Orchestrator picks the next unblocked ticket.
2. `implementor` implements it on a branch/worktree.
3. Run the relevant reviewers in order: `tester`, `correctness`,
   `architecture`, `readability`, `ux`.
4. Hand all reports to `implementor` for fixes.
5. **Commit** the resulting state.
6. Repeat steps 3–5 for a **second round**, so fixes from later reviewers do
   not invalidate earlier ones.
7. Mark the ticket `done`.

Two rounds total per ticket. Omit stages that do not apply, but keep the order
of those that remain.

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
- Status is owned by the orchestrator; update it in place.

## Reviewer report format

Each reviewer returns a single markdown report:

```markdown
# <Stage> review — <ticket id> (<short title>)

## Verdict
One or two sentences: ship / ship with fixes / needs rework.

## Issues
### 1. <title> (severity: low|medium|high)
- Location: path:line
- Problem: what is wrong and why it matters
- Suggested fix: concrete direction

## Non-issues
Things deliberately checked and found fine (keeps the implementor from
re-litigating them).
```

Reports are committed under `docs/reviews/<ticket-id>-<round>-<stage>.md`.

## Definition of done for a ticket

- Acceptance criteria met.
- Build, tests and lint pass.
- Both review rounds completed and feedback resolved or explicitly deferred.
- Ticket status set to `done` and committed.
