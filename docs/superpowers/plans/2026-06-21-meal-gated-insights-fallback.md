# Meal-Gated AI Insights with Goal-Linked Fallback Note — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The daily insights flow calls Gemini only when the day has at least one logged meal; on a no-meals day it writes a deterministic, encouraging Hebrew note (no AI) whose activity line is linked to the user's daily and weekly activity goals.

**Architecture:** Add a meal gate inside the existing `runInsightsForUser` orchestration. When no meals exist, build a `ParsedInsights`-shaped note via a new pure `buildFallbackInsights(day)` and write it through the unchanged `writeInsights` path (so evening still authors the tomorrow doc). Goal values used by the note live in a new `insights/goals.ts`, shared with the prompt as the single source of truth; the daily steps goal is read from the user's settings (`goalOverrides.steps`).

**Tech Stack:** TypeScript, Firebase Cloud Functions v2, Jest.

## Global Constraints

- All AI calls are server-side only; the fallback path makes **no** external call — it is pure + a Firestore write. (CLAUDE.md iron rule 1.)
- User-facing strings are **Hebrew** (product language). Code, comments, identifiers in English.
- The disclaimer is the fixed server-side constant `DISCLAIMER_HE` — never model/derived. (insightsParse.)
- No new dependencies. Reuse existing patterns.
- Daily steps goal source of truth: `users/{uid}.profile.goalOverrides.steps`; default `8000` only when unset (mirrors client `GoalCalculator.DEFAULT_STEPS`).
- Weekly goals are fixed constants: aerobic `150` min, strength `2` workouts.
- Phase is not "done" until the functions test suite passes.
- **Running tests:** `node`/`npm` are not on PATH. Run from the `functions/` directory with the nvm4w node available, e.g. prepend `C:\nvm4w\nodejs` to PATH for the shell, then `npm test`. (See memory: node-runtime-path.)

---

### Task 1: Server-side goals module + steps goal from user settings

Introduces shared goal constants, reads the user's steps-goal override into `DayData`, and points the insights prompt at the constants so there is one source of truth.

**Files:**
- Create: `functions/src/insights/goals.ts`
- Modify: `functions/src/insights/aggregate.ts` (`DayProfile` interface + `buildProfile`)
- Modify: `functions/src/prompts.ts` (`buildInsightsUserPrompt` weekly line, `buildInsightsSystemInstruction` weekly-goal lines)
- Test: `functions/test/aggregate.test.ts` (add cases)
- Test: `functions/test/goals.test.ts` (create)

**Interfaces:**
- Consumes: existing `DayData`, `DayProfile`, `RawDayInputs` from `aggregate.ts`.
- Produces:
  - `goals.ts`: `export const WEEKLY_AEROBIC_GOAL_MIN = 150;`, `export const WEEKLY_STRENGTH_GOAL = 2;`, `export const DEFAULT_STEPS_GOAL = 8000;`, and `export function dailyStepsGoal(profile: DayProfile | null): number`.
  - `aggregate.ts`: `DayProfile` gains optional `stepsGoalOverride?: number`.

- [ ] **Step 1: Write the failing aggregate test**

Add to `functions/test/aggregate.test.ts`:

```typescript
  it("reads goalOverrides.steps into stepsGoalOverride", () => {
    const d = buildDayData({
      date: "2026-06-13",
      currentYear: 2026,
      userDoc: { profile: { birthYear: 1990, gender: "male", goalOverrides: { steps: 12000 } } },
      healthDaily: null,
      meals: [],
      water: null,
    });
    expect(d.profile?.stepsGoalOverride).toBe(12000);
  });

  it("leaves stepsGoalOverride undefined when no override is set", () => {
    const d = buildDayData({
      date: "2026-06-13",
      currentYear: 2026,
      userDoc: { profile: { birthYear: 1990, gender: "male" } },
      healthDaily: null,
      meals: [],
      water: null,
    });
    expect(d.profile?.stepsGoalOverride).toBeUndefined();
  });
```

- [ ] **Step 2: Run the test to verify it fails**

PATH-prepend nvm4w node, then from `functions/`:

Run: `npm test -- aggregate`
Expected: FAIL — `stepsGoalOverride` is `undefined`/not a property (first new test fails the `toBe(12000)` assertion).

- [ ] **Step 3: Add `stepsGoalOverride` to `DayProfile` and extract it in `buildProfile`**

In `functions/src/insights/aggregate.ts`, extend the `DayProfile` interface:

```typescript
export interface DayProfile {
  gender?: string;
  weightKg?: number;
  heightCm?: number;
  age?: number;
  /** Self-declared usage goal: "lose" | "maintain" | "gain". Chosen by the user, never inferred. */
  primaryGoal?: string;
  /** Self-declared focus areas (e.g. "menopause"). Direct user input only — never derived from age/gender. */
  focusAreas?: string[];
  /** User's manual daily steps goal from profile.goalOverrides.steps; undefined when not customized. */
  stepsGoalOverride?: number;
}
```

In `buildProfile`, add the extraction before the `return`, and include it in the returned object:

```typescript
  const goalOverrides = (profile.goalOverrides ?? null) as Record<string, unknown> | null;
  const stepsGoalOverride = optNum(goalOverrides?.steps);
  return {
    gender: typeof profile.gender === "string" ? profile.gender : undefined,
    weightKg: optNum(profile.weightKg),
    heightCm: optNum(profile.heightCm),
    age: birthYear !== undefined ? currentYear - birthYear : undefined,
    primaryGoal: typeof profile.primaryGoal === "string" ? profile.primaryGoal : undefined,
    focusAreas: focusAreas && focusAreas.length > 0 ? focusAreas : undefined,
    stepsGoalOverride,
  };
```

- [ ] **Step 4: Run the aggregate test to verify it passes**

Run: `npm test -- aggregate`
Expected: PASS (all aggregate tests, including the two new ones).

- [ ] **Step 5: Write the failing goals test**

Create `functions/test/goals.test.ts`:

```typescript
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
```

- [ ] **Step 6: Run the goals test to verify it fails**

Run: `npm test -- goals`
Expected: FAIL — cannot find module `../src/insights/goals`.

- [ ] **Step 7: Create `functions/src/insights/goals.ts`**

```typescript
import { DayProfile } from "./aggregate";

/** Weekly exercise goals (fixed, general-population — never medical advice). */
export const WEEKLY_AEROBIC_GOAL_MIN = 150;
export const WEEKLY_STRENGTH_GOAL = 2;

/** Daily steps goal used only when the user has not customized it. Mirrors client GoalCalculator.DEFAULT_STEPS. */
export const DEFAULT_STEPS_GOAL = 8000;

/** The user's daily steps goal: their setting (profile.goalOverrides.steps) or the shared default. */
export function dailyStepsGoal(profile: DayProfile | null): number {
  return profile?.stepsGoalOverride ?? DEFAULT_STEPS_GOAL;
}
```

- [ ] **Step 8: Run the goals test to verify it passes**

Run: `npm test -- goals`
Expected: PASS.

- [ ] **Step 9: Point the prompt at the shared constants**

In `functions/src/prompts.ts`, add the import near the top:

```typescript
import { WEEKLY_AEROBIC_GOAL_MIN, WEEKLY_STRENGTH_GOAL } from "./insights/goals";
```

Replace the weekly line in `buildInsightsUserPrompt`:

```typescript
  const weeklyLine = `Weekly totals: ${day.weeklyAerobicMinutes} min aerobic exercise (goal: ${WEEKLY_AEROBIC_GOAL_MIN} min), ${day.weeklyStrengthWorkouts} strength workouts (goal: ${WEEKLY_STRENGTH_GOAL}).`;
```

In `buildInsightsSystemInstruction`, replace the weekly-goal line so the literals come from the constants:

```typescript
    `- Evaluate the user's weekly exercise progress: Aerobic goal is ${WEEKLY_AEROBIC_GOAL_MIN}+ minutes (vital for visceral fat reduction), and Strength/Resistance goal is at least ${WEEKLY_STRENGTH_GOAL} workouts.`,
```

(Leave the two following gentle-encouragement lines unchanged — they don't carry the numeric literal.)

- [ ] **Step 10: Run the prompt + full suite to verify no regression**

Run: `npm test`
Expected: PASS (prompt tests still match `150` and `2` via interpolation; all suites green).

- [ ] **Step 11: Commit**

```bash
git add functions/src/insights/goals.ts functions/src/insights/aggregate.ts functions/src/prompts.ts functions/test/goals.test.ts functions/test/aggregate.test.ts
git commit -m "feat(insights): shared goal constants + steps goal from user settings"
```

---

### Task 2: Fallback note builder

A pure function that turns a no-meals `DayData` into the `ParsedInsights` shape, with a goal-linked activity line.

**Files:**
- Create: `functions/src/insights/fallback.ts`
- Test: `functions/test/fallback.test.ts` (create)

**Interfaces:**
- Consumes: `DayData` from `aggregate.ts`; `ParsedInsights` from `insightsParse.ts`; `DISCLAIMER_HE` from `insightsParse.ts`; `WEEKLY_AEROBIC_GOAL_MIN`, `WEEKLY_STRENGTH_GOAL`, `dailyStepsGoal` from `goals.ts`.
- Produces: `export function buildFallbackInsights(day: DayData): ParsedInsights`.

- [ ] **Step 1: Write the failing test**

Create `functions/test/fallback.test.ts`:

```typescript
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- fallback`
Expected: FAIL — cannot find module `../src/insights/fallback`.

- [ ] **Step 3: Implement `functions/src/insights/fallback.ts`**

```typescript
import { DayData } from "./aggregate";
import { ParsedInsights } from "./insightsParse";
import { DISCLAIMER_HE } from "./insightsParse";
import { WEEKLY_AEROBIC_GOAL_MIN, WEEKLY_STRENGTH_GOAL, dailyStepsGoal } from "./goals";

/**
 * Deterministic, non-AI insights for a day with no logged meals.
 * Returns the same shape as parsed Gemini output so it flows through writeInsights
 * unchanged. All copy is Hebrew (product language); the activity line is linked to
 * the user's daily steps goal and the fixed weekly exercise goals.
 */
export function buildFallbackInsights(day: DayData): ParsedInsights {
  const stepsGoal = dailyStepsGoal(day.profile);
  const hasActivity = day.steps > 0 || day.workouts.length > 0;

  const activity = hasActivity
    ? `כל הכבוד על הפעילות היום! צברת ${day.steps} צעדים מתוך יעד של ${stepsGoal}, והשבוע ${day.weeklyAerobicMinutes} מתוך ${WEEKLY_AEROBIC_GOAL_MIN} דק' אירובי ו-${day.weeklyStrengthWorkouts} מתוך ${WEEKLY_STRENGTH_GOAL} אימוני כוח — שווה להמשיך כך.`
    : `עוד לא תועדה פעילות היום; כל תנועה נחשבת — כדאי לשאוף ל-${stepsGoal} צעדים ביום, ${WEEKLY_AEROBIC_GOAL_MIN} דק' אירובי ו-${WEEKLY_STRENGTH_GOAL} אימוני כוח בשבוע.`;

  return {
    today: {
      general:
        "עדיין לא רשמת ארוחות היום, אז אין מספיק נתונים לסיכום תזונתי — כדאי לעדכן את הארוחות והמים כדי לקבל תובנות מדויקות.",
      nutrition:
        "לא תועדו ארוחות היום; כדאי להוסיף את מה שאכלת ולעדכן את כמות המים כדי שנוכל לתת משוב תזונתי.",
      activity,
      sleep:
        "שינה סדירה ואיכותית תורמת לאנרגיה ולריכוז — כדאי לשמור על שעות שינה קבועות.",
    },
    tomorrow: {
      nutrition:
        "מחר כדאי לתעד את הארוחות והמים לאורך היום כדי לקבל תמונה תזונתית מלאה.",
      activity: `המשך לשאוף ל-${WEEKLY_AEROBIC_GOAL_MIN} דק' פעילות אירובית ו-${WEEKLY_STRENGTH_GOAL} אימוני כוח בשבוע.`,
      sleep: "כדאי לכוון לשעת שינה קבועה כדי להתעורר רענן יותר.",
    },
    disclaimer: DISCLAIMER_HE,
  };
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm test -- fallback`
Expected: PASS (all 5 cases).

- [ ] **Step 5: Commit**

```bash
git add functions/src/insights/fallback.ts functions/test/fallback.test.ts
git commit -m "feat(insights): goal-linked fallback note builder for no-meals days"
```

---

### Task 3: Meal gate in the orchestration

Wire the gate into `runInsightsForUser`: no meals → write the fallback note without calling Gemini, returning a new `"fallback"` status.

**Files:**
- Modify: `functions/src/insights/core.ts`
- Test: `functions/test/insightsCore.test.ts` (add cases)

**Interfaces:**
- Consumes: `buildFallbackInsights` from `fallback.ts`; existing `InsightsRunDeps`, `RunOptions`.
- Produces: `RunResult.status` widened to `"written" | "skipped" | "failed" | "fallback"`.

- [ ] **Step 1: Write the failing tests**

Add to `functions/test/insightsCore.test.ts`. First add a no-meals (but non-empty) day helper after `emptyDay`:

```typescript
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
```

Then add cases inside `describe("runInsightsForUser", ...)`:

```typescript
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
    const [, , parsed, mode] = writes[0] as [string, string, { today: { activity: string } }, string];
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
```

Note: the existing test "still runs on an empty day when skipEmpty=false ... expect status 'written'" must be updated — an empty day now produces a fallback note, not an AI write. Change that test's expectation:

```typescript
  it("still produces output on an empty day when skipEmpty=false (evening fallback note)", async () => {
    const { d, writes } = deps({ fetchDayData: async () => emptyDay() });
    const r = await runInsightsForUser("u1", "2026-06-13", { mode: "evening", trigger: "evening", skipEmpty: false }, d);
    expect(r.status).toBe("fallback");
    expect(writes).toHaveLength(1);
  });
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `npm test -- insightsCore`
Expected: FAIL — new cases expect `"fallback"` but the code still calls `generate` and returns `"written"`.

- [ ] **Step 3: Add the meal gate in `core.ts`**

In `functions/src/insights/core.ts`, add the import:

```typescript
import { buildFallbackInsights } from "./fallback";
```

Widen `RunResult`:

```typescript
export interface RunResult {
  status: "written" | "skipped" | "failed" | "fallback";
}
```

Inside `runInsightsForUser`, after the `skipEmpty && day.isEmpty` block and before the `generate` call, insert:

```typescript
    if (!day.hasMeals) {
      const fallback = buildFallbackInsights(day);
      await deps.write(uid, date, fallback, opts.mode, opts.trigger);
      logger.info("insights fallback note written", {
        uid, date, mode: opts.mode, trigger: opts.trigger, durationMs: Date.now() - started,
      });
      return { status: "fallback" };
    }
```

(The block stays inside the existing `try`, so a write failure is caught and returns `{ status: "failed" }`, preserving any prior insights — same as the AI path.)

- [ ] **Step 4: Run the tests to verify they pass**

Run: `npm test -- insightsCore`
Expected: PASS (all cases, including the updated empty-day-evening case).

- [ ] **Step 5: Commit**

```bash
git add functions/src/insights/core.ts functions/test/insightsCore.test.ts
git commit -m "feat(insights): meal-gate AI; write fallback note when no meals logged"
```

---

### Task 4: Batch observability for the fallback status

Count `fallback` writes in the scheduled batch log so runs are observable.

**Files:**
- Modify: `functions/src/generateInsights.ts` (`runForAllUsers`)

**Interfaces:**
- Consumes: `RunResult.status` (now includes `"fallback"`).
- Produces: no new exports; behavior + log change only.

- [ ] **Step 1: Update `runForAllUsers` to count fallbacks**

In `functions/src/generateInsights.ts`, change the counters and tally:

```typescript
  let written = 0, skipped = 0, failed = 0, fallback = 0;
  for (const userDoc of users.docs) {
    try {
      const r = await runInsightsForUser(userDoc.id, date, { mode, trigger, skipEmpty }, prodDeps());
      if (r.status === "written") written++;
      else if (r.status === "skipped") skipped++;
      else if (r.status === "fallback") fallback++;
      else failed++;
    } catch (e) {
      failed++;
      logger.error("insights user iteration error", { uid: userDoc.id, trigger, message: (e as Error).message });
    }
  }
  logger.info("insights batch complete", { trigger, date, users: users.size, written, skipped, fallback, failed });
```

- [ ] **Step 2: Build to verify types compile**

Run: `npm run build`
Expected: PASS (no TypeScript errors).

- [ ] **Step 3: Run the full suite**

Run: `npm test`
Expected: PASS (all suites green).

- [ ] **Step 4: Commit**

```bash
git add functions/src/generateInsights.ts
git commit -m "feat(insights): count fallback notes in scheduled batch log"
```

---

## Final Verification

- [ ] From `functions/` (with nvm4w node on PATH): `npm test` → all suites pass.
- [ ] `npm run build` → clean compile.
- [ ] Manual reasoning check against the spec table: evening no-meals → fallback (today+tomorrow); midday empty → skip; midday no-meals-with-activity → fallback; manual no-meals → fallback; any-trigger with meals → AI.
