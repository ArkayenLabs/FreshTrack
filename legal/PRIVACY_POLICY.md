<!--
  Published at https://www.arkayenlabs.com/privacy/goodbefore. The website renders
  its own copy in src/pages/privacy/GoodBefore.tsx: change both together.
  Written from the code as of v1.1.1 / 1.2 (10 November 2026). Re-check it when
  PREMIUM_ON_SALE, sign-in or cloud sync changes. Not legal advice; a review by a
  lawyer familiar with India's DPDP Act is still worth doing.
-->

# Privacy Policy for GoodBefore

**Effective and last updated:** 10 November 2026

GoodBefore ("the app", "we", "us") is a food expiration tracker published by Arkayen Labs, run by Balaji Thukuntala as a sole proprietor. GoodBefore was published earlier as FreshTrack, and this policy covers the app under both names. It explains what the app collects, why, and the choices you have.

## 1. In short

- You do not need an account, and GoodBefore does not ask you to create one.
- Your food list is stored on your phone. It is not uploaded to our servers.
- Receipts and printed dates are read on your phone. When you scan a barcode, the barcode number is sent to Open Food Facts to look up the product name.
- Usage statistics and crash reports are off unless you turn them on.
- We do not sell your data, and the app has no advertising.

## 2. What stays on your phone

Your items and everything about them (names, categories, dates, quantities, notes, status and history), your progress counts and your settings are stored on your phone. We do not receive them.

**Android backup.** If Android's backup to your Google Account is turned on, Android may include GoodBefore's food list in that backup, which Google stores under its own terms. GoodBefore's settings files are excluded from backups. You can turn Android backup off in your phone's settings.

**Export and import.** You can export your list to a CSV file and import one. The file goes only where you choose to save or share it.

## 3. Camera and photos

The camera is used to scan barcodes, photograph a receipt, and read dates printed on packets. Barcodes and text are read on your phone using Google ML Kit's on-device models. A receipt photo is saved temporarily on your phone while it is read and is then deleted. Photos are not uploaded.

Google's ML Kit library may send Google information about how these features perform, such as device type and error rates. It does not send your photos or the text read from them.

## 4. Barcode lookups

When you scan a barcode, the barcode number is sent to Open Food Facts (openfoodfacts.org), an open food database, to find the product's name, brand and category. Product images shown in the app are loaded from Open Food Facts. As with any internet request, their servers see your device's IP address. No account details or other personal information are sent. Open Food Facts handles these requests under its own privacy policy. Items you type in are not looked up.

## 5. Usage statistics and crash reports (off unless you turn them on)

Both are switched off when you install the app. The app asks you once, and you can change your choice at any time in Settings. Only if you turn them on:

- **Usage statistics**, through Google Firebase Analytics, record a small set of events: that an item was added and how (barcode, receipt or typed) with the number of items on your list at the time; that an item was dealt with (for example used or thrown away) and whether it was overdue; that an action was undone; that the app asked for a review; and, if a paid plan is offered, that its screens were opened. They never include item names, barcode numbers, notes, prices or dates. Firebase also records standard details such as an app instance identifier, device model, operating system and app version, and approximate country.
- **Crash reports**, through Firebase Crashlytics, record what went wrong and basic device details (model, operating system and app version) so bugs can be fixed.

If you never turn them on, none of this is collected.

## 6. Accounts

GoodBefore does not currently offer sign-in. If you created an account in an earlier version of the app, when it was called FreshTrack, Google Firebase Authentication holds your email address, your display name if you gave one, and an account identifier. You can delete that account from the app's Settings while signed in, or by email (see section 12).

Cloud backup and sync are not active. No food list is stored on our servers.

## 7. Purchases

Paid plans are not on sale. The app includes RevenueCat's library for purchases through Google Play, and RevenueCat may receive an anonymous app identifier and basic device information when the app starts. If paid plans are offered later, Google Play will handle payment and we will never receive your card details. This policy will be updated before that happens.

## 8. Google Play features

The app uses Google Play's in-app review and in-app update features. Ratings and updates are handled by Google Play under Google's terms.

## 9. Who processes data

- **Google**: Firebase Analytics and Crashlytics (only if you turn them on), Firebase Authentication (only for accounts created in earlier versions), ML Kit, Android backup and Google Play. Google's privacy information for Firebase is at https://firebase.google.com/support/privacy.
- **Open Food Facts**: barcode numbers, for product lookups.
- **RevenueCat**: an anonymous app identifier, for purchases.

We share data with no one else, except where required by law. We do not sell personal data and do not use it for advertising.

## 10. Where data is processed

Google and RevenueCat may process data on servers outside India, including in the United States. Where that happens, their standard contractual protections apply.

## 11. How long data is kept

- **On your phone:** until you delete it or uninstall the app.
- **Usage statistics:** under the retention settings of Firebase Analytics. They are not linked to your name or email.
- **Crash reports:** up to 90 days.
- **Accounts from earlier versions:** until you delete the account.

## 12. Your rights

Under India's Digital Personal Data Protection Act, and subject to it, you may ask to access, correct or erase personal data we hold about you, and nominate someone to exercise these rights for you. Most of your data never leaves your phone, so deleting items or uninstalling the app removes it. For anything else, including deleting an account created in an earlier version, email support@arkayenlabs.com. The steps are also at https://www.arkayenlabs.com/privacy/goodbefore/delete-account.

## 13. Grievance Officer

**Grievance Officer:** Balaji Thukuntala
**Email:** support@arkayenlabs.com
**On behalf of:** Arkayen Labs

We aim to respond within the time required by law.

## 14. Children

GoodBefore is not directed at children under 18. We do not knowingly collect personal data from children, and because usage statistics are off by default, the app does not profile anyone who has not turned them on. If you believe a child has given us personal data, contact us and we will delete it.

## 15. How your data is protected

- The food list is kept in the app's private storage on your phone.
- Settings are stored in encrypted storage on your phone.
- Release versions of the app do not log the contents of network requests.

## 16. Changes to this policy

If we change this policy, we will update the date above and, for material changes, tell you in the app. Continued use after a change means you accept the updated policy.

## 17. Contact

**Email:** support@arkayenlabs.com
**Developer:** Arkayen Labs
