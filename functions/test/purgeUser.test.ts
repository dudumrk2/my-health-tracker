import { purgeUser, PurgeDeps } from "../src/account/purgeUser";

describe("purgeUser", () => {
  function makeDeps() {
    const calls: string[] = [];
    const deps: PurgeDeps = {
      recursiveDelete: async (path: string) => { calls.push(`recursiveDelete:${path}`); },
      deleteAuthUser: async (uid: string) => { calls.push(`deleteAuthUser:${uid}`); },
    };
    return { deps, calls };
  }

  it("deletes Firestore data before the auth account", async () => {
    const { deps, calls } = makeDeps();
    await purgeUser("user123", deps);
    expect(calls).toEqual(["recursiveDelete:users/user123", "deleteAuthUser:user123"]);
  });

  it("does not delete the auth account if Firestore deletion fails", async () => {
    const calls: string[] = [];
    const deps: PurgeDeps = {
      recursiveDelete: async () => { throw new Error("boom"); },
      deleteAuthUser: async (uid: string) => { calls.push(`deleteAuthUser:${uid}`); },
    };
    await expect(purgeUser("user123", deps)).rejects.toThrow("boom");
    expect(calls).toEqual([]);
  });
});
