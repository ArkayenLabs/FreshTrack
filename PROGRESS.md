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

Last verified state, in two parts because they were measured at different
points.

*Against the tree at `d324f15`, all three gates re-run with `--rerun-tasks`:*
`./gradlew testDebugUnitTest lintDebug assembleRelease` succeeds — **173** JVM
unit tests pass, lint reports 0 errors and 124 warnings plus 1 hint, and a
signed minified APK is produced (77.2 MB, universal, four ABIs). This settles
the open question from the previous session: `1cdc6ea`'s unverified final edits
did not break anything.

*Against the tree with the receipt review sheet in it (`f3390f6`):* **211**
unit tests pass, lint reports 0 errors and 124 warnings, and `assembleRelease`
produces the signed APK. All three re-run with the tasks forced to execute.
`connectedDebugAndroidTest` is **16 of 16** on Pixel_35 — after repairing four
tests that the previous session's i18n and date-ordering commits had broken and
never re-run (`1db6921`).

**48** Firestore rules tests pass on the emulator, against the current
`kitchens/items/events` rules — unchanged, and not re-run since.

The figures above previously read 142 and 120, and before that 86 and 38. They
have now been stale three times running, always for the same reason: the tests
grew and the paragraph did not. Two traps worth naming, because both were hit
this session:

- An up-to-date task is not a pass. `testDebugUnitTest` reported `UP-TO-DATE`
  on a tree it had never run against, and the results directory still held a
  passing report from earlier. Read the task line, not just the exit code.
- `--tests "*Receipt*"` matches more than the new tests. A count taken from a
  filtered run is a count of everything the filter caught.

**Verified on a Pixel_35 API 35 emulator**, not just compiled:

- `connectedDebugAndroidTest` — **16** tests, 0 failures (was 14; two
  recognition tests added with the receipt work). These had never passed before
  the migration; see that commit for the three separate reasons. Four of them
  then silently broke again under the i18n and date-ordering commits and were
  not re-run until 10 Sep — the pattern to watch for is a device suite that is
  green in this file and has not actually been executed since the file said so.
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
"Mark as used" action, the CSV file picker, Google Sign-In, the receipt
*camera* path (the picker path is verified, above), and the minified release
APK (only the debug build has been installed).

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

### Capture
- [x] **Receipt review sheet** — capture by camera or file, an editable list
      where a row nothing can date stays visibly unfinished, per-row duplicate
      resolution (fold in, keep separate, leave out), and a commit that writes
      the whole sheet in one transaction or none of it. Lines the parser could
      not read are shown rather than dropped. An accepted shelf-life guess is
      saved as an estimate, not as a date the user confirmed, so a printed date
      scanned later still outranks it.

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

## Pick up here

**State at the end of 12 Sep 2026 — `main` pushed, release `v1.1.0` published,
tree clean.** 213 unit tests, lint 0 errors / 124 warnings. The connected suite
was last run on 10 Sep (16/16) and nothing since touches a surface it exercises.

**Done this session:**

- Pushed the 22 held-back commits. `v1.1.0` is on GitHub at `a5de216` with the
  signed universal APK attached — the APK built on 10 Sep, after the last code
  commit, so it was not rebuilt.
- **Naming, decided and done** (`20822cf`): a receipt line printed entirely in
  capitals is title-cased on the review sheet; a line in mixed case is left as
  printed, and a token with a digit (`2L`, `500G`) is kept. The rule lives in
  `ReceiptDraft`, not the parser, so what the parser saw is still what the
  unreadable-lines list shows. Two JVM tests pin it.
- **Firestore rules: decided, not deployed.** Nothing writes to Firestore until
  the outbox has a transport, and the ruleset that ships with that transport
  will also need locations, invites and entitlements. Deploying now buys
  nothing and means deploying twice. Revisit when sync lands — see "Next".

**Categories, audited on request.** Seven is not too many; the set is one short
and one misnamed:

- **Meat & Fish is missing.** `ShelfLifeTable` already has six rules for it
  (poultry 2 days, mince 2, fish 2, bacon 7, sliced meat 5, red meat 4) — the
  shortest lives and the most expensive waste in the table — but there is no
  category for the receipt parser to guess or the kitchen to filter by, so
  `CHICKEN BREAST` is dated correctly and filed under **Other**
  (`ReceiptDraft.kt`, `category ?: "Other"`). This is the one gap with a real
  cost. Adding it is a seed change for new installs *and* a `Migration(2, 3)`
  that inserts the row for existing ones, with a device test.
- **"Pantry" is a category named after a place.** The project rule is that
  category and location are different things, and the location set also has
  `Pantry` (`loc-pantry`). A tin of tuna is category Pantry, location Fridge.
  Rename the category — "Store Cupboard" (UK) reads naturally, "Dry Goods" is
  the US-neutral choice — and the rename must also update the `BY_CATEGORY`
  key in `ShelfLifeTable`, `CATEGORY_WORDS` in `ReceiptParser`, and existing
  rows via the same migration.
- **Ready Meals is missing too.** The chilled aisle — ready meals, hummus and
  dips, sandwiches, deli salads, fresh pasta — carries a 2–7 day use-by and
  has no home: not Leftovers (home-cooked, unlabelled), so Other, and no
  shelf-life rule, so every such receipt row comes up unresolved. A
  "Ready Meals" category with a fridge-3 / freezer-90 fallback fixes both.
  Folding it into Leftovers saves a chip but nobody files a shop-bought
  lasagne there.
- **Not missing, on purpose:** Frozen is a location; eggs sit with Dairy as
  every supermarket shelves them ("Dairy & Eggs" as the label, if the
  migration is happening anyway); snacks, condiments, spices, tinned and
  nuts are all store cupboard, where the thing that matters is an *opened*
  date — a feature, not a category; plant-based milks already land in Dairy
  via the `MILK` keyword and keep like it.
- **Beverages is the weakest of the rest**: one shelf-life rule (juice) and a
  category fallback, but receipts and barcodes name drinks readily, so it
  earns its chip. Fresh Produce, Dairy, Bakery, Leftovers each drive rules and
  are the four most-wasted kinds of food. Other is required as the honest
  fallback and correctly has no rule.
- No usage data exists to check any of this against: no analytics event
  carries a category, and analytics is opt-in and unlaunched.

**Done, 12 Sep 2026.** Room is at **version 3**. `MIGRATION_2_3` renames
Dairy → Dairy & Eggs and Pantry → Store Cupboard on both the category row and
every item that named it, inserts Meat & Fish and Ready Meals, and rewrites
sort order so an old install shows the new order. Values are literals in the
migration, not read from `DefaultCategories`, so a later change to the
defaults cannot change what 2→3 does. `ShelfLifeTable` and `ReceiptParser`
key on `DefaultCategories.X.name` so a rename cannot drift past the compiler;
both gained chilled-aisle rules and keywords (a ready meal wins over its meat
by keyword length; `HAM`/`COD` were left out because three letters catches
champagne). The four UI icon maps carry the nine, and the two that still
named Food/Cosmetics/Medicines now name the real set.

Verified: 216 unit tests, lint 0 errors / 124 warnings, **19/19 connected on
Pixel_35** including three new migration tests — 2→3 re-points items and
removes the old names, 2→3 yields the nine in order, and 1→3 runs the whole
chain. Schema `3.json` differs from `2.json` only by version, as a data-only
migration should. The add screen was opened on the device: the nine render
as an exact 3×3 grid, both new icons resolve, and the two-word names wrap the
way "Fresh Produce" already did. Not verified on screen: the kitchen card
colour and the filter row with a Meat & Fish or Ready Meals item — adding one
needs a date, and the picker was not driven.

**`sync-design.md` rewritten, 12 Sep 2026.** It now describes the outbox
contract as built, the remote shape the transport will write, push and pull
by server cursor, conflict handling (row LWW, quantity events rebased,
tombstone wins), bootstrap instead of replay for a first backup, and a build
order. Two findings from the rewrite:

- ~~**The claim leaves `actorUid = "guest"`** on events and outbox rows~~,
  which the rules would refuse on push. Fixed the same day: both claim queries
  rewrite `actorUid`, and a JVM test signs in over guest data and asserts
  nothing still names the guest. Room compile-checked the SQL; not run on a
  device, as no device test exercises the claim.
- **Location writes bypass the outbox.** `LocationRepositoryImpl` never
  enqueues, so a renamed shelf could not sync. Out of scope for the first
  transport; needs an `entityKind` column (`Migration(3, 4)`).

**Rules updated for the transport shape, 12 Sep 2026.** `serverUpdatedAt ==
request.time` on items and events, event document id must equal its
`operationId`, `lastOperationId` required on items. 54 emulator tests (was
48); each new clause was weakened in turn and exactly its tests failed. The
existing item-write tests were also tightened: several negatives had been
passing for a second reason (a missing `expiryDate`), and now start from a
complete valid write and break one thing. Still **not deployed** — that is
step 8 of the build order, after the transport.

**`RemoteStore` built for the new shape, 12 Sep 2026.** `RemoteProductStore`
and `RemoteProductDataSource` (still addressing `/pantries/{id}/products`)
are gone; `RemoteStore` + `FirestoreRemoteStore` cover kitchens/items/events:
ensure kitchen, read `isPremium`, `push` (item + event in one batch, server
timestamp stamped by the store), `eventExists`, erase account. `WireFormat`
is the pure row→fields mapping, 9 JVM tests: ISO date string, enums by name,
`lastOperationId` set, **kitchen never a field** (it is the path, from the
claimed outbox row), guest attribution replaced by the actor, other members'
attribution kept, `revision`/`id` never sent. `AccountDeleter` rewired; its
tests unchanged and green.

One design correction from building it: the Android SDK's batch commit
returns no server timestamp, so `revision` cannot be stamped at ack. It is
stamped when the device's own write comes back through the pull listener,
which `sync-design.md` §5–§6 now say. Not verified: `FirestoreRemoteStore`
itself — Firebase types cannot run on the JVM and there is no end-to-end
test yet (§10).

**Push engine built, 12 Sep 2026** (`883a8ba`, bootstrap in the next commit).
`OutboxPusher.push(kitchenId)`: entitlement read once (skipped when nothing
is queued and the kitchen is already backed up); drain oldest-first; ack only
on success; a permission refusal is a retry if the event is already there and
an entitlement stop otherwise; transient stops the run; permanent is tried
once per run and stuck after five, skipped and surfaced, never dropped. First
backup uploads every row and the whole ledger in 500-batches with a resumable
count, then drops only what was queued before it began. 20 JVM tests; the
once-per-run guard and the resume count were each removed and exactly their
tests failed. `SyncPreferences` lost its dead watermark pair and now
implements `SyncState`.

Known edge, not fixed: `peek` returns the oldest 50; if fifty consecutive
entries are stuck, nothing behind them is attempted. Fifty stuck entries is a
broken state that is surfaced anyway.

**Pull engine built, 12 Sep 2026.** `RemoteChangeApplier.apply(kitchenId)`:
items then events, paged from separate cursors, one Room transaction per
page, cursor moved after commit. An own write is recognised by
`lastClientId` on the document — a new wire field, stateless, so the
"recently acknowledged ids" set the previous note wanted is not needed — and
only stamps `revision`; anything else is applied if strictly newer than the
local revision. Events append with IGNORE, so replays and own events insert
nothing. Cursors and `revision` are microseconds, because a millisecond cursor
would re-fetch a document half a millisecond past it on every run. 11 JVM
tests, plus round-trip tests for the reverse wire mapping; disabling own-write
recognition fails exactly the test where a local edit would be regressed.
Lint 126 warnings (was 124): two more `UseKtx` on `SyncPreferences` setters,
matching the four the file already had.

**Worker and Settings wired, 12 Sep 2026.** `SyncRun` is one run — ensure
the kitchen document, push, pull, record the outcome — tested on the JVM (6
tests) so `SyncWorker` only maps DEFERRED to `Result.retry()`. Scheduled
periodic every 6h and as a one-shot when the app goes to the background (the
natural moment, and no coupling to the write path), both network-constrained.
Koin registers pusher, applier, run and `SyncState`. The Settings card is
honest by state: signed out → "Sign in with Premium to back up"; NOT_ENTITLED
→ "Backup needs Premium"; never → "Not backed up yet. Tap to back up now";
else "Backed up N ago", plus waiting and stuck counts when non-zero. The
unreachable `describeLastSync` now has a caller and its strings live in
resources. Tap runs a one-shot.

Verified on Pixel_35, signed out: 19/19 connected; Settings opens (so the
graph resolves, including `SyncState` and the WorkManager flow); the card
reads as designed; backgrounding the app starts `SyncWorker` and it returns
SUCCESS in under 200 ms with nothing in logcat. **Not verified: any signed-in
path** — no account can be signed in on the emulator, so ensure-kitchen, push
and pull have not run against a real Firestore. That is the end-to-end test
of §10, which is the next step, and it needs the Firestore emulator plus a
way to point the app at it.

Seen in passing: the splash reads a hardcoded `v1.0.0`
(`SplashScreen.kt:83`) while `versionName` is 1.1.0. Should read
`BuildConfig.VERSION_NAME`. Not changed here.

**End-to-end proven, 12 Sep 2026.** `SyncEndToEndTest` (androidTest) runs
`SyncRun` for two in-memory devices against the Firestore and Auth emulators
with the real `firestore.rules`: both use one of three offline, reconcile,
and both shelves say 1 while both ledgers say 2 used, with nothing left
queued; a second test backs up a never-synced kitchen whole. **2/2 passing**,
1.5 s and 8.7 s. `npm run test:sync-e2e:win` (`test:sync-e2e` elsewhere). The
test skips itself when the emulators are down, so the plain connected run
stays green (21 tests, 2 skipped).

Two things had to be built to get there:

- **The quantity rebase from §6**, which the step-4 pusher did not have —
  it was plain last-write-wins, and this scenario is exactly the one that
  exposes it. `OutboxPusher.rebased`, 6 JVM tests; disabling it fails
  exactly the two that depend on it.
- **The merged local row takes the server's revision.** Traced by hand
  before the device saw it: without that, B's pull in the same run treated
  A's document as newer and wrote it over the merge. Recorded in §6.

Getting the test to actually run took three environment fixes worth
knowing: `firebase emulators:exec` on Windows needs `.\gradlew.bat` (a bare
`gradlew` is not found); the emulators must bind to `127.0.0.1` in
`firebase.json`, because `localhost` resolves to `::1` and the Android
emulator's `10.0.2.2` only reaches IPv4 loopback; and the app forbids
cleartext HTTP, so `app/src/debug/res/xml/network_security_config.xml`
permits it to `10.0.2.2` alone. Release builds keep the strict config.

The splash no longer shows a version string (`SplashScreen.kt`); it was
hardcoded `v1.0.0`.

**Next: step 8 — deploy the rules.** Every reason to wait has gone: the
transport that writes the shape exists and is proven against them. After
that, the Play Billing server side, which is what finally sets `isPremium`.

**The receipt review sheet is done and device-verified.** Details under "Where
we are". The only receipt surface not exercised is the camera path (emulator
camera is synthetic); the picker path was driven end to end and Room checked.

**Habits that cost time this session, so the next one does not repeat them:**
read the Gradle task line, not the exit code — `UP-TO-DATE` is not a pass; a
count from `--tests "*Filter*"` includes everything the filter catches; and the
connected suite drifts silently, so run it after any string or locale change.

**The audience changed.** English-speaking markets first, US and UK. India is
not a launch market — barcode and product-data coverage there is too thin.
English only for now, but structured so a language is a translation job: text
lives in `res/values/strings.xml`, and `DateOrdering.forLocale` decides whether
03/04 is March or April.

**The receipt review sheet is built, and has never run on a device.** The two
halves that had never met are joined: `ReceiptParser` produces candidate rows,
`ShelfLifeTable` dates them, and `ReceiptDraft` is the pure domain layer between
them that decides what a row means and whether it may be saved at all. Capture
is a CameraX still plus a file picker, both converging on one on-device ML Kit
read; the temporary photo is deleted after recognition and an image the user
chose is left where it is.

What is verified: 30 new JVM tests, and the two claims that matter were checked
by breaking them — removing the unresolved-row guard from `commit`, and
replacing the merge-quantity sum with "last wins", failed exactly the two tests
covering those and nothing else.

A real hazard was found and closed before it could be discovered on a device.
`ReceiptParser` needs an item and its price on one line, and every test of it
supplied text typed by hand in exactly that shape. A recogniser is under no
obligation to oblige: a receipt is two columns with a wide gap, and reading it
as two blocks — every name, then every price — is entirely reasonable, at which
point no row has a price and the sheet opens empty with every unit test still
green. `ReceiptLines.assemble` now rebuilds rows from where the text physically
sat, so the pipeline is correct whichever way ML Kit behaves; text that already
arrived as whole lines passes through untouched. That property is what makes it
a fix rather than a bet, and it is checked on the JVM rather than needing a
device.

**Verified on Pixel_35, 10 Sep 2026**, by hand as well as by test. A rendered
six-line receipt was pushed to the device and taken through the picker:

- Real ML Kit, through `ReceiptLines`, produced six items with their prices.
  The columns were joined correctly, which is the thing the JVM tests could only
  assert about geometry written by hand.
- The row nothing could date (`ZORBANI WAFERS`: no rule, no category guess) came
  up tinted and marked, the banner read "1 row still needs a date, or leaving
  out", and **Save was disabled** until it was left out.
- The masked card line appeared nowhere — not as an item and not as an
  unreadable line. The two header lines were listed as unreadable rather than
  dropped. `Order No`, `SUBTOTAL`, `VAT`, `TOTAL` were filtered silently.
- After commit, Room held five rows, all `ESTIMATED`/`RULE` at 0.4 with
  `dateConfirmedByUserAt` **null** — an accepted estimate stayed an estimate.
  `TOMATOES` was quantity 2 at 75p, `RECEIPT`-sourced. Five `ITEM_CREATED`
  events and five `CREATE` outbox entries, each event's `operationId` matching
  exactly one outbox row, client sequence 1–5 contiguous.
- The user's own image was left on the device; nothing of ours was left in the
  cache.
- `connectedDebugAndroidTest`: **16 of 16**, including both recognition tests.

Not verified: the camera path. The emulator's camera is synthetic, and a
rendered receipt is not a photograph. The fixture debt owed for date capture is
owed here too, and the same rule applies: no accuracy claim, anywhere, until
real fixtures exist.

**The device run exposed a gap, since closed.** The kitchen list showed
`Sep 17` for the milk with nothing to say it was a guess: `Item.hasEstimatedDate`
existed on the model and no screen outside Today read it. That predated the
receipt work, but `ReceiptDraft` is the only producer of estimated dates in the
app, so the obligation went from latent to live the moment the sheet merged.
The card now reads `Sep 17 · est.` and the details screen says what the guess
was based on, using the same test Today already used (`e81c198`). A guess reads
as one on every surface.

`ItemRepository.import` turned out not to be usable for the commit, which the
previous note assumed it would be. Two reasons, both of which would have been
silent bugs: it re-decides for itself what is a duplicate and skips it, which
throws away a batch the person explicitly chose to keep separate; and it commits
row by row, so an interrupted receipt leaves half a shop with no way to tell
which half. `commitReceipt` takes already-resolved rows and writes them in one
transaction instead. `import` is untouched and still serves CSV.

Loose ends found during the sweep, none of them urgent:

- `AdvanceNoticeDaysDialog` in `SettingsScreen` is unreachable — the
  notification settings dialog that was removed. (`describeLastSync` was also
  unreachable; it gained a caller with the sync wiring on 12 Sep 2026.)
- ~~`EnhancedCategoryChip` in `ProductListScreen` and the icon mapping in
  `ProductDetailsScreen` still name Food, Cosmetics and Medicines.~~ Fixed
  with the category reshape, 12 Sep 2026.
- `assembleRelease` builds a four-ABI universal APK that Play does not ship.
  `bundleRelease` is the artefact that matches distribution and is not gated.

## Next

- [x] ~~**Run it on a device.**~~ Done — see the verified list above. The
      cutover executes: database created and seeded, DI graph resolves, and the
      write path behaves as designed.
- [ ] **Exercise the surfaces the emulator run did not reach**: widget
      placement and refresh, notification delivery and its action, the CSV
      picker, Google Sign-In, the receipt camera, and the *release* APK rather
      than the debug one.
- [x] ~~**Label estimated dates outside the review sheet.**~~ Done — card,
      details and Today agree (`e81c198`). Verified on device.
- [x] ~~**A date a year out renders as `Sep 10`.**~~ Done in the same commit:
      the year is shown when it differs from today's.
- [ ] **Rebuild sync on the outbox.** Designed — `sync-design.md` §4–§6 and
      the build order in §11. The queue and its idempotency keys exist; the
      transport does not. First step is the claim fix (§7).
- [ ] **Deploy the Firestore rules.** Written, 54 tests, verified meaningful by
      deliberately weakening rules and confirming exactly their tests failed.
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

      **Decided 12 Sep 2026: not until the transport exists.** Nothing writes
      to Firestore before then, so a deploy now protects nothing and is done
      twice. Deploy alongside the sync transport, with the full model covered.
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
  schema it mirrored. `sync-design.md` was rewritten against the outbox
  contract on 12 Sep 2026 and is the plan for the transport.
- **The outbox payload snapshot still says `local`/`guest`** after a claim.
  Deliberate — the transport must take kitchen and actor from the outbox
  columns, never the payload. See `sync-design.md` §7.
- ~~**No end-to-end test.**~~ `SyncEndToEndTest`, 12 Sep 2026: engine and
  rules together on the emulators, two devices, passing.
- **Cross-account claim edge case.** If a user signs out, updates, and a
  different account signs in as the first action after the update, that account
  claims the previous user's unclaimed rows. Narrow, but real.
- **`ItemRepositoryImplTest.sampleItem` says the repository mints its own
  id. It does not** — `Item.toEntity()` keeps the id it is given and `add`
  returns it. Found when the e2e test gave three items the same placeholder
  id and they overwrote one another. The comment is misleading; the tests
  pass because they never add two items. Worth a one-line fix.
- **Release build now builds, but is still unexercised.** A signed minified
  APK is produced, and the heap that prevented it is fixed. Only the debug
  build has been installed, so nobody has confirmed the barcode lookup or
  Room's generated code survive R8.
- **Store listing wording still says "no data collection".** Analytics is now
  off-by-default and opt-in, but the listing copy and Data Safety form still
  need updating to match, and the "no data collection" phrase should become
  "offline-first; optional, opt-in analytics".
- **Duplicate prevention covers import and receipts only.** Adding the same item
  twice by hand is still possible. `findDuplicate` exists on the repository and
  the add flow does not call it.
- ~~**Receipt item names are saved exactly as printed.**~~ Decided 12 Sep 2026:
  a line printed entirely in capitals is title-cased on the sheet, mixed case
  is left alone, and digit tokens are kept (`20822cf`). The parser still
  reports text as printed.
- **A receipt line total is divided into a per-unit price and rounded.** 1.50 for
  two tomatoes is 75p each, stamped `PriceSource.RECEIPT`. For quantities that do
  not divide evenly the per-unit price will not multiply back to the printed
  total. Exact whenever the quantity is one, which is most lines.
- **A weight is not a count.** `1.5 KG POTATOES` becomes one item with "1.5 KG"
  in its notes. The weight survives, but not as anything the app can compute on.
- **Receipt capture asks for the camera permission on entering the screen**, the
  same pattern flagged below for notifications, though here the reason is at
  least visible on screen before the dialog appears.
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
- ~~**`sync-design.md` is stale in both directions.**~~ Rewritten 12 Sep 2026.
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
