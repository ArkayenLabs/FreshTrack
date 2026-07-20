# FreshTrack — Phase 2 Readiness Audit

Findings from a full codebase pass, July 2026. These are the things to fix
**before** building Firestore sync, not after. Ordered by risk.

P0 is complete and verified on device. P1 design decisions are settled in
`sync-design.md` with rules in `firestore.rules`; no sync client code exists yet.

---

## P0 — Ship-blockers (wrong or unsafe in the app that is live today)

- [x] **Sign-out does not clear local data** — Resolved differently to the original
  plan. Every query now filters by `userId`, so another account cannot see the
  data without it being deleted. A destructive wipe was deferred until sync
  exists and there is a cloud copy to restore from.
  ~~Original finding:~~ — `FirebaseAuthRepositoryImpl.signOut()` only
  calls `auth.signOut()`. Room is untouched. User A signs out, User B signs in on the
  same phone, and B sees A's entire inventory. This is a privacy bug today and becomes a
  data-corruption bug the moment sync exists (B would upload A's items to B's account).
  Decide the rule: wipe on sign-out, or keep and claim on sign-in. Cannot stay undefined.

- [x] **No ownership field in the data model** — Done. `userId`, `updatedAt`,
  `isDeleted` and `deletedAt` added in `Migration(5, 6)`, verified on device.
  ~~Original finding:~~ — `ProductEntity` has no `userId`. Every
  row is anonymous. Firestore cannot be added until rows know who they belong to. This is
  the single biggest blocker and it requires `Migration(5, 6)`.

- [x] **Auto Backup is on with empty rules** — Done. Encrypted prefs excluded
  from cloud backup and device transfer; Room DB still included.
  ~~Original finding:~~ — `allowBackup="true"`, and both
  `backup_rules.xml` and `data_extraction_rules.xml` are still the unmodified Studio
  templates. Android is currently uploading the Room DB and preferences to the user's
  Google Drive. Two consequences:
  - It contradicts the "100% offline, no data collection" store listing.
  - `EncryptedSharedPreferences` restored onto a different device **cannot be decrypted**
    (the master key lives in the device keystore and is not backed up), which is a known
    crash-on-restore class. Exclude the encrypted prefs at minimum.

- [x] **HTTP logging at BODY level in release** — Done, gated on BuildConfig.DEBUG.
  ~~Original finding:~~ — `KoinModules.kt:62` sets
  `HttpLoggingInterceptor.Level.BODY` unconditionally. Full request and response bodies
  are written to logcat in production builds. Gate on `BuildConfig.DEBUG`.

- [x] **Raw Firebase error strings shown to users** — Done, mapped to safe
  messages; password reset no longer confirms whether an account exists.
  ~~Original finding:~~ — `AuthViewModel` assigns
  `error = e.message` in all five auth paths. These are technical English strings, and
  some ("There is no user record...") let an attacker probe which emails are registered.
  Map to friendly messages; do not distinguish "wrong password" from "no such account".

---

## P1 — Must be settled before writing any Firestore code

- [x] **Decide the sync data model** — Room-first, Firestore as mirror. See
  `sync-design.md`.
  ~~Original:~~ — Room stays source of truth with Firestore as
  mirror, or Firestore becomes source of truth. This decision drives everything else and
  is expensive to reverse. Recommend Room-first with background push, to protect the
  offline-first promise.

- [x] **Write Firestore security rules before the client** — Written in
  `firestore.rules`. Not yet deployed and not yet unit-tested.
  ~~Original:~~ — Rules default to open. A
  live app with an open collection is a data breach. Rules must enforce
  `request.auth.uid == resource.data.userId` for every read and write.

- [x] **Define the guest-to-account migration** — Done in P0; guest rows are
  claimed at app start and after login/registration.
  ~~Original:~~ — Someone uses the app as a guest for
  weeks, accumulates items, then signs in. Those rows have no owner. Claim them for the
  new account, or discard them? Silent data loss here would be the worst possible first
  impression for a paying user.

- [x] **Plan conflict resolution** — Last-write-wins on `updatedAt`, which is
  already in the schema. See `sync-design.md`.
  ~~Original:~~ — Same item edited on phone and tablet while offline.
  Needs a rule (last-write-wins with `updatedAt`, or per-field merge) plus an
  `updatedAt` column. Add it in the same migration as `userId`.

- [x] **Plan soft deletes** — Done in P0; `isDeleted`/`deletedAt` exist and all
  queries filter tombstones.
  ~~Original:~~ — A hard `DELETE` cannot propagate to other devices. Sync
  needs `isDeleted` + `deletedAt` (tombstones), also in the same migration.

- [ ] **Free-tier limits must exist before billing** — There is no concept of a plan in
  the codebase. Decide what free caps are (item count, history retention) and enforce
  server-side in rules, not only in the UI.

---

## P2 — Quality and correctness

- [ ] **Duplicate items are still possible** — `COLLATE NOCASE` makes comparison
  case-insensitive but does **not** prevent inserting "Milk" twice. There is no unique
  index on `products` and no pre-insert check in the add flow. Needs either a unique
  index or a "this item already exists, add to it?" prompt.

- [x] **Emoji still in the app** — Done, all six removed from NotificationHelper.
  ~~Original:~~ — Six in `NotificationHelper.kt` (lines 94, 95, 158,
  160, 161, 168), hidden as escaped unicode (`🚨` etc.) so they survived the
  earlier emoji sweep. Notifications currently render a siren, chart, package, and
  warning emoji. Replace with Material icons or plain text.

- [ ] **Notifications are generic** — Every alert is "N Products Expiring Soon" plus a
  name list. No urgency tiering, no variation, no action beyond opening the app. Options:
  differentiate today vs. this week, name the specific item when there is only one, add a
  "Mark as used" action directly on the notification, and vary the copy so it does not
  read as the same message every day.

- [ ] **No CSV import** — `CsvExporter` only exports, via a share Intent. Users can get
  data out but never back in. Worth pairing with sync, since import is also the manual
  recovery path when sync fails.

- [ ] **Release build never verified** — `isMinifyEnabled = true`, and
  `proguard-rules.pro` has no `-keepattributes Signature` for Retrofit generics. Retrofit
  ships its own consumer rules so this may be fine, but nobody has confirmed the barcode
  lookup actually works in a minified release build. Test it on a real release APK.

- [x] **`Migration(4, 5)` is untested at runtime** — Done. `MigrationTest` covers
  4→5, 5→6 and the full 1→6 chain; all three pass on device.
  ~~Original:~~ — Carried over from the last session.
  Needs a `MigrationTestHelper` test. Should be done alongside the `userId` migration.

---

## P3 — Positioning and compliance

- [ ] **Analytics contradicts the privacy claim** — The listing says "no data
  collection" while Firebase Analytics and Crashlytics are active. Reconcile the Play
  Data Safety declaration with reality, or make analytics opt-in.

- [ ] **No email verification** — Any address can register, including ones the user does
  not own. Matters more once an account holds paid entitlements.

- [ ] **Store listing statistic** — Still the US figure. Manual Play Console task.

- [ ] **Deferred auth is actually skippable auth** — Users still see a Login screen with
  a Skip. True deferred auth shows no login until a cloud feature is touched. The old
  checklist marks this both fixed and pending; it is pending.

---

## Suggested order

1. P0 items — they are wrong in production right now
2. Design decisions in P1 — on paper, before code
3. One migration adding `userId`, `updatedAt`, `isDeleted`, `deletedAt` together
4. Firestore rules, then the sync client
5. P2 quality work
