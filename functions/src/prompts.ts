// functions/src/prompts.ts
//
// Single source of truth for every prompt we send to the AI and for which model
// we run against. All AI calls are server-side only (keys never reach the client).
//
//   - GEMINI_MODEL ............. the model id all functions use
//   - Contract A (meals) ....... analyzeMeal: system instruction + schema + user turns
//   - Contract B (insights) .... generateInsights: system instruction + user prompt + schema
import { SchemaType } from "@google-cloud/vertexai";
import { DayData } from "./insights/aggregate";

/** The Gemini model every function runs against. Override per environment via GEMINI_MODEL. */
export const GEMINI_MODEL = process.env.GEMINI_MODEL || "gemini-2.5-flash";

// ───────────────────────────── Contract A — meals (analyzeMeal) ─────────────────────────────

export interface ProfileContext {
  weightKg?: number;
  heightCm?: number;
  gender?: string;
}

export const MEAL_RESPONSE_SCHEMA = {
  type: SchemaType.OBJECT,
  properties: {
    items: {
      type: SchemaType.ARRAY,
      items: {
        type: SchemaType.OBJECT,
        properties: {
          name: { type: SchemaType.STRING },
          quantity: { type: SchemaType.STRING },
          calories: { type: SchemaType.NUMBER },
          proteinG: { type: SchemaType.NUMBER },
          carbsG: { type: SchemaType.NUMBER },
          fatG: { type: SchemaType.NUMBER },
        },
        required: ["name", "quantity", "calories", "proteinG", "carbsG", "fatG"],
      },
    },
    recommendation: { type: SchemaType.STRING },
    quality: {
      type: SchemaType.OBJECT,
      properties: {
        processedScore: { type: SchemaType.NUMBER },
        hasComplexCarbs: { type: SchemaType.BOOLEAN },
        hasSimpleCarbs: { type: SchemaType.BOOLEAN },
        hasHealthyFats: { type: SchemaType.BOOLEAN },
        insulinImpact: { type: SchemaType.STRING },
      },
      required: ["processedScore", "hasComplexCarbs", "hasSimpleCarbs", "hasHealthyFats", "insulinImpact"],
    },
    lowConfidence: { type: SchemaType.BOOLEAN },
  },
  required: ["items", "recommendation", "quality", "lowConfidence"],
} as const;

export function buildMealSystemInstruction(profile: ProfileContext | null): string {
  const profileLine = profile
    ? `User context for portion estimation: weight ${profile.weightKg ?? "?"} kg, height ${profile.heightCm ?? "?"} cm, gender ${profile.gender ?? "?"}.`
    : "No user profile available.";

  return [
    "You are a nutrition analyzer. Identify food items and estimate their nutritional values.",
    profileLine,
    "Rules:",
    "- Respond with JSON only, matching the provided schema. No markdown, no wrapping text.",
    "- Write the 'name' and 'quantity' fields in Hebrew (the product language). Keep all numeric values as plain numbers.",
    "- Use metric units. Quantity is a short human-readable string in Hebrew (e.g. '150 גרם', '1 כוס').",
    "- In the 'recommendation' field, provide a single, focused, actionable recommendation in Hebrew (product language) for adding an ingredient or side dish that would upgrade the meal nutritionally (e.g. adding protein, healthy fats, fiber, vegetables, or balancing the glycemic index). Limit it to one short sentence. If the input contains no food, set the recommendation field to an empty string.",
    "- In the 'quality' object, evaluate the nutritional quality of the whole meal:",
    "  * processedScore: 1 (fully unprocessed/whole foods like raw fruit/vegetables/pure meat) to 5 (highly ultra-processed foods like snacks/sweet drinks/processed meats).",
    "  * hasComplexCarbs: true if the meal contains whole grains, legumes, or starchy vegetables.",
    "  * hasSimpleCarbs: true if the meal contains refined flour, sugar, sweets, or high-sugar processed foods.",
    "  * hasHealthyFats: true if the meal contains olive oil, avocado, tahini, nuts, or fatty fish.",
    "  * insulinImpact: 'low' (minimal blood sugar spike, high in protein/fiber/healthy fats), 'medium' (moderate spike), or 'high' (rapid blood sugar/insulin spike, high in refined carbs/sugar).",
    "- Estimate values when uncertain; set lowConfidence=true when the identification or amounts are uncertain.",
    "- If the input (text or image) contains no food, return an empty items array, lowConfidence=false, empty recommendation, and processedScore=1, hasComplexCarbs=false, hasSimpleCarbs=false, hasHealthyFats=false, insulinImpact='low'.",
    "- Do NOT diagnose, give medical advice, or recommend diets. Only identify and estimate.",
  ].join("\n");
}

/** User-turn text for a text-described meal. */
export function mealTextPrompt(text: string): string {
  return `Analyze this meal description: ${text}`;
}

/** User-turn text accompanying an inline meal image. */
export function mealImagePrompt(): string {
  return "Analyze the food in this image.";
}

// ──────────────────────────── Contract B — insights (generateInsights) ──────────────────────

/**
 * Split response schema. The model returns one focused sentence per category.
 * The `disclaimer` is NOT requested from the model — it is a fixed server-side
 * constant (see insights/insightsParse.DISCLAIMER_HE).
 */
export const INSIGHTS_RESPONSE_SCHEMA = {
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
export function buildInsightsSystemInstruction(): string {
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
    "Demographics & Special Context Guidelines:",
    "- If the user profile indicates a FEMALE aged 40 or older, she is in the pre-menopause/menopause demographic. For this profile, tailor recommendations to their specific metabolic changes:",
    "  * Satiety signals in the brain decrease due to low estrogen. Write in a highly empathetic tone, making it clear that weight fluctuations or hunger are physiological, not a personal failure.",
    "  * Highly emphasize an insulin-lowering diet: recommend eating healthy fats (tahini, avocado, olive oil, nuts), vegetables, and high-quality protein, while avoiding insulin-spiking carbs/sugar.",
    "  * Highly emphasize strength/resistance training to prevent the muscle loss caused by hormonal drops.",
    "Weekly Exercise Goals Guidance:",
    "- Evaluate the user's weekly exercise progress: Aerobic goal is 150+ minutes (vital for visceral fat reduction), and Strength/Resistance goal is at least 2 workouts.",
    "- If the weekly aerobic minutes are low, gently encourage cardiorespiratory activity (e.g. fast walking, cycling, running).",
    "- If weekly strength workouts are below 2, gently suggest adding a resistance/strength session to protect muscle and support bone density.",
  ].join("\n");
}

/** Compact, deterministic data summary fed to the model as the user turn. */
export function buildInsightsUserPrompt(day: DayData): string {
  const p = day.profile;
  const profileLine = p
    ? `Profile: gender ${p.gender ?? "?"}, age ${p.age ?? "?"}, weight ${p.weightKg ?? "?"} kg, height ${p.heightCm ?? "?"} cm.`
    : "Profile: not available.";

  const workoutLine =
    day.workouts.length > 0
      ? day.workouts.map((w) => `${w.type} ${w.durationMin}min`).join(", ")
      : "none";

  const weeklyLine = `Weekly totals: ${day.weeklyAerobicMinutes} min aerobic exercise (goal: 150 min), ${day.weeklyStrengthWorkouts} strength workouts (goal: 2).`;

  const t = day.meals.totals;
  const mealLine =
    day.meals.count > 0
      ? `${day.meals.count} meal(s), totals ~${t.calories} kcal, protein ${t.proteinG} g, carbs ${t.carbsG} g, fat ${t.fatG} g`
      : "no meals logged yet";

  return [
    `Date: ${day.date}.`,
    profileLine,
    `Activity: ${day.steps} steps. Workouts: ${workoutLine}.`,
    weeklyLine,
    `Sleep: ${day.sleepMinutes} minutes${day.sleepMinutes === 0 ? " (no sleep data)" : ""}.`,
    `Nutrition: ${mealLine}.`,
    `Water: ${day.waterMl} ml.`,
    "Produce focused, supportive one-sentence insights per the schema, in Hebrew.",
  ].join("\n");
}
