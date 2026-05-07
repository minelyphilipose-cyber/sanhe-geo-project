export function errorMessage(err: unknown, fallback: string) {
  const data = (err as any)?.response?.data
  return data?.message || data?.msg || (err as any)?.message || fallback
}
