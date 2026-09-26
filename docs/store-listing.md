# Store listing — what is live

The Google Play listing text as published on **26 Sep 2026** with v1.1.1
(versionCode 6). This file is the tracked copy; the image sources and the
generators that build them live in `playstore-listing-ss/` (git-ignored,
because the raw captures are large and regenerable):

- `make_slide.py <01..05>` builds `listing/slide-0N.png` (1080x1920) from a
  raw emulator capture plus the app's own palette and fonts.
- `make_feature.py` builds `listing/feature-graphic.png` (1024x500).
- Screenshot order on the listing: 01 Today, 02 receipt, 05 three ways in,
  03 kitchen, 04 progress.

**Keep this in step with the app.** Every claim below is a claim about
behaviour that exists today. Nothing here mentions Premium, sync, household
or AI, because none of them are on sale yet; the Plus paragraph goes in when
`PREMIUM_ON_SALE` flips. `COMPLIANCE.md` tracks the one claim that needs
care: analytics is opt-in and off by default, so "no data collection" is not
the wording used.

---


Plain claims only: everything below is in the app today. No Premium, sync,
household or AI wording until those ship. US English is the default listing;
the en-GB block at the end swaps the words that differ.

## App name (30 characters max)

GoodBefore: Expiration Tracker

(30 exactly. The category term is in the title because the brand has no
recognition yet. Play indexes the title heaviest.)

## Short description (80 characters max)

Track expiration dates. Scan receipts and barcodes. Free. No account needed.

(76. Visible above the fold on the listing and indexed.)

## Full description (4000 characters max)

Know what to use before it goes off.

GoodBefore keeps a list of the food in your kitchen and tells you what needs eating first. Add a whole shop by photographing the receipt, scan a barcode, or type it in. Then the Today screen shows what is due, and one tap says you used it.

HOW IT WORKS

• Photograph the receipt. Every line becomes an item with an estimated date, ready to check and save.
• Scan the barcode. Name and category come off the packet.
• Point the camera at the printed date. The app reads it and asks you to confirm.
• Type it in for leftovers, loose veg, and anything without a barcode.

WHAT NEEDS USING TODAY

The Today screen lists what is overdue, due today, due tomorrow, and due this week, with one action each: Use, Freeze, Later, or Toss. Freezing moves the item to the freezer and gives it a new date as a guide. Mistakes can be undone.

An estimated date is always labeled as an estimate. A date printed on the packet is never overwritten by a guess.

YOUR KITCHEN, IN ONE LIST

Fridge, cupboard, and freezer together, sorted by date so the soonest is always on top. Filter by category: Fresh Produce, Dairy & Eggs, Meat & Fish, Ready Meals, Bakery, Beverages, Store Cupboard, Leftovers.

AN HONEST COUNT

Progress shows how many items you used and how many you threw away, and how many days it has been since you last wasted anything. The numbers come from what you actually did and cannot be edited away. No streaks that break, no scolding.

REMINDERS AND A WIDGET

A daily check tells you what is about to expire, with actions in the notification. A weekly summary shows how the week went. The home screen widget shows what needs using next without opening the app.

PRIVATE BY DEFAULT

• No account needed.
• Works fully offline.
• Receipts and barcodes are read on the phone and never uploaded.
• Usage statistics are off unless you turn them on.
• Export everything to CSV at any time.

FREE

Every feature above is free, with no item limits and no ads.

DATE LABELS

Best-before and sell-by dates are about quality, not safety. GoodBefore says so on the item, so good food is not thrown away over a date on a label.

Start with what is in the fridge. It takes a minute.

## What's new (release notes, 500 characters max)

en-US:

Version 1.1.1

• Fixed a crash when adding an item.
• Fixed the app failing to open after a reinstall.
• New empty screens that show the three ways to add food.
• Freeze is now an action on Today.
• Overdue best-before items say so: quality, not safety.
• Home screen widget rebuilt. Tap a row to open that item, and resize it.
• The save button no longer sits behind the navigation bar.

en-GB: the same, with the fifth line as "Items past their best before date say so: quality, not safety."

## en-GB (United Kingdom) differences

App name: GoodBefore Food Expiry Tracker
Short: Track food expiry dates. Scan receipts and barcodes. Free. No account needed.

In the full description, replace:
• "expiration" with "expiry"
• "Toss" with "Bin"
• "labeled" with "labelled"
• the DATE LABELS paragraph with: "Best before is about quality, not safety. Use by is different and the app treats it that way. GoodBefore says so on the item, so good food is not binned over a date on a label."

## Keywords carried in the text (for reference, do not paste anywhere)

food expiration tracker, expiry tracker, expiration date, food waste, fridge inventory, pantry tracker, receipt scanner, barcode scanner, best before, use by, kitchen inventory, food tracker offline, no account
