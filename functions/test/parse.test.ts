import { parseGeminiResult, ParseError } from "../src/parse";

describe("parseGeminiResult", () => {
  it("parses valid JSON and recomputes totals from items", () => {
    const raw = JSON.stringify({
      items: [
        { name: "Egg", quantity: "2", calories: 140, proteinG: 12, carbsG: 1, fatG: 10 },
        { name: "Toast", quantity: "1 slice", calories: 80, proteinG: 3, carbsG: 15, fatG: 1 },
      ],
      lowConfidence: false,
    });
    const r = parseGeminiResult(raw);
    expect(r.items).toHaveLength(2);
    expect(r.totals).toEqual({ calories: 220, proteinG: 15, carbsG: 16, fatG: 11 });
    expect(r.lowConfidence).toBe(false);
  });

  it("handles no-food result as empty items with zero totals", () => {
    const r = parseGeminiResult(JSON.stringify({ items: [], lowConfidence: false }));
    expect(r.items).toHaveLength(0);
    expect(r.totals).toEqual({ calories: 0, proteinG: 0, carbsG: 0, fatG: 0 });
  });

  it("throws ParseError on non-JSON output", () => {
    expect(() => parseGeminiResult("here is your meal: ...")).toThrow(ParseError);
  });

  it("throws ParseError when items is missing", () => {
    expect(() => parseGeminiResult(JSON.stringify({ lowConfidence: true }))).toThrow(ParseError);
  });

  it("coerces missing numeric fields to 0 and missing lowConfidence to false", () => {
    const raw = JSON.stringify({ items: [{ name: "X", quantity: "1" }] });
    const r = parseGeminiResult(raw);
    expect(r.items[0].calories).toBe(0);
    expect(r.lowConfidence).toBe(false);
  });
});
