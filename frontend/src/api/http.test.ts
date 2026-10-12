import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiRequestError, REQUEST_TIMEOUT_MS, request } from './http';

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('request', () => {
  it('fails with TIMEOUT when the server never answers', async () => {
    vi.useFakeTimers();
    vi.stubGlobal(
      'fetch',
      vi.fn(
        (_url: string, init: RequestInit) =>
          new Promise((_resolve, reject) => {
            init.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
          }),
      ),
    );

    const outcome = request('/rounds').catch((error: unknown) => error);
    await vi.advanceTimersByTimeAsync(REQUEST_TIMEOUT_MS);

    const error = await outcome;
    expect(error).toBeInstanceOf(ApiRequestError);
    expect((error as ApiRequestError).code).toBe('TIMEOUT');
  });
});
