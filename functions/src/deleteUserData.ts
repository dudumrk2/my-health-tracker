import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { purgeUser, prodPurgeDeps, PurgeDeps } from "./account/purgeUser";

const FUNCTION_REGION = process.env.FUNCTION_REGION || "us-central1";

/** Testable core: enforces auth then purges. Throws HttpsError("unauthenticated") with no auth. */
export async function handleDeleteUserData(
  auth: { uid: string } | undefined,
  deps: PurgeDeps
): Promise<{ success: true }> {
  if (!auth) {
    throw new HttpsError("unauthenticated", "נדרשת התחברות.");
  }
  await purgeUser(auth.uid, deps);
  return { success: true };
}

export const deleteUserData = onCall(
  { enforceAppCheck: true, timeoutSeconds: 120, region: FUNCTION_REGION },
  async (request: CallableRequest): Promise<{ success: true }> => {
    const started = Date.now();
    const uid = request.auth?.uid;
    try {
      const result = await handleDeleteUserData(request.auth, prodPurgeDeps());
      logger.info("deleteUserData ok", { uid, durationMs: Date.now() - started });
      return result;
    } catch (e) {
      if (e instanceof HttpsError) throw e;
      logger.error("deleteUserData failed", {
        uid, durationMs: Date.now() - started, message: (e as Error).message,
      });
      throw new HttpsError("internal", "לא ניתן למחוק את החשבון כרגע. נסה שוב מאוחר יותר.");
    }
  }
);
