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
const PANTRY = 'pantry-1';

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
  // Seed a pantry owned by Alice, with rules bypassed.
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'pantries', PANTRY), {
      name: 'Alice pantry',
      ownerUid: ALICE,
      memberUids: [ALICE],
      createdAt: 1,
    });
    await setDoc(doc(db, 'pantries', PANTRY, 'products', 'milk'), {
      name: 'Milk',
      updatedAt: 100,
      isDeleted: false,
    });
    await setDoc(doc(db, 'users', ALICE), { displayName: 'Alice', plan: 'free' });
  });
});

const alice = () => testEnv.authenticatedContext(ALICE).firestore();
const bob = () => testEnv.authenticatedContext(BOB).firestore();
const anon = () => testEnv.unauthenticatedContext().firestore();

describe('unauthenticated access', () => {
  it('cannot read a pantry', async () => {
    await assertFails(getDoc(doc(anon(), 'pantries', PANTRY)));
  });

  it('cannot read products', async () => {
    await assertFails(getDoc(doc(anon(), 'pantries', PANTRY, 'products', 'milk')));
  });

  it('cannot write products', async () => {
    await assertFails(
      setDoc(doc(anon(), 'pantries', PANTRY, 'products', 'x'), { updatedAt: 1 })
    );
  });
});

describe('pantry membership', () => {
  it('a member can read their pantry', async () => {
    await assertSucceeds(getDoc(doc(alice(), 'pantries', PANTRY)));
  });

  it('a non-member cannot read the pantry', async () => {
    await assertFails(getDoc(doc(bob(), 'pantries', PANTRY)));
  });

  it('a member can read products', async () => {
    await assertSucceeds(getDocs(collection(alice(), 'pantries', PANTRY, 'products')));
  });

  it('a non-member cannot read products', async () => {
    await assertFails(getDocs(collection(bob(), 'pantries', PANTRY, 'products')));
  });

  it('a non-member cannot write products', async () => {
    await assertFails(
      setDoc(doc(bob(), 'pantries', PANTRY, 'products', 'stolen'), {
        name: 'Stolen',
        updatedAt: 200,
      })
    );
  });
});

describe('pantry creation', () => {
  it('can create a pantry owned by and containing only yourself', async () => {
    await assertSucceeds(
      setDoc(doc(bob(), 'pantries', 'bob-pantry'), {
        name: 'Bob pantry',
        ownerUid: BOB,
        memberUids: [BOB],
        createdAt: 1,
      })
    );
  });

  it('cannot create a pantry owned by someone else', async () => {
    await assertFails(
      setDoc(doc(bob(), 'pantries', 'fake'), {
        name: 'Fake',
        ownerUid: ALICE,
        memberUids: [ALICE],
        createdAt: 1,
      })
    );
  });

  it('cannot create a pantry that already contains someone else', async () => {
    await assertFails(
      setDoc(doc(bob(), 'pantries', 'sneaky'), {
        name: 'Sneaky',
        ownerUid: BOB,
        memberUids: [BOB, ALICE],
        createdAt: 1,
      })
    );
  });
});

describe('pantry ownership is not transferable by members', () => {
  it('a non-member cannot add themselves as a member', async () => {
    await assertFails(
      updateDoc(doc(bob(), 'pantries', PANTRY), { memberUids: [ALICE, BOB] })
    );
  });

  it('the owner can add a member', async () => {
    await assertSucceeds(
      updateDoc(doc(alice(), 'pantries', PANTRY), { memberUids: [ALICE, BOB] })
    );
  });

  it('the owner cannot hand ownership to someone else', async () => {
    await assertFails(updateDoc(doc(alice(), 'pantries', PANTRY), { ownerUid: BOB }));
  });

  it('the owner cannot remove themselves from the member list', async () => {
    await assertFails(updateDoc(doc(alice(), 'pantries', PANTRY), { memberUids: [] }));
  });
});

describe('product writes', () => {
  it('a member can update a product', async () => {
    await assertSucceeds(
      updateDoc(doc(alice(), 'pantries', PANTRY, 'products', 'milk'), {
        name: 'Whole Milk',
        updatedAt: 300,
      })
    );
  });

  it('a write without updatedAt is rejected', async () => {
    await assertFails(
      setDoc(doc(alice(), 'pantries', PANTRY, 'products', 'no-ts'), { name: 'No timestamp' })
    );
  });

  it('a write with a non-numeric updatedAt is rejected', async () => {
    await assertFails(
      setDoc(doc(alice(), 'pantries', PANTRY, 'products', 'bad-ts'), {
        name: 'Bad timestamp',
        updatedAt: 'yesterday',
      })
    );
  });

  it('hard delete is refused even for the owner', async () => {
    await assertFails(deleteDoc(doc(alice(), 'pantries', PANTRY, 'products', 'milk')));
  });

  it('soft delete via isDeleted is allowed', async () => {
    await assertSucceeds(
      updateDoc(doc(alice(), 'pantries', PANTRY, 'products', 'milk'), {
        isDeleted: true,
        deletedAt: 400,
        updatedAt: 400,
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

  it('cannot delete a user document', async () => {
    await assertFails(deleteDoc(doc(alice(), 'users', ALICE)));
  });
});
