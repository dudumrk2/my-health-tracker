export class ValidationError extends Error {}

export interface AnalyzeMealRequest {
  inputType: "text" | "image";
  text?: string;
  imageBase64?: string;
  date: string;
}

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

export function validateRequest(data: unknown): AnalyzeMealRequest {
  if (typeof data !== "object" || data === null) {
    throw new ValidationError("Request body is missing.");
  }
  const d = data as Record<string, unknown>;

  const inputType = d.inputType;
  if (inputType !== "text" && inputType !== "image") {
    throw new ValidationError("inputType must be 'text' or 'image'.");
  }

  const date = d.date;
  if (typeof date !== "string" || !DATE_RE.test(date)) {
    throw new ValidationError("date must be in yyyy-MM-dd format.");
  }

  if (inputType === "text") {
    const text = d.text;
    if (typeof text !== "string" || text.trim().length === 0) {
      throw new ValidationError("text is required for text input.");
    }
    return { inputType, text: text.trim(), date };
  }

  const imageBase64 = d.imageBase64;
  if (typeof imageBase64 !== "string" || imageBase64.trim().length === 0) {
    throw new ValidationError("imageBase64 is required for image input.");
  }
  return { inputType, imageBase64, date };
}
