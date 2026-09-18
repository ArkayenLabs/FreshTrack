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

**State at the end of 18 Sep 2026 — `main` at the docs commit after `1c8e577`,
not pushed, tree clean.** 284 unit tests, lint 0 errors / 128 warnings, rules
61 (`npm run test:rules`), functions 26 (`npm run test:functions`). Connected:
`LoginScreenTest` 5/5 and `EncryptedPreferencesRecoveryTest` 5/5 on the
owner's phone (iQOO Z7 Pro, Android 15); the full connected suite has **not**
been re-run since 12 Sep. Rules and the three Play-native functions are
deployed to `freshtrack-3c379` (Blaze since 15 Sep). Graph current.

**Google Sign-In is unblocked, unverified.** All three SHA-1s are in Firebase
(debug `8c27…`, upload `920d…`, Play app-signing `5225…`) and the new
`app/google-services.json` (gitignored) carries all three Android OAuth
clients. A release APK was built at the end of the day for the owner to
sideload; **the first thing to do next session is ask whether Continue with
Google opened the account picker on it.** If it did, Google works for Play
users too. If not, the fingerprint in Firebase does not match the key that
signed the APK — check with `keytool -printcert -jarfile app-release.apk`.

**Decision, 18 Sep 2026: billing moves to RevenueCat on both platforms.** iOS
(`C:\Users\balaj\Downloads\GoodBefore-ios`, docs 01–07) already chose
RevenueCat over StoreKit 2, and one cross-store entitlement per Firebase uid
is what RevenueCat is for. Consequences, none started yet: the Play-native
client (`PlayBillingClient`, `PremiumClaim`) and functions (`verifyPurchase`,
`onPlayNotification`, `reconcileEntitlements`, topic `play-billing`) get
replaced by the RC SDK and one webhook function that writes the same two
documents; `entitlements/{uid}`, the rules, `PremiumScreen` and the docs
survive. The owner has a RevenueCat account. Play Console RTDN must point at
RC's topic, not ours. The iOS docs still describe the old `/pantries` schema
and need re-pointing at `/kitchens`.

**Shipping state.** v1.1.0 on Play crashes on Add item (R8) and crash-loops
after a reinstall (encrypted prefs) — both fixed here, both waiting on a
v1.1.1 upload. The bundle built earlier today predates the sign-in rework;
**rebuild before uploading**, after the Google fingerprints are in Firebase.
`PREMIUM_ON_SALE = false` hides Premium until a purchase is proven.

**Use the graph first.** `graphify query "<question>"`, `graphify path`,
`graphify explain` — do not read the source tree to find things.

**Done today:** push + release v1.1.0; receipt names title-cased; categories
reshaped to nine (`Migration(2, 3)`); `sync-design.md` rewritten and the
whole transport built and proven end to end (rules, remote store, push,
bootstrap, pull, quantity rebase, worker, Settings card); rules deployed;
design pass in two layers (Plus Jakarta Sans, one type scale, three radii,
motion, flat tinted cards, urgency pills, tiles). Details are in the
sections below and in the git log.

**Done 15 Sep 2026: the Play Billing server side is built, tested and
deployed.** `functions/` holds three 2nd-gen Cloud Functions in `asia-south1`
— `verifyPurchase`, `onPlayNotification`, `reconcileEntitlements` — the only
writer of `kitchens/{id}.isPremium`, with the receipt in `entitlements/{uid}`
(new rule, owner-readable, client-writable by nobody). Design is
`sync-design.md` §8. No Kotlin changed. Verified: 26 functions tests on the
Firestore emulator with Play faked, 7 new rules tests; the token-ownership
guard, the CANCELED-until-expiry rule and the entitlements read rule were each
broken once and failed exactly their own test; all three functions load in the
Functions emulator in `asia-south1`.

**Deployed 15 Sep 2026** after the project moved to Blaze (billing account
"Firebase Payment", shared with another app; usage is metered per project).
Verified live: all three functions `ACTIVE` in `asia-south1`
(`gcloud functions list --v2`); the live ruleset read back from the Rules API
(published 17:34:59Z) contains `match /entitlements`; `verifyPurchase`
answers 401 to an unauthenticated POST; the `play-billing` topic exists with
`google-play-developer-notifications@system.gserviceaccount.com` as
publisher; a test notification and a foreign-package notification published
by hand reached `onPlayNotification` within 3 s and were logged as
"received" and "ignored" respectively; the daily scheduler job is `ENABLED`.
Deploys must be run by the owner from a terminal — the auto-mode classifier
refuses `firebase deploy` and billing links. PowerShell needs the target
list quoted: `--only "functions,firestore:rules"`. First deploy also needed
`FUNCTIONS_DISCOVERY_TIMEOUT=120` (the 10 s default was too short for a
cold `@googleapis/androidpublisher` require under OneDrive) and the
Extensions API enabled.

Not verified: anything against the real Play API. That needs the Play
Console side, which is the owner's: create subscription `premium` with base
plans `monthly` and `annual` (the product id is an allow-list in
`functions/src/play.ts`); set the RTDN topic to
`projects/freshtrack-3c379/topics/play-billing` and press "Send test
notification" (it should appear in the function log); invite
`91023389919-compute@developer.gserviceaccount.com` under Users and
permissions with *View financial data* and *Manage orders and
subscriptions*; add a license tester.

**Done 15 Sep 2026, later: the Play Billing client.** `data/billing/`:
`PlayBilling` (interface) and `PlayBillingClient` (Billing Library 8.3.0 —
Play's floor for updates from 31 Aug 2026 is v8; 9.x exists and its API is
not verified here), `EntitlementRemote` (the `verifyPurchase` callable in
`asia-south1` plus a read of `entitlements/{uid}`), and `PremiumClaim`, which
is the only path from a purchase to the server: `claim` after the sheet,
`claimUnacknowledged` on every `MainActivity.onResume` (one local Play query;
network only for a purchase the app died before verifying), `restore` from the
button. When the server says premium, `SyncWorker.syncNow` fires. Nothing
acknowledges a purchase on the client — the function does, after verifying.
`PremiumScreen` (route `premium`): one paid feature described as what it is,
Play's own prices, yearly preselected, "Renews automatically until cancelled"
under the button. Guests are sent to Login. The Settings backup card now opens
it when signed out or `NOT_ENTITLED`, and backs up now otherwise.

Verified: 9 JVM tests on `PremiumClaim` with Play and the server faked;
removing the unacknowledged filter failed exactly the two tests on it. On
Pixel_35 (debug build): app starts with the resume hook in place, Settings
card reads "Tap to see plans." and opens Premium, the guest branch renders,
and Play's `BILLING_UNAVAILABLE` (no Play account, app not on a track) lands
as the snackbar and nothing else. **Not verified:** the signed-in branch on a
device, the purchase sheet, `verifyPurchase` from the app, restore — all need
the app on the internal testing track with a license tester, which needs the
Play Console steps above. The Billing Library logs a caught
`IllegalArgumentException: Service not registered` from its own
auto-reconnect when Play is absent; library noise, not ours.

Known and left: after Login the stack is `… Premium, Today` (Login pops itself
and pushes Today), so the person is not returned to Premium after signing in.
Same as the Settings → Login path today.

**Fixed 18 Sep 2026: crash loop on launch after a reinstall.** The owner's
phone (iQOO Z7 Pro, Android 15) crashed three times on 16 Sep in
`Application.onCreate` with `AEADBadTagException` from
`ConsentPreferences.<init>`: a backup restore had put back an
EncryptedSharedPreferences file whose keystore master key the uninstall had
deleted. The backup rules excluded `freshtrack_prefs.xml` for exactly this
reason and nothing else — three encrypted files added since
(`freshtrack_consent_prefs`, `freshtrack_sync_prefs`, `goodbefore_client`)
were never listed. Any user who reinstalled or changed phone would have hit
it. Fix: all four files excluded from backup, and one shared opener,
`encryptedPreferences()`, that deletes and recreates a file it cannot open
(`GeneralSecurityException` *and* `IOException` — a corrupt key set surfaces
as the latter, found by the test). Losing these files is by design
survivable: consent reverts to off, flags re-derive, cursors re-pull, the
outbox is in Room. Verified on the phone itself: 5 connected tests, one of
which deletes a test-only master key from the keystore and shows the raw
`create` throwing exactly what the crash did; removing the security catch
failed exactly the recovery test. The fixed build launches on the phone
after a `bmgr restore`. Not reproduced: the exact 16 Sep restore set — the
one `bmgr` offers is older and has none of our files.

**Fixed 18 Sep 2026: the release build crashed on Add item.** Reproduced on
the owner's phone from a fresh `assembleRelease` and retraced with its own
mapping: `KoinModules.kt:180`, `get<Retrofit>().create(OpenFoodFactsApi::class.java)`,
a `ClassCastException` with no message — R8's signature for a cast it has
proved impossible. The interface is only ever instantiated through a Proxy
R8 cannot see, and with Koin the instance passes through the registry as
`Any` rather than a typed field, so R8 full mode concluded no instance could
exist: it removed the interface's only method, dropped `OpenFoodFactsResponse`
entirely (the mapping had neither), and rewrote the cast into a bare throw.
Retrofit's own `-if` rule was in the merged config and did not fire because
the method it conditions on was already dead. Fix: `-keep interface
…OpenFoodFactsApi { *; }` and `-keep class …data.remote.dto.** { *; }` in
`proguard-rules.pro`. v1.1.0 on Play was built from the old rules and will
crash on Add for anyone who taps it — **a release with this fix is urgent.**

**Found on the way: the barcode lookup was never called.** `AddEditProductScreen`
wired the scanner result to `updateBarcode` (fills the field) instead of
`setBarcodeFromScanner` (fills the field and looks the product up). Nothing in
the app invoked the lookup at all, on any build. One-line fix, plus three
ViewModel tests on `setBarcodeFromScanner` that did not exist.

Verified on the phone (release APK, minified): Add opens; the owner scanned
`8901491100243` and the name filled in as "Rolled Oats" from OpenFoodFacts
through Retrofit and Gson; crash buffer empty. Mapping now shows
`getProductByBarcode` on the interface and both DTOs. 284 unit, lint 0/128.
Not automated: nothing runs instrumentation against the release variant. Until
it does, **the gate before any Play upload is manual: install the release
APK, open Add, scan a barcode.**

**v1.1.1 (versionCode 6) built 18 Sep 2026** — `app-release.aab`, the two
crash fixes, and Premium hidden behind `PREMIUM_ON_SALE = false` in
`data/billing/PlayBilling.kt` (the Settings card is back to its pre-billing
behaviour; the resume hook does not run; nothing touches Play Billing —
confirmed on the phone: no `BillingClient` line in the log). Flip the flag
when the internal-testing purchase has been proven. Upload is the owner's.

**Fixed 18 Sep 2026, later: the sign-in flow the owner walked through on
the phone.** Five things, all confirmed in code first: (1) "New here?Sign up"
and "Already have an account?Sign in" ran together because the space was a
*trailing* space in `strings.xml`, which the resource compiler strips — every
earlier "fix" in the string was undone at build time; the gap is now a
`Spacer` in the layout and the terms line is one string with two link spans.
(2) Google Sign-In: Firebase had only the debug keystore's SHA-1, so any
release build got DEVELOPER_ERROR, which the screen caught and ignored. The
error is now reported (`AuthError.GOOGLE_UNAVAILABLE`); the upload key and
Play's app-signing key still have to be added in Firebase — owner's step.
(3) Back from Sign in exited the app: the Settings banner reused `onSignOut`,
which clears the stack; it has its own `onSignIn` now, a plain push.
(4) Onboarding went straight into the app; it now lands on Sign in, which has
"Continue without an account" on the same screen. (5) Both auth screens fit
one screen without scrolling: no logo block, Google first, then email.
Verified on the phone: `LoginScreenTest` 5/5 including a new
`assertIsDisplayed` on every exit; walked onboarding → Sign in → Create
account → back → guest → Settings → Sign in → back to Settings; the Google
account picker opened on the debug build (fingerprint registered), which
isolates the release failure to the missing fingerprints. 284 unit, lint
0/128. **v1.1.1's bundle predates this — rebuild before uploading.**

**Next, in order:**

1. **Confirm Google Sign-In on the sideloaded release APK** (owner reports;
   see above). Then **v1.1.1 to production**: versionCode 6 is still unused,
   `bundleRelease`, manual gate (release APK: Add opens, scan a barcode,
   Settings → Sign in → back, Google picker opens), hand the `.aab` to the
   owner to upload. Watch Vitals for the Add crash disappearing. Owner also
   renames the store listing FreshTrack → GoodBefore.
2. **RevenueCat webhook function** (replaces the three Play-native ones).
   The owner has a RevenueCat account; nothing is configured there yet. Steps
   1–6 of the setup are in the 18 Sep conversation and summarised at item 5
   below. Build the function first (needs nothing from the owner), then the
   SDK swap, then the console work.
3. **RevenueCat Android SDK swap** (replaces `PlayBillingClient` and
   `PremiumClaim`; keeps `PremiumScreen`, `EntitlementRemote.current`,
   `PREMIUM_ON_SALE`). RC's SDK bundles its own Billing Library version.
4. **RevenueCat + Play Console setup** — owner: RC project `GoodBefore` +
   Google Play app `com.arkayenlabs.freshtrack`; a GCP service account
   (Pub/Sub Admin) whose JSON is uploaded to RC and whose email is invited in
   Play Console (View financial data, Manage orders and subscriptions);
   product `premium` with base plans `monthly`/`annual`; RTDN topic → RC's
   (not ours); entitlement `premium`, products imported, offering `default`
   with `$rc_monthly`/`$rc_annual`; webhook URL + Authorization secret;
   license tester. Public Google SDK key (`goog_…`) into the app.
5. **Internal testing track + license-tester purchase**, end to end.
6. **Retire** `verifyPurchase`, `onPlayNotification`, `reconcileEntitlements`,
   topic `play-billing`.
7. **Connected suite** re-run in full.
8. **iOS docs**: `/pantries` → `/kitchens`, `planAccess` → `isPremium`.
9. **Data Safety + privacy policy**: RevenueCat, opt-in analytics.
10. Design, third layer — receipt review, scanner, launcher icon, login logo.
11. Locations sync (`Migration(3, 4)`, `entityKind` on outbox).
12. Household (`docs/strategy.md` says it is the first thing after billing).
13. Dead code: `AdvanceNoticeDaysDialog`.
14. Unverified surfaces: widget, notification action, CSV picker, receipt
    camera, release-variant instrumentation.
15. Store listing wording and statistic.
16. Legal template dates; refund/subscription terms before billing ships.
17. Notification permission timing.
18. Login → return to where the person came from (Premium) after sign-in.
19. Duplicate prevention on manual add.
20. Cross-account claim edge case.
21. APK size: unbundled ML Kit text recognition would cut ~40 MB of the
    universal APK (Play already ships ~31 MB per device). Owner's call.

**Habits that hold:** read the Gradle task line, not the exit code; count
tests from the XML; weaken a rule or a guard and watch exactly its tests
fail; look at a design change on a screen before calling it done; seed the
emulator kitchen with `run-as` + sqlite3 rather than driving the form; the
emulator boots in ~60 s cold and must bind Firebase emulators to
`127.0.0.1`; `firebase emulators:exec` on Windows wants `.\gradlew.bat`.

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
- [x] ~~**Deploy the Firestore rules.**~~ Deployed 12 Sep 2026 after the
      end-to-end test passed against them. 54 emulator tests.
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
- [ ] **Play Billing.** Server side built, tested and deployed 15 Sep 2026
      (`functions/`); client built the same day (`data/billing/`,
      `PremiumScreen`). Not yet proven against real Play — needs the Play
      Console configuration and a testing-track build. See "Pick up here".
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
- **No instrumentation runs against the release variant.** The R8 crash on
  Add (fixed 18 Sep 2026) was invisible to every automated gate. Manual gate
  before a Play upload: release APK, open Add, scan a barcode.
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
- ~~**The theme is dark-only** and sets `window.statusBarColor`.~~ Stale
  before the design pass began: `Theme.kt` had light and dark schemes and no
  status-bar call. Struck 12 Sep 2026.
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
