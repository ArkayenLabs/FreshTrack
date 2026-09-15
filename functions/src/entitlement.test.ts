import assert from "node:assert/strict";
import { after, before, beforeEach, describe, it } from "node:test";
import { initializeApp, deleteApp, App } from "firebase-admin/app";
import { Firestore, getFirestore } from "firebase-admin/firestore";
import {
  bindPurchase,
  decide,
  ENTITLEMENTS,
  EntitlementError,
  KITCHENS,
  personalKitchenId,
  reconcile,
  refreshByToken,
} from "./entitlement";
import { PlayApi, PlayNotFound, PlaySubscription } from "./play";

// Runs against the Firestore emulator: `npm run test:functions` from the
// repository root wraps this in `firebase emulators:exec`, which sets
// FIRESTORE_EMULATOR_HOST. Play is faked; each test states what Play would
// say and checks what ends up in the two documents the app reads.

const PROJECT = "demo-freshtrack";
const ALICE = "alice-uid";
const BOB = "bob-uid";
const NOW = Date.parse("2026-09-15T12:00:00Z");
const DAY = 24 * 60 * 60 * 1000;
const iso = (ms: number) => new Date(ms).toISOString();

const subscription = (overrides: Partial<PlaySubscription> = {}): PlaySubscription => ({
  subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
  acknowledgementState: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
  lineItems: [{ productId: "premium", basePlanId: "monthly", expiryTime: iso(NOW + 30 * DAY) }],
  ...overrides,
});

class FakePlay implements PlayApi {
  readonly subscriptions = new Map<string, PlaySubscription>();
  readonly acknowledged: Array<{ token: string; productId: string }> = [];

  async getSubscription(token: string): Promise<PlaySubscription> {
    const sub = this.subscriptions.get(token);
    if (!sub) throw new PlayNotFound(token);
    return sub;
  }

  async acknowledge(token: string, productId: string): Promise<void> {
    this.acknowledged.push({ token, productId });
  }
}

let app: App;
let db: Firestore;
let play: FakePlay;

before(() => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, "FIRESTORE_EMULATOR_HOST is not set; run through firebase emulators:exec");
  app = initializeApp({ projectId: PROJECT });
  db = getFirestore(app);
});

after(async () => {
  await deleteApp(app);
});

beforeEach(async () => {
  const res = await fetch(
    `http://${process.env.FIRESTORE_EMULATOR_HOST}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`,
    { method: "DELETE" }
  );
  assert.equal(res.status, 200);
  play = new FakePlay();
});

const kitchen = async (uid: string) => (await db.collection(KITCHENS).doc(personalKitchenId(uid)).get()).data();
const entitlement = async (uid: string) => (await db.collection(ENTITLEMENTS).doc(uid).get()).data();

describe("decide", () => {
  it("ACTIVE and IN_GRACE_PERIOD are premium", () => {
    assert.equal(decide(subscription(), NOW).isPremium, true);
    assert.equal(decide(subscription({ subscriptionState: "SUBSCRIPTION_STATE_IN_GRACE_PERIOD" }), NOW).isPremium, true);
  });

  it("CANCELED keeps premium until the paid period ends, and not a moment past it", () => {
    const canceled = subscription({ subscriptionState: "SUBSCRIPTION_STATE_CANCELED" });
    assert.equal(decide(canceled, NOW).isPremium, true);
    assert.equal(decide(canceled, NOW + 30 * DAY).isPremium, false);
  });

  it("ON_HOLD, PAUSED, EXPIRED and PENDING are not premium", () => {
    for (const state of ["ON_HOLD", "PAUSED", "EXPIRED", "PENDING", "UNSPECIFIED"]) {
      assert.equal(decide(subscription({ subscriptionState: `SUBSCRIPTION_STATE_${state}` }), NOW).isPremium, false, state);
    }
  });

  it("a product this project does not sell grants nothing, however active", () => {
    const other = subscription({ lineItems: [{ productId: "something-else", expiryTime: iso(NOW + DAY) }] });
    const d = decide(other, NOW);
    assert.equal(d.isPremium, false);
    assert.equal(d.productId, null);
  });

  it("carries expiry as epoch millis and the base plan for the receipt", () => {
    const d = decide(subscription(), NOW);
    assert.equal(d.expiryTime, NOW + 30 * DAY);
    assert.equal(d.basePlanId, "monthly");
    assert.equal(d.productId, "premium");
  });
});

describe("bindPurchase", () => {
  it("a first purchase creates the personal kitchen in the shape the app would, flagged premium", async () => {
    play.subscriptions.set("tok-1", subscription());
    const decision = await bindPurchase(db, play, ALICE, "tok-1", NOW);
    assert.equal(decision.isPremium, true);

    const k = await kitchen(ALICE);
    assert.deepEqual(
      { name: k?.name, ownerUid: k?.ownerUid, memberUids: k?.memberUids, isPremium: k?.isPremium, createdAt: k?.createdAt },
      { name: "My kitchen", ownerUid: ALICE, memberUids: [ALICE], isPremium: true, createdAt: NOW }
    );
    const e = await entitlement(ALICE);
    assert.equal(e?.purchaseToken, "tok-1");
    assert.equal(e?.isPremium, true);
    assert.equal(e?.state, "SUBSCRIPTION_STATE_ACTIVE");
    assert.equal(e?.expiryTime, NOW + 30 * DAY);
    assert.ok(e?.updatedAt, "updatedAt is stamped");
  });

  it("an existing kitchen only gains the flag; its name and members are left alone", async () => {
    await db.collection(KITCHENS).doc(personalKitchenId(ALICE)).set({
      name: "Flat 3",
      ownerUid: ALICE,
      memberUids: [ALICE],
      createdAt: 1,
    });
    play.subscriptions.set("tok-1", subscription());
    await bindPurchase(db, play, ALICE, "tok-1", NOW);
    const k = await kitchen(ALICE);
    assert.equal(k?.name, "Flat 3");
    assert.deepEqual(k?.memberUids, [ALICE]);
    assert.equal(k?.createdAt, 1);
    assert.equal(k?.isPremium, true);
  });

  it("acknowledges a purchase Play is still waiting on, exactly once, and not one already acknowledged", async () => {
    play.subscriptions.set("tok-1", subscription({ acknowledgementState: "ACKNOWLEDGEMENT_STATE_PENDING" }));
    await bindPurchase(db, play, ALICE, "tok-1", NOW);
    assert.deepEqual(play.acknowledged, [{ token: "tok-1", productId: "premium" }]);

    play.subscriptions.set("tok-2", subscription());
    await bindPurchase(db, play, ALICE, "tok-2", NOW);
    assert.equal(play.acknowledged.length, 1);
  });

  it("a purchase that is not premium is bound but not acknowledged, and the flag is false", async () => {
    play.subscriptions.set("tok-1", subscription({
      subscriptionState: "SUBSCRIPTION_STATE_PENDING",
      acknowledgementState: "ACKNOWLEDGEMENT_STATE_PENDING",
    }));
    const decision = await bindPurchase(db, play, ALICE, "tok-1", NOW);
    assert.equal(decision.isPremium, false);
    assert.equal(play.acknowledged.length, 0);
    assert.equal((await kitchen(ALICE))?.isPremium, false);
    assert.equal((await entitlement(ALICE))?.purchaseToken, "tok-1");
  });

  it("a token already bound to another account is refused and nothing is written", async () => {
    play.subscriptions.set("tok-1", subscription());
    await bindPurchase(db, play, ALICE, "tok-1", NOW);

    await assert.rejects(bindPurchase(db, play, BOB, "tok-1", NOW), (e: unknown) =>
      e instanceof EntitlementError && e.code === "token-bound-elsewhere"
    );
    assert.equal(await kitchen(BOB), undefined);
    assert.equal(await entitlement(BOB), undefined);
    assert.equal((await kitchen(ALICE))?.isPremium, true);
  });

  it("the same account may re-bind its own token, as after a reinstall", async () => {
    play.subscriptions.set("tok-1", subscription());
    await bindPurchase(db, play, ALICE, "tok-1", NOW);
    await bindPurchase(db, play, ALICE, "tok-1", NOW + DAY);
    assert.equal((await kitchen(ALICE))?.isPremium, true);
    assert.equal((await entitlement(ALICE))?.purchaseToken, "tok-1");
  });

  it("a subscription for a product this project does not sell is refused and nothing is written", async () => {
    play.subscriptions.set("tok-1", subscription({ lineItems: [{ productId: "other" }] }));
    await assert.rejects(bindPurchase(db, play, ALICE, "tok-1", NOW), (e: unknown) =>
      e instanceof EntitlementError && e.code === "unknown-product"
    );
    assert.equal(await kitchen(ALICE), undefined);
  });

  it("a token Play has never seen propagates PlayNotFound and writes nothing", async () => {
    await assert.rejects(bindPurchase(db, play, ALICE, "nope", NOW), PlayNotFound);
    assert.equal(await kitchen(ALICE), undefined);
  });
});

describe("refreshByToken", () => {
  beforeEach(async () => {
    play.subscriptions.set("tok-1", subscription());
    await bindPurchase(db, play, ALICE, "tok-1", NOW);
  });

  it("Play saying EXPIRED turns the flag off on both documents", async () => {
    play.subscriptions.set("tok-1", subscription({ subscriptionState: "SUBSCRIPTION_STATE_EXPIRED" }));
    const r = await refreshByToken(db, play, "tok-1", NOW + 31 * DAY);
    assert.equal(r.outcome, "updated");
    assert.equal((await kitchen(ALICE))?.isPremium, false);
    const e = await entitlement(ALICE);
    assert.equal(e?.isPremium, false);
    assert.equal(e?.state, "SUBSCRIPTION_STATE_EXPIRED");
  });

  it("a renewal moves the expiry forward and keeps the flag on", async () => {
    play.subscriptions.set("tok-1", subscription({
      lineItems: [{ productId: "premium", basePlanId: "monthly", expiryTime: iso(NOW + 60 * DAY) }],
    }));
    await refreshByToken(db, play, "tok-1", NOW + 30 * DAY);
    assert.equal((await kitchen(ALICE))?.isPremium, true);
    assert.equal((await entitlement(ALICE))?.expiryTime, NOW + 60 * DAY);
  });

  it("a token nobody has bound is reported and nothing is written", async () => {
    play.subscriptions.set("tok-stranger", subscription());
    const r = await refreshByToken(db, play, "tok-stranger", NOW);
    assert.equal(r.outcome, "unknown-token");
    const all = await db.collection(ENTITLEMENTS).get();
    assert.equal(all.size, 1);
    assert.equal(all.docs[0].id, ALICE);
  });

  it("an upgrade arrives as a new token naming the old one, and the entitlement follows it", async () => {
    play.subscriptions.set("tok-2", subscription({
      linkedPurchaseToken: "tok-1",
      lineItems: [{ productId: "premium", basePlanId: "annual", expiryTime: iso(NOW + 365 * DAY) }],
    }));
    const r = await refreshByToken(db, play, "tok-2", NOW);
    assert.equal(r.outcome, "updated");
    const e = await entitlement(ALICE);
    assert.equal(e?.purchaseToken, "tok-2");
    assert.equal(e?.basePlanId, "annual");
    assert.equal((await kitchen(ALICE))?.isPremium, true);
  });

  it("a token Play has forgotten is treated as expired", async () => {
    play.subscriptions.delete("tok-1");
    const r = await refreshByToken(db, play, "tok-1", NOW + 400 * DAY);
    assert.equal(r.outcome, "updated");
    assert.equal((await kitchen(ALICE))?.isPremium, false);
    assert.equal((await entitlement(ALICE))?.state, "GONE");
  });

  it("a kitchen deleted since the purchase is recreated rather than left missing", async () => {
    await db.collection(KITCHENS).doc(personalKitchenId(ALICE)).delete();
    await refreshByToken(db, play, "tok-1", NOW);
    const k = await kitchen(ALICE);
    assert.equal(k?.ownerUid, ALICE);
    assert.equal(k?.isPremium, true);
  });
});

describe("reconcile", () => {
  it("re-checks only premium entitlements whose paid period is over, and revokes what Play no longer backs", async () => {
    // Alice: lapsed and Play agrees it expired.
    play.subscriptions.set("tok-a", subscription({ lineItems: [{ productId: "premium", expiryTime: iso(NOW - DAY) }] }));
    await bindPurchase(db, play, ALICE, "tok-a", NOW - 10 * DAY);
    play.subscriptions.set("tok-a", subscription({
      subscriptionState: "SUBSCRIPTION_STATE_EXPIRED",
      lineItems: [{ productId: "premium", expiryTime: iso(NOW - DAY) }],
    }));
    // Bob: our copy says lapsed, but Play renewed him and we missed the notification.
    play.subscriptions.set("tok-b", subscription({ lineItems: [{ productId: "premium", expiryTime: iso(NOW - DAY) }] }));
    await bindPurchase(db, play, BOB, "tok-b", NOW - 10 * DAY);
    play.subscriptions.set("tok-b", subscription({ lineItems: [{ productId: "premium", expiryTime: iso(NOW + 29 * DAY) }] }));
    // Carol: still inside her period; must not be touched.
    play.subscriptions.set("tok-c", subscription());
    await bindPurchase(db, play, "carol-uid", "tok-c", NOW);
    play.subscriptions.delete("tok-c");

    const result = await reconcile(db, play, NOW);

    assert.deepEqual(result, { checked: 2, revoked: 1 });
    assert.equal((await kitchen(ALICE))?.isPremium, false);
    assert.equal((await kitchen(BOB))?.isPremium, true);
    assert.equal((await entitlement(BOB))?.expiryTime, NOW + 29 * DAY);
    assert.equal((await kitchen("carol-uid"))?.isPremium, true);
  });

  it("an entitlement already off is not re-checked", async () => {
    play.subscriptions.set("tok-a", subscription({
      subscriptionState: "SUBSCRIPTION_STATE_EXPIRED",
      lineItems: [{ productId: "premium", expiryTime: iso(NOW - DAY) }],
    }));
    await bindPurchase(db, play, ALICE, "tok-a", NOW);
    play.subscriptions.delete("tok-a");
    assert.deepEqual(await reconcile(db, play, NOW), { checked: 0, revoked: 0 });
  });
});
