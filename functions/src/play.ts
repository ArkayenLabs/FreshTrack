import { androidpublisher, androidpublisher_v3 } from "@googleapis/androidpublisher";
import { GoogleAuth } from "google-auth-library";

/** The app whose purchases we will believe. Anything else is refused. */
export const PACKAGE_NAME = "com.arkayenlabs.freshtrack";

/** The subscription products that grant premium, as configured in Play Console. */
export const PREMIUM_PRODUCT_IDS: ReadonlySet<string> = new Set(["premium"]);

/**
 * The subset of `purchases.subscriptionsv2.get` this project reads. Kept as a
 * plain type so tests can hand the logic a subscription without the API.
 */
export interface PlaySubscription {
  /** `SUBSCRIPTION_STATE_ACTIVE`, `..._CANCELED`, `..._EXPIRED`, and so on. */
  subscriptionState: string;
  /** `ACKNOWLEDGEMENT_STATE_PENDING` until someone acknowledges the purchase. */
  acknowledgementState: string;
  lineItems: Array<{
    productId: string;
    basePlanId?: string;
    /** RFC 3339. */
    expiryTime?: string;
  }>;
  /** Set on an upgrade or downgrade: the token this one replaces. */
  linkedPurchaseToken?: string;
}

/** Play has never seen this token, or has forgotten it. */
export class PlayNotFound extends Error {
  constructor(token: string) {
    super(`Play has no subscription for token ${token.slice(0, 8)}…`);
  }
}

export interface PlayApi {
  getSubscription(purchaseToken: string): Promise<PlaySubscription>;
  acknowledge(purchaseToken: string, productId: string): Promise<void>;
}

/**
 * The real thing. Authenticates as the function's runtime service account,
 * which must be invited in Play Console with "View financial data" and
 * "Manage orders and subscriptions".
 */
export class GooglePlayApi implements PlayApi {
  private readonly client: androidpublisher_v3.Androidpublisher;

  constructor() {
    this.client = androidpublisher({
      version: "v3",
      auth: new GoogleAuth({ scopes: ["https://www.googleapis.com/auth/androidpublisher"] }),
    });
  }

  async getSubscription(purchaseToken: string): Promise<PlaySubscription> {
    let data: androidpublisher_v3.Schema$SubscriptionPurchaseV2;
    try {
      ({ data } = await this.client.purchases.subscriptionsv2.get({
        packageName: PACKAGE_NAME,
        token: purchaseToken,
      }));
    } catch (e) {
      const status = (e as { code?: number }).code;
      if (status === 404 || status === 410) throw new PlayNotFound(purchaseToken);
      throw e;
    }
    return {
      subscriptionState: data.subscriptionState ?? "SUBSCRIPTION_STATE_UNSPECIFIED",
      acknowledgementState: data.acknowledgementState ?? "ACKNOWLEDGEMENT_STATE_UNSPECIFIED",
      lineItems: (data.lineItems ?? []).map((line) => ({
        productId: line.productId ?? "",
        basePlanId: line.offerDetails?.basePlanId ?? undefined,
        expiryTime: line.expiryTime ?? undefined,
      })),
      linkedPurchaseToken: data.linkedPurchaseToken ?? undefined,
    };
  }

  async acknowledge(purchaseToken: string, productId: string): Promise<void> {
    await this.client.purchases.subscriptions.acknowledge({
      packageName: PACKAGE_NAME,
      subscriptionId: productId,
      token: purchaseToken,
    });
  }
}
