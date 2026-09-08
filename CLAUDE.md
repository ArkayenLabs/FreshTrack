# FreshTrack — Project Context for AI

> Read this before touching any code. **Current status and what is left is in `PROGRESS.md`** — start there. Strategic context is in `summary.md`; sync design in `sync-design.md`.

---

## What This App Is

**FreshTrack** — Android food expiry tracker (Kotlin, Jetpack Compose). Live on Google Play. India-first audience. Core promise: track groceries, get expiry alerts, reduce food waste. **Offline-first, privacy-preserving — no cloud data collection.**

---

## Architecture

| Layer | Key Files |
|---|---|
| UI / Screens | `presentation/screen/` |
| Navigation | `presentation/navigation/Navigation.kt` — single NavHost, guest mode routing |
| ViewModels | `presentation/viewmodel/` |
| Local DB | `data/local/GoodBeforeDatabase.kt` (Room v1, `goodbefore_database`), `data/local/entities/ItemEntity.kt` |
| Repositories | `data/repository/` |
| DI | `di/KoinModules.kt` (Koin) |
| Preferences | `data/preferences/OnboardingPreferences.kt` — includes guest mode flag |
| Notifications | `data/notification/` — WorkManager + NotificationHelper |
| Auth | Firebase Auth (Email/Password + Google Sign-In) via `presentation/viewmodel/AuthViewModel.kt` |
| Sync | `data/sync/` — ProductSyncer + SyncWorker; `data/remote/firestore/` |
| Session | `data/session/UserSession.kt` — current uid and active pantry |

**DB version: 2** (`goodbefore_database`). The previous `freshtrack_database`
and its 1→7 migration chain were deleted during the GoodBefore model migration,
which was only safe because there is no installed base. That freedom is spent:
`Migration(1, 2)` (undo's reversal columns) is the pattern to follow — every
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
- **Deletes are soft.** Set `isDeleted`; a hard delete cannot propagate to another
  device. Only the queries under "Sync" in `ItemDao` may see tombstones.
- **Free tier is a feature gate, not a quantity cap.** No item limits — capping
  local storage would strand data for existing users and contradict the listing.
- **`isPremium` is server-written only.** Rules refuse any client write to it.
- **`COLLATE NOCASE`** on `name` and `category` fields — prevents milk/Milk duplicates.
- **Categories are food-only:** Fresh Produce, Dairy, Bakery, Beverages, Pantry, Leftovers, Other. No Medicine/Cosmetics.
- **`notificationEnabled` stays in `ItemEntity`** (DB field) even though the UI toggle was removed — do not drop this column.
- **No emoji in UI strings.** Use Material icons only.
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
  ├── Onboarding not done → OnboardingScreen → (Get Started or Skip) → guest → Dashboard
  └── else → Dashboard (as guest)
```

**Deferred auth:** Login is never forced at startup. A new user lands on the
Dashboard as a guest. Login is reached only on intent — the Settings "Using as
Guest → Sign In" banner, or tapping a cloud feature. On sign-in, guest data
claims into the account.

Settings "Sign Out" clears guest flag AND Firebase session, returns to Login.

---

## Current Status

**See `PROGRESS.md`** — it is the single source of truth for what is done, what is
next, and what is knowingly incomplete. Do not reconstruct status from git log.

Short version: Phase 1 done. Backup & Sync built but inert, because nothing sets
`isPremium` yet. Rules written and tested but not deployed.

---

## Pricing & Monetization

Free tier: full local inventory, barcode scan, notifications, CSV export, guest mode.
(Receipt scanning is *planned*, not built — there is no ML Kit text-recognition
dependency in the project. Do not describe it as shipped.)
Premium (planned): Backup/Sync, AI recipes, family sharing, storage zones, extended history.
India pricing target: ₹299–499/yr. USD base: $3.99/mo · $14.99/yr.

---

## Conventions

- Kotlin + Jetpack Compose. No XML layouts.
- All Room migrations must be additive — never drop columns used in DAOs.
- Koin for DI. Do not use Hilt.
- `EncryptedSharedPreferences` for all preference files.
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
