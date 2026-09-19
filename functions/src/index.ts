import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { setGlobalOptions } from "firebase-functions/v2";
import { HttpsError, onCall, onRequest } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { onMessagePublished } from "firebase-functions/v2/pubsub";
import { onSchedule } from "firebase-functions/v2/scheduler";
import * as logger from "firebase-functions/logger";
import { bindPurchase, EntitlementError, reconcile, refreshByToken } from "./entitlement";
import { classify, DeveloperNotification } from "./notification";
import { GooglePlayApi, PlayNotFound } from "./play";
import {
  isAuthorized,
  refreshFromRevenueCat,
  RevenueCatClient,
  subjectsOf,
  WebhookEvent,
} from "./revenuecat";

// Same region as the Firestore database (asia-south1), so every entitlement
// write stays in one region. Instances are capped because nothing here is
// user-facing latency: a purchase verification can queue for a second.
setGlobalOptions({ region: "asia-south1", maxInstances: 10 });

initializeApp();
const db = getFirestore();
const play = new GooglePlayApi();

// Set once by the owner with `firebase functions:secrets:set`. The first is
// the Authorization value typed into the RevenueCat webhook form; the second
// a v1 secret API key, for reading a customer back.
const revenueCatWebhookAuth = defineSecret("REVENUECAT_WEBHOOK_AUTH");
const revenueCatApiKey = defineSecret("REVENUECAT_API_KEY");

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

/**
 * RevenueCat's webhook. Replaces the three Play-native functions above once
 * a purchase is proven through it: RevenueCat merges Play and App Store
 * state per uid and calls here on every change. The event body is only used
 * to learn whose record moved; the record itself is read back from
 * RevenueCat and the flag rewritten from that, so a stale or repeated
 * delivery is harmless. Anything but 200 makes RevenueCat retry (five
 * times over about two and a half hours), so a RevenueCat or Firestore
 * failure is a 500 and an event that needs nothing is a 200.
 */
export const revenueCatWebhook = onRequest(
  { secrets: [revenueCatWebhookAuth, revenueCatApiKey] },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).send("POST only");
      return;
    }
    if (!isAuthorized(req.get("Authorization"), revenueCatWebhookAuth.value())) {
      logger.warn("RevenueCat webhook refused: bad Authorization");
      res.status(401).send("Unauthorized");
      return;
    }
    const event = (req.body as { event?: WebhookEvent } | undefined)?.event;
    if (!event || typeof event.type !== "string") {
      res.status(400).send("No event");
      return;
    }
    if (event.type === "TEST") {
      logger.info("RevenueCat test event received");
      res.status(200).json({ updated: [] });
      return;
    }

    const rc = new RevenueCatClient(revenueCatApiKey.value());
    const updated: string[] = [];
    try {
      for (const uid of subjectsOf(event)) {
        const result = await refreshFromRevenueCat(db, rc, uid, Date.now());
        if (result.outcome === "updated") {
          updated.push(uid);
          logger.info("Entitlement refreshed from RevenueCat", {
            uid,
            eventType: event.type,
            eventId: event.id,
            isPremium: result.decision.isPremium,
            state: result.decision.state,
            store: result.decision.store,
          });
        } else {
          logger.info("RevenueCat event for a uid with nothing to record", { uid, eventType: event.type });
        }
      }
    } catch (e) {
      logger.error("RevenueCat webhook failed", { eventType: event.type, eventId: event.id, error: String(e) });
      res.status(500).send("Retry");
      return;
    }
    res.status(200).json({ updated });
  }
);
