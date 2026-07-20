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
known gaps listed below. Backup & Sync is **built but inert** — nothing grants
premium yet, so every cloud write is refused by design.

Last verified state: 58 unit tests, 4 migration tests on device, 36 Firestore
rules tests — all passing.

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

## Next

- [ ] **Play Billing Cloud Function** — nothing sets `isPremium`, so sync is
      currently inert for every user. This is the blocker.
- [ ] **Deploy the rules** — `firebase deploy --only firestore:rules`. They exist
      and pass tests but are not live.
- [ ] **Legal & compliance** — see `COMPLIANCE.md`. Account deletion is a live
      Play policy violation; the "no data collection" claim contradicts active
      analytics.

---

## Known gaps

Not bugs to fix today, but things that are true and should not be forgotten.

- **One-cycle echo.** A row pulled from the server gets pushed back once with
  identical content on the following sync. Costs a redundant write per pulled
  row. Proper fix is a per-row pending flag instead of a watermark.
- **Polling, not live sync.** `sync-design.md` says "listen"; what exists is
  every 6 hours plus on sign-in. No real-time listener.
- **No end-to-end test.** The engine is tested with mocks and the rules against
  the emulator, but never the two together.
- **Cross-account claim edge case.** If a user signs out, updates, and a
  different account signs in as the first action after the update, that account
  claims the previous user's unclaimed rows. Narrow, but real.
- **Release build never verified.** `isMinifyEnabled = true` and nobody has
  confirmed the barcode lookup works in a minified release APK.
- **Analytics contradicts the store listing.** It says "no data collection"
  while Analytics and Crashlytics are active.
- **Duplicate prevention covers import only.** Adding the same item twice by
  hand is still possible; only CSV import checks for duplicates.
- **Widget rendering unverified.** Provider registers and the refresh path runs,
  but it has not been placed on a real home screen.
- **Notification action unverified.** "Mark as used" is wired but has not been
  tapped on a device.
- **CSV file picker unverified.** Parsing and dedupe are tested; choosing a real
  file through the picker is not.
- **Deferred auth is really skippable auth.** Users still see a Login screen
  with a Skip, rather than no login until a cloud feature is touched.
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
