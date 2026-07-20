# FreshTrack — Legal & Compliance Checklist

What needs to exist for a published app that now has accounts, analytics, and
cloud sync. Checklist only — **nothing here has been written yet.**

> **I am not a lawyer and this is not legal advice.** This is an engineer's list
> of what applies and why, so you know what to produce and what to ask a lawyer
> about. For a revenue-generating product handling other people's data, have the
> final Privacy Policy and Terms reviewed by an Indian lawyer familiar with the
> DPDP Act. The research below is current as of July 2026.

---

## What changed, and why the old policy no longer fits

The previous policy described a fully offline app. That is no longer what
FreshTrack is. It now:

- Creates **accounts** (Firebase Auth: email/password and Google Sign-In)
- Runs **Firebase Analytics and Crashlytics**, which collect by default
- Has a **Firestore sync path** built (inert today, but the code ships)
- Calls **OpenFoodFacts**, a third party, on every barcode scan

Each of those is a disclosure obligation that did not previously exist.

---

## P0 — Live risk on a published app

- [ ] **Account deletion is missing entirely.** Google Play requires that any app
      offering account creation provides **both** an in-app deletion path **and**
      a publicly reachable web URL for deletion requests, with the URL declared
      in the Data Safety form. There is no delete-account flow in the codebase at
      all. This has been fully enforced since April 2024, and non-compliance can
      mean removal from Play. Highest priority item on this list.

- [ ] **The store listing says "no data collection" while Analytics and
      Crashlytics are active.** Firebase collects on first run unless explicitly
      disabled. If the Data Safety form was filled in on the basis of the old
      offline app, it is now a misdeclaration — which Play treats as a policy
      violation independently of the underlying collection being lawful.
      Decide: disable analytics by default behind consent, or declare it
      accurately and change the listing wording. Do not leave the two disagreeing.

- [ ] **Re-file the Data Safety form** covering: account identifiers (email,
      display name, uid), crash logs and diagnostics, analytics events, and — once
      sync is live — the inventory itself. Disclosures must match the privacy
      policy exactly; mismatches are themselves a violation.

- [ ] **Privacy Policy must be rewritten before sync is enabled for anyone.**
      The moment the first product document reaches Firestore, personal data is
      being stored on a server the policy does not describe.

---

## Documents to produce

- [ ] **Privacy Policy** (public URL, linked in Play listing and in-app)
      Must cover: what is collected and why; that inventory stays on-device for
      free users; Firebase Auth / Analytics / Crashlytics / Firestore as
      processors; OpenFoodFacts as a third-party call; retention periods;
      deletion rights and how to exercise them; children's data; grievance
      contact; international transfer (Google stores outside India).

- [ ] **Terms of Service** — the in-app screen is 69 lines of placeholder text
      that references a Privacy Policy which does not exist. Needs real terms:
      acceptable use, account termination, disclaimer of warranties, limitation
      of liability, governing law, and — critically — **an explicit disclaimer
      that expiry dates are user-entered and the app must not be relied on for
      food-safety decisions.** That disclaimer is the one most specific to what
      this product does.

- [ ] **Account deletion web page** — public, functional, discoverable, naming
      the app and developer, stating what is deleted and what is retained.

- [ ] **Data retention & deletion policy** — how long tombstones, analytics, and
      crash logs live. Note the tension: soft deletes deliberately keep rows so
      deletions can propagate, which needs stating rather than hiding.

- [ ] **Sub-processor list** — Google (Firebase Auth, Firestore, Analytics,
      Crashlytics), OpenFoodFacts. Required for transparency and by GDPR if you
      have any EU users.

- [ ] **Refund / subscription terms** — before billing ships. Play has its own
      refund rules; yours must not contradict them.

---

## India — DPDP Act 2023 and DPDP Rules 2025

Your primary market, so this matters most.

- [ ] **Standalone consent notice**, separate from the Terms, in plain language,
      itemising each category of data and each purpose. A single "I accept"
      covering account creation, analytics and crash reporting together is
      specifically called out as non-compliant.
- [ ] **Notice availability in the scheduled Indian languages** — the Rules
      require the notice be available in the 22 scheduled languages.
- [ ] **Named grievance officer** with working contact details, published.
- [ ] **Rights mechanisms**: access, correction, erasure, and nomination of
      another person to exercise rights on the user's behalf.
- [ ] **Children's data — this one is a trap.** DPDP defines a child as **under
      18**, not 13 as under COPPA or GDPR. Tracking and behavioural profiling of
      children is *banned outright*, and verifiable parental consent is required.
      A food-tracking app plausibly has under-18 users, and Firebase Analytics
      profiles by default. Decide whether to age-gate or to disable analytics
      generally.
- [ ] **Timeline**: procedural provisions took effect 14 Nov 2025; the Consent
      Manager regime from 14 Nov 2026; substantive operational obligations from
      **14 May 2027**. Penalties run to ₹250 crore per violation category.

---

## If you have any users outside India

- [ ] **GDPR** (EU/UK) — lawful basis for analytics is consent, and Firebase
      must be off until it is given. Firebase's Data Processing Terms and
      Standard Contractual Clauses cover the transfer, but you must accept them
      and disclose the transfer.
- [ ] **CCPA/CPRA** (California) — "Do Not Sell or Share" disclosure. Analytics
      identifiers can count as sharing.
- [ ] **COPPA** (US, under 13) — only if you knowingly serve children.

Check your Play Console country breakdown before investing here. If usage is
overwhelmingly Indian, DPDP is where effort belongs — but the listing is
worldwide by default, so this is worth *checking* rather than assuming.

---

## Brand protection

- [ ] **Trademark** "FreshTrack" and "Arkayen Labs" in the relevant Indian
      classes. Note the name is generic-sounding, so search for conflicts first —
      there may already be similar marks.
- [ ] **Own the domain** used in the deep link (`freshtrack.arkayenlabs.com`)
      and any deletion/policy URLs, so they cannot lapse.
- [ ] **Confirm licence compatibility** of dependencies for commercial
      distribution — the OSS licences screen exists, so the data is already there.
- [ ] **Check OpenFoodFacts terms** — their data is under an open licence with
      attribution conditions. Verify you are meeting them.

---

## Suggested order

1. Account deletion (in-app + web) — the live Play violation
2. Reconcile analytics with the "no data collection" claim, then re-file Data Safety
3. Privacy Policy and Terms of Service, reviewed by a lawyer
4. DPDP consent notice, grievance officer, rights mechanisms
5. Trademark and domain, which are slow but not urgent
6. Refund and subscription terms, before billing ships

---

## Sources

- [Google Play: Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
- [Google Play: App account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en)
- [DPDP Rules 2025, notified (PIB)](https://static.pib.gov.in/WriteReadData/specificdocs/documents/2025/nov/doc20251117695301.pdf)
- [EY: Decoding the DPDP Act 2023 and Rules 2025](https://www.ey.com/en_in/insights/cybersecurity/decoding-the-digital-personal-data-protection-act-2023)
- [CookieYes: India DPDPA updated guide](https://www.cookieyes.com/blog/india-digital-personal-data-protection-act-dpdpa/)
- [Firebase: Data Processing and Security Terms](https://firebase.google.com/terms/data-processing-terms)
- [Firebase: Privacy and Security](https://firebase.google.com/support/privacy)
