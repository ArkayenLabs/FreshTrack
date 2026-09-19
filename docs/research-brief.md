# GoodBefore — research brief

Two parts. **Part A** is what exists today, written for an AI (or a person)
who has never seen the app. **Part B** is the prompt to hand to a research
AI along with Part A. Nothing in Part A is aspirational; if it is not built,
it says so.

---

## Part A — the app as built (19 Sep 2026)

### One line

GoodBefore (store listing still says FreshTrack) is an Android food-expiry
tracker: you put in what you bought, it tells you what to use before it goes
off, and it keeps an honest count of what you used versus threw away.

### Stage

- Live on Google Play under `com.arkayenlabs.freshtrack`. v1.1.0 is live but
  crashes on Add; v1.1.1 (the fix, plus everything below) is built and about
  to be uploaded. Effectively: **no real user base yet.**
- Solo developer. An iOS version is in early build (SwiftUI, same backend).
- Target markets: **US and UK first**, then other English-speaking. India is
  deliberately not a launch market (barcode/product-data coverage is too
  thin).
- Language: English only, structured for more later.
- No usage data yet. Analytics exists but is opt-in and off by default.

### What the app does (all free, all on-device)

1. **Three ways to add food**
   - Scan a barcode → name and category from Open Food Facts.
   - Photograph a receipt → on-device text recognition turns a whole shop
     into a review sheet; duplicates of things already in the kitchen are
     surfaced, not silently merged.
   - Type it in.
   - Optional: point the camera at the printed date on a packet; the app
     reads it and asks you to confirm. An ambiguous date (03/04) shows both
     readings; nothing is saved without being shown.
2. **Expiry with provenance.** Every date knows where it came from
   (printed, scanned, typed, estimated), whether it is a use-by or
   best-before, and whether the person confirmed it. An estimate can never
   silently overwrite a printed date. The UI labels estimates as such.
3. **Today** — the home tab. A ranked "rescue list" of what is overdue, due
   today, due tomorrow, due this week, each with one primary action (Use)
   and secondary ones (Later, Bin). Undo is a snackbar. When nothing is
   due, it shows what is coming up next. First run shows the three ways in.
4. **Kitchen** — the inventory, with category chips, filters (all /
   expiring / expired) and sorts. Categories are food-only: Fresh Produce,
   Dairy & Eggs, Meat & Fish, Ready Meals, Bakery, Beverages, Store
   Cupboard, Leftovers, Other. Storage location (fridge/freezer/pantry) is
   a separate concept from category.
5. **Progress** — items used, items wasted, used-vs-wasted ratio, and a
   "days waste-free" count that restarts rather than "breaks" (no punitive
   streak). All figures are derived from an append-only event ledger, so
   editing or deleting an item cannot rewrite what you were told you did.
6. **History** — the ledger, readable.
7. **Notifications** — daily expiry check with actions, weekly summary.
   Permission is asked only after the first item exists.
8. **Home-screen widget** — what needs using first, tap a row to open it,
   tap + to add; resizable; shows what is coming up when nothing is urgent.
9. **CSV export.**
10. **Guest mode.** No account needed for any of the above. Sign-in
    (Google or email) exists only as the gateway to Premium; guest data is
    claimed into the account on sign-in.

### What is built but not switched on

- **Backup & Sync (Premium).** Room is the source of truth; Firestore
  mirrors it through an outbox. Multi-device sync is built and proven on
  emulators, hidden behind a flag until a purchase is proven.
- **Billing.** RevenueCat on both platforms. Webhook deployed and verified;
  SDK in the app; our own paywall screen (one feature, monthly/yearly,
  yearly preselected). Products not yet created in Play Console; nothing is
  purchasable yet. Proposed price: $3.99/mo, $19.99/yr, household included.
- **The data model already assumes a household**: items belong to a
  *kitchen*, not a user; a kitchen can have up to six members; Premium is a
  property of the kitchen. The invite flow is not built.

### What is decided but not built (from `docs/strategy.md`)

- **Household** — one payer, up to six people free; the invite *is* the
  referral loop. First thing after billing.
- **Rescue plans** — "what do I cook tonight from what's dying?" On-device
  first; a cloud model for Premium.
- **Learned shelf life and price memory** — after a month the app knows
  *your* milk in *your* fridge lasts 9 days; names the foods this household
  always wastes. The accumulated model of the kitchen is the moat.
- **The Sunday statement** — a weekly recap of what was saved and wasted.
- Shareable cards, give-a-month.

### What does not exist

- No AI/LLM features of any kind. Scanning is ML Kit OCR/barcode detection,
  not reasoning.
- No recipes, no meal planning, no shopping list.
- No social features, no sharing, no referral.
- No web app. No iOS release yet.
- No monetisation live. No item caps or ads — the free tier is a feature
  gate, never a quantity cap.

### Non-negotiables (design constraints the ideas must respect)

- **Offline-first and private.** Everything works with no account and no
  network. No cloud data collection. Cloud only for Premium features the
  person opts into.
- **Honest numbers.** Progress is read from the ledger, never from a
  counter that can drift. Nothing is deleted; reversals are their own
  events.
- **No punishment mechanics.** Streaks restart; copy never scolds.
- **Free tier is generous by design.** Everything that works on-device is
  free forever. Premium is for things that genuinely need a server: sync,
  household, cloud intelligence.
- **Material 3, no emoji, no gradients, no decoration for its own sake.**
- Small team: one developer. Ideas that need a content team, a marketplace
  or ongoing manual operations are out.

### Tech, for scoping

Kotlin, Jetpack Compose, Room (SQLite), Koin, WorkManager, Glance widget,
ML Kit (barcode, text recognition), Firebase Auth + Firestore, Cloud
Functions (Node), RevenueCat. Min SDK 24. Every user-facing string is a
resource. Unit tests: 288. Rules and functions tested on emulators.

---

## Part B — the prompt

Copy everything below the line into the research AI, with Part A attached
or pasted above it.

---

You are a product strategist and growth researcher for consumer mobile apps.
Above is a complete, honest description of my Android app, GoodBefore, as it
exists today, including what is deliberately not built and the constraints
any idea must respect. Read it fully before answering.

I am a solo developer. The app is live but has essentially no users yet.
Billing is days away from being switchable on. I want to know what to do
next, in what order, and why, across every dimension that matters:

1. **Making people pay** — what is worth paying for in this category, what
   isn't, price and packaging, trials, the paywall moment, what converts in
   food/household apps specifically.
2. **Retention** — day 1 / 7 / 30 / 180. What brings someone back to a food
   tracker; what makes the data they have accumulated worth staying for;
   what habits or rituals (daily, weekly) actually stick; what kills these
   apps (usually: the entry cost of logging).
3. **Actually reducing food waste** — not vanity metrics. What evidence
   exists on which interventions change behaviour (reminders, visibility,
   money framing, social, meal planning); what a tracker can do that a
   person's memory cannot; how to measure impact honestly.
4. **Getting people to share it** — what would make someone show this app
   to another person; household/family/flatmate mechanics; shareable
   moments; referral structures that don't feel like referral structures.
5. **Distribution** — store listing and ASO for US/UK; which channels work
   for solo-dev utility apps (Reddit, TikTok, press, communities, partners
   like supermarkets or councils, sustainability programmes); what is
   realistic with zero budget.
6. **AI** — where a model genuinely helps (recognising food from a photo,
   estimating shelf life, "what do I cook tonight", parsing receipts better,
   natural-language add) and where it is a gimmick. On-device vs cloud
   trade-offs given the privacy stance. What is cheap to run per user.
7. **Tracking & measurement** — the minimum instrumentation to learn what
   works, compatible with opt-in analytics and no cloud data collection
   for free users; which 5–8 metrics matter; how to run experiments as one
   person.
8. **Anything else** — competitors doing something clever, adjacent
   categories worth borrowing from, risks I am not seeing, and things in
   Part A you think are mistakes.

Research this properly: cite real competitors (Fango, NoWaste, Kitchen
Pal, Too Good To Go, Olio, Bring!, AnyList, Paprika, and others you find),
real studies on food-waste behaviour, real data on subscription conversion
and retention in household/utility apps, and real examples of solo-dev
apps that grew. Prefer evidence over opinion; say when something is a
guess.

**Output format — this matters.** Your answer will be handed to a coding
AI that will implement the ideas in this codebase. So after a short
narrative (at most a page: the three or four things that matter most and
the order to do them in), give **every idea as its own card** in exactly
this shape:

```
### IDEA: <short name>
- Area: pay | retention | waste-impact | sharing | distribution | ai | measurement | other
- One line: <what it is, in one sentence>
- Why it works: <the mechanism, and the evidence or example behind it>
- For this app specifically: <how it fits GoodBefore's existing features, data model and constraints; what already exists that it builds on>
- User sees: <describe the screen, moment or message concretely — what appears, when, what the person does>
- Free or Premium: <and why>
- Needs: <on-device only | server | model | third-party service | content> — and rough cost per user if not zero
- Effort: S | M | L for one developer, with the reason
- Measures of success: <the one or two numbers that would show it worked>
- Risks: <what could go wrong, including how it could violate the constraints in Part A>
- Priority: now | after billing | later | probably not
- Sources: <links or citations>
```

Give at least 25 cards. Include ideas you would advise against, marked
"probably not", with the reason — those are as useful as the good ones.
Do not pad; do not repeat the same idea under two areas. Do not propose
anything that needs a content team, manual operations, ads, item caps, or
collecting free users' data to a server.
