import { HttpsError } from "firebase-functions/v2/https";
import { mapInsightsError } from "../src/generateInsights";

describe("mapInsightsError", () => {
  it("maps a 429 quota error to resource-exhausted HttpsError", () => {
    const r = mapInsightsError({ code: 429, message: "Resource exhausted" });
    expect(r).toBeInstanceOf(HttpsError);
    expect(r.code).toBe("resource-exhausted");
    expect(r.message).toBe("שירות ה-AI עמוס כרגע. נסה שוב בעוד רגע.");
  });

  it("maps a generic Error to internal HttpsError", () => {
    const r = mapInsightsError(new Error("vertex blew up"));
    expect(r).toBeInstanceOf(HttpsError);
    expect(r.code).toBe("internal");
    expect(r.message).toBe("לא ניתן לרענן את התובנות כרגע. נסה שוב מאוחר יותר.");
  });

  it("maps an arbitrary object to internal HttpsError", () => {
    const r = mapInsightsError({ status: "500" });
    expect(r.code).toBe("internal");
  });
});
