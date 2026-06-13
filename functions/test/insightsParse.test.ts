import { parseInsights, InsightsParseError, DISCLAIMER_HE } from "../src/insights/insightsParse";

const validRaw = () =>
  JSON.stringify({
    today: {
      general: "יום מאוזן בסך הכול.",
      nutrition: "צריכת החלבון נראית טובה.",
      activity: "הגעת לכמות צעדים יפה.",
      sleep: "ישנת מספר שעות סביר.",
    },
    tomorrow: {
      nutrition: "כדאי לשקול ארוחת בוקר עשירה בחלבון.",
      activity: "אפשר לשלב הליכה קצרה בבוקר.",
      sleep: "כדאי לשקול לכבות מסכים מוקדם יותר.",
    },
  });

describe("parseInsights", () => {
  it("parses a valid split output into today + tomorrow blocks", () => {
    const r = parseInsights(validRaw());
    expect(r.today.general).toBe("יום מאוזן בסך הכול.");
    expect(r.today.nutrition).toBe("צריכת החלבון נראית טובה.");
    expect(r.today.activity).toBe("הגעת לכמות צעדים יפה.");
    expect(r.today.sleep).toBe("ישנת מספר שעות סביר.");
    expect(r.tomorrow.nutrition).toBe("כדאי לשקול ארוחת בוקר עשירה בחלבון.");
    expect(r.tomorrow.activity).toBe("אפשר לשלב הליכה קצרה בבוקר.");
    expect(r.tomorrow.sleep).toBe("כדאי לשקול לכבות מסכים מוקדם יותר.");
  });

  it("always attaches the fixed server-side disclaimer (ignoring any model-provided one)", () => {
    const withModelDisclaimer = JSON.parse(validRaw());
    withModelDisclaimer.disclaimer = "תתייעץ עם רופא מיד!!"; // model output must be ignored
    const r = parseInsights(JSON.stringify(withModelDisclaimer));
    expect(r.disclaimer).toBe(DISCLAIMER_HE);
  });

  it("throws on non-JSON output", () => {
    expect(() => parseInsights("here are your insights:")).toThrow(InsightsParseError);
  });

  it("throws when a today category field is missing", () => {
    const o = JSON.parse(validRaw());
    delete o.today.sleep;
    expect(() => parseInsights(JSON.stringify(o))).toThrow(InsightsParseError);
  });

  it("throws when a tomorrow category field is missing", () => {
    const o = JSON.parse(validRaw());
    delete o.tomorrow.nutrition;
    expect(() => parseInsights(JSON.stringify(o))).toThrow(InsightsParseError);
  });

  it("throws when a category field is empty/blank", () => {
    const o = JSON.parse(validRaw());
    o.today.general = "   ";
    expect(() => parseInsights(JSON.stringify(o))).toThrow(InsightsParseError);
  });

  it("throws when the tomorrow block is entirely missing", () => {
    const o = JSON.parse(validRaw());
    delete o.tomorrow;
    expect(() => parseInsights(JSON.stringify(o))).toThrow(InsightsParseError);
  });
});
