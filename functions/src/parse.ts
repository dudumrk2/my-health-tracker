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

export interface MealQuality {
  processedScore: number;
  hasComplexCarbs: boolean;
  hasSimpleCarbs: boolean;
  hasHealthyFats: boolean;
  insulinImpact: string;
}

export interface MealResult {
  items: MealItem[];
  totals: MealTotals;
  recommendation: string;
  quality: MealQuality;
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

  const q = (obj.quality ?? {}) as Record<string, unknown>;
  const quality: MealQuality = {
    processedScore: typeof q.processedScore === "number" ? Math.round(q.processedScore) : 1,
    hasComplexCarbs: q.hasComplexCarbs === true,
    hasSimpleCarbs: q.hasSimpleCarbs === true,
    hasHealthyFats: q.hasHealthyFats === true,
    insulinImpact: typeof q.insulinImpact === "string" ? q.insulinImpact : "low",
  };

  return {
    items,
    totals,
    recommendation: str(obj.recommendation),
    quality,
    lowConfidence: obj.lowConfidence === true,
  };
}
