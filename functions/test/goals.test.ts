import {
  WEEKLY_AEROBIC_GOAL_MIN,
  WEEKLY_STRENGTH_GOAL,
  DEFAULT_STEPS_GOAL,
  dailyStepsGoal,
} from "../src/insights/goals";

describe("insights goals", () => {
  it("exposes the fixed weekly goal constants", () => {
    expect(WEEKLY_AEROBIC_GOAL_MIN).toBe(150);
    expect(WEEKLY_STRENGTH_GOAL).toBe(2);
    expect(DEFAULT_STEPS_GOAL).toBe(8000);
  });

  it("returns the user's steps override when set", () => {
    expect(dailyStepsGoal({ stepsGoalOverride: 12000 })).toBe(12000);
  });

  it("falls back to the default when no override and when profile is null", () => {
    expect(dailyStepsGoal({})).toBe(DEFAULT_STEPS_GOAL);
    expect(dailyStepsGoal(null)).toBe(DEFAULT_STEPS_GOAL);
  });
});
