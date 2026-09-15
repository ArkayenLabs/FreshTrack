import { PACKAGE_NAME } from "./play";

/**
 * A Real-time developer notification, as Play publishes it to Pub/Sub.
 * Only the fields this project reads; the rest of the schema is ignored.
 */
export interface DeveloperNotification {
  version?: string;
  packageName?: string;
  eventTimeMillis?: string;
  subscriptionNotification?: {
    version?: string;
    /** 1 RECOVERED … 13 EXPIRED, 20 PENDING_PURCHASE_CANCELED. Informational only. */
    notificationType?: number;
    purchaseToken?: string;
    subscriptionId?: string;
  };
  testNotification?: { version?: string };
  voidedPurchaseNotification?: unknown;
  oneTimeProductNotification?: unknown;
}

export type Classified =
  | { kind: "subscription"; purchaseToken: string; notificationType: number }
  | { kind: "test" }
  | { kind: "foreign-package"; packageName: string | undefined }
  | { kind: "ignored"; reason: string };

/**
 * Decides what to do with a notification without doing it. The token is the
 * only thing acted on — `notificationType` says what Play thinks happened,
 * but the handler re-reads the subscription and lets that decide. A message
 * for another package is refused rather than looked up: a topic is per
 * project, not per app, and a second app on this project must not be able
 * to flip this one's entitlements.
 */
export function classify(n: DeveloperNotification): Classified {
  if (n.packageName !== PACKAGE_NAME) return { kind: "foreign-package", packageName: n.packageName };
  if (n.testNotification) return { kind: "test" };
  const sub = n.subscriptionNotification;
  if (sub) {
    if (typeof sub.purchaseToken !== "string" || sub.purchaseToken.length === 0) {
      return { kind: "ignored", reason: "subscription notification without a purchase token" };
    }
    return {
      kind: "subscription",
      purchaseToken: sub.purchaseToken,
      notificationType: sub.notificationType ?? 0,
    };
  }
  // A voided subscription purchase also arrives as SUBSCRIPTION_REVOKED, so
  // the void itself carries nothing extra; one-time products are not sold.
  if (n.voidedPurchaseNotification) return { kind: "ignored", reason: "voided purchase" };
  if (n.oneTimeProductNotification) return { kind: "ignored", reason: "one-time product" };
  return { kind: "ignored", reason: "no recognised payload" };
}
