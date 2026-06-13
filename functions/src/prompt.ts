import { SchemaType } from "@google-cloud/vertexai";

export interface ProfileContext {
  weightKg?: number;
  heightCm?: number;
  gender?: string;
}

export const RESPONSE_SCHEMA = {
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
    lowConfidence: { type: SchemaType.BOOLEAN },
  },
  required: ["items", "lowConfidence"],
} as const;

export function buildSystemInstruction(profile: ProfileContext | null): string {
  const profileLine = profile
    ? `User context for portion estimation: weight ${profile.weightKg ?? "?"} kg, height ${profile.heightCm ?? "?"} cm, gender ${profile.gender ?? "?"}.`
    : "No user profile available.";

  return [
    "You are a nutrition analyzer. Identify food items and estimate their nutritional values.",
    profileLine,
    "Rules:",
    "- Respond with JSON only, matching the provided schema. No markdown, no wrapping text.",
    "- Use metric units. Quantity is a short human-readable string (e.g. '150 g', '1 cup').",
    "- Estimate values when uncertain; set lowConfidence=true when the identification or amounts are uncertain.",
    "- If the input (text or image) contains no food, return an empty items array and lowConfidence=false.",
    "- Do NOT diagnose, give medical advice, or recommend diets. Only identify and estimate.",
  ].join("\n");
}
