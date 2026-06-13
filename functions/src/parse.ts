export class ParseError extends Error {}

export interface MealItem {
  name: string;
  quantity: string;
  calories: number;
  proteinG: number;
  carbsG: number;
  fatG: number;
}

export interface MealTotals {
  calories: number;
  proteinG: number;
  carbsG: number;
  fatG: number;
}

export interface MealResult {
  items: MealItem[];
  totals: MealTotals;
  lowConfidence: boolean;
}

function num(v: unknown): number {
  return typeof v === "number" && isFinite(v) ? Math.round(v) : 0;
}

function str(v: unknown): string {
  return typeof v === "string" ? v : "";
}

export function parseGeminiResult(raw: string): MealResult {
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    throw new ParseError("Model did not return valid JSON.");
  }
  if (typeof parsed !== "object" || parsed === null) {
    throw new ParseError("Model output is not an object.");
  }
  const obj = parsed as Record<string, unknown>;
  if (!Array.isArray(obj.items)) {
    throw new ParseError("Model output is missing items array.");
  }

  const items: MealItem[] = obj.items.map((it) => {
    const o = (it ?? {}) as Record<string, unknown>;
    return {
      name: str(o.name),
      quantity: str(o.quantity),
      calories: num(o.calories),
      proteinG: num(o.proteinG),
      carbsG: num(o.carbsG),
      fatG: num(o.fatG),
    };
  });

  const totals = items.reduce<MealTotals>(
    (acc, it) => ({
      calories: acc.calories + it.calories,
      proteinG: acc.proteinG + it.proteinG,
      carbsG: acc.carbsG + it.carbsG,
      fatG: acc.fatG + it.fatG,
    }),
    { calories: 0, proteinG: 0, carbsG: 0, fatG: 0 }
  );

  return { items, totals, lowConfidence: obj.lowConfidence === true };
}
