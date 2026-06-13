import { HttpsError } from "firebase-functions/v2/https";
import { mapErrorToHttpsError } from "../src/analyzeMeal";

describe("mapErrorToHttpsError", () => {
  it("maps a 429 quota error to resource-exhausted HttpsError", () => {
    const error = { code: 429, message: "Resource exhausted" };
    const result = mapErrorToHttpsError(error);
    expect(result).toBeInstanceOf(HttpsError);
    expect(result.code).toBe("resource-exhausted");
    expect(result.message).toBe("AI service is busy. Please try again shortly.");
  });

  it("maps standard Error to internal HttpsError", () => {
    const error = new Error("Gemini API connection timeout or other internal error");
    const result = mapErrorToHttpsError(error);
    expect(result).toBeInstanceOf(HttpsError);
    expect(result.code).toBe("internal");
    expect(result.message).toBe("Could not analyze the meal. You can enter it manually.");
  });

  it("maps arbitrary object error to internal HttpsError", () => {
    const error = { status: "500", detail: "Internal Server Error" };
    const result = mapErrorToHttpsError(error);
    expect(result).toBeInstanceOf(HttpsError);
    expect(result.code).toBe("internal");
    expect(result.message).toBe("Could not analyze the meal. You can enter it manually.");
  });
});
