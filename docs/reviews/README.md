# Review reports

One directory per ticket, one file per review role:

```
docs/reviews/<ticket-id>/
  tester.md
  correctness.md
  architecture.md
  readability.md
  ux.md
```

- Each reviewer owns exactly one file and appends a `## Round <n>` section per
  review round. It never edits any other file.
- The orchestrator tells the reviewer the ticket id and round number, then
  commits the reports with the ticket's work.
- Format and pipeline: `docs/workflow.md`.
- Roles are omitted when irrelevant, so a directory need not contain all five
  files.
