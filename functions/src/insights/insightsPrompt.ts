import { SchemaType } from "@google-cloud/vertexai";
import { DayData } from "./aggregate";

/**
 * Split response schema (Contract B). The model returns one focused sentence per
 * category. The `disclaimer` is NOT requested from the model — it is a fixed
 * server-side constant (see insightsParse.DISCLAIMER_HE).
 */
export const RESPONSE_SCHEMA = {
  type: SchemaType.OBJECT,
  properties: {
    today: {
      type: SchemaType.OBJECT,
      properties: {
        general: { type: SchemaType.STRING },
        nutrition: { type: SchemaType.STRING },
        activity: { type: SchemaType.STRING },
        sleep: { type: SchemaType.STRING },
      },
      required: ["general", "nutrition", "activity", "sleep"],
    },
    tomorrow: {
      type: SchemaType.OBJECT,
      properties: {
        nutrition: { type: SchemaType.STRING },
        activity: { type: SchemaType.STRING },
        sleep: { type: SchemaType.STRING },
      },
      required: ["nutrition", "activity", "sleep"],
    },
  },
  required: ["today", "tomorrow"],
} as const;

/** Contract B — supportive daily-feedback coach. Output language is Hebrew (product language). */
export function buildSystemInstruction(): string {
  return [
    "You are a supportive personal health coach giving general daily feedback.",
    "You speak to a single private user about their own day.",
    "Output rules:",
    "- Respond with JSON only, matching the provided schema. No markdown, no wrapping text.",
    "- Write every sentence in Hebrew. Each field is exactly ONE short, focused sentence.",
    "- 'today' summarizes how the day went so far; 'tomorrow' gives gentle emphases/prep for the next day.",
    "- 'general' (today only) is a one-sentence overall reflection across nutrition, activity and sleep.",
    "Tone & safety:",
    "- Supportive and non-judgmental. Prefer suggestions ('כדאי לשקול') over commands ('אתה חייב').",
    "- Base feedback ONLY on the data provided below. Do not invent numbers or hard targets that were not given.",
    "- This is NOT medical or personalized dietary advice. Do not diagnose or prescribe diets.",
    "- If a category has no data, give a gentle, encouraging note for that category rather than a number.",
  ].join("\n");
}

/** Compact, deterministic data summary fed to the model as the user turn. */
export function buildUserPrompt(day: DayData): string {
  const p = day.profile;
  const profileLine = p
    ? `Profile: gender ${p.gender ?? "?"}, age ${p.age ?? "?"}, weight ${p.weightKg ?? "?"} kg, height ${p.heightCm ?? "?"} cm.`
    : "Profile: not available.";

  const workoutLine =
    day.workouts.length > 0
      ? day.workouts.map((w) => `${w.type} ${w.durationMin}min`).join(", ")
      : "none";

  const t = day.meals.totals;
  const mealLine =
    day.meals.count > 0
      ? `${day.meals.count} meal(s), totals ~${t.calories} kcal, protein ${t.proteinG} g, carbs ${t.carbsG} g, fat ${t.fatG} g`
      : "no meals logged yet";

  return [
    `Date: ${day.date}.`,
    profileLine,
    `Activity: ${day.steps} steps. Workouts: ${workoutLine}.`,
    `Sleep: ${day.sleepMinutes} minutes${day.sleepMinutes === 0 ? " (no sleep data)" : ""}.`,
    `Nutrition: ${mealLine}.`,
    `Water: ${day.waterMl} ml.`,
    "Produce focused, supportive one-sentence insights per the schema, in Hebrew.",
  ].join("\n");
}
