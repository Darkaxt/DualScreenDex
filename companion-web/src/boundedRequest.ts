export async function boundedRequest<T>(
  request: Promise<T>,
  timeoutMillis: number,
  timeoutMessage: string,
): Promise<T> {
  if (!Number.isFinite(timeoutMillis) || timeoutMillis <= 0) {
    throw new RangeError('Request timeout must be positive');
  }
  let timer: number | undefined;
  const timeout = new Promise<never>((_resolve, reject) => {
    timer = window.setTimeout(() => reject(new Error(timeoutMessage)), timeoutMillis);
  });
  try {
    return await Promise.race([request, timeout]);
  } finally {
    if (timer != null) window.clearTimeout(timer);
  }
}
