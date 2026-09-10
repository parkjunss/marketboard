type Poster = <T>(path: string, options: { method: string; headers: Record<string, string>; body: unknown }) => Promise<T>;
const inFlight = new Map<string, Promise<unknown>>();

/** Keep the key across uncertain responses/reloads; only an acknowledged success releases it. */
export async function idempotentPost<T>(fetcher: Poster, path: string, body: unknown, userId: string): Promise<T> {
  const bytes = new TextEncoder().encode(JSON.stringify(body));
  const hash = Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)), byte => byte.toString(16).padStart(2, '0')).join('');
  const slot = `marketboard.request:${userId}:${path}:${hash}`;
  const active = inFlight.get(slot);
  if (active) return active as Promise<T>;
  const key = sessionStorage.getItem(slot) ?? crypto.randomUUID();
  sessionStorage.setItem(slot, key);
  const promise = fetcher<T>(path, { method: 'POST', headers: { 'Idempotency-Key': key }, body }).then(response => {
    sessionStorage.removeItem(slot);
    return response;
  }).finally(() => { inFlight.delete(slot); });
  inFlight.set(slot, promise);
  return promise;
}
