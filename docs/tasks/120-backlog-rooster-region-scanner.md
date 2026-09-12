---
name: Backlog: ChestScanner split into rooster-region
status: backlog
parent: Polish
depends-on: []
reviewers: [correctness, architecture, readability]
---

## Goal
The `ChestScanner` TODO ("Some of this should go to rooster-region") waits on
API in the sibling `rooster-region` repo. Split the scan loop there (grid
enumeration / filters) once that repo ships it, then replace the local
int-triple loop.

## Deferred from
Ticket 100 (deferral section): "ChestScanner split to `rooster-region` →
backlog ticket referencing `rooster-region`."

## Scope
- Add the needed enumeration/filter API to `rooster-region`, then consume it
  from `capture/ChestScanner.kt` here.
- Remove the TODO once shipped.

## Acceptance criteria
- TODO gone from `ChestScanner.kt`; behaviour unchanged.
