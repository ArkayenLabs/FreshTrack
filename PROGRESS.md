# FreshTrack — Progress Log

Running record of what has been done and what is left. Updated as work lands.
No code here — see the linked documents for detail.

**Related documents**
- `CLAUDE.md` — architecture, conventions, constraints
- `summary.md` — strategic research (background, not a task list)
- `checklist.md` — original product roadmap
- `checklist-phase2-readiness.md` — the audit that drove most of this work
- `sync-design.md` — Backup & Sync design decisions

---

## Where we are

Phase 1 retention work is done. The Phase 2 readiness audit is closed except for
known gaps listed below.

**The app has been migrated from the FreshTrack data model to the GoodBefore
one.** Expiry is now a calendar date carrying provenance, resolution is a single
state rather than two booleans, storage location is its own entity, and history
is an append-only event ledger with a sync outbox. The old Room database, its
migration chain and the old sync client were deleted rather than adapted; this
was only safe because there is no installed base to carry forward.

Backup & Sync is **not available**. The old client mirrored the old schema and
was removed with it. Local changes queue in the outbox and go nowhere until the
push/pull work lands. The Settings card says so rather than offering a button
that does nothing.

Last verified state (run 8 Sep 2026, not inherited from an earlier session):
`./gradlew testDebugUnitTest lintDebug assembleRelease` succeeds — 86 JVM unit
tests pass, lint reports 0 errors, a signed minified APK is produced. 38
Firestore rules tests pass on the emulator, though the rules still describe the
old `/pantries/{id}/products` shape and have not been updated for the new model.

**Nothing has been run on a device.** No emulator or handset was attached at any
point, so the Koin graph, Room database creation and seeding, the widget,
notification actions and the CSV file picker are compile- and unit-tested only.
Treat "it builds" as exactly that.

---

## Done

### Phase 1 — retention
- [x] **Impact Dashboard** — waste-free days, used vs. wasted, ratio bar. All
      figures derived from Room, not stored counters.
- [x] **Soft streak framing** — the streak is days-since-last-discard, so it
      restarts instead of breaking. No punitive reset exists.
- [x] Removed a parallel counter store that silently missed anything resolved
      from the Dashboard.

### Repository hygiene
- [x] Deleted unused duplicate theme package and Android Studio template tests
- [x] Ignored tool output and `google-services.json`
- [x] Split a long-uncommitted working tree into 14 dependency-ordered commits,
      each verified to compile independently

### P0 — things that were wrong in the shipped app
- [x] **HTTP bodies no longer logged in release builds**
- [x] **Encrypted preferences excluded from Auto Backup** — they cannot be
      decrypted after restore onto another device
- [x] **Auth errors no longer reveal which emails are registered**, including
      password reset
- [x] **Six emoji removed from notifications** — they were escaped unicode, which
      is why an earlier sweep missed them
- [x] **Products now have an owner** — every query is scoped, so two accounts on
      one device cannot see each other's data. Fixed by filtering, not deleting.

### P1 — decisions settled before writing sync code
- [x] **Room stays source of truth**, Firestore mirrors it
- [x] **Products live under a pantry**, not a user, so household sharing later
      moves no data
- [x] **Last-write-wins on `updatedAt`**
- [x] **Soft deletes** so removals can propagate
- [x] **Free tier is a feature gate, not a quantity cap** — no item limits, no
      counter infrastructure needed
- [x] **Firestore security rules written and tested** before any client code

### Backup & Sync
- [x] Pull, apply by last-write-wins, push, with separate pull/push watermarks
- [x] Tombstones sync both ways
- [x] Runs on a background worker; failure never blocks the UI
- [x] Sync engine has no Firebase dependency, so its logic is JVM-testable

### Two-tier model
- [x] **Deferred auth** — Login is no longer forced at startup. New users land on
      the Dashboard as guests; Login is reached only on intent (Settings sign-in
      banner or a cloud feature). Realises the Guest + Premium decision.

### Compliance & consent
- [x] **Account deletion** — in-app flow; remote-first ordering so data is never
      stranded; rules allow owner hard-delete.
- [x] **Analytics consent gate** — Analytics and Crashlytics OFF by default via
      manifest flags; turned on only after explicit consent. First-run prompt,
      plus a Settings toggle to change or withdraw anytime. This unblocks the
      privacy policy.

### Phase 1 follow-ups and quality
- [x] **Notifications reworked** — urgency tiering, the item named when there is
      only one, and a "Mark as used" action. Also fixed already-expired items
      never being notified at all.
- [x] **Home-screen widget** (Glance) — overdue and this week, reads Room
      directly so it works offline and for guests.
- [x] **CSV import with duplicate detection** — plus two exporter bugs found on
      the way in: unescaped category, and locale-dependent dates.
- [x] **Sync UI** — the Backup & Sync card reports real status instead of
      "coming soon".

### Testing
- [x] Migration tests for 4→5, 5→6, 6→7 and the full 1→7 chain, run on device
- [x] 36 Firestore rules tests on the emulator
- [x] 58 unit tests covering sync, document mapping, notification copy,
      widget selection, and CSV round-tripping
- [x] Every test suite verified by deliberately breaking the thing it covers and
      confirming the right tests failed

---

## Decisions

- **Two tiers only: Guest (free, offline) + Premium (cloud).** Login is not a
  tier; it is the gateway to premium. A free logged-in tier was rejected because
  it was functionally identical to guest. Concretely this means finishing
  deferred auth: stop showing Login as the default first screen, and prompt
  sign-in only when a guest taps a cloud feature. Guest data already claims into
  the account on sign-in. Decided Aug 2026.

---

## Next

- [ ] **Run it on a device.** Highest priority and cheapest. The schema cutover
      has never executed: nothing has confirmed the database is created, the
      default categories and locations are seeded, or the dependency graph
      resolves at startup.
- [ ] **Rebuild sync on the outbox.** Push queued operations, pull by cursor,
      order by server revision rather than device clock. The queue and its
      idempotency keys exist; the transport does not.
- [ ] **Update the Firestore rules** for kitchens/items/events. The current
      rules and their 38 tests still describe the old pantry/product shape, so
      they pass while guarding a schema the client no longer writes.
- [ ] **Play Billing.** Nothing grants an entitlement, so every paid path is
      inert by construction.
- [ ] **Legal & compliance** — see `COMPLIANCE.md`. The listing still claims
      "no data collection" while consented analytics exists, and the legal
      templates still carry placeholder dates.

---

## Known gaps

Not bugs to fix today, but things that are true and should not be forgotten.

- ~~**One-cycle echo.**~~ Resolved by design: the watermark is gone and an
  explicit outbox makes the pending set a fact rather than an inference. The
  transport that consumes it is not written yet.
- **No sync at all, by choice.** The polling client was deleted with the
  schema it mirrored. `sync-design.md` now describes neither what exists nor
  what is planned and needs rewriting against the outbox contract.
- **No end-to-end test.** The engine is tested with mocks and the rules against
  the emulator, but never the two together.
- **Cross-account claim edge case.** If a user signs out, updates, and a
  different account signs in as the first action after the update, that account
  claims the previous user's unclaimed rows. Narrow, but real.
- **Release build now builds, but is still unexercised.** A signed minified
  APK is produced, and the heap that prevented it is fixed. Nobody has
  installed it and confirmed the barcode lookup survives R8.
- **Store listing wording still says "no data collection".** Analytics is now
  off-by-default and opt-in, but the listing copy and Data Safety form still
  need updating to match, and the "no data collection" phrase should become
  "offline-first; optional, opt-in analytics".
- **Duplicate prevention covers import only.** Adding the same item twice by
  hand is still possible. `findDuplicate` exists on the repository and the add
  flow does not call it.
- **Widget rendering unverified.** Provider registers and the refresh path runs,
  but it has not been placed on a real home screen.
- **Notification action unverified.** "Mark as used" is wired but has not been
  tapped on a device.
- **CSV file picker unverified.** Parsing and dedupe are tested; choosing a real
  file through the picker is not.
- **Store listing statistic** still uses the US figure. Play Console task.

---

## Corrections made along the way

Kept because the reasoning matters more than the outcome.

- **`ExpiryBadge` was not dead code.** It was called from within its own file;
  a grep that excluded the declaration hid the call site. Deleted, caught by the
  compiler, restored.
- **Migration tests compiled but could not run.** Schemas were not packaged as
  test assets and the helper had no migrations registered. "Compiles" was never
  evidence they would pass.
- **Instrumentation tests never compiled** when first committed — only unit tests
  had been run.
- **`pantryId` should have been in the same migration as `userId`.** The privacy
  fix was designed before the remote model, so the key had to change afterwards.
  Cheap now, expensive after launch.
- **Tombstones were unreachable.** All 16 DAO queries filtered them out, so a
  deletion could never have been pushed. The columns existed; the plumbing did
  not.

---

## Introduced by the migration

Things that are true now and were not before. Recorded so they are not
rediscovered as surprises.

- **The Firestore rules guard a shape the client no longer writes.** They pass
  their tests, which makes this easy to miss.
- **`sync-design.md` is stale in both directions** — it describes an
  implementation that was deleted and a design that was superseded.
- **22 tests were deleted, not replaced.** They covered the old syncer,
  document mapping and repository. The behaviour is gone; the coverage those
  areas will need in the new model does not exist yet.
- **No Room migration tests exist.** The old chain was deleted with the old
  database. Version 1 has no predecessor, so there is nothing to test yet — but
  the first schema change from here needs both a migration and a test, and
  destructive fallback must never be added to the builder.
- **`notificationEnabled` survives with no UI.** Carried deliberately; the
  expiry query honours it and dropping it would change behaviour silently.
