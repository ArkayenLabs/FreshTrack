import { timingSafeEqual } from "node:crypto";
import { Firestore } from "firebase-admin/firestore";
import { Decision, ENTITLEMENTS, KITCHENS, personalKitchenId, writeEntitlement } from "./entitlement";

// RevenueCat holds the merged Play and App Store state for one app user id,
// which the app sets to the Firebase uid on sign-in. Its webhook says that
// something changed; what changed is read back from RevenueCat's own record
// of the customer, never from the event body, so a late or repeated
// delivery rewrites the same truth rather than a stale claim.

/** The one entitlement this app sells, as configured in the RevenueCat dashboard. */
export const PREMIUM_ENTITLEMENT_ID = "premium";

/** An id the SDK made up before anyone signed in. Nothing to bind it to. */
const ANONYMOUS_PREFIX = "$RCAnonymousID:";
export const isAnonymous = (appUserId: string) => appUserId.startsWith(ANONYMOUS_PREFIX);

/** `GET /v1/subscribers/{app_user_id}`, narrowed to the fields decided on. */
export interface Subscriber {
  entitlements: Record<
    string,
    {
      expires_date: string | null;
      grace_period_expires_date?: string | null;
      product_identifier: string;
      purchase_date: string;
    }
  >;
  subscriptions: Record<
    string,
    {
      expires_date: string | null;
      grace_period_expires_date?: string | null;
      unsubscribe_detected_at?: string | null;
      billing_issues_detected_at?: string | null;
      refunded_at?: string | null;
      auto_resume_date?: string | null;
      /** Lowercase here, unlike the webhook: `play_store`, `app_store`, … */
      store: string;
      is_sandbox: boolean;
      period_type?: string;
    }
  >;
}

export interface RevenueCatApi {
  getSubscriber(appUserId: string): Promise<Subscriber>;
}

/** The v1 REST API with a secret key. `fetch` is Node 22's own. */
export class RevenueCatClient implements RevenueCatApi {
  constructor(private readonly apiKey: string) {}

  async getSubscriber(appUserId: string): Promise<Subscriber> {
    const res = await fetch(`https://api.revenuecat.com/v1/subscribers/${encodeURIComponent(appUserId)}`, {
      headers: { Authorization: `Bearer ${this.apiKey}`, Accept: "application/json" },
    });
    if (!res.ok) throw new Error(`RevenueCat ${res.status} for subscriber`);
    const body = (await res.json()) as { subscriber: Subscriber };
    return body.subscriber;
  }
}

/**
 * `state` vocabulary of a RevenueCat-derived receipt; `store` says which
 * shop the money went through. Together they let Settings say "renews",
 * "ends on", or "payment problem" without knowing either store's own terms.
 */
export type RcState = "ACTIVE" | "CANCELLED" | "GRACE" | "PAUSED" | "REFUNDED" | "EXPIRED" | "NONE";

export interface RcDecision extends Decision {
  state: RcState;
  /** `PLAY_STORE`, `APP_STORE`, … in the webhook's spelling; null with no purchase. */
  store: string | null;
  /** True for a license-tester or sandbox purchase. Recorded, not gated on. */
  isSandbox: boolean;
}

const ms = (iso: string | null | undefined): number | null => {
  if (!iso) return null;
  const parsed = Date.parse(iso);
  return Number.isNaN(parsed) ? null : parsed;
};

/** Google Play products arrive as `<subscription>:<base plan>`; the App Store has no base plan. */
const basePlanOf = (productId: string): string | null => {
  const i = productId.indexOf(":");
  return i < 0 ? null : productId.slice(i + 1);
};

/**
 * Pure. Access lasts until the later of the entitlement's expiry and its
 * grace period, whatever the reason, unless the store refunded it. Within
 * that window a cancellation is still premium — that is the store's contract
 * with the buyer — and a billing issue is premium for as long as the store
 * gives grace. Past it, a paused Play subscription is `PAUSED`, a refund is
 * `REFUNDED`, anything else `EXPIRED`. No entitlement at all is `NONE`.
 */
export function decideFromSubscriber(subscriber: Subscriber, now: number): RcDecision {
  const entitlement = subscriber.entitlements?.[PREMIUM_ENTITLEMENT_ID];
  if (!entitlement) {
    return { isPremium: false, state: "NONE", productId: null, basePlanId: null, expiryTime: null, store: null, isSandbox: false };
  }
  const productId = entitlement.product_identifier;
  const subscription = subscriber.subscriptions?.[productId];
  const expiry = ms(entitlement.expires_date);
  const graceEnd = Math.max(ms(entitlement.grace_period_expires_date) ?? 0, ms(subscription?.grace_period_expires_date) ?? 0);
  const accessUntil = Math.max(expiry ?? 0, graceEnd);
  const refunded = !!subscription?.refunded_at;
  const base = {
    productId,
    basePlanId: basePlanOf(productId),
    expiryTime: expiry,
    store: subscription?.store ? subscription.store.toUpperCase() : null,
    isSandbox: subscription?.is_sandbox ?? false,
  };

  if (refunded) return { ...base, isPremium: false, state: "REFUNDED" };
  // A lifetime entitlement has no expiry; this app sells none, but null
  // must not read as "expired at epoch".
  if (expiry === null || accessUntil > now) {
    const state: RcState = subscription?.unsubscribe_detected_at
      ? "CANCELLED"
      : subscription?.billing_issues_detected_at
        ? "GRACE"
        : "ACTIVE";
    return { ...base, isPremium: true, state };
  }
  return { ...base, isPremium: false, state: subscription?.auto_resume_date ? "PAUSED" : "EXPIRED" };
}

export type RefreshOutcome = { outcome: "nothing-to-record" } | { outcome: "updated"; decision: RcDecision };

/**
 * Read RevenueCat's record of the uid and rewrite the kitchen flag and the
 * receipt from it. A uid with no premium and no documents yet is left
 * alone — an experiment-enrolment event for a free user must not conjure a
 * kitchen for someone who never synced.
 */
export async function refreshFromRevenueCat(
  db: Firestore,
  rc: RevenueCatApi,
  uid: string,
  now: number
): Promise<RefreshOutcome> {
  const subscriber = await rc.getSubscriber(uid);
  const decision = decideFromSubscriber(subscriber, now);
  return db.runTransaction(async (tx) => {
    const kitchen = await tx.get(db.collection(KITCHENS).doc(personalKitchenId(uid)));
    if (!decision.isPremium && !kitchen.exists) {
      const receipt = await tx.get(db.collection(ENTITLEMENTS).doc(uid));
      if (!receipt.exists) return { outcome: "nothing-to-record" } as const;
    }
    writeEntitlement(tx, db, uid, { ...decision, source: "revenuecat" }, kitchen.exists, now);
    return { outcome: "updated", decision } as const;
  });
}

/** The body RevenueCat posts: `{ api_version, event }`. Only what is decided on. */
export interface WebhookEvent {
  type: string;
  id?: string;
  app_user_id?: string;
  transferred_from?: string[];
  transferred_to?: string[];
}

/**
 * Whose record to re-read for an event. A `TEST` from the dashboard names
 * nobody. A `TRANSFER` moved purchases between ids, so both sides change.
 * Every other type carries the one id it is about. Anonymous ids are
 * dropped: a guest cannot buy, and there is no kitchen to flag.
 */
export function subjectsOf(event: WebhookEvent): string[] {
  const raw =
    event.type === "TEST"
      ? []
      : event.type === "TRANSFER"
        ? [...(event.transferred_from ?? []), ...(event.transferred_to ?? [])]
        : event.app_user_id
          ? [event.app_user_id]
          : [];
  return [...new Set(raw.filter((id) => typeof id === "string" && id.length > 0 && !isAnonymous(id)))];
}

/** The dashboard's Authorization header value, compared in constant time. */
export function isAuthorized(header: string | undefined, secret: string): boolean {
  if (!header || !secret) return false;
  const a = Buffer.from(header);
  const b = Buffer.from(secret);
  return a.length === b.length && timingSafeEqual(a, b);
}
