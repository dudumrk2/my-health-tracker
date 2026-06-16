import { handleDeleteUserData } from "../src/deleteUserData";
import { PurgeDeps } from "../src/account/purgeUser";
import { HttpsError } from "firebase-functions/v2/https";

describe("handleDeleteUserData", () => {
  function noopDeps(calls: string[]): PurgeDeps {
    return {
      recursiveDelete: async (p) => { calls.push(`recursiveDelete:${p}`); },
      deleteAuthUser: async (u) => { calls.push(`deleteAuthUser:${u}`); },
    };
  }

  it("rejects an unauthenticated request", async () => {
    await expect(handleDeleteUserData(undefined, noopDeps([]))).rejects.toBeInstanceOf(HttpsError);
    await expect(handleDeleteUserData(undefined, noopDeps([]))).rejects.toMatchObject({
      code: "unauthenticated",
    });
  });

  it("purges the authenticated user and returns success", async () => {
    const calls: string[] = [];
    const result = await handleDeleteUserData({ uid: "abc" }, noopDeps(calls));
    expect(result).toEqual({ success: true });
    expect(calls).toEqual(["recursiveDelete:users/abc", "deleteAuthUser:abc"]);
  });
});
