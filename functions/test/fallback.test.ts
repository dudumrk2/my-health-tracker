import { buildFallbackInsights } from "../src/insights/fallback";
import { DISCLAIMER_HE } from "../src/insights/insightsParse";
import { DayData } from "../src/insights/aggregate";

const baseNoMealsDay = (over: Partial<DayData> = {}): DayData => ({
  date: "2026-06-13",
  profile: { gender: "male", stepsGoalOverride: undefined },
  steps: 0,
  sleepMinutes: 0,
  workouts: [],
  meals: { count: 0, totals: { calories: 0, proteinG: 0, carbsG: 0, fatG: 0 } },
  waterMl: 0,
  hasHealthData: false,
  hasMeals: false,
  isEmpty: true,
  weeklyAerobicMinutes: 0,
  weeklyStrengthWorkouts: 0,
  ...over,
});

describe("buildFallbackInsights", () => {
  it("returns all required fields as non-empty Hebrew strings with the fixed disclaimer", () => {
    const r = buildFallbackInsights(baseNoMealsDay());
    for (const v of [r.today.general, r.today.nutrition, r.today.activity, r.today.sleep,
      r.tomorrow.nutrition, r.tomorrow.activity, r.tomorrow.sleep]) {
      expect(typeof v).toBe("string");
      expect(v.trim().length).toBeGreaterThan(0);
      expect(v).toMatch(/[֐-׿]/); // contains Hebrew
    }
    expect(r.disclaimer).toBe(DISCLAIMER_HE);
  });

  it("today.general and today.nutrition prompt logging meals and water", () => {
    const r = buildFallbackInsights(baseNoMealsDay());
    const combined = `${r.today.general} ${r.today.nutrition}`;
    expect(combined).toContain("ארוח"); // meals
    expect(combined).toContain("מים");  // water
  });

  it("activity line reflects progress and goals when activity exists", () => {
    const r = buildFallbackInsights(baseNoMealsDay({
      steps: 9000,
      workouts: [{ type: "Running", durationMin: 30 }],
      hasHealthData: true,
      isEmpty: false,
      weeklyAerobicMinutes: 90,
      weeklyStrengthWorkouts: 1,
    }));
    expect(r.today.activity).toContain("9000");
    expect(r.today.activity).toContain("8000"); // default steps goal
    expect(r.today.activity).toContain("150");  // weekly aerobic goal
    expect(r.today.activity).toContain("2");    // weekly strength goal
  });

  it("activity line nudges toward goals when no activity logged", () => {
    const r = buildFallbackInsights(baseNoMealsDay());
    expect(r.today.activity).toContain("8000");
    expect(r.today.activity).toContain("150");
  });

  it("uses the user's steps override as the daily goal", () => {
    const r = buildFallbackInsights(baseNoMealsDay({
      steps: 5000,
      hasHealthData: true,
      isEmpty: false,
      profile: { gender: "male", stepsGoalOverride: 12000 },
    }));
    expect(r.today.activity).toContain("12000");
    expect(r.today.activity).not.toContain("8000");
  });
});
