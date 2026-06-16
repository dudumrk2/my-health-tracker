import { buildDayData } from "../src/insights/aggregate";

describe("buildDayData", () => {
  it("aggregates a full day (profile + health + meals + water) into sane DayData", () => {
    const d = buildDayData({
      date: "2026-06-13",
      currentYear: 2026,
      userDoc: { profile: { birthYear: 1990, gender: "male", weightKg: 80, heightCm: 180 } },
      healthDaily: {
        steps: 8500,
        sleepMinutes: 420,
        workouts: [
          { type: "Running", durationMin: 30, source: "health_connect" },
          { type: "Walking", durationMin: 15, source: "manual" },
        ],
      },
      meals: [
        { date: "2026-06-13", totals: { calories: 500, proteinG: 30, carbsG: 40, fatG: 20 } },
        { date: "2026-06-13", totals: { calories: 300, proteinG: 10, carbsG: 50, fatG: 5 } },
      ],
      water: { amountMl: 1500 },
    });

    expect(d.date).toBe("2026-06-13");
    expect(d.profile).toEqual({ gender: "male", weightKg: 80, heightCm: 180, age: 36 });
    expect(d.steps).toBe(8500);
    expect(d.sleepMinutes).toBe(420);
    expect(d.workouts).toEqual([
      { type: "Running", durationMin: 30 },
      { type: "Walking", durationMin: 15 },
    ]);
    expect(d.meals.count).toBe(2);
    expect(d.meals.totals).toEqual({ calories: 800, proteinG: 40, carbsG: 90, fatG: 25 });
    expect(d.waterMl).toBe(1500);
    expect(d.hasHealthData).toBe(true);
    expect(d.hasMeals).toBe(true);
    expect(d.isEmpty).toBe(false);
  });

  it("carries self-declared primaryGoal and focusAreas onto the profile", () => {
    const d = buildDayData({
      date: "2026-06-13",
      currentYear: 2026,
      userDoc: {
        profile: {
          birthYear: 1976,
          gender: "female",
          weightKg: 65,
          heightCm: 165,
          primaryGoal: "lose",
          focusAreas: ["menopause", "heart_health"],
        },
      },
      healthDaily: null,
      meals: [],
      water: null,
    });

    expect(d.profile?.primaryGoal).toBe("lose");
    expect(d.profile?.focusAreas).toEqual(["menopause", "heart_health"]);
  });

  it("handles a partial day (meals only, no health/water/profile)", () => {
    const d = buildDayData({
      date: "2026-06-13",
      currentYear: 2026,
      userDoc: null,
      healthDaily: null,
      meals: [{ date: "2026-06-13", totals: { calories: 200, proteinG: 5, carbsG: 30, fatG: 2 } }],
      water: null,
    });

    expect(d.profile).toBeNull();
    expect(d.steps).toBe(0);
    expect(d.sleepMinutes).toBe(0);
    expect(d.workouts).toEqual([]);
    expect(d.meals.count).toBe(1);
    expect(d.meals.totals.calories).toBe(200);
    expect(d.waterMl).toBe(0);
    expect(d.hasHealthData).toBe(false);
    expect(d.hasMeals).toBe(true);
    expect(d.isEmpty).toBe(false);
  });

  it("handles an empty day (nothing collected) → isEmpty true", () => {
    const d = buildDayData({
      date: "2026-06-13",
      currentYear: 2026,
      userDoc: { profile: { birthYear: 1990, gender: "female", weightKg: 60, heightCm: 165 } },
      healthDaily: null,
      meals: [],
      water: null,
    });

    expect(d.profile).toEqual({ gender: "female", weightKg: 60, heightCm: 165, age: 36 });
    expect(d.meals.count).toBe(0);
    expect(d.meals.totals).toEqual({ calories: 0, proteinG: 0, carbsG: 0, fatG: 0 });
    expect(d.hasHealthData).toBe(false);
    expect(d.hasMeals).toBe(false);
    expect(d.waterMl).toBe(0);
    expect(d.isEmpty).toBe(true);
  });

  it("tolerates malformed/missing fields without throwing", () => {
    const d = buildDayData({
      date: "2026-06-13",
      currentYear: 2026,
      userDoc: { profile: { gender: "female" } },
      healthDaily: { steps: "oops", workouts: "not-an-array" } as never,
      meals: [{ date: "2026-06-13" } as never],
      water: { amountMl: "nope" } as never,
    });

    expect(d.profile).toEqual({ gender: "female", weightKg: undefined, heightCm: undefined, age: undefined });
    expect(d.steps).toBe(0);
    expect(d.workouts).toEqual([]);
    expect(d.meals.count).toBe(1);
    expect(d.meals.totals.calories).toBe(0);
    expect(d.waterMl).toBe(0);
  });
});
