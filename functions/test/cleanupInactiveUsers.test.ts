import { lastActivitySignal, isInactive, UserActivity } from "../src/cleanupInactiveUsers";

const DAY = 24 * 60 * 60 * 1000;

describe("lastActivitySignal", () => {
  it("takes the max of lastActiveAt and lastMealAt", () => {
    const a: UserActivity = { lastActiveAt: 100, createdAt: 50, lastMealAt: 200 };
    expect(lastActivitySignal(a)).toBe(200);
  });

  it("falls back to createdAt when lastActiveAt is missing", () => {
    const a: UserActivity = { createdAt: 500, lastMealAt: 300 };
    expect(lastActivitySignal(a)).toBe(500);
  });

  it("is 0 when nothing is known", () => {
    expect(lastActivitySignal({})).toBe(0);
  });
});

describe("isInactive", () => {
  const now = 100 * DAY;

  it("is inactive when the newest signal is older than the threshold", () => {
    const a: UserActivity = { lastActiveAt: now - 31 * DAY };
    expect(isInactive(a, now, 30)).toBe(true);
  });

  it("is active when within the threshold", () => {
    const a: UserActivity = { lastActiveAt: now - 29 * DAY };
    expect(isInactive(a, now, 30)).toBe(false);
  });

  it("a recent meal keeps an otherwise-stale user active", () => {
    const a: UserActivity = { lastActiveAt: now - 60 * DAY, lastMealAt: now - 5 * DAY };
    expect(isInactive(a, now, 30)).toBe(false);
  });

  it("missing lastActiveAt falls back to a recent createdAt (kept active)", () => {
    const a: UserActivity = { createdAt: now - 2 * DAY };
    expect(isInactive(a, now, 30)).toBe(false);
  });

  it("never deletes a user with no known signals", () => {
    expect(isInactive({}, now, 30)).toBe(false);
  });

  it("is active when the signal is exactly 30 days old (strict boundary)", () => {
    const a: UserActivity = { lastActiveAt: now - 30 * DAY };
    expect(isInactive(a, now, 30)).toBe(false);
  });
});
