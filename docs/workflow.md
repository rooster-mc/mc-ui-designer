# Workflow

How work is planned, executed, reviewed and committed in this repository.

## Roles

The primary agent is an **orchestrator**. It does not implement. It plans,
writes tickets, delegates to subagents, and administers the git repository.

Subagents (defined in `.opencode/agent/`):

| Agent | Job |
|---|---|
| `implementor` | Implements the ticket and its tests. The only agent that edits source. |
| `tester` | Reviews test coverage: missing, excessive, brittle. |
| `correctness` | Hunts technical bugs, wrong behaviour, spec mismatches. |
| `architecture` | Checks fit with existing structure, extendability, over-generalisation. |
| `readability` | Checks clarity, file hygiene, formatting, followability. |
| `ux` | Checks the player-facing loop: actions, feedback, presentation. |

Omit a reviewer when it is genuinely irrelevant (e.g. `ux` for pure backend).
Every reviewer owns exactly one report file, bound to its role (see
[Review reports](#review-reports)); the orchestrator hands those reports back
to `implementor`.

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

## Verification ownership

- The **implementor** is the only agent that runs the build, the test suite, and
  the formatter/lint. It must leave the tree green before a ticket goes to
  review, and report the exact commands and results.
- Reviewers do **not** run long or slow tools (Gradle, `just test`, the dev
  server, ktlint, ...). They assume a green tree: if tests were not green, the
  ticket would not have reached review. Reviewing is reading and reasoning, not
  re-executing.
- If a reviewer suspects a failure, it states the suspicion and the scenario in
  its report; the orchestrator sends it to the implementor to reproduce. This
  keeps slow verification in one place instead of repeating it per stage.
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
- Status is owned by the orchestrator; update it in place.

## Review reports

Each reviewer writes to exactly one file, bound to its role:

```
docs/reviews/<ticket-id>/<role>.md
```

- The reviewer creates the ticket directory if needed and appends a
  `## Round <n>` section; it never touches any other file.
- This is the only write a reviewer may perform. Source stays untouched; the
  orchestrator commits the report alongside the ticket's work.
- The orchestrator tells each reviewer the ticket id and round number.

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
orchestrator can hand feedback to `implementor` without re-reading files.

## Definition of done for a ticket

- Acceptance criteria met.
- Build, tests and lint pass.
- Both review rounds completed and feedback resolved or explicitly deferred.
- Ticket status set to `done` and committed.
