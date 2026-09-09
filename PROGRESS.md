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

**Date-label capture works end to end.** Point the camera at a printed date,
confirm what it read, and the item carries a date that says it came off a
packet rather than out of someone's head. The deterministic parser came first
and stands alone: it is the fallback the AI contract requires and the check on
a model when one arrives. An ambiguous numeric date returns both readings and
neither can be saved without being shown to someone. A packing date is dropped
rather than ranked low. Provenance survives to the saved row, so a confirmed
scan can afterwards only be changed by the user.

Not yet: photographs of real packets. Recognition is exercised against rendered
labels, which proves the pipeline is joined, not that it copes with glare,
curved film or dot-matrix printing. That fixture set is the next honest step
before any accuracy claim.

**The app has a shell.** Today, Kitchen and Progress are three tabs in a bottom
bar rather than one screen with links out of it, and the Dashboard is gone
rather than merely unreachable — screen, ViewModel, Koin registration and its
two tests all removed, net −358 lines. `ItemListViewModel` outlived the file it
was named after and now has its own. Verified on a device, not only compiled:
all three tabs switch, Kitchen keeps its tab highlighted despite its route
carrying an optional argument, and system back from a tab returns to Today
instead of leaving the app.

**Today Rescue is built.** The app now answers "what should I do" rather than
only "what do I have": a deterministic ranked list of what is worth using,
each row stating its reason. Ranking lives in the domain, separate from
anything that might later generate suggestions — a model may explain the list
but must never be able to put an item on it.

Last verified state (all four gates re-run 9 Sep 2026, against a clean tree):
`./gradlew testDebugUnitTest lintDebug assembleRelease` succeeds — **142** JVM
unit tests pass, lint reports 0 errors and 120 warnings, and a signed minified
APK is produced. **48** Firestore rules tests pass on the emulator, against the
current `kitchens/items/events` rules. The unit tests were re-run with
`--rerun-tasks`; an up-to-date task is not a pass.

The three figures above previously read 86, 38 and "the old
`/pantries/{id}/products` shape". All three were stale — the undo commit added
tests and the rules were rewritten — which is the recurring lesson that this
paragraph is the easiest thing in the file to leave behind.

**Verified on a Pixel_35 API 35 emulator**, not just compiled:

- `connectedDebugAndroidTest` — **14** tests, 0 failures. These had never
  passed before the migration; see that commit for the three separate reasons.
- The database is created with all five tables, and the seed callback
  populates all seven categories and four locations. Enums store by name.
- Guest route works: onboarding Skip lands on Today with no account.
- Adding an item writes row, event and outbox entry together, with the event
  and the outbox entry sharing one operation id.
- A date picked as 11 Sep is stored as `2026-09-11` and reads back as
  "Sep 11, 2026 / 3 days". The UTC round trip through Material's picker does
  not shift the day.
- Using 1 of 3 leaves **one** row at quantity 2, still ACTIVE, and appends
  `QUANTITY_USED` with quantity 1 — no synthetic second row, which is the
  behaviour the ledger was introduced to fix.
- The rebrand renders: the app shows "GoodBefore" on device.
- Today Rescue works end to end — empty state, then after adding an item due
  today it reads "One food is worth using today" with a "Spinach · Due today
  · x2" row, and "Use one" takes it to quantity 1 with `QUANTITY_USED|1` in
  the ledger.

Still unverified on a device: the widget, notification delivery and its
"Mark as used" action, the CSV file picker, Google Sign-In, and the minified
release APK (only the debug build has been installed).

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

- [x] ~~**Run it on a device.**~~ Done — see the verified list above. The
      cutover executes: database created and seeded, DI graph resolves, and the
      write path behaves as designed.
- [ ] **Exercise the surfaces the emulator run did not reach**: widget
      placement and refresh, notification delivery and its action, the CSV
      picker, Google Sign-In, and the *release* APK rather than the debug one.
- [ ] **Rebuild sync on the outbox.** Push queued operations, pull by cursor,
      order by server revision rather than device clock. The queue and its
      idempotency keys exist; the transport does not.
- [ ] **Deploy the Firestore rules.** Written, 48 tests, verified meaningful by
      deliberately weakening three rules and confirming exactly three failed.
      Still **not deployed**, but no longer an unknown: the live ruleset was
      read back from the Rules API on 8 Sep 2026 and is the *old* FreshTrack
      `pantries/products` version, published 5 Aug 2026. It is deny-by-default
      throughout with no `allow ... if true` anywhere, so production is closed,
      not open — this is housekeeping rather than a live exposure. `.firebaserc`
      now names `freshtrack-3c379` so the deploy target is explicit instead of
      living implicitly in a gitignored `google-services.json`.

      Worth deciding rather than doing reflexively: the new rules cover
      kitchens/items/events only, while the target model in the upgrade pack
      also has locations, invites, entitlements and aiJobs. Deploying now means
      deploying again when sync and household land.
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
  APK is produced, and the heap that prevented it is fixed. Only the debug
  build has been installed, so nobody has confirmed the barcode lookup or
  Room's generated code survive R8.
- **Store listing wording still says "no data collection".** Analytics is now
  off-by-default and opt-in, but the listing copy and Data Safety form still
  need updating to match, and the "no data collection" phrase should become
  "offline-first; optional, opt-in analytics".
- **Duplicate prevention covers import only.** Adding the same item twice by
  hand is still possible. `findDuplicate` exists on the repository and the add
  flow does not call it.
- **Widget rendering unverified.** Provider registers and the refresh path runs,
  but it has not been placed on a home screen, on the emulator or otherwise.
- **Notification action unverified.** "Mark as used" is wired but has not been
  tapped on a device.
- **CSV file picker unverified.** Parsing and dedupe are tested; choosing a real
  file through the picker is not.
- **Store listing statistic** still uses the US figure. Play Console task.
- **Date capture has no fixture set.** Recognition is tested against text
  rendered onto a bitmap, not photographs of packets. No accuracy figure may be
  claimed, in the listing or anywhere else, until real fixtures exist.
- **Bare six- and eight-digit dates are not parsed**, because they cannot be
  told from batch codes, and neither is MM/YY without a century. Both would
  trade a visible failure for a confident wrong answer.
- **The release APK is now 77.1MB**, up from 34.5MB, because bundled ML Kit
  text recognition ships an 11.1MB native pipeline per ABI. That number is a
  universal APK carrying four ABIs and is *not* what Play distributes — a
  single-ABI install is about 31.6MB, so the real cost is roughly 12MB. Worth
  knowing that `assembleRelease` therefore measures an artifact nobody ships;
  `bundleRelease` is the one that matches distribution and is not in the gate.
- **Lint can fail spuriously** when `lintAnalyze*` runs in the same invocation
  as `assembleRelease`: lint reads KSP-generated release sources while that
  task is regenerating them, and dies with a FileNotFoundException that reads
  like a lint bug. Seen once on 9 Sep 2026 and clean on an immediate re-run.
- **Progress still shows a sparkle icon in its empty state.** The tab icon was
  moved off `Insights` because a sparkle motif as permanent navigation is
  exactly what the design system rules out; the illustration inside the screen
  is the same icon and was left for the design pass rather than widened into
  the navigation commit.
- **History is still reached from Settings**, not from Progress, though the two
  answer the same question. Deliberately not moved with the navigation change;
  it is a Progress-screen design decision, not a routing one.
- **The theme is dark-only** and sets `window.statusBarColor`, which is
  deprecated and ignored from API 35 where edge-to-edge is enforced — and the
  app targets 36. There is no light scheme at all. This is the substance of the
  design pass, not a cosmetic preference.
- **The notification permission is still requested at first composition**, now
  visibly: on a fresh install the system dialog appears over the onboarding
  carousel, before the user has been shown any reason to say yes.

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
- **Notification permission is requested at first composition**, from both
  MainActivity and DashboardScreen. It works, but it asks before the user has
  been shown any reason to say yes, and the target flow calls for asking only
  after they choose a reminder. Worth revisiting with the Today work rather
  than in isolation.
- ~~**Today has no undo.**~~ Built. Room is at **version 2**; the reversal is
  recorded as an event pointing at what it reverses, and the impact sums and
  the waste-free streak both net it out. `Migration(1, 2)` has a device test.
- ~~**Dashboard is now unreachable in normal use**~~ Removed with the
  navigation rework, as planned: route, screen, ViewModel, Koin registration
  and its two instrumentation tests.
