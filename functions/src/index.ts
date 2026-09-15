import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { setGlobalOptions } from "firebase-functions/v2";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { onMessagePublished } from "firebase-functions/v2/pubsub";
import { onSchedule } from "firebase-functions/v2/scheduler";
import * as logger from "firebase-functions/logger";
import { bindPurchase, EntitlementError, reconcile, refreshByToken } from "./entitlement";
import { classify, DeveloperNotification } from "./notification";
import { GooglePlayApi, PlayNotFound } from "./play";

// Same region as the Firestore database (asia-south1), so every entitlement
// write stays in one region. Instances are capped because nothing here is
// user-facing latency: a purchase verification can queue for a second.
setGlobalOptions({ region: "asia-south1", maxInstances: 10 });

initializeApp();
const db = getFirestore();
const play = new GooglePlayApi();

/** The Pub/Sub topic Play Console is configured to publish RTDNs to. */
export const PLAY_BILLING_TOPIC = "play-billing";

/**
 * Called by the app straight after Play reports a purchase, and again from
 * `queryPurchases` on start so a reinstall re-binds. Input: `{purchaseToken}`.
 * The product is taken from Play, never from the caller.
 */
export const verifyPurchase = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in before verifying a purchase.");
  const purchaseToken = (request.data as { purchaseToken?: unknown } | undefined)?.purchaseToken;
  if (typeof purchaseToken !== "string" || purchaseToken.length === 0) {
    throw new HttpsError("invalid-argument", "purchaseToken is required.");
  }

  try {
    const decision = await bindPurchase(db, play, uid, purchaseToken, Date.now());
    logger.info("verifyPurchase", { uid, isPremium: decision.isPremium, state: decision.state });
    return decision;
  } catch (e) {
    if (e instanceof PlayNotFound) throw new HttpsError("not-found", "Play has no record of that purchase.");
    if (e instanceof EntitlementError) {
      if (e.code === "token-bound-elsewhere") {
        throw new HttpsError("permission-denied", "That purchase belongs to another account.");
      }
      throw new HttpsError("failed-precondition", "That purchase is not a premium subscription.");
    }
    logger.error("verifyPurchase failed", { uid, error: String(e) });
    throw new HttpsError("internal", "Could not verify the purchase.");
  }
});

/**
 * Real-time developer notifications. Play publishes one per subscription
 * change; the handler re-reads the subscription and rewrites the flag.
 * Throwing makes Pub/Sub redeliver, which is what a transient Play or
 * Firestore failure deserves; everything else is logged and acknowledged.
 */
export const onPlayNotification = onMessagePublished(PLAY_BILLING_TOPIC, async (event) => {
  const notification = event.data.message.json as DeveloperNotification;
  const classified = classify(notification);
  switch (classified.kind) {
    case "test":
      logger.info("Play test notification received");
      return;
    case "foreign-package":
      logger.warn("RTDN for another package ignored", { packageName: classified.packageName });
      return;
    case "ignored":
      logger.info("RTDN ignored", { reason: classified.reason });
      return;
    case "subscription": {
      const result = await refreshByToken(db, play, classified.purchaseToken, Date.now());
      if (result.outcome === "unknown-token") {
        logger.info("RTDN for a token no account has bound", {
          notificationType: classified.notificationType,
        });
      } else {
        logger.info("Entitlement refreshed", {
          uid: result.uid,
          notificationType: classified.notificationType,
          isPremium: result.decision.isPremium,
          state: result.decision.state,
        });
      }
      return;
    }
  }
});

/** Daily belt for a missed notification. See `reconcile`. */
export const reconcileEntitlements = onSchedule("every 24 hours", async () => {
  const result = await reconcile(db, play, Date.now());
  logger.info("Entitlements reconciled", result);
});
