export type PlatformCredentialInput = 'apiKey' | 'primaryKeyRef'

export function switchPlatformCredentialSource(
  changed: PlatformCredentialInput,
  apiKey: string,
  primaryKeyRef: string,
) {
  if (changed === 'apiKey' && apiKey.trim()) {
    return { apiKey, primaryKeyRef: '' }
  }
  if (changed === 'primaryKeyRef' && primaryKeyRef.trim()) {
    return { apiKey: '', primaryKeyRef }
  }
  return { apiKey, primaryKeyRef }
}

export function platformCredentialClearFlags(apiKey: string, primaryKeyRef: string) {
  return {
    clearApiKey: Boolean(primaryKeyRef.trim()),
    clearPrimaryKeyRef: Boolean(apiKey.trim()),
  }
}
