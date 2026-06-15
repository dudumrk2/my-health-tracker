import { buildMealSystemInstruction, MEAL_RESPONSE_SCHEMA, ProfileContext } from "../src/prompts";

describe("buildMealSystemInstruction", () => {
  it("includes Contract A safety constraints", () => {
    const s = buildMealSystemInstruction(null);
    expect(s).toMatch(/JSON/i);
    expect(s).toMatch(/items/);
    expect(s).toMatch(/lowConfidence/);
    expect(s.toLowerCase()).toContain("empty");
  });

  it("includes profile context when provided", () => {
    const p: ProfileContext = { weightKg: 80, heightCm: 180, gender: "male" };
    const s = buildMealSystemInstruction(p);
    expect(s).toContain("80");
    expect(s).toContain("180");
  });
});

describe("MEAL_RESPONSE_SCHEMA", () => {
  it("requires items array and lowConfidence", () => {
    expect(MEAL_RESPONSE_SCHEMA.properties.items).toBeDefined();
    expect(MEAL_RESPONSE_SCHEMA.properties.lowConfidence).toBeDefined();
  });
});
