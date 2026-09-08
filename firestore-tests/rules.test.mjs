import { before, after, beforeEach, describe, it } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, updateDoc, deleteDoc, collection, getDocs } from 'firebase/firestore';

// Each test asserts one property claimed in sync-design.md. If a rule is
// loosened, the corresponding test here should fail.

const ALICE = 'alice-uid';
const BOB = 'bob-uid';
const KITCHEN = 'kitchen-1';              // free tier, owned by Alice
const PREMIUM_KITCHEN = 'kitchen-premium'; // premium, owned by Alice

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'demo-freshtrack',
    firestore: {
      rules: readFileSync('firestore.rules', 'utf8'),
      host: '127.0.0.1',
      port: 8080,
    },
  });
});

after(async () => {
  await testEnv?.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
  // Seed a kitchen owned by Alice, with rules bypassed.
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'kitchens', KITCHEN), {
      name: 'Alice kitchen',
      ownerUid: ALICE,
      memberUids: [ALICE],
      createdAt: 1,
    });
    await setDoc(doc(db, 'kitchens', KITCHEN, 'items', 'milk'), {
      name: 'Milk',
      expiryDate: '2026-09-11',
      updatedAt: 100,
      isDeleted: false,
    });
    // A premium kitchen, flagged the way a Cloud Function would after verifying
    // a Play Billing purchase.
    await setDoc(doc(db, 'kitchens', PREMIUM_KITCHEN), {
      name: 'Alice premium kitchen',
      ownerUid: ALICE,
      memberUids: [ALICE],
      isPremium: true,
      createdAt: 1,
    });
    await setDoc(doc(db, 'kitchens', PREMIUM_KITCHEN, 'items', 'eggs'), {
      name: 'Eggs',
      expiryDate: '2026-09-20',
      updatedAt: 100,
      isDeleted: false,
    });
    // One ledger entry, so the append-only rules have something to fail against.
    await setDoc(doc(db, 'kitchens', PREMIUM_KITCHEN, 'events', 'evt-1'), {
      itemId: 'eggs',
      type: 'ITEM_CREATED',
      actorUid: ALICE,
      quantity: 1,
      occurredAt: 100,
      operationId: 'op-1',
    });
    await setDoc(doc(db, 'users', ALICE), { displayName: 'Alice', plan: 'free' });
  });
});

const alice = () => testEnv.authenticatedContext(ALICE).firestore();
const bob = () => testEnv.authenticatedContext(BOB).firestore();
const anon = () => testEnv.unauthenticatedContext().firestore();

describe('unauthenticated access', () => {
  it('cannot read a kitchen', async () => {
    await assertFails(getDoc(doc(anon(), 'kitchens', KITCHEN)));
  });

  it('cannot read items', async () => {
    await assertFails(getDoc(doc(anon(), 'kitchens', KITCHEN, 'items', 'milk')));
  });

  it('cannot write items', async () => {
    await assertFails(
      setDoc(doc(anon(), 'kitchens', KITCHEN, 'items', 'x'), { updatedAt: 1 })
    );
  });
});

describe('kitchen membership', () => {
  it('a member can read their kitchen', async () => {
    await assertSucceeds(getDoc(doc(alice(), 'kitchens', KITCHEN)));
  });

  it('a non-member cannot read the kitchen', async () => {
    await assertFails(getDoc(doc(bob(), 'kitchens', KITCHEN)));
  });

  it('a member can read items', async () => {
    await assertSucceeds(getDocs(collection(alice(), 'kitchens', KITCHEN, 'items')));
  });

  it('a non-member cannot read items', async () => {
    await assertFails(getDocs(collection(bob(), 'kitchens', KITCHEN, 'items')));
  });

  it('a non-member cannot write items even to a premium kitchen', async () => {
    await assertFails(
      setDoc(doc(bob(), 'kitchens', PREMIUM_KITCHEN, 'items', 'stolen'), {
        name: 'Stolen',
        updatedAt: 200,
      })
    );
  });
});

describe('kitchen creation', () => {
  it('can create a kitchen owned by and containing only yourself', async () => {
    await assertSucceeds(
      setDoc(doc(bob(), 'kitchens', 'bob-kitchen'), {
        name: 'Bob kitchen',
        ownerUid: BOB,
        memberUids: [BOB],
        createdAt: 1,
      })
    );
  });

  it('cannot create a kitchen owned by someone else', async () => {
    await assertFails(
      setDoc(doc(bob(), 'kitchens', 'fake'), {
        name: 'Fake',
        ownerUid: ALICE,
        memberUids: [ALICE],
        createdAt: 1,
      })
    );
  });

  it('cannot create a kitchen that already contains someone else', async () => {
    await assertFails(
      setDoc(doc(bob(), 'kitchens', 'sneaky'), {
        name: 'Sneaky',
        ownerUid: BOB,
        memberUids: [BOB, ALICE],
        createdAt: 1,
      })
    );
  });
});

describe('kitchen ownership is not transferable by members', () => {
  it('a non-member cannot add themselves as a member', async () => {
    await assertFails(
      updateDoc(doc(bob(), 'kitchens', KITCHEN), { memberUids: [ALICE, BOB] })
    );
  });

  it('the owner can add a member', async () => {
    await assertSucceeds(
      updateDoc(doc(alice(), 'kitchens', PREMIUM_KITCHEN), { memberUids: [ALICE, BOB] })
    );
  });

  it('the owner cannot hand ownership to someone else', async () => {
    await assertFails(updateDoc(doc(alice(), 'kitchens', KITCHEN), { ownerUid: BOB }));
  });

  it('the owner cannot remove themselves from the member list', async () => {
    await assertFails(updateDoc(doc(alice(), 'kitchens', KITCHEN), { memberUids: [] }));
  });
});

describe('item writes (premium kitchen)', () => {
  it('a member can update a item', async () => {
    await assertSucceeds(
      updateDoc(doc(alice(), 'kitchens', PREMIUM_KITCHEN, 'items', 'eggs'), {
        name: 'Free Range Eggs',
        expiryDate: '2026-09-20',
        updatedAt: 300,
      })
    );
  });

  it('a write without updatedAt is rejected', async () => {
    await assertFails(
      setDoc(doc(alice(), 'kitchens', PREMIUM_KITCHEN, 'items', 'no-ts'), {
        name: 'No timestamp',
        expiryDate: '2026-09-20',
      })
    );
  });

  it('a write with a non-numeric updatedAt is rejected', async () => {
    await assertFails(
      setDoc(doc(alice(), 'kitchens', PREMIUM_KITCHEN, 'items', 'bad-ts'), {
        name: 'Bad timestamp',
        expiryDate: '2026-09-20',
        updatedAt: 'yesterday',
      })
    );
  });

  it('the owner may hard delete, for account deletion', async () => {
    // Firestore does not cascade into subcollections, so account deletion has
    // to remove each item explicitly or they are orphaned forever.
    await assertSucceeds(
      deleteDoc(doc(alice(), 'kitchens', PREMIUM_KITCHEN, 'items', 'eggs'))
    );
  });

  it('a non-owner member cannot hard delete', async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'kitchens', PREMIUM_KITCHEN), {
        name: 'Shared', ownerUid: ALICE, memberUids: [ALICE, BOB],
        isPremium: true, createdAt: 1,
      });
    });
    // A member can still soft delete; they just cannot erase shared history.
    await assertFails(
      deleteDoc(doc(bob(), 'kitchens', PREMIUM_KITCHEN, 'items', 'eggs'))
    );
  });

  it('soft delete via isDeleted is allowed', async () => {
    await assertSucceeds(
      updateDoc(doc(alice(), 'kitchens', PREMIUM_KITCHEN, 'items', 'eggs'), {
        isDeleted: true,
        expiryDate: '2026-09-20',
        deletedAt: 400,
        updatedAt: 400,
      })
    );
  });
});

describe('free tier gates cloud writes, not reads', () => {
  it('a free kitchen cannot write items', async () => {
    await assertFails(
      setDoc(doc(alice(), 'kitchens', KITCHEN, 'items', 'new-item'), {
        name: 'New Item',
        expiryDate: '2026-09-11',
        updatedAt: 500,
      })
    );
  });

  it('a free kitchen cannot update existing items', async () => {
    await assertFails(
      updateDoc(doc(alice(), 'kitchens', KITCHEN, 'items', 'milk'), {
        name: 'Changed',
        expiryDate: '2026-09-11',
        updatedAt: 500,
      })
    );
  });

  it('a free kitchen can still read its items', async () => {
    // A lapsed subscriber must be able to read and export what they already
    // uploaded, rather than have it held hostage.
    await assertSucceeds(getDoc(doc(alice(), 'kitchens', KITCHEN, 'items', 'milk')));
  });

  it('a client cannot create a kitchen pre-flagged as premium', async () => {
    await assertFails(
      setDoc(doc(bob(), 'kitchens', 'bob-premium'), {
        name: 'Bob premium',
        ownerUid: BOB,
        memberUids: [BOB],
        isPremium: true,
        createdAt: 1,
      })
    );
  });

  it('an owner cannot flag their own kitchen as premium', async () => {
    await assertFails(updateDoc(doc(alice(), 'kitchens', KITCHEN), { isPremium: true }));
  });
});

describe('household size limits', () => {
  it('a free kitchen cannot add a second member', async () => {
    await assertFails(
      updateDoc(doc(alice(), 'kitchens', KITCHEN), { memberUids: [ALICE, BOB] })
    );
  });

  it('a premium kitchen can add members', async () => {
    await assertSucceeds(
      updateDoc(doc(alice(), 'kitchens', PREMIUM_KITCHEN), { memberUids: [ALICE, BOB] })
    );
  });

  it('a premium kitchen allows up to six members', async () => {
    await assertSucceeds(
      updateDoc(doc(alice(), 'kitchens', PREMIUM_KITCHEN), {
        memberUids: [ALICE, BOB, 'c', 'd', 'e', 'f'],
      })
    );
  });

  it('a premium kitchen rejects a seventh member', async () => {
    await assertFails(
      updateDoc(doc(alice(), 'kitchens', PREMIUM_KITCHEN), {
        memberUids: [ALICE, BOB, 'c', 'd', 'e', 'f', 'g'],
      })
    );
  });
});

describe('user documents and entitlements', () => {
  it('can read your own user document', async () => {
    await assertSucceeds(getDoc(doc(alice(), 'users', ALICE)));
  });

  it('cannot read someone else user document', async () => {
    await assertFails(getDoc(doc(bob(), 'users', ALICE)));
  });

  it('cannot grant yourself premium on create', async () => {
    await assertFails(
      setDoc(doc(bob(), 'users', BOB), { displayName: 'Bob', plan: 'premium' })
    );
  });

  it('can create a user document without a plan', async () => {
    await assertSucceeds(setDoc(doc(bob(), 'users', BOB), { displayName: 'Bob' }));
  });

  it('cannot upgrade your own plan', async () => {
    await assertFails(updateDoc(doc(alice(), 'users', ALICE), { plan: 'premium' }));
  });

  it('can update other profile fields without touching plan', async () => {
    await assertSucceeds(
      updateDoc(doc(alice(), 'users', ALICE), { displayName: 'Alice Smith' })
    );
  });

  it('can delete your own user document', async () => {
    // Required for the account deletion path.
    await assertSucceeds(deleteDoc(doc(alice(), 'users', ALICE)));
  });

  it('cannot delete someone else user document', async () => {
    await assertFails(deleteDoc(doc(bob(), 'users', ALICE)));
  });
});

describe('the event ledger is append only', () => {
  const event = (over = {}) => ({
    itemId: 'eggs',
    type: 'QUANTITY_USED',
    actorUid: ALICE,
    quantity: 1,
    occurredAt: 500,
    operationId: 'op-new',
    ...over,
  });

  it('a member can append an event', async () => {
    await assertSucceeds(
      setDoc(doc(alice(), 'kitchens', PREMIUM_KITCHEN, 'events', 'evt-2'), event())
    );
  });

  it('an event cannot be edited, even by the owner', async () => {
    // The whole point of the ledger: impact is read from it, so a mutable
    // event would let a client rewrite what the household was told it did.
    await assertFails(
      updateDoc(doc(alice(), 'kitchens', PREMIUM_KITCHEN, 'events', 'evt-1'), {
        quantity: 99,
      })
    );
  });

  it('an event cannot be attributed to someone else', async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'kitchens', PREMIUM_KITCHEN), {
        name: 'Shared', ownerUid: ALICE, memberUids: [ALICE, BOB],
        isPremium: true, createdAt: 1,
      });
    });
    await assertFails(
      setDoc(
        doc(bob(), 'kitchens', PREMIUM_KITCHEN, 'events', 'evt-forged'),
        event({ actorUid: ALICE })
      )
    );
  });

  it('an event without a usable timestamp is rejected', async () => {
    await assertFails(
      setDoc(
        doc(alice(), 'kitchens', PREMIUM_KITCHEN, 'events', 'evt-bad'),
        event({ occurredAt: 'later' })
      )
    );
  });

  it('an event without an operation id is rejected', async () => {
    // Without it the server cannot recognise a replayed push, and a retried
    // sync would double count a use.
    const { operationId, ...withoutId } = event();
    await assertFails(
      setDoc(doc(alice(), 'kitchens', PREMIUM_KITCHEN, 'events', 'evt-noop'), withoutId)
    );
  });

  it('a free kitchen cannot append events', async () => {
    await assertFails(
      setDoc(doc(alice(), 'kitchens', KITCHEN, 'events', 'evt-free'), event())
    );
  });

  it('a non-member cannot read the ledger', async () => {
    await assertFails(
      getDoc(doc(bob(), 'kitchens', PREMIUM_KITCHEN, 'events', 'evt-1'))
    );
  });

  it('the owner may delete events, for account deletion', async () => {
    await assertSucceeds(
      deleteDoc(doc(alice(), 'kitchens', PREMIUM_KITCHEN, 'events', 'evt-1'))
    );
  });
});

describe('item shape', () => {
  it('an epoch millis expiry is rejected', async () => {
    // A client still thinking in instants would write dates every other client
    // misreads, and by then the damage is in everyone's inventory.
    await assertFails(
      setDoc(doc(alice(), 'kitchens', PREMIUM_KITCHEN, 'items', 'epoch'), {
        name: 'Epoch',
        expiryDate: 1789000000000,
        updatedAt: 500,
      })
    );
  });

  it('an ISO date expiry is accepted', async () => {
    await assertSucceeds(
      setDoc(doc(alice(), 'kitchens', PREMIUM_KITCHEN, 'items', 'iso'), {
        name: 'Iso',
        expiryDate: '2026-12-01',
        updatedAt: 500,
      })
    );
  });
});
