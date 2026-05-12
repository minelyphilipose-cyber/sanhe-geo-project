export const EXTENSION_VERSION = __EXTENSION_VERSION__

const EXTENSION_PROFILE = __EXTENSION_PROFILE__

function normalizeApiBase(value: string | undefined): string {
  const fallback = EXTENSION_PROFILE.apiBaseUrl
  return (value || fallback).replace(/\/$/, '')
}

function validateApiBase(value: string): string {
  if (value !== EXTENSION_PROFILE.apiBaseUrl) {
    throw new Error(`VITE_GEO_API_BASE_URL is not allowed: ${value}`)
  }
  if (import.meta.env.VITE_GEO_EXTENSION_PROFILE === 'production' && value.startsWith('http://')) {
    throw new Error('Production extension API base must use HTTPS')
  }
  return value
}

export const API_BASE_URL = validateApiBase(normalizeApiBase(import.meta.env.VITE_GEO_API_BASE_URL))
