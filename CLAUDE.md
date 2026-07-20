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
| Local DB | `data/local/FreshTrackDatabase.kt` (Room v7), `data/local/entities/ProductEntity.kt` |
| Repositories | `data/repository/` |
| DI | `di/KoinModules.kt` (Koin) |
| Preferences | `data/preferences/OnboardingPreferences.kt` — includes guest mode flag |
| Notifications | `data/notification/` — WorkManager + NotificationHelper |
| Auth | Firebase Auth (Email/Password + Google Sign-In) via `presentation/viewmodel/AuthViewModel.kt` |
| Sync | `data/sync/` — ProductSyncer + SyncWorker; `data/remote/firestore/` |
| Session | `data/session/UserSession.kt` — current uid and active pantry |

**DB version: 7.** Migrations live in `FreshTrackDatabase.kt`. Adding columns always requires a new `Migration(n, n+1)`.

---

## Key Decisions & Constraints

- **Offline-first.** Room is the source of truth. Firestore mirrors it; a sync
  failure must never block a read. See `sync-design.md`.
- **`pantryId` is the access key, not `userId`.** Products belong to a pantry so a
  shared household pantry works. `userId` is attribution only. Never filter
  user-facing queries by `userId`.
- **Deletes are soft.** Set `isDeleted`; a hard delete cannot propagate to another
  device. Only the queries under "Sync" in `ProductDao` may see tombstones.
- **Free tier is a feature gate, not a quantity cap.** No item limits — capping
  local storage would strand data for existing users and contradict the listing.
- **`isPremium` is server-written only.** Rules refuse any client write to it.
- **`COLLATE NOCASE`** on `name` and `category` fields — prevents milk/Milk duplicates.
- **Categories are food-only:** Fresh Produce, Dairy, Bakery, Beverages, Pantry, Leftovers, Other. No Medicine/Cosmetics.
- **`notificationEnabled` stays in `ProductEntity`** (DB field) even though the UI toggle was removed — do not drop this column.
- **No emoji in UI strings.** Use Material icons only.
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
  ├── Onboarding not done → OnboardingScreen → Login (or Skip → guest → Dashboard)
  └── else → LoginScreen → Dashboard
                         └── "Continue without account" → guest → Dashboard
```

Settings "Sign Out" clears guest flag AND Firebase session, returns to Login.

---

## Current Status

**See `PROGRESS.md`** — it is the single source of truth for what is done, what is
next, and what is knowingly incomplete. Do not reconstruct status from git log.

Short version: Phase 1 done. Backup & Sync built but inert, because nothing sets
`isPremium` yet. Rules written and tested but not deployed.

---

## Pricing & Monetization

Free tier: full local inventory, barcode scan, notifications, CSV export, guest mode, ML Kit receipt scan.
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
- Do not drop `notificationEnabled` from `ProductEntity`.
- Do not use Hilt, TailwindCSS, or any web framework.
- Do not filter user-facing product queries by `userId` — use `pantryId`.
- Do not hard-delete products; set `isDeleted`.
- Do not let a client write `isPremium` or `plan`.
- Do not add item-count caps to the free tier.
