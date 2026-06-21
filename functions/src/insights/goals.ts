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
