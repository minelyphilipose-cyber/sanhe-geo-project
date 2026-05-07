export const EXTENSION_VERSION = '0.1.0'

// TODO(Sprint 2 deploy): replace placeholder with the real production API origin and
// generate manifest host_permissions from the same allowlist.
const ALLOWED_API_BASES = [
  'http://localhost:8080',
  'https://api.example.com',
] as const

function normalizeApiBase(value: string | undefined): string {
  const fallback = import.meta.env.PROD ? 'https://api.example.com' : 'http://localhost:8080'
  return (value || fallback).replace(/\/$/, '')
}

function validateApiBase(value: string): string {
  if (!ALLOWED_API_BASES.includes(value as (typeof ALLOWED_API_BASES)[number])) {
    throw new Error(`VITE_GEO_API_BASE_URL is not allowed: ${value}`)
  }
  if (import.meta.env.PROD && value.startsWith('http://')) {
    throw new Error('Production extension API base must use HTTPS')
  }
  return value
}

export const API_BASE_URL = validateApiBase(normalizeApiBase(import.meta.env.VITE_GEO_API_BASE_URL))
