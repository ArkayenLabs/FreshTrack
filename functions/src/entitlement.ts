import { FieldValue, Firestore, Transaction } from "firebase-admin/firestore";
import { PlayApi, PlayNotFound, PlaySubscription, PREMIUM_PRODUCT_IDS } from "./play";

// The only code anywhere that writes `kitchens/{id}.isPremium`. The rules
// refuse every client write to it, so a purchase becomes an entitlement here
// or not at all. Everything below takes Firestore and a PlayApi as arguments
// so it runs on the emulator with Play faked.

export const KITCHENS = "kitchens";
export const ENTITLEMENTS = "entitlements";

/** Mirrors `SyncRun.DEFAULT_KITCHEN_NAME` and `personalKitchenId` in the app. */
export const DEFAULT_KITCHEN_NAME = "My kitchen";
export const personalKitchenId = (uid: string) => `personal-${uid}`;

/**
 * What Play's word means for the flag. `state` is Play's own
 * `subscriptionState`, kept so Settings can say "cancels on …" rather than
 * just on/off; `GONE` is ours, for a token Play no longer knows.
 */
export interface Decision {
  isPremium: boolean;
  state: string;
  productId: string | null;
  basePlanId: string | null;
  /** Epoch millis, matching the app's `createdAt` convention; null if Play gave none. */
  expiryTime: number | null;
}

/** Shape of `entitlements/{uid}`: the receipt behind the kitchen's flag. */
export interface EntitlementDoc extends Decision {
  purchaseToken: string;
  updatedAt: FirebaseFirestore.Timestamp;
}

export class EntitlementError extends Error {
  constructor(readonly code: "unknown-product" | "token-bound-elsewhere") {
    super(code);
  }
}

/**
 * Pure. ACTIVE and IN_GRACE_PERIOD are premium on Play's say-so. CANCELED
 * keeps access until the paid period runs out — that is Play's contract
 * with the buyer, and the app honours it. Everything else (ON_HOLD, PAUSED,
 * EXPIRED, PENDING, a revocation, which Play reports as EXPIRED) is off.
 */
export function decide(sub: PlaySubscription, now: number): Decision {
  const line = sub.lineItems.find((l) => PREMIUM_PRODUCT_IDS.has(l.productId));
  const parsed = line?.expiryTime ? Date.parse(line.expiryTime) : NaN;
  const base = {
    state: sub.subscriptionState,
    productId: line?.productId ?? null,
    basePlanId: line?.basePlanId ?? null,
    expiryTime: Number.isNaN(parsed) ? null : parsed,
  };
  if (!line) return { ...base, isPremium: false };
  switch (sub.subscriptionState) {
    case "SUBSCRIPTION_STATE_ACTIVE":
    case "SUBSCRIPTION_STATE_IN_GRACE_PERIOD":
      return { ...base, isPremium: true };
    case "SUBSCRIPTION_STATE_CANCELED":
      return { ...base, isPremium: base.expiryTime !== null && base.expiryTime > now };
    default:
      return { ...base, isPremium: false };
  }
}

const gone = (previous: Partial<Decision>): Decision => ({
  isPremium: false,
  state: "GONE",
  productId: previous.productId ?? null,
  basePlanId: previous.basePlanId ?? null,
  expiryTime: previous.expiryTime ?? null,
});

/**
 * Kitchen flag and entitlement receipt, in one transaction, so a reader can
 * never see one without the other. The receipt is the decision plus whatever
 * identifies the purchase to its source — a Play token here, a RevenueCat
 * `source` marker in `revenuecat.ts`. Creates the personal kitchen if the user
 * has never synced, in the same shape `FirestoreRemoteStore.ensureKitchenExists`
 * would — a bare `{isPremium}` document would satisfy that method's existence
 * check and leave a kitchen with no members that the rules deny outright.
 */
export function writeEntitlement(
  tx: Transaction,
  db: Firestore,
  uid: string,
  receipt: Decision & Record<string, unknown>,
  kitchenExists: boolean,
  now: number
): void {
  const kitchenRef = db.collection(KITCHENS).doc(personalKitchenId(uid));
  if (kitchenExists) {
    tx.update(kitchenRef, { isPremium: receipt.isPremium });
  } else {
    tx.set(kitchenRef, {
      name: DEFAULT_KITCHEN_NAME,
      ownerUid: uid,
      memberUids: [uid],
      createdAt: now,
      isPremium: receipt.isPremium,
    });
  }
  tx.set(db.collection(ENTITLEMENTS).doc(uid), {
    ...receipt,
    updatedAt: FieldValue.serverTimestamp(),
  });
}

async function findByToken(
  db: Firestore,
  purchaseToken: string,
  tx?: Transaction
): Promise<FirebaseFirestore.QueryDocumentSnapshot | null> {
  const q = db.collection(ENTITLEMENTS).where("purchaseToken", "==", purchaseToken).limit(1);
  const snap = tx ? await tx.get(q) : await q.get();
  return snap.empty ? null : snap.docs[0];
}

/**
 * The app just bought something and says so. Believe Play, not the app:
 * fetch the subscription, decide, acknowledge if Play is still waiting for
 * that (an unacknowledged purchase is refunded after three days), and bind
 * the token to this uid. A token already bound to a different account is
 * refused — one purchase is one account's.
 */
export async function bindPurchase(
  db: Firestore,
  play: PlayApi,
  uid: string,
  purchaseToken: string,
  now: number
): Promise<Decision> {
  const sub = await play.getSubscription(purchaseToken);
  const decision = decide(sub, now);
  if (decision.productId === null) throw new EntitlementError("unknown-product");

  if (decision.isPremium && sub.acknowledgementState === "ACKNOWLEDGEMENT_STATE_PENDING") {
    await play.acknowledge(purchaseToken, decision.productId);
  }

  await db.runTransaction(async (tx) => {
    const bound = await findByToken(db, purchaseToken, tx);
    if (bound !== null && bound.id !== uid) throw new EntitlementError("token-bound-elsewhere");
    const kitchen = await tx.get(db.collection(KITCHENS).doc(personalKitchenId(uid)));
    writeEntitlement(tx, db, uid, { ...decision, purchaseToken }, kitchen.exists, now);
  });
  return decision;
}

export type RefreshOutcome =
  | { outcome: "unknown-token" }
  | { outcome: "updated"; uid: string; decision: Decision };

/**
 * Play says something changed for a token. Re-read the subscription rather
 * than trusting the notification, find whose it is, and rewrite the flag.
 *
 * An upgrade or downgrade arrives as a new token whose Play record names
 * the old one in `linkedPurchaseToken`; the entitlement moves to the new
 * token. A token nobody has bound is ignored: there is no uid to attach it
 * to, and the app will present it through `verifyPurchase` on its next
 * `queryPurchases`. A token Play has forgotten (404/410) is treated as
 * expired.
 */
export async function refreshByToken(
  db: Firestore,
  play: PlayApi,
  purchaseToken: string,
  now: number
): Promise<RefreshOutcome> {
  let sub: PlaySubscription | null;
  try {
    sub = await play.getSubscription(purchaseToken);
  } catch (e) {
    if (!(e instanceof PlayNotFound)) throw e;
    sub = null;
  }

  const bound =
    (await findByToken(db, purchaseToken)) ??
    (sub?.linkedPurchaseToken ? await findByToken(db, sub.linkedPurchaseToken) : null);
  if (bound === null) return { outcome: "unknown-token" };

  const uid = bound.id;
  const decision = sub ? decide(sub, now) : gone(bound.data() as Partial<Decision>);
  await db.runTransaction(async (tx) => {
    const kitchen = await tx.get(db.collection(KITCHENS).doc(personalKitchenId(uid)));
    writeEntitlement(tx, db, uid, { ...decision, purchaseToken }, kitchen.exists, now);
  });
  return { outcome: "updated", uid, decision };
}

/**
 * The belt for a missed notification: any entitlement whose paid period is
 * over but which still reads premium is re-checked against Play. The query
 * is on `expiryTime` alone — adding `isPremium` to it would need a composite
 * index the emulator does not enforce, so the flag is filtered here instead.
 */
export async function reconcile(
  db: Firestore,
  play: PlayApi,
  now: number
): Promise<{ checked: number; revoked: number }> {
  const snap = await db.collection(ENTITLEMENTS).where("expiryTime", "<", now).get();
  let checked = 0;
  let revoked = 0;
  for (const doc of snap.docs) {
    const ent = doc.data() as EntitlementDoc;
    // A receipt RevenueCat wrote has no Play token to re-check; RevenueCat
    // owns its own expiry.
    if (!ent.isPremium || !ent.purchaseToken) continue;
    checked++;
    const result = await refreshByToken(db, play, ent.purchaseToken, now);
    if (result.outcome === "updated" && !result.decision.isPremium) revoked++;
  }
  return { checked, revoked };
}
