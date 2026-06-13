import { callWithRetry, isRetryable, TimeoutError } from "../src/vertexClient";

describe("isRetryable", () => {
  it("treats 429/503/timeout as retryable", () => {
    expect(isRetryable({ code: 429 })).toBe(true);
    expect(isRetryable({ code: 503 })).toBe(true);
    expect(isRetryable(new TimeoutError())).toBe(true);
  });
  it("treats other errors as non-retryable", () => {
    expect(isRetryable({ code: 400 })).toBe(false);
    expect(isRetryable(new Error("boom"))).toBe(false);
  });
});

describe("callWithRetry", () => {
  it("returns on first success", async () => {
    const fn = jest.fn().mockResolvedValue("ok");
    await expect(callWithRetry(fn, { retries: 2, baseDelayMs: 0 })).resolves.toBe("ok");
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it("retries retryable errors up to the limit then throws", async () => {
    const fn = jest.fn().mockRejectedValue({ code: 503 });
    await expect(callWithRetry(fn, { retries: 2, baseDelayMs: 0 })).rejects.toEqual({ code: 503 });
    expect(fn).toHaveBeenCalledTimes(3); // 1 + 2 retries
  });

  it("does not retry non-retryable errors", async () => {
    const fn = jest.fn().mockRejectedValue({ code: 400 });
    await expect(callWithRetry(fn, { retries: 2, baseDelayMs: 0 })).rejects.toEqual({ code: 400 });
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it("succeeds after a transient failure", async () => {
    const fn = jest.fn().mockRejectedValueOnce({ code: 429 }).mockResolvedValue("ok");
    await expect(callWithRetry(fn, { retries: 2, baseDelayMs: 0 })).resolves.toBe("ok");
    expect(fn).toHaveBeenCalledTimes(2);
  });
});
