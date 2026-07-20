# FreshTrack — Project Context for AI

> Read this before touching any code. Full strategic context is in `summary.md`. Current work queue is in `checklist.md`.

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
| Local DB | `data/local/FreshTrackDatabase.kt` (Room v5), `data/local/entities/ProductEntity.kt` |
| Repositories | `data/repository/` |
| DI | `di/KoinModules.kt` (Koin) |
| Preferences | `data/preferences/OnboardingPreferences.kt` — includes guest mode flag |
| Notifications | `data/notification/` — WorkManager + NotificationHelper |
| Auth | Firebase Auth (Email/Password + Google Sign-In) via `presentation/viewmodel/AuthViewModel.kt` |

**DB version: 5.** Migrations live in `FreshTrackDatabase.kt`. Adding columns always requires a new `Migration(n, n+1)`.

---

## Key Decisions & Constraints

- **Offline-first.** Room is the source of truth. No Firestore yet (Backup & Sync is stubbed as Premium).
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

## Current Sprint — Checklist

See **`checklist.md`** for the full ordered list. Summary of remaining items:

**Bugs (do first):**
- [x] Auth wall fixed (deferred auth / guest mode)
- [ ] Store listing statistic — manual Play Console update (Indian audience stat)

**Phase 1 (near-zero infrastructure):**
- [x] Impact Dashboard (derived from `isConsumed`/`isDiscarded` + new `resolvedDate`)
- [x] Soft streak framing
- [ ] Home-screen widget (Glance API)

**Phase 2:**
- [ ] ML Kit receipt scan (free tier, on-device)
- [ ] Household sharing + Backup & Sync (Firestore)

**Phase 3 (premium):**
- [ ] AI recipe suggestions (paid API)
- [ ] Multi-location storage zones
- [ ] Environmental impact report

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
