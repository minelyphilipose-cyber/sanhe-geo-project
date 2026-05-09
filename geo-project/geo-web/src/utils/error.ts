export function errorMessage(err: unknown, fallback: string) {
  const data = (err as any)?.response?.data
  const status = (err as any)?.response?.status ?? (err as any)?.status
  const code = (err as any)?.code ?? data?.code
  if (status === 401 || code === 401) {
    return data?.message && data.message !== 'Unauthorized'
      ? data.message
      : '登录状态已失效，请重新登录后重试'
  }
  return data?.message || data?.msg || (err as any)?.message || fallback
}
