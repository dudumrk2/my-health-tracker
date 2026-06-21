import { runInsightsForUser, InsightsRunDeps } from "../src/insights/core";
import { DayData } from "../src/insights/aggregate";

const fullDay = (): DayData => ({
  date: "2026-06-13",
  profile: { gender: "male", weightKg: 80, heightCm: 180, age: 36 },
  steps: 8000,
  sleepMinutes: 420,
  workouts: [{ type: "Running", durationMin: 30 }],
  meals: { count: 2, totals: { calories: 800, proteinG: 40, carbsG: 90, fatG: 25 } },
  waterMl: 1500,
  hasHealthData: true,
  hasMeals: true,
  isEmpty: false,
  weeklyAerobicMinutes: 0,
  weeklyStrengthWorkouts: 0,
});

const noMealsDay = (): DayData => ({
  date: "2026-06-13",
  profile: { gender: "male", weightKg: 80, heightCm: 180, age: 36 },
  steps: 9000,
  sleepMinutes: 400,
  workouts: [{ type: "Running", durationMin: 30 }],
  meals: { count: 0, totals: { calories: 0, proteinG: 0, carbsG: 0, fatG: 0 } },
  waterMl: 1000,
  hasHealthData: true,
  hasMeals: false,
  isEmpty: false,
  weeklyAerobicMinutes: 60,
  weeklyStrengthWorkouts: 1,
});

const emptyDay = (): DayData => ({
  date: "2026-06-13",
  profile: null,
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
});

const validModelOutput = JSON.stringify({
  today: { general: "g", nutrition: "tn", activity: "ta", sleep: "ts" },
  tomorrow: { nutrition: "mn", activity: "ma", sleep: "ms" },
});

function deps(over: Partial<InsightsRunDeps> = {}): { d: InsightsRunDeps; writes: unknown[][] } {
  const writes: unknown[][] = [];
  const d: InsightsRunDeps = {
    fetchDayData: async () => fullDay(),
    generate: async () => validModelOutput,
    write: async (...args: unknown[]) => {
      writes.push(args);
    },
    ...over,
  };
  return { d, writes };
}

describe("runInsightsForUser", () => {
  it("happy path: generates, parses, and writes with the given mode/trigger", async () => {
    const { d, writes } = deps();
    const r = await runInsightsForUser("u1", "2026-06-13", { mode: "evening", trigger: "evening", skipEmpty: false }, d);
    expect(r.status).toBe("written");
    expect(writes).toHaveLength(1);
    const [uid, date, parsed, mode, trigger] = writes[0] as [string, string, { today: unknown }, string, string];
    expect(uid).toBe("u1");
    expect(date).toBe("2026-06-13");
    expect(mode).toBe("evening");
    expect(trigger).toBe("evening");
    expect(parsed.today).toEqual({ general: "g", nutrition: "tn", activity: "ta", sleep: "ts" });
  });

  it("does NOT write when generation fails (preserves existing insights)", async () => {
    const { d, writes } = deps({
      generate: async () => {
        throw Object.assign(new Error("boom"), { code: 503 });
      },
    });
    const r = await runInsightsForUser("u1", "2026-06-13", { mode: "todayOnly", trigger: "manual", skipEmpty: false }, d);
    expect(r.status).toBe("failed");
    expect(writes).toHaveLength(0);
  });

  it("does NOT write when model output is unparseable", async () => {
    const { d, writes } = deps({ generate: async () => "not json" });
    const r = await runInsightsForUser("u1", "2026-06-13", { mode: "evening", trigger: "evening", skipEmpty: false }, d);
    expect(r.status).toBe("failed");
    expect(writes).toHaveLength(0);
  });

  it("skips an empty day when skipEmpty=true (midday) without calling Gemini or writing", async () => {
    let generated = false;
    const { d, writes } = deps({
      fetchDayData: async () => emptyDay(),
      generate: async () => {
        generated = true;
        return validModelOutput;
      },
    });
    const r = await runInsightsForUser("u1", "2026-06-13", { mode: "todayOnly", trigger: "midday", skipEmpty: true }, d);
    expect(r.status).toBe("skipped");
    expect(generated).toBe(false);
    expect(writes).toHaveLength(0);
  });

  it("still produces output on an empty day when skipEmpty=false (evening fallback note)", async () => {
    const { d, writes } = deps({ fetchDayData: async () => emptyDay() });
    const r = await runInsightsForUser("u1", "2026-06-13", { mode: "evening", trigger: "evening", skipEmpty: false }, d);
    expect(r.status).toBe("fallback");
    expect(writes).toHaveLength(1);
  });

  it("no meals: writes the fallback note WITHOUT calling Gemini", async () => {
    let generated = false;
    const { d, writes } = deps({
      fetchDayData: async () => noMealsDay(),
      generate: async () => {
        generated = true;
        return validModelOutput;
      },
    });
    const r = await runInsightsForUser("u1", "2026-06-13", { mode: "evening", trigger: "evening", skipEmpty: false }, d);
    expect(r.status).toBe("fallback");
    expect(generated).toBe(false);
    expect(writes).toHaveLength(1);
    const [, , parsed, mode] = writes[0] as [string, string, { today: { activity: string } }, string, string];
    expect(mode).toBe("evening");
    expect(parsed.today.activity).toContain("9000"); // goal-linked activity from the day
  });

  it("empty day at midday still skips before the meal gate", async () => {
    let generated = false;
    const { d, writes } = deps({
      fetchDayData: async () => emptyDay(),
      generate: async () => {
        generated = true;
        return validModelOutput;
      },
    });
    const r = await runInsightsForUser("u1", "2026-06-13", { mode: "todayOnly", trigger: "midday", skipEmpty: true }, d);
    expect(r.status).toBe("skipped");
    expect(generated).toBe(false);
    expect(writes).toHaveLength(0);
  });

  it("empty day at evening writes the fallback note (no Gemini)", async () => {
    let generated = false;
    const { d, writes } = deps({
      fetchDayData: async () => emptyDay(),
      generate: async () => {
        generated = true;
        return validModelOutput;
      },
    });
    const r = await runInsightsForUser("u1", "2026-06-13", { mode: "evening", trigger: "evening", skipEmpty: false }, d);
    expect(r.status).toBe("fallback");
    expect(generated).toBe(false);
    expect(writes).toHaveLength(1);
  });

  it("a day with meals still calls Gemini (AI path unchanged)", async () => {
    let generated = false;
    const { d, writes } = deps({
      generate: async () => {
        generated = true;
        return validModelOutput;
      },
    });
    const r = await runInsightsForUser("u1", "2026-06-13", { mode: "evening", trigger: "evening", skipEmpty: false }, d);
    expect(r.status).toBe("written");
    expect(generated).toBe(true);
    expect(writes).toHaveLength(1);
  });
});
