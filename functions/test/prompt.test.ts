import { buildSystemInstruction, RESPONSE_SCHEMA, ProfileContext } from "../src/prompt";

describe("buildSystemInstruction", () => {
  it("includes Contract A safety constraints", () => {
    const s = buildSystemInstruction(null);
    expect(s).toMatch(/JSON/i);
    expect(s).toMatch(/items/);
    expect(s).toMatch(/lowConfidence/);
    expect(s.toLowerCase()).toContain("empty");
  });

  it("includes profile context when provided", () => {
    const p: ProfileContext = { weightKg: 80, heightCm: 180, gender: "male" };
    const s = buildSystemInstruction(p);
    expect(s).toContain("80");
    expect(s).toContain("180");
  });
});

describe("RESPONSE_SCHEMA", () => {
  it("requires items array and lowConfidence", () => {
    expect(RESPONSE_SCHEMA.properties.items).toBeDefined();
    expect(RESPONSE_SCHEMA.properties.lowConfidence).toBeDefined();
  });
});
