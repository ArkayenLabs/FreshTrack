# FreshTrack — Remaining Work Checklist

Items derived from `summary.md` strategic research + issues identified during the V2 hardening pass.
Ordered by phase. Do one at a time.

---

## Bugs & Issues Found

- [x] **Auth wall contradicts store listing** — Fixed. Added "Continue without account" to Login, "Skip" on Onboarding sets guest mode, Settings shows a guest banner with a Sign In prompt. Guest flag stored in existing encrypted SharedPrefs.
- [ ] **Store listing statistic is wrong for the audience** — Manual task (Play Console). Replace "Americans waste $1,500/year" with: "Indian households waste ~55 kg of food per person per year — totalling ₹1.55 lakh crore annually, largely due to poor meal planning."

---

## Phase 1 — Now (Reuses Existing Data & Infrastructure)

- [x] **Impact Dashboard** — Done. New screen at Settings > Your Impact, reachable via `Screen.Impact`. Shows waste-free day count, items used vs. wasted, and a used/wasted ratio bar. All figures derived from Room via `ProductRepository.getImpactStats()`; the old `UserRetentionPreferences` counter store was removed because it missed every resolution initiated from the Dashboard. Added `resolvedDate` to `ProductEntity` with `Migration(4, 5)`.
  - **Money-saved counter deferred** — there is no price field on `ProductEntity`, so a rupee figure could only be fabricated from an assumed average. Needs a real per-item price/estimate input first.
- [x] **Soft Streak Framing** — Done. The streak is derived as days-since-last-discard rather than a stored counter, so it restarts instead of breaking; `resetStreak()` is gone. A discard today reads "Fresh start / Your waste-free count starts again today."
- [ ] **Home-Screen Widget** — Glance API widget showing items expiring today or this week. WorkManager scheduling already exists. Highest-leverage passive retention mechanic in this category.
- [ ] **Deferred Auth** — Let users add and interact with a few items locally before any account prompt. Only trigger sign-in/sign-up when the user explicitly wants a cloud feature (sync, sharing). Fixes the store listing contradiction and aligns with the offline-first positioning.

---

## Phase 2 — Next (Same Engineering Lift Already Scoped)

- [ ] **ML Kit Receipt / Photo Scan (Free)** — Integrate Google ML Kit Text Recognition (on-device, offline, zero cost per scan, supports Devanagari). User photographs a grocery receipt; app parses and bulk-adds items. Structural differentiator against cloud-AI competitors who pay per scan.
- [ ] **Household / Family Sharing + Backup & Sync** — Move source of truth from local Room to Firestore with real-time listeners. Shared pantry across household members. Price as a family plan above single-user tier. This is the sync work already stubbed in Settings.

---

## Phase 3 — Later (Needs Sync Foundation First)

- [ ] **AI Recipe Suggestions — Premium Gate** — "Use it before it's gone" feature. Requires a paid recipe API or LLM call (real per-request cost). Gate behind premium subscription. This is the feature most cited as turning a tracker into a long-term habit.
- [ ] **Multi-Location Storage Zones — Premium** — Fridge / Freezer / Pantry / Garage as a first-class inventory dimension, separate from category. Power-user depth feature.
- [ ] **Environmental Impact Report — Premium** — Extended version of the Impact Dashboard with CO₂ estimates, food waste cost in rupees, and historical trends. Builds on the free Impact Dashboard data.

---

## Pricing / Monetization (When Premium Gate Is Built)

- [ ] **Set up in-app billing** — Implement Google Play Billing Library for subscription tiers.
- [ ] **Native INR pricing** — ₹299–499/year as the primary India market price. USD base: $3.99/mo · $14.99/yr · $29.99 lifetime · $24.99 family/yr. Do not just auto-convert a dollar price; set the INR tier explicitly in Play Console.

---

## Watch List (Not Now — Monitor)

- [ ] **MCP Server integration** — Allowing AI assistants (Claude, ChatGPT) to query a user's inventory directly. At least one competitor already ships this. Not worth prioritizing yet for a small team, but track it.
