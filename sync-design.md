# FreshTrack — Backup & Sync Design

Decisions for Phase 2. Written before any sync client code, so the expensive
choices are settled on paper rather than in a migration against live data.

Status: **design agreed, rules written and tested, sync client not implemented.**

Rules tests: `npm run test:rules` (needs Java for the Firestore emulator; runs
against project `demo-freshtrack`, which can never reach production).

---

## Source of truth

**Room stays the source of truth. Firestore is a mirror.**

The app's whole position is offline-first and private. If Firestore became the
source of truth, every read would depend on the network and the offline promise
would be a lie. Instead:

- All reads come from Room. The UI never waits on the network.
- Writes go to Room first, then push to Firestore in the background.
- Firestore changes pull into Room and the UI updates through the existing Flows.
- A user who never signs in is unaffected — nothing leaves the device.

This also means sync can fail, retry, or be offline indefinitely without the app
degrading.

---

## Document shape

```
/users/{uid}
    displayName   string
    plan          "free" | "premium"    server-written only
    pantryIds     [pantryId]
    createdAt     number

/pantries/{pantryId}
    name          string
    ownerUid      uid
    memberUids    [uid]                 the access-control list
    createdAt     number

/pantries/{pantryId}/products/{productId}
    ... all ProductEntity fields ...
    updatedAt     number                drives conflict resolution
    isDeleted     bool                  tombstone
    deletedAt     number | null
```

**Why a pantry layer instead of `/users/{uid}/products`.** Household sharing is
on the roadmap. Nesting products under a user makes sharing a migration of every
product document of every sharing user, in production, while people are writing
to them. With a pantry in between, a household is a pantry with more entries in
`memberUids` — no data moves, ever. Each user gets one personal pantry at signup.

**Local rows carry `pantryId` too.** Room mirrors the remote key: `ProductEntity`
has `pantryId` as its access key (added in `Migration(6, 7)`), with `userId` kept
only for attribution. Filtering locally by the viewer's uid would break the
moment a shared pantry is downloaded — the same item would be either mislabelled
as theirs or invisible, depending which uid was stamped. Signed out, rows sit in
the `local` pantry; on sign-in they are claimed into `personal-{uid}`.

**Pantry ids are derived, not allocated.** A personal pantry is always
`personal-{uid}`, so the client can address it without a round trip and creating
it twice is harmless.

**Why `memberUids` is an array on the pantry document.** Security rules can read
it in a single `get()`. A separate members subcollection would need to be kept in
sync with the rules' view of membership, which is a second source of truth for
the thing that controls access — the last place that is worth having one.

---

## Access control

Rules live in `firestore.rules`. Key properties:

- Deny by default.
- **No `list` permission on `/pantries`.** Rules cannot reliably constrain what a
  collection query returns. The client reads its own user document, takes
  `pantryIds`, and fetches each pantry by id. This removes a whole class of
  accidental exposure.
- `ownerUid` is immutable, and only the owner edits `memberUids`, so a member
  cannot promote themselves or evict the owner.
- A pantry must be created owned by, and containing only, its creator.
- `plan` cannot be written by a client. Entitlements come from a Cloud Function
  that has verified a Play Billing purchase. A client-writable plan field is a
  free premium subscription for anyone who decompiles the app.
- Hard deletes are refused. Deletion is setting `isDeleted`, so it can propagate.

---

## Conflict resolution

**Last write wins, compared on `updatedAt`.**

`updatedAt` already exists in the schema and is stamped by the repository on
every write, so no new plumbing is needed. On conflict the higher `updatedAt`
wins outright.

This can lose an edit when two devices change the same item while both offline.
For a pantry tracker that is a minor annoyance, not data loss, and it is simple
enough to explain to a user. Per-field merge was considered and rejected as far
more code and edge cases than the problem justifies.

Rules reject any write without a numeric `updatedAt`, so a malformed write
cannot silently win.

---

## Sync algorithm

1. On sign-in, claim guest rows locally (already implemented), then push any rows
   not yet in Firestore.
2. Maintain a `lastSyncedAt` watermark per pantry.
3. Pull: listen to products where `updatedAt > lastSyncedAt`. For each, if the
   remote `updatedAt` is newer than the local row, overwrite locally.
4. Push: send local rows whose `updatedAt` is newer than the last successful
   push. Retry with backoff; a failed push must never block the UI.
5. Tombstones apply on both sides — `isDeleted` rows sync like any other change
   and are filtered out of every user-facing query.

---

## Free tier limits

**There are no quantity caps. The paywall is a feature gate.**

| | Free | Premium |
|---|---|---|
| Local inventory | Unlimited | Unlimited |
| Barcode scan, notifications, CSV export, history | Yes | Yes |
| Cloud backup & sync (writes) | No | Yes |
| Reading data already uploaded | Yes | Yes |
| Household members | 1 | 6 |

Three reasons there is no item cap:

1. **It would break live users.** People are already using the app with unlimited
   local items. A retroactive cap would strand data on their phones.
2. **It contradicts the listing.** "No account, 100% offline" is what the 5.0
   reviews praise. Capping offline storage undermines the thing that works.
3. **Local storage is free to us.** Firestore reads and writes are not. The
   paywall belongs where the cost is.

This also removes the counter infrastructure entirely — no `productCount`, no
transactional increments, no Cloud Function trigger to keep a count accurate.

**Reads survive a downgrade.** If a subscription lapses, the user can still read
and export everything already uploaded; only further backup stops. Locking people
out of data they already gave us would be holding it hostage.

**Where the entitlement lives.** `isPremium` sits on the pantry document, not the
owner's user profile. The rules already fetch the pantry to check membership, so
this costs no extra billed read; checking the owner's profile would double the
reads on every product write. A Cloud Function sets it after verifying a Play
Billing purchase — rules refuse any client write to it, on both create and
update.

**Household size** is enforced with `memberUids.size()`, which needs no counter
because the list is already on the document.

## Open items before implementation

- [x] Free-tier model decided: feature gate, no quantity caps. See above.
- [x] Rules unit tests — 27 tests in `firestore-tests/rules.test.mjs`, run with
      `npm run test:rules`. Verified meaningful by deliberately loosening two
      rules and confirming the matching tests failed.
- [ ] Cloud Function for Play Billing verification to write `plan`
- [ ] Account deletion path (must clean up pantries; cannot be a client delete)
- [ ] Reconcile analytics with the "no data collection" store listing
