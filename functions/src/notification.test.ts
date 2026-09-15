import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { classify } from "./notification";
import { PACKAGE_NAME } from "./play";

describe("classify", () => {
  it("a subscription notification for this app yields its token", () => {
    assert.deepEqual(
      classify({
        version: "1.0",
        packageName: PACKAGE_NAME,
        eventTimeMillis: "1",
        subscriptionNotification: { version: "1.0", notificationType: 4, purchaseToken: "tok", subscriptionId: "premium" },
      }),
      { kind: "subscription", purchaseToken: "tok", notificationType: 4 }
    );
  });

  it("another package on the same project is refused, token or not", () => {
    const r = classify({
      packageName: "com.example.other",
      subscriptionNotification: { purchaseToken: "tok", notificationType: 4 },
    });
    assert.equal(r.kind, "foreign-package");
  });

  it("the Play Console test button is recognised as such", () => {
    assert.deepEqual(classify({ packageName: PACKAGE_NAME, testNotification: { version: "1.0" } }), { kind: "test" });
  });

  it("a subscription notification with no token is ignored, not acted on", () => {
    const r = classify({ packageName: PACKAGE_NAME, subscriptionNotification: { notificationType: 4 } });
    assert.equal(r.kind, "ignored");
  });

  it("voided and one-time payloads are ignored", () => {
    assert.equal(classify({ packageName: PACKAGE_NAME, voidedPurchaseNotification: {} }).kind, "ignored");
    assert.equal(classify({ packageName: PACKAGE_NAME, oneTimeProductNotification: {} }).kind, "ignored");
    assert.equal(classify({ packageName: PACKAGE_NAME }).kind, "ignored");
  });
});
