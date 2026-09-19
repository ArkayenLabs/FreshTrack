# Assessment of the 19 Sep 2026 research

The research is in `docs/research-2026-09-19.md`, unedited. This file is
what we make of it: what it confirms, what it gets wrong about this codebase,
what we cannot verify, and the build order that results. It supersedes the
"Sequence" paragraph in `docs/strategy.md` where the two differ; the four
systems in that document (value, premium, retention, distribution) still
stand.

## The verdict in one paragraph

It agrees with `docs/strategy.md` on every large call: household first after
billing, then the pieces that compound with data (Sunday statement, waste
hotspots, price memory, learned timing), and only then generative AI, as a
Rescue Plan rather than an assistant. It adds five small things we had not
listed, all of which are cheap and fit the model: **Freeze** as a rescue
action, a **date-label coach** that differs between the US and UK, a
**three-item activation** definition, a **review prompt after a real
rescue**, and an **eight-metric scorecard**. It rejects, with reasons we
share, an AI chat tab, fridge-photo inventory, a recipe/planner suite, a
surplus marketplace, item caps, and lifetime Premium. Its central warning is
the right one: the risk is not missing features, it is that logging food is
too much work and nobody reaches the point where Today has anything to say.

## Checked against the code

Things the research could not know, which change its effort estimates:

| Claim | What the code says | Effect |
|---|---|---|
| Freeze is "M: storage/date-state semantics matter" | `DateKind.FROZEN` already exists ("revised horizon after freezing, always a user action"); `LocationType.FREEZER` exists; `ExpiryDate.canBeReplacedBy` already decides what may overwrite what | Freeze is S/M: mostly the Today action, the freezer-window rule and copy. No migration. |
| "AI output must never write directly to Room" | `DateSource.AI` exists at precedence 0, below `RULE`, so an AI date can never replace a printed or confirmed one | Already enforced by the model; Rescue Plan / Quick Add inherit it. |
| Honest money ledger needs "receipt-price parsing" | `priceMinorUnits`, `priceCurrency`, `priceSource` are on every item | Storage and provenance exist; the receipt parser does not yet extract prices, and Progress does not sum them. Still M, but the hard part (provenance) is done. |
| "Trigger the store-rating request…" | There is no in-app review prompt anywhere in the app | Net new, S: Play In-App Review API plus a local gate. |
| Eight-metric scorecard "mostly event definitions" | `AnalyticsHelper` has four events (`item_scanned`, `item_added`, `item_consumed`, `item_discarded`) at three call sites; analytics is opt-in and off by default | The events need defining and wiring; the consent plumbing exists. |
| Contextual paywall: "put Backup & Sync above authentication" | Already the flow: Settings card → Premium screen → guest is sent to sign in | Nothing to build; the trial offer is the only addition. |
| Kitchen search for "Check Before You Buy" | Kitchen has filter and sort, no text search | Search is net new before a shortcut can reuse it. |
| Household "already anticipated" | Correct: items belong to a `kitchenId`, `memberUids` up to six is enforced in rules, Premium is a kitchen property, sync is built | The invite flow, membership removal and lapse handling are the work. L, as it says. |

## What we do not accept as-is

- **"Voluntary 30-day research mode."** We already have opt-in analytics
  that is off by default. A second, time-boxed consent adds a consent
  surface and an expiry mechanism for the same data. Fold it: define the
  eight events, keep the existing single opt-in, and ask for it after a
  genuine success rather than at first launch (that timing change is the
  good part of the idea).
- **Three-item activation copy** ("2 more and GoodBefore can start
  watching your kitchen"). The mechanism is right; the nudge as written is a
  progress bar in disguise. Implement it as one quiet line on Today's
  coming-up state when the kitchen has one or two items, and nothing else.
  No counter, no badge, no repeat.
- **Date-label coach.** Right idea, but the safety wording is not something
  to draft from memory. Use the FSA (UK) and FDA/USDA (US) pages it cites
  as the only sources, keep each explanation to one sentence, and show it
  only on an overdue best-before item. Never on use-by.
- **Receipt Fast Lane** is sound but is "now" only if the receipt flow is
  measured first; we do not know today whether people abandon at review.
  It moves to after the scorecard exists.

## What we cannot verify

Cited by the research, not checked by us. None of them changes a decision,
but none should be repeated as fact in copy or a listing:

- The 119-household Japanese study and its 29% figure (MDPI 2025).
- RevenueCat 2026 numbers: 42.5% vs 25.5% trial conversion by trial length;
  0.9% D35 download-to-paid on Play; AI apps churning ~30% faster.
- Competitor prices as of Sep 2026 (Fango $2.99/$22.99, KitchenPal
  $3.99/$14.99, AnyList $14.99, CozZo $17.99, NoWaste $6.99).
- ML Kit GenAI Prompt API: API 26+ and AICore device gating. Plausible;
  must be verified against the current docs before Quick Add or Rescue
  Plan are scoped, because our min SDK is 24 and the conventional paths
  must stay first-class regardless.
- One model name in the original text ("GPT-5.6 Luna") is not one we
  recognise; treated as noise, the pricing point stands on its own.

## Build order

Two tracks. The owner's track is console and content; the code track is
what gets built in the app, in order. Nothing on the code track waits for
the owner's track except where marked.

**Owner, now:** upload the evening v1.1.1 bundle; rename the listing to
GoodBefore; RevenueCat/Play Console (item 4 in `PROGRESS.md`); write the
listing with the Play ASO skill once it exists (intent-first: "food expiry
tracker", "expiration" for US, "expiry" for UK); start recording the
receipt-scan and date-scan clips.

**Code, release after v1.1.1 (call it v1.2):**

1. **Eight-metric events** (S). Define and wire: `first_item`,
   `third_item`, `add_completed{method}`, `item_resolved{outcome}`,
   `premium_intent`, `paywall_shown`, `trial_started`, `purchase`. Ask for
   the existing analytics opt-in after the first real rescue, not at
   launch. Everything else in the list is derivable from these.
2. **Review prompt after a rescue** (S). Play In-App Review, gated: app
   installed ≥ 3 days, ≥ 3 resolved items, the person just marked a
   Today item Used, never more than once per 60 days locally regardless of
   what the API decides.
3. **Three-item line on Today** (S). One sentence, as above.
4. **Date-label coach** (S). One sentence on an overdue best-before item,
   per locale, from the cited sources only.
5. **Freeze** (S/M). Fourth action on a Today row where the item's location
   is not already the freezer: moves location to freezer, sets a
   `FROZEN`-kind estimated date from a small per-category table, keeps the
   original date and provenance untouched, writes its own event so it
   shows in History and can be undone like Use/Bin.

Then, in the same release if billing is ready, or the next one if not:

6. **`PREMIUM_ON_SALE` flip + 30-day trial on first Premium intent**
   (S, needs owner's item 4/5 done). Products: `premium` with `monthly`
   and `annual`, trial offer on both, household included in the copy.

**After billing is proven (v1.3):** Household — invite by link, join,
membership list, remove, what happens on lapse (local data always kept,
read-only sync, export). Then Household activity in History (actor on each
event; the ledger already has `actorUid`).

**Then, compounding with data (v1.4+):** Sunday Statement (local, from the
ledger; notification Sunday morning) → shareable Sunday card → Waste
Hotspots (minimum-sample gated) → prices from receipts and the money ledger
→ learned household timing.

**Then AI:** Rescue Plan, on-device first (Gemini Nano via ML Kit where
the device supports it, cloud for Premium elsewhere), structured input from
the rescue list, output marked as suggestions, never a safety claim. Then
natural-language Quick Add into the existing form. Receipt-repair fallback
only if the receipt completion metric says review is where people leave.

**Not building:** AI chat tab, fridge-photo inventory, recipe library or
meal planner, marketplace or feed, item caps, ads, lifetime Premium.
