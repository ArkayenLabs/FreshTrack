# GoodBefore — Product Strategy

> 19 Sep 2026: an external research pass on this strategy is in
> `research-2026-09-19.md`; `research-assessment.md` reconciles the two and
> now holds the build order. This document is the reasoning behind the four
> systems; the sequence at the end is superseded where they differ.

*18 Sep 2026. Supersedes the "Strategic Research & Roadmap" section that lived
in `summary.md` from July 2026; that section is kept there only as history.*

The question this answers: what would make this app reach tens or hundreds of
thousands of users and pay for itself — designed as four connected systems
(value, premium, retention, distribution) rather than as a list of features.

---

## 1. The fundamental outcome

Strip the features away and the job is: **"Nothing in my kitchen goes bad
without me knowing in time, and when something is about to, I know what to do
with it tonight."** The user's currencies are certainty (is this still fine?),
time (what's for dinner?) and money — a US family of four wastes ~$1,500 a
year, a UK one ~£1,000 (ReFED 2026, WRAP). The pain is weekly and recurring:
every shop restarts the clock.

Two findings decide everything else:

- **Trackers die at the alert.** The consistent 2026 complaint across Fridgely,
  NoWaste and KitchenPal is that the app says "beef expires tomorrow" and stops
  — "an app that tracks your fridge is a tool, not a habit". The value gap is
  the step *after* the alert, and Today already sits exactly there.
- **The kitchen is a shared object, and the direct competitor prices it that
  way.** Fango: $2.99/mo or $22.99/yr, one month free, a household of four on
  one subscription, members join by an 8-character code *with no account*.
  AnyList: $9.99 individual, $14.99 household; Bring! is free for two. Nobody
  in the category has yet made the household the *growth* engine rather than
  a feature. That is the opening.

What GoodBefore already has that they don't, and must never trade away:
on-device capture at zero marginal cost (their receipt scan is a cloud call
they have to charge for), a date model that separates a printed date from a
guess, and an append-only ledger that makes the impact numbers honest. These
are not features; they are why the paid product can be trusted.

## 2. Mechanics worth transferring from outside the category

| From | Mechanic | Transfer |
|---|---|---|
| Splitwise | One person logs, everyone sees; the shared ledger pulls the second person in | The shared kitchen: the shopper logs, the cook gets Today. Both have a reason to open it. |
| Too Good To Go | Money and CO₂ counters as identity (120M registered) | Impact is already derived from the ledger. Make it a monthly *statement*, not a dashboard tile. |
| Duolingo | Sign-up after value; soft streaks | Already done. Keep the streak soft. |
| Spotify Wrapped / Strava | A periodic, shareable artefact of *your* data | "Your kitchen this month": saved, binned, the one food you always waste. Rendered on-device, shared as an image. |
| YNAB | Accumulated personal data is the moat | Learned shelf life: *your* milk in *your* fridge lasts 9 days. Hard to leave after six months. |
| Yuka | Scan → verdict → "look what it says" | The receipt review sheet is a verdict on a whole shop; "3 of these will die by Thursday" is a screenshot moment. |

## 3. The four systems

### Value — free, and the reason to install

- Capture in under 10 s: barcode, receipt, printed date, all on-device, all
  free. This is the structural wedge; it stays free forever.
- **Today Rescue** as the daily screen: not a list, a decision — use, later,
  bin. Built.
- Honest dates: an estimate reads as one on every surface. Built. Add one
  thing: the guess *learns* from what was actually binned — the free tier gets
  the generic table, Plus gets *your* kitchen's table (below).
- Weekly rhythm: a shop-day capture prompt, a Sunday statement. Notifications
  are decisions ("Use the spinach tonight?") with the resolve action in the
  notification, never nags.

### Premium — speed, intelligence, collaboration, certainty

Ranked by willingness to pay, judged against what the market already charges
for:

1. **Household kitchen — one payer, up to six people, free for the others.**
   The kitchen is the entitlement (`isPremium` is already modelled on the
   kitchen, not the user). Join by code. The strongest paid feature in the
   category, and it doubles as distribution (§5). Design for less friction
   than the current plan: a member should join *without creating an account
   first*; Fango's no-account join is the bar. Sign-in is for the payer.
2. **Rescue plans — "what do I cook tonight from what's dying?"** The #1
   complaint, unsolved by the trackers, and a real per-call cost, which is
   exactly what should be paid for. Two tiers of intelligence: on-device
   (Gemini Nano via AICore where the phone has it — zero cost, matches the
   privacy story, can be free) and a cloud model for Plus with pantry-aware
   plans for the week. The pantry is the prompt; the output is three dinners
   ordered by what dies first.
3. **Learned shelf life and price memory.** After a month the app knows
   *your* bananas go in five days and that £14 of coriander was binned this
   quarter. Personalisation from accumulated data; no competitor does it; it
   only gets better with time.
4. **Backup and sync across devices.** Necessary, built, low standalone
   willingness to pay — bundle it, don't lead with it.
5. **The monthly statement and yearly review** — retention and sharing; Plus
   gets full history and the shareable card.

**Rejected** (sound good, do not move pay, retain or grow): item or history
caps (strand data, contradict the listing — already ruled out), cosmetic
themes, "remove ads", medicine and cosmetics, calorie tracking, generic chat,
the everything-app meal planner, public leaderboards (noisy, and the brand is
"we won't trick you").

### Retention — why it is worth more at day 7 / 30 / 180 / 365

- **Day 7:** the first Sunday statement — "used 11, binned 2, saved about
  $19". Impact exists; give it a moment.
- **Day 30:** learned dates start replacing table dates; the guesses get
  visibly better; the streak means something.
- **Day 180:** price memory names the foods this household *always* wastes;
  Rescue plans know its taste; leaving means losing the model of the kitchen.
- **Day 365:** the yearly review. And the household: if a partner or flatmate
  sees the same fridge, quitting is a shared decision — the strongest
  retention lever there is.
- **Always:** the widget and resolve-from-notification (both built, both
  unverified on a device).

Caveat: habit formation averages ~66 days, so the free tier must carry
someone through two months on its own. It does, if capture stays fast and
Today stays a decision.

### Distribution — how usage creates users

- **The household invite is the loop.** One payer → up to five free members
  who experience the full paid product → each one who moves out (students,
  flatmates, couples splitting a home) starts a kitchen and becomes a payer.
  Plus *is* the referral. Instrument it: household attach rate and
  members-who-later-subscribe are the two numbers that decide whether this
  business works.
- **Shareable statements.** Monthly and yearly cards, rendered on-device, no
  account needed to view. "We saved £84 this month" is the only thing people
  in this category voluntarily post.
- **Referral: give a month, get a month.** Cheap, standard, stacks with the
  household loop.
- **The receipt verdict** as a screenshot moment after every shop.
- Skip communities and public feeds; the product is private by design and the
  audience does not want a social network about their fridge.

## 4. The strongest combination

**Free:** capture (barcode, receipt, printed date), Today Rescue, generic
shelf-life table, soft streak, weekly statement, widget, one kitchen on one
device, CSV export. The full product, no caps, no account.

**GoodBefore Plus — one tier, household included:** a shared kitchen for up
to six on one payment, Rescue plans, learned shelf life and price memory,
backup and sync, full history, monthly and yearly review cards.

**Pricing:** $3.99/month · **$19.99/year** · no lifetime. Household-included
puts it against Fango ($22.99) and AnyList household ($14.99), not against
$7-a-year solo trackers; the annual is where margin and retention are;
lifetime caps LTV on exactly the users who would stay longest. Free trial:
**14 days**, not Fango's month — long enough to see the first Sunday statement
and the learned dates begin, short enough to convert before novelty fades.
Localised by Play and the App Store from the USD base.

**Retention engine:** the Sunday statement, learned dates, the household.
**Distribution loop:** the household invite, the shareable cards, give-a-month.

**Sequence:** the household is the first thing to build after billing — it is
premium, retention and distribution at once, and the entitlement model already
assumes it. Rescue plans second (on-device first, cloud for Plus). Learned
shelf life third; it needs months of ledger data to show, so the collection
starts now.

Two changes to the current plan that follow from this: "Plus" is the better
name than "Premium" (it says *more*, not *gated*), and household-member join
should be designed no-account-first, with sign-in only for the payer.

## Sources

- Remy, *Best apps to reduce food waste at home (2026)* — remyapp.io/blog/best-apps-to-reduce-food-waste-at-home-2026
- ChefsPantry, *Best food expiry tracker apps* — chefspantry.io/blog/best-food-expiry-tracker-apps
- Fango pricing and household — fango.fi/en/ and fango.fi/en/blog/bring-app-alternatives/
- Homsy, *Best shared grocery list app 2026* — gethomsy.com/blog/meal-planning/shared-grocery-list-app
- AnyList — anylist.com
- Too Good To Go — en.wikipedia.org/wiki/Too_Good_To_Go
- ReFED, *2026 U.S. Food Waste Report* — refed.org/food-waste/refed-us-food-waste-report-2026/
- WRAP, *average UK household of four wastes £200 by mid-March* — wrap.ngo/media-centre/press-releases
