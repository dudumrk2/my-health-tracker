import { logger } from "firebase-functions/v2";
import { DayData } from "./aggregate";
import { buildSystemInstruction, buildUserPrompt } from "./insightsPrompt";
import { parseInsights, ParsedInsights, InsightsParseError } from "./insightsParse";
import { WriteMode } from "./writeInsights";

export interface RunOptions {
  mode: WriteMode;
  trigger: string;
  /** When true, an empty day is skipped (no Gemini call, no write) — used by the midday run. */
  skipEmpty: boolean;
}

export interface RunResult {
  status: "written" | "skipped" | "failed";
}

/** Dependencies injected so the orchestration is unit-testable without Firestore/Vertex. */
export interface InsightsRunDeps {
  fetchDayData: (uid: string, date: string) => Promise<DayData>;
  generate: (systemInstruction: string, userPrompt: string) => Promise<string>;
  write: (
    uid: string,
    date: string,
    parsed: ParsedInsights,
    mode: WriteMode,
    trigger: string
  ) => Promise<void>;
}

/**
 * Orchestrates aggregate → Gemini → parse → write for a single user/date.
 * On any failure (Gemini error or unparseable output) nothing is written, so a
 * pre-existing insights document is preserved and the next scheduled run retries.
 */
export async function runInsightsForUser(
  uid: string,
  date: string,
  opts: RunOptions,
  deps: InsightsRunDeps
): Promise<RunResult> {
  const started = Date.now();
  try {
    const day = await deps.fetchDayData(uid, date);

    if (opts.skipEmpty && day.isEmpty) {
      logger.info("insights skip empty day", { uid, date, trigger: opts.trigger });
      return { status: "skipped" };
    }

    const raw = await deps.generate(buildSystemInstruction(), buildUserPrompt(day));
    const parsed = parseInsights(raw);
    await deps.write(uid, date, parsed, opts.mode, opts.trigger);

    logger.info("insights written", {
      uid, date, mode: opts.mode, trigger: opts.trigger, durationMs: Date.now() - started,
    });
    return { status: "written" };
  } catch (e) {
    logger.error("insights run failed", {
      uid, date, trigger: opts.trigger, durationMs: Date.now() - started,
      kind: e instanceof InsightsParseError ? "parse" : "vertex",
      message: (e as Error).message, // safe: our message, not raw model/PII
    });
    return { status: "failed" };
  }
}
