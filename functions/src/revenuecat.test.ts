import assert from "node:assert/strict";
import { after, before, beforeEach, describe, it } from "node:test";
import { initializeApp, deleteApp, App } from "firebase-admin/app";
import { Firestore, getFirestore } from "firebase-admin/firestore";
import { ENTITLEMENTS, KITCHENS, personalKitchenId, reconcile } from "./entitlement";
import { PlayApi, PlayNotFound, PlaySubscription } from "./play";
import {
  decideFromSubscriber,
  isAuthorized,
  refreshFromRevenueCat,
  RevenueCatApi,
  Subscriber,
  subjectsOf,
} from "./revenuecat";

// Same harness as entitlement.test.ts: the Firestore emulator, RevenueCat
// faked. Each test states what RevenueCat's record of the customer says and
// checks the two documents the app reads.

const PROJECT = "demo-freshtrack";
const ALICE = "alice-uid";
const BOB = "bob-uid";
const NOW = Date.parse("2026-09-19T12:00:00Z");
const DAY = 24 * 60 * 60 * 1000;
const iso = (ms: number) => new Date(ms).toISOString();

type Sub = Subscriber["subscriptions"][string];

/** A Play monthly, mid-period, nothing wrong with it. */
const subscriber = (sub: Partial<Sub> = {}, productId = "premium:monthly", ent: Partial<Subscriber["entitlements"][string]> = {}): Subscriber => ({
  entitlements: {
    premium: {
      expires_date: iso(NOW + 20 * DAY),
      grace_period_expires_date: null,
      product_identifier: productId,
      purchase_date: iso(NOW - 10 * DAY),
      ...ent,
    },
  },
  subscriptions: {
    [productId]: {
      expires_date: iso(NOW + 20 * DAY),
      grace_period_expires_date: null,
      unsubscribe_detected_at: null,
      billing_issues_detected_at: null,
      refunded_at: null,
      auto_resume_date: null,
      store: "play_store",
      is_sandbox: false,
      period_type: "normal",
      ...sub,
    },
  },
});

const nobody: Subscriber = { entitlements: {}, subscriptions: {} };

class FakeRevenueCat implements RevenueCatApi {
  readonly customers = new Map<string, Subscriber>();
  readonly asked: string[] = [];
  async getSubscriber(appUserId: string): Promise<Subscriber> {
    this.asked.push(appUserId);
    // Get-or-create: an unknown id is a customer with nothing.
    return this.customers.get(appUserId) ?? nobody;
  }
}

class NoPlay implements PlayApi {
  async getSubscription(token: string): Promise<PlaySubscription> {
    throw new PlayNotFound(token);
  }
  async acknowledge(): Promise<void> {}
}

let app: App;
let db: Firestore;
let rc: FakeRevenueCat;

before(() => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, "FIRESTORE_EMULATOR_HOST is not set; run through firebase emulators:exec");
  app = initializeApp({ projectId: PROJECT }, "revenuecat-tests");
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
  rc = new FakeRevenueCat();
});

const kitchen = async (uid: string) => (await db.collection(KITCHENS).doc(personalKitchenId(uid)).get()).data();
const entitlement = async (uid: string) => (await db.collection(ENTITLEMENTS).doc(uid).get()).data();

describe("decideFromSubscriber", () => {
  it("an active subscription is premium, with the store, plan and expiry carried for the receipt", () => {
    const d = decideFromSubscriber(subscriber(), NOW);
    assert.equal(d.isPremium, true);
    assert.equal(d.state, "ACTIVE");
    assert.equal(d.productId, "premium:monthly");
    assert.equal(d.basePlanId, "monthly");
    assert.equal(d.store, "PLAY_STORE");
    assert.equal(d.expiryTime, NOW + 20 * DAY);
    assert.equal(d.isSandbox, false);
  });

  it("cancelled keeps premium until the paid period ends, and not a moment past it", () => {
    const cancelled = subscriber({ unsubscribe_detected_at: iso(NOW - DAY) });
    assert.equal(decideFromSubscriber(cancelled, NOW).state, "CANCELLED");
    assert.equal(decideFromSubscriber(cancelled, NOW).isPremium, true);
    assert.equal(decideFromSubscriber(cancelled, NOW + 20 * DAY).isPremium, false);
    assert.equal(decideFromSubscriber(cancelled, NOW + 20 * DAY).state, "EXPIRED");
  });

  it("a billing issue is premium for as long as the store gives grace, from either record", () => {
    const expired = { expires_date: iso(NOW - DAY) };
    const onSub = subscriber({ ...expired, billing_issues_detected_at: iso(NOW - DAY), grace_period_expires_date: iso(NOW + 6 * DAY) }, "premium:monthly", expired);
    assert.deepEqual([decideFromSubscriber(onSub, NOW).isPremium, decideFromSubscriber(onSub, NOW).state], [true, "GRACE"]);
    const onEnt = subscriber({ ...expired, billing_issues_detected_at: iso(NOW - DAY) }, "premium:monthly", { ...expired, grace_period_expires_date: iso(NOW + 6 * DAY) });
    assert.equal(decideFromSubscriber(onEnt, NOW).isPremium, true);
    assert.equal(decideFromSubscriber(onEnt, NOW + 7 * DAY).isPremium, false);
  });

  it("a refund is off at once, even inside the paid period", () => {
    const d = decideFromSubscriber(subscriber({ refunded_at: iso(NOW - 1000) }), NOW);
    assert.equal(d.isPremium, false);
    assert.equal(d.state, "REFUNDED");
  });

  it("a paused Play subscription past its period is PAUSED, not EXPIRED", () => {
    const expired = { expires_date: iso(NOW - DAY) };
    const paused = subscriber({ ...expired, auto_resume_date: iso(NOW + 30 * DAY) }, "premium:monthly", expired);
    assert.equal(decideFromSubscriber(paused, NOW).state, "PAUSED");
    assert.equal(decideFromSubscriber(paused, NOW).isPremium, false);
  });

  it("an App Store product has no base plan and names its store", () => {
    const d = decideFromSubscriber(subscriber({ store: "app_store", is_sandbox: true }, "com.arkayenlabs.goodbefore.annual"), NOW);
    assert.equal(d.basePlanId, null);
    assert.equal(d.store, "APP_STORE");
    assert.equal(d.isSandbox, true);
    assert.equal(d.isPremium, true);
  });

  it("a customer with no premium entitlement is NONE", () => {
    const d = decideFromSubscriber(nobody, NOW);
    assert.deepEqual(d, { isPremium: false, state: "NONE", productId: null, basePlanId: null, expiryTime: null, store: null, isSandbox: false });
  });

  it("an entitlement under another name grants nothing", () => {
    const other: Subscriber = {
      entitlements: { pro: { expires_date: iso(NOW + DAY), product_identifier: "premium:monthly", purchase_date: iso(NOW) } },
      subscriptions: subscriber().subscriptions,
    };
    assert.equal(decideFromSubscriber(other, NOW).isPremium, false);
  });
});

describe("refreshFromRevenueCat", () => {
  it("a first purchase creates the personal kitchen in the shape the app would, flagged premium, with a RevenueCat receipt", async () => {
    rc.customers.set(ALICE, subscriber());
    const result = await refreshFromRevenueCat(db, rc, ALICE, NOW);
    assert.equal(result.outcome, "updated");

    const k = await kitchen(ALICE);
    assert.deepEqual(
      { name: k?.name, ownerUid: k?.ownerUid, memberUids: k?.memberUids, isPremium: k?.isPremium, createdAt: k?.createdAt },
      { name: "My kitchen", ownerUid: ALICE, memberUids: [ALICE], isPremium: true, createdAt: NOW }
    );
    const e = await entitlement(ALICE);
    assert.equal(e?.isPremium, true);
    assert.equal(e?.state, "ACTIVE");
    assert.equal(e?.store, "PLAY_STORE");
    assert.equal(e?.basePlanId, "monthly");
    assert.equal(e?.expiryTime, NOW + 20 * DAY);
    assert.equal(e?.source, "revenuecat");
    assert.equal(e?.purchaseToken, undefined);
    assert.ok(e?.updatedAt, "updatedAt is stamped");
  });

  it("an existing kitchen only gains the flag; its name and members are left alone", async () => {
    await db.collection(KITCHENS).doc(personalKitchenId(ALICE)).set({ name: "Flat 3", ownerUid: ALICE, memberUids: [ALICE, BOB], createdAt: 1 });
    rc.customers.set(ALICE, subscriber());
    await refreshFromRevenueCat(db, rc, ALICE, NOW);
    const k = await kitchen(ALICE);
    assert.equal(k?.name, "Flat 3");
    assert.deepEqual(k?.memberUids, [ALICE, BOB]);
    assert.equal(k?.isPremium, true);
  });

  it("an expiry turns the flag off and the receipt says why", async () => {
    rc.customers.set(ALICE, subscriber());
    await refreshFromRevenueCat(db, rc, ALICE, NOW);
    rc.customers.set(ALICE, subscriber({ expires_date: iso(NOW - DAY) }, "premium:monthly", { expires_date: iso(NOW - DAY) }));
    const result = await refreshFromRevenueCat(db, rc, ALICE, NOW);
    assert.equal(result.outcome, "updated");
    assert.equal((await kitchen(ALICE))?.isPremium, false);
    assert.equal((await entitlement(ALICE))?.state, "EXPIRED");
  });

  it("a free user with no documents is left without any — no kitchen is conjured", async () => {
    const result = await refreshFromRevenueCat(db, rc, ALICE, NOW);
    assert.equal(result.outcome, "nothing-to-record");
    assert.equal(await kitchen(ALICE), undefined);
    assert.equal(await entitlement(ALICE), undefined);
  });

  it("a free user who has synced gets an explicit off, not silence", async () => {
    await db.collection(KITCHENS).doc(personalKitchenId(ALICE)).set({ name: "My kitchen", ownerUid: ALICE, memberUids: [ALICE], createdAt: 1, isPremium: true });
    const result = await refreshFromRevenueCat(db, rc, ALICE, NOW);
    assert.equal(result.outcome, "updated");
    assert.equal((await kitchen(ALICE))?.isPremium, false);
    assert.equal((await entitlement(ALICE))?.state, "NONE");
  });

  it("the Play reconciler leaves a RevenueCat receipt alone", async () => {
    rc.customers.set(ALICE, subscriber({ expires_date: iso(NOW - DAY) }, "premium:monthly", { expires_date: iso(NOW - DAY) }));
    // Written while still inside the period, so it reads premium with a past expiry by the time reconcile runs.
    await refreshFromRevenueCat(db, rc, ALICE, NOW - 2 * DAY);
    assert.equal((await entitlement(ALICE))?.isPremium, true);
    const result = await reconcile(db, new NoPlay(), NOW);
    assert.deepEqual(result, { checked: 0, revoked: 0 });
    assert.equal((await kitchen(ALICE))?.isPremium, true, "untouched: RevenueCat owns this one");
  });
});

describe("subjectsOf", () => {
  it("a lifecycle event names its one app user id", () => {
    assert.deepEqual(subjectsOf({ type: "INITIAL_PURCHASE", app_user_id: ALICE }), [ALICE]);
    assert.deepEqual(subjectsOf({ type: "EXPIRATION", app_user_id: ALICE }), [ALICE]);
  });

  it("a transfer names both sides, once each", () => {
    assert.deepEqual(subjectsOf({ type: "TRANSFER", transferred_from: [ALICE, BOB], transferred_to: [BOB] }), [ALICE, BOB]);
  });

  it("a test event names nobody", () => {
    assert.deepEqual(subjectsOf({ type: "TEST", app_user_id: ALICE }), []);
  });

  it("an anonymous id is dropped: a guest cannot buy and has no kitchen", () => {
    assert.deepEqual(subjectsOf({ type: "INITIAL_PURCHASE", app_user_id: "$RCAnonymousID:abc" }), []);
    assert.deepEqual(subjectsOf({ type: "TRANSFER", transferred_from: ["$RCAnonymousID:abc"], transferred_to: [ALICE] }), [ALICE]);
  });
});

describe("isAuthorized", () => {
  it("only the exact configured value passes", () => {
    assert.equal(isAuthorized("Bearer s3cret", "Bearer s3cret"), true);
    assert.equal(isAuthorized("Bearer s3cret ", "Bearer s3cret"), false);
    assert.equal(isAuthorized("Bearer S3CRET", "Bearer s3cret"), false);
    assert.equal(isAuthorized(undefined, "Bearer s3cret"), false);
    assert.equal(isAuthorized("", ""), false, "an unset secret authorizes nobody");
  });
});
