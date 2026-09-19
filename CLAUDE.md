# FreshTrack — Project Context for AI

> Read this before touching any code. **Current status and what is left is in `PROGRESS.md`** — start there. Product strategy is in `docs/strategy.md` (`summary.md` is history); sync design in `sync-design.md`.

---

## What This App Is

**FreshTrack** — Android food expiry tracker (Kotlin, Jetpack Compose). Live on Google Play. English-speaking markets first — US and UK, then other en- locales. India is deliberately not a launch market: the barcode and product-data coverage there is too thin to build capture on. Core promise: track groceries, get expiry alerts, reduce food waste. **Offline-first, privacy-preserving — no cloud data collection.**

---

## Architecture

| Layer | Key Files |
|---|---|
| UI / Screens | `presentation/screen/` |
| Navigation | `presentation/navigation/Navigation.kt` — single NavHost, guest mode routing |
| ViewModels | `presentation/viewmodel/` |
| Local DB | `data/local/GoodBeforeDatabase.kt` (Room, `goodbefore_database`, version 3), `data/local/entities/ItemEntity.kt` |
| Repositories | `data/repository/` |
| DI | `di/KoinModules.kt` (Koin) |
| Preferences | `data/preferences/` — every file is `EncryptedSharedPreferences` opened via `encryptedPreferences()`; `OnboardingPreferences` holds the guest flag |
| Notifications | `data/notification/` — WorkManager + NotificationHelper |
| Widget | `widget/` — Glance, `SizeMode.Exact`; `WidgetContent` is the pure, tested part. Header (icon, name, Add), urgency-dotted rows that deep-link to their item (`MainActivity.EXTRA_OPEN_ITEM` / `EXTRA_NAVIGATE_TO` → `LaunchTarget` in `Navigation.kt`), "Coming up" fills spare rows, app palette in light and dark, system corner radius, picker preview cut from a real render |
| Auth | Firebase Auth (Email/Password + Google Sign-In) via `presentation/viewmodel/AuthViewModel.kt` |
| Sync | `data/sync/` — `OutboxPusher`, `RemoteChangeApplier`, `SyncRun`, `SyncWorker`, `WireFormat`; `data/remote/firestore/FirestoreRemoteStore`. Inert until `isPremium` is set — see `sync-design.md` |
| Session | `data/session/UserSession.kt` — current uid and active kitchen |
| Billing (server) | `functions/` — Cloud Functions, the only writer of `isPremium` and `entitlements/{uid}`. `revenueCatWebhook` (built 19 Sep 2026, not yet deployed) is the one that stays: RevenueCat calls it, it reads the customer back from RevenueCat and rewrites the two documents. `verifyPurchase`, `onPlayNotification`, `reconcileEntitlements` are the Play-native ones still deployed; retired once a purchase is proven through RevenueCat. Tests: `npm run test:functions` |
| Billing (client) | `data/billing/` — `PremiumStore` (interface) over `RevenueCatStore` (RevenueCat SDK, which carries Play Billing), `EntitlementRemote` (reads `entitlements/{uid}`); `presentation/screen/premium/PremiumScreen.kt`. `PREMIUM_ON_SALE = false` hides all of it until a purchase is proven. The SDK's app user id is the Firebase uid, kept in step by an auth listener. The client never decides it is premium: after a purchase it polls the server's copy until the webhook has landed. Public SDK key is `REVENUECAT_GOOGLE_KEY` in `gradle.properties` → `BuildConfig` |

**DB version: 3** (`goodbefore_database`). The previous `freshtrack_database`
and its 1→7 migration chain were deleted during the GoodBefore model migration,
which was only safe because there is no installed base. That freedom is spent:
`Migration(1, 2)` (undo's reversal columns) and `Migration(2, 3)` (a data-only
reshape of the category set) are the patterns to follow — every
schema change needs a real `Migration(n, n+1)` plus a device test that proves
existing rows survive and still mean the same thing.
`fallbackToDestructiveMigration` must never be added.

---

## Key Decisions & Constraints

- **Offline-first.** Room is the source of truth. Firestore mirrors it; a sync
  failure must never block a read. See `sync-design.md`.
- **`kitchenId` is the access key, not the creator's uid.** Items belong to a
  kitchen so a shared household works. `createdBy` is attribution only. Never
  filter user-facing queries by it.
- **Expiry is a `LocalDate`, never an instant.** A date pinned to UTC midnight
  shifts by a day when rendered elsewhere. It travels with its provenance
  (`dateKind`, `dateSource`, confidence, confirmation) so the UI can separate
  fact from estimate, and `ExpiryDate.canBeReplacedBy` decides what may
  overwrite what.
- **A resolution can be undone, but nothing is erased.** The reversal is its
  own event pointing at what it reverses; impact and the waste-free streak net
  the two. Never implement undo by deleting the original event.
- **History is an append-only ledger.** Impact is read from `item_events`, not
  counted off row state, so editing or deleting an item cannot rewrite what the
  user was told they did. Every mutation writes row, event and outbox entry in
  one transaction via `ItemRepositoryImpl.record`.
- **Category and location are different things.** "Dairy" is what it is;
  "Fridge" is where it is.
- **Item ids are minted by the repository, never by a screen.** `ItemRepositoryImpl.add`
  assigns the id and ignores whatever the caller passed. The Add screen passes
  `""`, and an empty string is a valid primary key: from the GoodBefore
  migration until 19 Sep 2026 every manually added item overwrote the one
  before it. `ItemRepositoryImplTest` has the test that catches it.
- **Empty is a screen, not a placeholder.** Today, Kitchen and Progress each
  have a first-run/empty state that says what to do next and takes the person
  there (`WaysToAdd` for the three ways food gets in); a filter that matches
  nothing is a different screen from an empty kitchen; and "not loaded yet"
  is a third state (`null`) that renders nothing, so the empty screen never
  flashes over real content. While the kitchen is empty there is one Add
  path, in the body, and no FAB.
- **Deletes are soft.** Set `isDeleted`; a hard delete cannot propagate to another
  device. Only the queries under "Sync" in `ItemDao` may see tombstones.
- **Free tier is a feature gate, not a quantity cap.** No item limits — capping
  local storage would strand data for existing users and contradict the listing.
- **`isPremium` is server-written only.** Rules refuse any client write to it.
- **`COLLATE NOCASE`** on `name` and `category` fields — prevents milk/Milk duplicates.
- **Categories are food-only:** Fresh Produce, Dairy & Eggs, Meat & Fish, Ready Meals, Bakery, Beverages, Store Cupboard, Leftovers, Other. No Medicine/Cosmetics. Names are stored on every item, so changing one is a migration (`MIGRATION_2_3` is the pattern), and a category is never named after a location — "Pantry" is where, "Store Cupboard" is what.
- **`notificationEnabled` stays in `ItemEntity`** (DB field) even though the UI toggle was removed — do not drop this column.
- **No emoji in UI strings.** Use Material icons only.
- **Type, shape and motion are decided in the theme, not at the call site.**
  Plus Jakarta Sans via `Typography` in `Type.kt`; never set `fontWeight` or
  `fontSize` on a `Text` — pick a bigger role. Corners are
  `MaterialTheme.shapes.small/medium/large` (10/16/24), never a literal.
  Screen transitions live in `Motion.kt`; tappable surfaces use
  `Modifier.pressable`.
- **English only for now, but structured for more later.** User-facing text
  belongs in `res/values/strings.xml`, not hardcoded in Kotlin, so adding a
  locale is a translation job rather than a refactor. `values/` is **US
  English** (declared with `tools:locale="en"`): "expiration", "license",
  "organize", "toss". `values-en-rGB` (19 Sep 2026) overrides only what
  differs: "expiry", "licence", "organise", "bin", and the date-label coach
  in FSA terms. Keep the two in step: a new base string with a US-only word
  gets its en-GB line the same day. Do not add other languages yet.
- **Usage metrics are the eight in `UsageMetrics`, fired from the
  repository's write path and the two Premium screens.** Never from a
  screen for adds or resolutions: that is how a path gets missed or counted
  twice. No names, prices, dates or barcodes in any event. Collection is
  opt-in, asked once after the first item is used, never at launch.
- **The store review is asked after a real rescue only** (`ReviewPrompter`:
  ≥3 days installed, ≥3 resolved, ≥60 days since last ask), on top of Play's
  own quota. No pre-screen dialog.
- **Freeze is a Today action, not a resolution.** It moves the item to the
  freezer with a `FROZEN_UNTIL` date three months out, user-sourced and
  labelled a guide to quality. The printed date stays in the ledger. No
  per-food safety table: one window, editable.
- **The date-label coach appears only on an overdue best-before or sell-by
  item, never on a use-by.** US wording follows FDA/USDA (dates are about
  quality); UK wording follows the FSA (use-by is safety, so it gets no
  reassurance).
- **Never hardcode a date order.** `03/04` is March in the US and April in the
  UK. Use `DateOrdering.forLocale`.
- **Two tiers only: Guest (free) and Premium.** Login is NOT a middle tier — it is the gateway to premium. Guest is the full free offline app with no account; a user signs in only when they buy premium or tap a cloud feature, and their guest data claims into the new account. Do not build "free logged-in" perks — that tier does not exist by design.
- **Guest mode:** Users can skip login. Flag stored in `OnboardingPreferences.isGuestMode()`. Splash screen checks this.
- **`toggleNotification()` has been removed** from `AddEditProductViewModel` — do not re-add.
- **Impact stats are derived from Room, never counted separately.** `resolvedDate` is stamped in `ProductRepositoryImpl.markAsConsumed/markAsDiscarded` so every call site is covered. Do not reintroduce a counter store — a previous `UserRetentionPreferences` did this and silently missed dashboard-initiated resolutions.
- **Streaks are soft.** The waste-free count is derived as days-since-last-discard, so it restarts rather than "breaks." No `resetStreak()`, no punitive copy.

---

## Auth Flow

```
SplashScreen
  ├── Firebase user logged in → Dashboard
  ├── Guest mode (OnboardingPreferences.isGuestMode()) → Dashboard
  ├── Onboarding not done → OnboardingScreen → (Get Started or Skip) → Login
  │       Login offers Google, email, "Create account" and "Continue without an account" on one screen
  └── else → Dashboard (as guest)
```

**Deferred auth:** Login is never forced. After onboarding it is offered once,
with "Continue without an account" on the same screen; otherwise it is reached
on intent — the Settings "Using as Guest → Sign In" banner (a plain push, so
back returns to Settings), or tapping a cloud feature. On sign-in, guest data
claims into the account and Today becomes the root of the back stack.

**Google Sign-In needs every signing fingerprint in Firebase**: debug, upload,
and Play's app-signing key. Only debug was registered until 18 Sep 2026, so
release builds failed with DEVELOPER_ERROR — silently, until the auth screens
started reporting it.

Settings "Sign Out" clears guest flag AND Firebase session, returns to Login.

---

## Current Status

**See `PROGRESS.md`** — it is the single source of truth for what is done, what is
next, and what is knowingly incomplete. Do not reconstruct status from git log.

Short version: Backup & Sync built and proven on the emulators; rules and
the Play-native billing functions deployed; billing is moving to RevenueCat
(both platforms) and nothing is purchasable yet, so sync stays inert in
production. Two crash fixes (R8 on Add, encrypted prefs after reinstall) and
the sign-in rework are committed and waiting on a v1.1.1 upload. Product
strategy is in `docs/strategy.md`.

---

## Pricing & Monetization

Free tier: full local inventory, barcode, receipt and printed-date scanning
(all on-device via ML Kit), notifications, Today, history, progress, widget,
CSV export, guest mode. No caps.
Premium ("Plus" is the proposed name): Backup/Sync (built; product `premium`,
base plans `monthly`/`annual`, not yet created in Play Console). Next by
`docs/strategy.md`: household (one payer, up to six), Rescue plans, learned
shelf life.
USD base: $3.99/mo; `docs/strategy.md` proposes $19.99/yr with household
included (earlier figure $14.99/yr). Localised by the stores; no India-specific
target while it is not a launch market.

---

## Conventions

- Kotlin + Jetpack Compose. No XML layouts.
- All Room migrations must be additive — never drop columns used in DAOs.
- Koin for DI. Do not use Hilt.
- `EncryptedSharedPreferences` for all preference files, opened only through `encryptedPreferences()` in `data/preferences/`, and every such file listed in both backup-rules XMLs. A file the keystore cannot open is deleted and recreated; a missed exclusion crash-looped the app after a reinstall (18 Sep 2026).
- `ExportSchema = true` on `@Database`. Schema JSON auto-exported.
- Build tool: Gradle with Kotlin DSL (`build.gradle.kts`).
- Min SDK: check `app/build.gradle.kts`. Target: latest stable.

---

## Do Not Do

- Do not add cloud calls to features marked as free/offline.
- Do not re-add notification toggle to Add/Edit Product or Settings.
- Do not add Medicine/Cosmetics back to categories.
- Do not use emoji in any UI string.
- Do not drop `notificationEnabled` from `ItemEntity`.
- Do not use Hilt, TailwindCSS, or any web framework.
- Do not filter user-facing item queries by the creator's uid — use `kitchenId`.
- Do not hard-delete items; set `isDeleted`.
- Do not store expiry as an instant, and do not let an estimate overwrite a
  printed or user-confirmed date.
- Do not derive impact by counting rows; read the event ledger.
- Do not let a client write `isPremium` or `plan`.
- Do not add item-count caps to the free tier.
- Do not put a trailing space in a string resource to separate it from the
  next composable — the resource compiler strips it. Spacing goes in the layout
  or inside one sentence with placeholders.
- Do not swallow a sign-in failure; every auth failure reaches `AuthError`.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
