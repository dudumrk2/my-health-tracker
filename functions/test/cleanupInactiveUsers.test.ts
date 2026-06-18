import {
  lastActivitySignal,
  isInactive,
  activeByBaseSignal,
  parseInactiveDays,
  UserActivity,
} from "../src/cleanupInactiveUsers";

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

describe("activeByBaseSignal (meals-query skip)", () => {
  const now = 100 * DAY;

  it("is true when a recent heartbeat proves activity (skip meals)", () => {
    expect(activeByBaseSignal({ lastActiveAt: now - 5 * DAY }, now, 30)).toBe(true);
  });

  it("is true when createdAt is recent and there is no heartbeat", () => {
    expect(activeByBaseSignal({ createdAt: now - 2 * DAY }, now, 30)).toBe(true);
  });

  it("is false when the base signal is stale (must still check meals)", () => {
    expect(activeByBaseSignal({ lastActiveAt: now - 40 * DAY }, now, 30)).toBe(false);
  });

  it("is false when there is no base signal at all (must still check meals)", () => {
    expect(activeByBaseSignal({}, now, 30)).toBe(false);
  });
});

describe("parseInactiveDays", () => {
  it("uses a valid positive number", () => {
    expect(parseInactiveDays("15")).toBe(15);
  });

  it("falls back to 30 for non-numeric input", () => {
    expect(parseInactiveDays("abc")).toBe(30);
  });

  it("falls back to 30 for undefined or blank", () => {
    expect(parseInactiveDays(undefined)).toBe(30);
    expect(parseInactiveDays("   ")).toBe(30);
  });

  it("falls back to 30 for zero or negative", () => {
    expect(parseInactiveDays("0")).toBe(30);
    expect(parseInactiveDays("-5")).toBe(30);
  });
});
