// @vitest-environment node

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import request from './request'
import viteConfig, {
  DEFAULT_API_PROXY_TIMEOUT_MS,
  DIAGNOSTIC_PROXY_TIMEOUT_MS,
} from '../../vite.config.js'
import {
  DIAGNOSTIC_REQUEST_TIMEOUT_MS,
  executeDiagnostic,
  type DiagnosticRunRequest,
} from './modelDiagnostic'

vi.mock('./request', () => ({
  default: {
    post: vi.fn(),
    get: vi.fn(),
  },
}))

interface ProxyTimeoutConfig {
  timeout?: number
  proxyTimeout?: number
}

function nginxLocation(config: string, location: string) {
  const escapedLocation = location.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return config.match(new RegExp(`location ${escapedLocation} \\{[\\s\\S]*?\\n\\s*\\}`))?.[0]
}

describe('model diagnostic API', () => {
  beforeEach(() => vi.mocked(request.post).mockResolvedValue({} as never))

  it('uses the frozen mode property and 200 second client timeout', async () => {
    const payload: DiagnosticRunRequest = {
      sessionId: '00000000-0000-4000-8000-000000000001',
      clientRequestId: '00000000-0000-4000-8000-000000000002',
      platformConfigId: 3,
      modelTier: 'LOW',
      mode: 'WEB_SEARCH',
      testMode: 'FREE_CHAT',
      userMessage: 'hello',
    }

    await executeDiagnostic(payload)

    expect(DIAGNOSTIC_REQUEST_TIMEOUT_MS).toBe(200_000)
    expect(request.post).toHaveBeenCalledWith(
      '/admin/model-diagnostics/runs',
      expect.objectContaining({ mode: 'WEB_SEARCH', modelTier: 'LOW' }),
      { timeout: 200_000 },
    )
    expect((payload as unknown as Record<string, unknown>).diagnosticMode).toBeUndefined()
  })

  it('keeps Vite diagnostics at 210 seconds and ordinary APIs at 120 seconds', () => {
    const proxy = viteConfig.server?.proxy as Record<string, ProxyTimeoutConfig>
    const proxyPaths = Object.keys(proxy)

    expect(DIAGNOSTIC_PROXY_TIMEOUT_MS).toBe(210_000)
    expect(DEFAULT_API_PROXY_TIMEOUT_MS).toBe(120_000)
    expect(proxyPaths.indexOf('/api/admin/model-diagnostics')).toBeLessThan(proxyPaths.indexOf('/api'))
    expect(proxy['/api/admin/model-diagnostics']).toMatchObject({
      timeout: 210_000,
      proxyTimeout: 210_000,
    })
    expect(proxy['/api']).toMatchObject({
      timeout: 120_000,
      proxyTimeout: 120_000,
    })
  })

  it('keeps container and host Nginx diagnostics at 210 seconds without expanding ordinary APIs', () => {
    const containerConfig = readFileSync(
      resolve(process.cwd(), 'nginx.conf'), 'utf8',
    )
    const hostConfig = readFileSync(
      resolve(process.cwd(), '../deploy/nginx/geo.conf.example'), 'utf8',
    )

    for (const config of [containerConfig, hostConfig]) {
      const diagnosticLocation = nginxLocation(config, '^~ /api/admin/model-diagnostics/')
      expect(diagnosticLocation).toBeTruthy()
      expect(diagnosticLocation).toContain('proxy_send_timeout 210s;')
      expect(diagnosticLocation).toContain('proxy_read_timeout 210s;')
    }

    const containerApiLocation = nginxLocation(containerConfig, '/api/')
    expect(containerApiLocation).toContain('proxy_read_timeout 120s;')

    const hostFallbackLocation = nginxLocation(hostConfig, '/')
    expect(hostFallbackLocation).toContain('proxy_send_timeout 120s;')
    expect(hostFallbackLocation).toContain('proxy_read_timeout 120s;')
  })
})
