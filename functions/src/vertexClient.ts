// functions/src/vertexClient.ts
export class TimeoutError extends Error {
  constructor() {
    super("Vertex call timed out.");
  }
}

export function isRetryable(err: unknown): boolean {
  if (err instanceof TimeoutError) return true;
  const code = (err as { code?: number })?.code;
  return code === 429 || code === 503;
}

export function withTimeout<T>(promise: Promise<T>, ms: number): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(() => reject(new TimeoutError()), ms);
    promise.then(
      (v) => {
        clearTimeout(timer);
        resolve(v);
      },
      (e) => {
        clearTimeout(timer);
        reject(e);
      }
    );
  });
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

export async function callWithRetry<T>(
  fn: () => Promise<T>,
  opts: { retries: number; baseDelayMs: number }
): Promise<T> {
  let attempt = 0;
  for (;;) {
    try {
      return await fn();
    } catch (err) {
      if (attempt >= opts.retries || !isRetryable(err)) throw err;
      await sleep(opts.baseDelayMs * Math.pow(2, attempt));
      attempt++;
    }
  }
}
