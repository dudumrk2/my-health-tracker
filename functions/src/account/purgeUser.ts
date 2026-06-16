import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getAuth } from "firebase-admin/auth";

if (getApps().length === 0) initializeApp();

/** Injectable side effects so the ordering logic is unit-testable without the Admin SDK. */
export interface PurgeDeps {
  recursiveDelete(path: string): Promise<void>;
  deleteAuthUser(uid: string): Promise<void>;
}

/**
 * Permanently deletes a user. Firestore data (the `users/{uid}` doc and ALL its
 * subcollections) is removed first, then the Firebase Auth account — so a Firestore
 * failure leaves the account intact and the operation retryable.
 */
export async function purgeUser(uid: string, deps: PurgeDeps): Promise<void> {
  await deps.recursiveDelete(`users/${uid}`);
  await deps.deleteAuthUser(uid);
}

/** Production side effects backed by the Admin SDK. */
export function prodPurgeDeps(): PurgeDeps {
  return {
    recursiveDelete: async (path: string) => {
      const db = getFirestore();
      await db.recursiveDelete(db.doc(path));
    },
    deleteAuthUser: async (uid: string) => {
      await getAuth().deleteUser(uid);
    },
  };
}
