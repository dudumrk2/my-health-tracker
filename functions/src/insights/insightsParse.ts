export class InsightsParseError extends Error {}

export interface InsightToday {
  general: string;
  nutrition: string;
  activity: string;
  sleep: string;
}

export interface InsightTomorrow {
  nutrition: string;
  activity: string;
  sleep: string;
}

export interface ParsedInsights {
  today: InsightToday;
  tomorrow: InsightTomorrow;
  disclaimer: string;
}

/** Fixed, non-medical disclaimer. Always written server-side, never taken from model output. */
export const DISCLAIMER_HE =
  "התובנות הן מידע כללי בלבד ואינן מהוות ייעוץ רפואי או תזונתי. להחלטות בריאות יש להתייעץ עם איש מקצוע מוסמך.";

function requireSentence(obj: Record<string, unknown>, block: string, field: string): string {
  const v = obj[field];
  if (typeof v !== "string" || v.trim().length === 0) {
    throw new InsightsParseError(`Missing or empty field '${block}.${field}'.`);
  }
  return v.trim();
}

function requireObject(parent: Record<string, unknown>, key: string): Record<string, unknown> {
  const v = parent[key];
  if (typeof v !== "object" || v === null) {
    throw new InsightsParseError(`Missing or invalid '${key}' block.`);
  }
  return v as Record<string, unknown>;
}

/** Parses the split model output, validating every category field, and attaches the fixed disclaimer. */
export function parseInsights(raw: string): ParsedInsights {
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    throw new InsightsParseError("Model did not return valid JSON.");
  }
  if (typeof parsed !== "object" || parsed === null) {
    throw new InsightsParseError("Model output is not an object.");
  }
  const obj = parsed as Record<string, unknown>;

  const todayRaw = requireObject(obj, "today");
  const tomorrowRaw = requireObject(obj, "tomorrow");

  const today: InsightToday = {
    general: requireSentence(todayRaw, "today", "general"),
    nutrition: requireSentence(todayRaw, "today", "nutrition"),
    activity: requireSentence(todayRaw, "today", "activity"),
    sleep: requireSentence(todayRaw, "today", "sleep"),
  };

  const tomorrow: InsightTomorrow = {
    nutrition: requireSentence(tomorrowRaw, "tomorrow", "nutrition"),
    activity: requireSentence(tomorrowRaw, "tomorrow", "activity"),
    sleep: requireSentence(tomorrowRaw, "tomorrow", "sleep"),
  };

  return { today, tomorrow, disclaimer: DISCLAIMER_HE };
}
