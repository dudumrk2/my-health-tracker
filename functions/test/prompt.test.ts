import {
  buildMealSystemInstruction,
  MEAL_RESPONSE_SCHEMA,
  ProfileContext,
  buildInsightsSystemInstruction,
  buildInsightsUserPrompt,
  mealImagePrompt,
} from "../src/prompts";
import { DayData } from "../src/insights/aggregate";

describe("buildMealSystemInstruction", () => {
  it("includes Contract A safety constraints", () => {
    const s = buildMealSystemInstruction(null);
    expect(s).toMatch(/JSON/i);
    expect(s).toMatch(/items/);
    expect(s).toMatch(/lowConfidence/);
    expect(s.toLowerCase()).toContain("empty");
  });

  it("includes profile context when provided", () => {
    const p: ProfileContext = { weightKg: 80, heightCm: 180, gender: "male" };
    const s = buildMealSystemInstruction(p);
    expect(s).toContain("80");
    expect(s).toContain("180");
  });
});

describe("MEAL_RESPONSE_SCHEMA", () => {
  it("requires items array and lowConfidence", () => {
    expect(MEAL_RESPONSE_SCHEMA.properties.items).toBeDefined();
    expect(MEAL_RESPONSE_SCHEMA.properties.lowConfidence).toBeDefined();
  });
});

describe("buildInsightsSystemInstruction (self-declared focus, no auto-demographics)", () => {
  const s = buildInsightsSystemInstruction();

  it("never infers a medical state from age or gender", () => {
    expect(s).not.toMatch(/aged 40/i);
    expect(s).not.toMatch(/40 or older/i);
    expect(s.toLowerCase()).not.toContain("pre-menopause");
    expect(s.toLowerCase()).toContain("never infer");
  });

  it("treats focusAreas as the only trigger for sensitive content", () => {
    expect(s).toMatch(/focusAreas/);
    expect(s.toLowerCase()).toContain("self-declared");
  });

  it("only references menopause conditionally and always points to a clinician", () => {
    expect(s.toLowerCase()).toContain("menopause");
    expect(s.toLowerCase()).toContain("consult");
    // Conditional phrasing, not an automatic assertion about the user.
    expect(s).toMatch(/if focusAreas includes 'menopause'/i);
  });
});

function emptyDay(profile: DayData["profile"]): DayData {
  return {
    date: "2026-06-13",
    profile,
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
  };
}

describe("buildInsightsUserPrompt (goal + declared focus)", () => {
  it("includes primaryGoal and declared focusAreas as context", () => {
    const p = buildInsightsUserPrompt(
      emptyDay({ gender: "female", age: 50, primaryGoal: "lose", focusAreas: ["menopause"] })
    );
    expect(p).toMatch(/lose/);
    expect(p).toMatch(/menopause/);
  });

  it("omits the focus-areas line when none were declared", () => {
    const p = buildInsightsUserPrompt(emptyDay({ gender: "female", age: 50, primaryGoal: "maintain" }));
    expect(p.toLowerCase()).not.toContain("focus areas");
  });
});

describe("mealImagePrompt", () => {
  it("returns the fixed instruction when no note is given", () => {
    expect(mealImagePrompt()).toBe("Analyze the food in this image.");
  });

  it("includes the note and keeps the image as the primary source", () => {
    const p = mealImagePrompt("עם רוטב טחינה");
    expect(p).toContain("עם רוטב טחינה");
    expect(p.toLowerCase()).toContain("primary source");
  });

  it("treats a blank note as no note", () => {
    expect(mealImagePrompt("   ")).toBe("Analyze the food in this image.");
  });
});
