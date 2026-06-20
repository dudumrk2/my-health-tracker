import { validateRequest, ValidationError } from "../src/validation";

describe("validateRequest", () => {
  it("accepts a valid text request", () => {
    const r = validateRequest({ inputType: "text", text: "2 eggs", date: "2026-06-13" });
    expect(r.inputType).toBe("text");
    expect(r.text).toBe("2 eggs");
    expect(r.date).toBe("2026-06-13");
  });

  it("accepts a valid image request", () => {
    const r = validateRequest({ inputType: "image", imageBase64: "abc", date: "2026-06-13" });
    expect(r.inputType).toBe("image");
    expect(r.imageBase64).toBe("abc");
  });

  it("rejects unknown inputType", () => {
    expect(() => validateRequest({ inputType: "audio", date: "2026-06-13" })).toThrow(ValidationError);
  });

  it("rejects text mode with empty text", () => {
    expect(() => validateRequest({ inputType: "text", text: "   ", date: "2026-06-13" })).toThrow(ValidationError);
  });

  it("rejects image mode with missing imageBase64", () => {
    expect(() => validateRequest({ inputType: "image", date: "2026-06-13" })).toThrow(ValidationError);
  });

  it("rejects a bad date format", () => {
    expect(() => validateRequest({ inputType: "text", text: "x", date: "13/06/2026" })).toThrow(ValidationError);
  });

  it("rejects a null payload", () => {
    expect(() => validateRequest(null)).toThrow(ValidationError);
  });

  it("keeps a trimmed note on an image request", () => {
    const r = validateRequest({ inputType: "image", imageBase64: "abc", text: "  עם רוטב טחינה  ", date: "2026-06-13" });
    expect(r.inputType).toBe("image");
    expect(r.imageBase64).toBe("abc");
    expect(r.text).toBe("עם רוטב טחינה");
  });

  it("omits the note on an image request when it is blank", () => {
    const r = validateRequest({ inputType: "image", imageBase64: "abc", text: "   ", date: "2026-06-13" });
    expect(r.text).toBeUndefined();
  });

  it("omits the note on an image request when it is absent", () => {
    const r = validateRequest({ inputType: "image", imageBase64: "abc", date: "2026-06-13" });
    expect(r.text).toBeUndefined();
  });

  it("rejects an image note longer than 500 characters", () => {
    const long = "a".repeat(501);
    expect(() => validateRequest({ inputType: "image", imageBase64: "abc", text: long, date: "2026-06-13" })).toThrow(ValidationError);
  });
});
