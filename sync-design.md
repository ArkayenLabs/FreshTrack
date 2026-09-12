# GoodBefore — Backup & Sync Design

How local changes reach the cloud and other devices, written against what the
app actually stores today rather than what it used to. The previous version of
this document described a polling client that mirrored rows by timestamp; that
client and the schema it mirrored were deleted in the GoodBefore migration.
This one describes the outbox that replaced it and the transport that has not
been written yet.

Status, 12 Sep 2026: **transport built and proven end to end against the
Firestore emulator with the real rules; rules not deployed; nothing sets
`isPremium`, so it is inert in production.** Nothing consumes the
outbox. The Settings card says so.

Rules tests: `npm run test:rules` (Firestore emulator, project `demo-freshtrack`,
which can never reach production).

---

## 1. What exists

Facts about the code, checked on 12 Sep 2026. Anything in later sections that
contradicts this section is a plan, not a description.

| Piece | Where | State |
|---|---|---|
| `items` table with `kitchenId`, `revision`, `isDeleted`, `updatedAt` | `ItemEntity` | Built |
| `item_events` append-only ledger, unique on `operationId` | `ItemEventEntity`, `ItemEventDao` | Built |
| `outbox` queue, one row per change, same transaction as the change | `OutboxEntity`, `OutboxDao`, `ItemRepositoryImpl.record` | Built, proven on device: contiguous `clientSequence`, each event's `operationId` matches one outbox row |
| `locations` table with `kitchenId`, `updatedAt` | `LocationEntity` | Built, **not queued** — `LocationRepositoryImpl` writes bypass the outbox |
| Guest → account claim | `ItemRepositoryImpl.claimLocalData` | Built; rewrites `kitchenId` on items, events and outbox rows |
| `RemoteStore` + `FirestoreRemoteStore` | `data/sync`, `data/remote/firestore` | Built for the §3 shape (12 Sep 2026): ensure kitchen, read entitlement, `push` one item + event batch, `eventExists`, erase account. `WireFormat` maps rows to fields, JVM-tested |
| `RemoteError` classification | `data/sync/RemoteError.kt` | Built: `PermissionDenied` / `Transient` / `Permanent` |
| Firestore rules for `users`, `kitchens`, `kitchens/items`, `kitchens/events` | `firestore.rules` | Written for the transport shape (§3), 54 tests, **not deployed**; live project still runs the old `pantries/products` ruleset from 5 Aug 2026, deny-by-default |
| Entitlement | `kitchens/{id}.isPremium` | Rules refuse client writes; **nothing sets it** |
| Push engine | `data/sync/OutboxPusher.kt` | Built, 20 JVM tests (12 Sep 2026): incremental drain, retry/stuck, entitlement gate, and the first backup with resumable ledger upload |
| Pull engine | `data/sync/RemoteChangeApplier.kt` | Built, 11 JVM tests (12 Sep 2026): paged by cursor, own writes stamp `revision`, events append with IGNORE, cursor moves after commit |
| `SyncRun`, `SyncWorker` | `data/sync/` | Built (12 Sep 2026): one run = ensure kitchen, push, pull, record outcome. Periodic 6h + one-shot when the app goes to the background, network-constrained. Settings card shows status, pending and stuck |
| Quantity rebase | `OutboxPusher.rebased` | Built (12 Sep 2026), 6 JVM tests: a quantity event pushed against a row someone else changed applies its delta to theirs |
| End-to-end | `androidTest/.../SyncEndToEndTest.kt` | **Passing** (12 Sep 2026): two devices, real rules, Firestore + Auth emulators. `npm run test:sync-e2e` (`:win` on Windows); skips when the emulators are down |

---

## 2. Principles that do not change

**Room is the source of truth. Firestore is a mirror.** Every read comes from
Room; the UI never waits on the network; a sync failure never blocks a read.
A user who never signs in is unaffected — nothing leaves the device.

**`kitchenId` is the access key.** Items, events and locations belong to a
kitchen, never to a viewer's uid. A household is a kitchen with more entries in
`memberUids`. `createdBy` and `actorUid` are attribution only.

**Deletes are soft.** `isDeleted` is set and the tombstone syncs like any other
change. Hard deletes exist only for account erasure.

**History is a ledger.** Impact is read from `item_events`, never counted off
row state. Sync must never rewrite or drop an event; it may only append.

**Premium is a feature gate, not a cap.** The free tier is the whole app,
offline, with no item limit. Writing to the cloud is the paid feature. Reads of
data already uploaded survive a lapse — data is never held hostage.

**`isPremium` is server-written only.** A client-writable entitlement is a free
subscription for anyone with a decompiler.

---

## 3. Remote shape

```
/users/{uid}
    displayName       string
    kitchenIds        [kitchenId]
    createdAt         number

/kitchens/{kitchenId}                       kitchenId = "personal-{uid}" for the personal one
    name              string
    ownerUid          uid
    memberUids        [uid]                  the access-control list
    isPremium         bool                   server-written only
    createdAt         number

/kitchens/{kitchenId}/items/{itemId}
    ...every ItemEntity field...
    expiryDate        string  ISO-8601 calendar date, never a number
    serverUpdatedAt   timestamp  == request.time on every write; the sync cursor
    lastOperationId   string  the operation that produced this state

/kitchens/{kitchenId}/events/{operationId}  document id IS the operation id
    ...every ItemEventEntity field...
    serverUpdatedAt   timestamp

/kitchens/{kitchenId}/locations/{locationId}   planned — see §9
```

Three things here were new relative to the rules as first written, and are
now in `firestore.rules` (12 Sep 2026):

1. **`serverUpdatedAt` must equal `request.time`.** The client writes
   `FieldValue.serverTimestamp()`; the rule checks
   `request.resource.data.serverUpdatedAt == request.time`. This is what makes
   the pull cursor a server fact rather than a device clock. Two devices'
   clocks are not comparable; one server's clock is.
2. **Event document id is the operation id.** The rules allow `create` on
   events and never `update`, so writing the same event twice is refused. The
   Android SDK has no create-only write — a batch `set` on an existing event
   is an update and comes back `PERMISSION_DENIED` — so the client cannot tell
   "already there" from "not allowed" by the error alone. It tells them apart
   with one read: if `events/{operationId}` exists, the push was a retry and
   is acknowledged. That read plus the no-update rule is the whole idempotency
   mechanism — no Cloud Function needed.
3. **`lastOperationId` on items**, so the operation that produced a state is
   named, and **`lastClientId`**, the installation that wrote it. On pull a
   device recognises its own write coming back by `lastClientId` — stateless,
   and unaffected by whether the outbox row has already been acknowledged.
   (The rules require the first; the second is informational.)

There is still deliberately **no `list` on `/kitchens`**. The client reads its
own user document, takes `kitchenIds`, and fetches each kitchen by id. (The
user document is not written yet — the personal kitchen id is derived, so
nothing needs it until household sharing.)

---

## 4. The outbox contract

One row per change, written in the same transaction as the row and its event
(`ItemRepositoryImpl.record`). A change cannot exist locally without being
queued, and an entry leaves only on acknowledgement, never on a timestamp.

| Field | Meaning | Who sets it |
|---|---|---|
| `operationId` | Idempotency key. Same id → server no-op. Also the event's `operationId` and the event's remote document id | `record` |
| `entityId` | Item id | `record` |
| `kitchenId` | Which kitchen; rewritten by claim | `record`, `claimLocalData` |
| `actorUid` | Who; **see §7, currently wrong after claim** | `record` |
| `clientId` | This installation | `ClientIdProvider` |
| `clientSequence` | Monotonic per install; push order | `record` |
| `baseRevision` | Item `revision` the change was made against; null for create | `record` |
| `operationType` | `CREATE` / `UPDATE` / `TOMBSTONE` / `RESTORE`; `EVENT_APPEND` is declared but nothing produces it | `record` |
| `occurredAtClient` | Device time the change happened | `record` |
| `payload` | JSON snapshot of the `ItemEntity` **at the moment of the change**, local format, not the wire format | `OutboxPayload.serialise` |
| `attemptCount`, `lastAttemptAt`, `lastError` | Retry bookkeeping | transport |

The payload is a snapshot on purpose: pushing whatever the row says at send time
would collapse several offline edits into the last one. The mapping from the
snapshot to the wire document happens at push time, so the backend contract can
change without rewriting queued rows.

What an operation means to the server:

- **CREATE** — set the item document from the snapshot. If it already exists
  (retry after a lost ack), treat as done.
- **UPDATE** — set the item document from the snapshot, subject to §6 conflict
  handling.
- **TOMBSTONE** — an UPDATE whose snapshot has `isDeleted = true`. Named
  separately so the queue is readable and so a future server can refuse a hard
  delete without inspecting the payload.
- **RESTORE** — an UPDATE that reverses a resolution. The event carries
  `reversesEventId`; the item snapshot carries the restored quantity.

Every operation also appends its event. **Item write and event append are one
Firestore batch**, so the ledger and the row cannot diverge on the server any
more than they can locally.

---

## 5. Push

Runs as a WorkManager unique periodic job (six hours — a backup is not a
chat) plus a unique one-shot whenever the app goes to the background, which is
the natural moment for "what I just did" to go up and needs no coupling to the
write path; the Settings card can also trigger one. Both are constrained to
network-connected. Signed out, the run does nothing, not even a read. Signed
in, `SyncRun` first makes sure the kitchen document exists (the rules look it
up on every write), then pushes, then pulls — the pull happens even if the
push was deferred, because the other device's changes are worth having
regardless — and records the outcome for the card. Only a deferred run asks
WorkManager to retry: a free account is not an error.

```
for each kitchen the session can see:
    batch = outboxDao.peek(kitchenId, 50)          oldest clientSequence first
    for op in batch:
        doc  = wire(op.payload)                     map snapshot → wire fields
        write = firestore.batch()
            .set(items/{op.entityId}, doc + serverUpdatedAt + lastOperationId=op.operationId)
            .create(events/{op.operationId}, event(op))
        result = commit(write)
        on success            → ack(op)
        on PermissionDenied   → if events/{op.operationId} exists → ack(op)   a retry; the server has it
                                else → stop this kitchen             free or lapsed; expected, not an error
        on Transient          → recordFailure, stop, let WorkManager back off
        on Permanent          → recordFailure; after 5 attempts it is "stuck"
```

`ack(op)` is `outboxDao.acknowledge([op.operationId])` and nothing else. The
Android SDK's batch commit returns no server timestamp, so `items.revision`
cannot be stamped here; it is stamped when the write comes back through the
pull listener (§6), which delivers a device's own writes like anyone else's.
Until then `revision` is whatever it was, and `baseRevision` on a follow-up
edit may be stale by one round trip — the conflict check in §6 tolerates
that, because a stale base only means one extra read.

The existence read costs one document read per retry, never per push, so it
is free in the common case.

Stuck entries (`getStuck`, threshold 5) are surfaced in Settings as "N changes
could not be backed up", never silently dropped. The likely cause is a rules
rejection of a malformed document, which is a bug to fix, not a row to lose.

**Order matters within an entity and is preserved by `clientSequence`.** Across
entities it does not, but the queue is pushed in order anyway because it is
simpler and the batches are small.

**First backup is not a replay.** When a kitchen becomes premium for the first
time, the outbox may hold months of operations from before there was anywhere
to send them, many superseded. Bootstrap instead: upload every row, tombstones
included, with `lastOperationId = "bootstrap-{itemId}"`; upload the whole
ledger as events in batches of 500 (Firestore's limit), **recording the count
after each batch** — an event cannot be written twice, so an interrupted upload
resumes from that count rather than repeating, and a batch that landed before
the crash is recognised by one existence read; then drop outbox entries whose
`clientSequence` is at or below what was queued when the upload began. A
change made during the upload has a higher sequence and goes up on its own
afterwards. The count lives in `SyncState` (`SyncPreferences`), per kitchen.

This also bounds the outbox for free users: a kitchen that has never
bootstrapped may be compacted to one entry per entity at any time, because the
ledger — not the outbox — is the history. (Not built; the queue is small.)

---

## 6. Pull

**By cursor, not by time.** Each kitchen keeps two cursors — one for `items`,
one for `events` — each the highest `serverUpdatedAt` applied so far. They are
separate because the two are paged separately, and one shared cursor could
skip whatever the shorter page had not reached. A paged query,
`where serverUpdatedAt > cursor order by serverUpdatedAt limit N`, is run per
sync rather than a live listener: it fits a WorkManager run and is testable on
the JVM; a foreground listener can be layered on later using the same
applier. Each page is applied in one Room transaction and the cursor moves
only after that commits, so a crash mid-page re-delivers rather than skips.

**The cursor is in microseconds.** A Firestore timestamp is seconds plus
nanoseconds; milliseconds would truncate, and a document half a millisecond
past the cursor would be re-fetched on every run forever. Microseconds round
trip through a Long exactly. `revision` is the same value.

Applying a pulled **item**:

```
if doc.lastClientId == this installation
    → stamp local revision = doc.serverUpdatedAt; do not touch the row
      (our own write back from the server: the row already says this, or
      something newer that is still queued; only the ordering value is news)
else if local.revision >= doc.serverUpdatedAt
    → skip; already applied
else
    → itemDao.upsertFromRemote(row with revision = doc.serverUpdatedAt)
```

Applying a pulled **event**: `eventDao.appendAll` with `IGNORE`. The unique
index on `operationId` makes replay free. Events are never compared or
resolved; they are facts.

The pull path writes **no outbox entries and no events of its own.** A pulled
change is something that already happened elsewhere; recording it again would
push it back and double-count it.

### Conflicts

Two devices edit the same item offline. When the second push arrives, the
server document's `lastOperationId` is not what the pusher's `baseRevision`
saw.

- **Row state: last write wins, in server order.** The later push overwrites.
  This can lose one edit to a name or a date — a nuisance, not data loss, and
  explainable to a person. Per-field merge was considered and rejected as far
  more code than the problem justifies.
- **Quantity events are deltas and are rebased, not overwritten.** Both devices
  "use 1" of 3 offline; each snapshot says quantity 2; naive LWW leaves 2 while
  the ledger says 2 were used. The pusher detects the conflict, reads the
  server row, applies its event's delta (`quantity` on a `QUANTITY_USED` /
  `QUANTITY_DISCARDED` event) to the server quantity, and pushes that. The
  ledger already carries the delta, so this is a read and a subtraction, not
  a merge engine. The local row takes the merged answer too — no event, no
  outbox entry, nothing new happened — **and the server's revision**, because
  it now incorporates that state; without that, the pull in the same run
  would see the other device's document as newer and write it over the
  merge. If the delta empties the item it resolves the way the repository
  resolves one: state set, quantity left. Built and proven end to end.
- **Tombstone beats update.** A deleted item stays deleted; a concurrent edit to
  it is dropped. A RESTORE is an explicit later decision and wins over the
  tombstone it reverses.

The client detects a conflict by reading the server document before pushing
a **quantity event** with a non-null `baseRevision`, and comparing the
document's `serverUpdatedAt` with that base (a document last written by this
same installation is never a conflict). One read per quantity event, none for
other edits or creates. Acceptable at the write rates of a kitchen; not
acceptable for a bulk bootstrap, which is why bootstrap is CREATE only.

---

## 7. Sign-in and the claim

`claimLocalData` moves guest rows into `personal-{uid}`. It rewrites
`kitchenId` on items, events and outbox rows, `createdBy`/`lastEditedBy` on
items, and — since 12 Sep 2026 — `actorUid` on events and outbox rows. The
rules require `request.resource.data.actorUid == uid()` on every event create,
so before that fix every event a person recorded before signing in would have
been refused on push and sat in the outbox as "stuck". The person has, by
signing in, said that the guest was them, and the claim now says so
everywhere. A JVM test signs in over guest data and asserts no event, outbox
row or item still names the guest.

One thing the claim deliberately does **not** rewrite: the `kitchenId`,
`createdBy` and `lastEditedBy` **inside** `outbox.payload`, a JSON snapshot
taken before sign-in. The transport must map kitchen and attribution **from
the outbox columns**, never from the payload, so a stale snapshot cannot
reintroduce `"local"` or `"guest"` on the wire. This is a rule for the push
engine, and its test belongs with the push engine.

The cross-account edge case stays: sign out, act, sign in as a different
account, and that account claims the rows. Narrow, and recorded in
`PROGRESS.md`; not made worse by anything here.

---

## 8. Entitlement

`isPremium` lives on the kitchen document because the rules already fetch the
kitchen for membership; checking the owner's profile would double the billed
reads on every write.

Nothing sets it. Play Billing verification needs a trusted server — a Cloud
Function on the purchase notification, or a scheduled reconciliation against
the Play Developer API — and this project has no Cloud Functions yet. Until it
does, every paid path is inert by construction, and the transport must not be
built on the assumption that it can be tested end to end against production:
it is tested against the emulator with `isPremium` set by the test.

The client learns its entitlement by reading its kitchen document, caches the
answer, and re-reads on app start and on `PermissionDenied`. A `false` means the
worker does not push, not that it pushes and fails.

Household size (`memberUids.size() <= 6` when premium, `1` otherwise) is
enforced in rules with no counter.

---

## 9. What is out of scope for the first transport, and why

- **Locations.** `LocationEntity` has `kitchenId` and `updatedAt` but its
  repository never writes an outbox entry, so a renamed shelf would not sync.
  Fix needs an `entityKind` column on `outbox` — `Migration(3, 4)`, additive —
  and a `kitchens/{id}/locations` rule. Items sync first; a location the other
  device has not seen resolves to "no location", which the UI already renders.
- **Invites.** Household sharing is a kitchen with more members. The invite
  flow (a code, a pending-member document, owner acceptance) is its own rule
  set and its own screen; nothing in §4–§6 changes for it because access is
  already by `kitchenId`.
- **Entitlements collection and aiJobs.** Named in the target model; neither
  has a consumer yet.

Deploying rules now would mean deploying again for each of these. **Decision,
12 Sep 2026: rules deploy alongside the transport, not before.**

---

## 10. Testing

- **Engine on the JVM.** The push/pull logic takes `OutboxDao`, `ItemDao`,
  `ItemEventDao` and a `RemoteStore` interface; `FakeDaos.kt` already exists.
  Tests: ack removes exactly the acknowledged op;
  `PermissionDenied` with the event already present acks; `PermissionDenied`
  with no event stops without touching attempt counts; a Transient failure increments and stops; five Permanent failures
  make an op stuck; a pulled own-write is skipped; a pulled newer row is
  applied; a pulled older row is not; a replayed event inserts once; a quantity
  conflict rebases.
- **Rules on the emulator.** `rules.test.mjs` covers `serverUpdatedAt ==
  request.time` (absent, client-supplied timestamp, numeric), event id ==
  operation id, `lastOperationId` presence, and that a retried event is
  refused rather than duplicated. Keep the habit: weaken a rule, watch
  exactly its test fail.
- **One end-to-end — built and passing.** `SyncEndToEndTest`: engine plus
  rules together, two in-memory devices with their own client ids, one
  account and one kitchen on the Firestore + Auth emulators, the kitchen
  flagged premium through the emulator's owner endpoint (standing in for the
  Cloud Function). Both use one of three offline; after three runs both
  shelves say 1 and both ledgers say 2 used; nothing is left queued. A second
  test backs a never-synced kitchen up whole and checks the other device has
  every row and every event. Run with `npm run test:sync-e2e` (`:win` on
  Windows). It skips itself when the emulators are not up, so the ordinary
  connected run does not need them. It uses a private `demo-freshtrack`
  FirebaseApp, so it cannot reach production; and debug builds carry a
  network-security override allowing plain HTTP to `10.0.2.2` only — the
  emulator host — which release builds do not have.

---

## 11. Build order

1. ~~Fix the claim (§7).~~ Done, 12 Sep 2026.
2. ~~Rules: add `serverUpdatedAt`, event-id-is-operation-id,
   `lastOperationId`; extend tests.~~ Done, 12 Sep 2026 — 54 tests, each new
   clause verified by weakening it and watching only its tests fail.
3. ~~`RemoteStore` interface for the new shape; Firestore implementation;
   retire `RemoteProductStore` and the `/pantries` constants.~~ Done,
   12 Sep 2026. `WireFormat` is the mapping and is where the §7 rule lives:
   the kitchen is the path, never a field, and guest attribution becomes the
   outbox row's actor.
4. ~~Push engine, JVM-tested. Bootstrap path included.~~ Done, 12 Sep 2026.
   `OutboxPusher`, 20 tests; the once-per-run guard and the resume count were
   each verified by removing them and watching exactly their tests fail.
5. ~~Pull engine, JVM-tested.~~ Done, 12 Sep 2026. `RemoteChangeApplier`,
   11 tests; disabling own-write recognition fails exactly the test that
   shows a local edit being regressed.
6. ~~WorkManager wiring; Settings card shows pending count and stuck
   count.~~ Done, 12 Sep 2026. `SyncRun` (6 JVM tests), `SyncWorker`, the
   card. Verified on Pixel_35 signed out: Settings resolves the graph, the
   card reads correctly, backgrounding the app runs the worker to SUCCESS.
7. ~~End-to-end test on the emulator.~~ Done, 12 Sep 2026, and it found
   its first bug before it ran (the merged row's revision, above).
8. Deploy rules. Then, and only then, the Play Billing server side.

---

## Open decisions

- **Cursor granularity.** `serverUpdatedAt` is a timestamp; two writes in the
  same millisecond are possible under bootstrap. The listener uses `>` and
  events are deduplicated by id, so the worst case is one re-applied item,
  which is idempotent. Acceptable; noted.
- **`EVENT_APPEND`.** Declared, unused. Either remove it or give it the one job
  it plausibly has — an event with no row change, which the current write path
  never produces. Lean: remove.
- **Bootstrap size.** A kitchen of a few hundred items and a few thousand
  events is a few thousand writes once. Fine. Ten thousand events is not
  unthinkable for a long-standing household; batch in 500s and resume by
  cursor if interrupted.
