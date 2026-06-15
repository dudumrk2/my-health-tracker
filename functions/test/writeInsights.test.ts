import { writeInsights, nextDate, InsightsDb } from "../src/insights/writeInsights";
import { ParsedInsights, DISCLAIMER_HE } from "../src/insights/insightsParse";

const SERVER_TS = "__server_ts__";

const parsed = (): ParsedInsights => ({
  today: { general: "g", nutrition: "tn", activity: "ta", sleep: "ts" },
  tomorrow: { nutrition: "mn", activity: "ma", sleep: "ms" },
  disclaimer: DISCLAIMER_HE,
});

interface WriteCall {
  path: string;
  data: Record<string, unknown>;
  options: { merge: boolean };
}

function fakeDb(): { db: InsightsDb; calls: WriteCall[] } {
  const calls: WriteCall[] = [];
  const db: InsightsDb = {
    doc(path: string) {
      return {
        async set(data: Record<string, unknown>, options: { merge: boolean }) {
          calls.push({ path, data, options });
        },
      };
    },
  };
  return { db, calls };
}

describe("nextDate", () => {
  it("advances one day", () => {
    expect(nextDate("2026-06-13")).toBe("2026-06-14");
  });
  it("rolls over month boundary", () => {
    expect(nextDate("2026-06-30")).toBe("2026-07-01");
  });
  it("rolls over year boundary", () => {
    expect(nextDate("2026-12-31")).toBe("2027-01-01");
  });
});

describe("writeInsights", () => {
  it("todayOnly writes ONLY the today block (merge) and never touches tomorrow", async () => {
    const { db, calls } = fakeDb();
    await writeInsights(db, "u1", "2026-06-13", parsed(), "todayOnly", "midday", SERVER_TS);

    expect(calls).toHaveLength(1);
    const c = calls[0];
    expect(c.path).toBe("users/u1/insights/2026-06-13");
    expect(c.options).toEqual({ merge: true });
    expect(c.data.today).toEqual(parsed().today);
    expect("tomorrow" in c.data).toBe(false); // merge preserves last night's tomorrow
    expect(c.data.disclaimer).toBe(DISCLAIMER_HE);
    expect(c.data.trigger).toBe("midday");
    expect(c.data.date).toBe("2026-06-13");
    expect(c.data.generatedAt).toBe(SERVER_TS);
  });

  it("evening writes today→{D} and tomorrow→{D+1} as two merged writes", async () => {
    const { db, calls } = fakeDb();
    await writeInsights(db, "u1", "2026-06-13", parsed(), "evening", "evening", SERVER_TS);

    expect(calls).toHaveLength(2);

    const todayWrite = calls.find((c) => c.path === "users/u1/insights/2026-06-13")!;
    expect(todayWrite).toBeDefined();
    expect(todayWrite.options).toEqual({ merge: true });
    expect(todayWrite.data.today).toEqual(parsed().today);
    expect("tomorrow" in todayWrite.data).toBe(false);

    const tomorrowWrite = calls.find((c) => c.path === "users/u1/insights/2026-06-14")!;
    expect(tomorrowWrite).toBeDefined();
    expect(tomorrowWrite.options).toEqual({ merge: true });
    expect(tomorrowWrite.data.tomorrow).toEqual(parsed().tomorrow);
    expect("today" in tomorrowWrite.data).toBe(false); // does not overwrite D+1's own today
    expect(tomorrowWrite.data.date).toBe("2026-06-14");
    expect(tomorrowWrite.data.disclaimer).toBe(DISCLAIMER_HE);
  });
});
