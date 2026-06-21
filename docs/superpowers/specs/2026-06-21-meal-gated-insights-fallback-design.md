# Design: Meal-Gated AI Insights with Goal-Linked Fallback Note

**Date:** 2026-06-21
**Status:** Approved
**Area:** Cloud Functions — `generateInsights` (Contract B / daily insights)

## Problem

The daily insights flow calls Gemini on every scheduled run regardless of
whether the user logged any meals that day. On a day with no meals there is
little nutritional substance for the AI to summarize, so the call is wasteful
and the resulting "today" note is thin. We want to:

1. Call the AI **only when the day has at least one meal logged**.
2. On a no-meals day, write a generic, encouraging note (no AI) that prompts the
   user to log meals and water.
3. When physical activity exists that day, reflect it — specifically **linked to
   the daily and weekly activity goals**.

## Scope

The rule is universal across all three insights triggers:

- `generateInsightsEvening` (21:00 cron)
- `generateInsightsMidday` (15:00 cron)
- `generateInsightsManual` (in-app refresh)

"Call the AI only if meals were logged; otherwise write the generic note."

## Non-Goals

- No change to the meals (Contract A / `analyzeMeal`) flow.
- No porting of the full client `GoalCalculator` to functions. Only the specific
  goal values the fallback note references are made available server-side.
- Water/meals fields in the fallback note remain logging prompts, not
  goal-progress statements. Only the **activity** field is goal-linked.

## Design

### 1. Gate logic — `functions/src/insights/core.ts` (`runInsightsForUser`)

New evaluation order:

1. `fetchDayData`.
2. `if (opts.skipEmpty && day.isEmpty)` → return `{ status: "skipped" }`
   *(unchanged; protects the midday run from nagging on a totally-empty day).*
3. **`if (!day.hasMeals)` → build the fallback note, write it (no Gemini call),
   return `{ status: "fallback" }`.**
4. else → existing AI path (generate → parse → write), return
   `{ status: "written" }`.

Resulting behavior per trigger:

| Trigger | Totally empty | Activity/water but no meals | Has meals |
|---|---|---|---|
| Evening (`skipEmpty:false`) | fallback note | fallback note | AI |
| Midday (`skipEmpty:true`) | skip | fallback note | AI |
| Manual (`skipEmpty:false`) | fallback note | fallback note | AI |

The fallback write goes through the existing `writeInsights` unchanged:
- Evening mode → writes `today` to `insights/{date}` **and** a generic
  `tomorrow` block to `insights/{date+1}`.
- `todayOnly` mode (midday/manual) → writes only the `today` block.

### 2. Fallback builder — new `functions/src/insights/fallback.ts`

```
buildFallbackInsights(day: DayData): ParsedInsights
```

Pure, deterministic, Hebrew. Returns the exact `{ today, tomorrow, disclaimer }`
shape that `parseInsights` would produce, so it is interchangeable with AI output
downstream. Attaches the fixed `DISCLAIMER_HE`.

Field content:

- **today.general** — overall encouragement to log meals (and water) so a real
  daily summary can be produced.
- **today.nutrition** — gentle prompt to log today's meals; mention water.
- **today.activity** — goal-linked:
  - Steps vs daily steps goal.
  - Weekly aerobic minutes vs 150 min, weekly strength workouts vs 2.
  - When activity exists, acknowledge progress; when it does not, gently nudge
    toward the goals.
- **today.sleep** — gentle generic note.
- **tomorrow.nutrition / activity / sleep** — generic encouragement (log
  meals/water tomorrow; keep moving toward the weekly goals).

### 3. Server-side goals — new `functions/src/insights/goals.ts`

Extract goal constants reused by both the insights prompt and the fallback:

- `WEEKLY_AEROBIC_GOAL_MIN = 150`
- `WEEKLY_STRENGTH_GOAL = 2`
- `DAILY_STEPS_GOAL = 8000` (mirrors client `GoalCalculator.DEFAULT_STEPS`)

The daily steps goal honors `profile.goalOverrides.steps` when present; the
weekly goals stay fixed constants (matching today's hardcoded prompt values).

Supporting change: extend `aggregate.ts` so `buildProfile` extracts
`goalOverrides.steps` into `DayProfile` (new optional field, e.g.
`stepsGoalOverride?: number`). `prompts.ts` `buildInsightsUserPrompt` /
`buildInsightsSystemInstruction` reference the new constants instead of inline
`150` / `2` literals so there is a single source of truth.

### 4. Observability — `functions/src/insights/core.ts` + `generateInsights.ts`

- Add `"fallback"` to `RunResult.status`.
- `runForAllUsers` counts `fallback` alongside `written` / `skipped` / `failed`
  in the `insights batch complete` log line.
- Log a distinct `info` line when a fallback note is written
  (`uid`, `date`, `trigger`).

## Error Handling

The fallback path performs no external (Vertex) call, so its only failure mode
is the Firestore write — handled by the existing `try/catch` in
`runInsightsForUser`, returning `{ status: "failed" }` and preserving any
existing insights document for the next run, exactly as the AI path does.

## Testing

- `functions/test/insightsCore.test.ts`
  - No-meals day writes a fallback note **without** invoking the injected
    `generate` dependency.
  - No-meals evening run writes both today and tomorrow (via the write spy).
  - Meals day still invokes `generate` (AI path unchanged).
  - Midday totally-empty day still returns `skipped`.
- New `functions/test/fallback.test.ts`
  - `buildFallbackInsights` returns all required fields, in Hebrew, with the
    fixed disclaimer.
  - Activity field reflects goal progress when activity exists, and nudges when
    it does not.
  - `goalOverrides.steps` overrides the default daily steps goal in the activity
    line.
- Update `functions/test/prompt.test.ts` if assertions depend on the inline
  `150` / `2` literals now sourced from `goals.ts`.

Phase rule: the phase is not "done" until `npm test` (functions) passes.

## Files Touched

- `functions/src/insights/fallback.ts` (new)
- `functions/src/insights/goals.ts` (new)
- `functions/src/insights/core.ts`
- `functions/src/insights/aggregate.ts`
- `functions/src/prompts.ts`
- `functions/src/generateInsights.ts`
- `functions/test/insightsCore.test.ts`
- `functions/test/fallback.test.ts` (new)
- `functions/test/prompt.test.ts` (if affected)
