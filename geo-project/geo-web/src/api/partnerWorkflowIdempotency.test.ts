// @vitest-environment node

import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from './request'
import { createReport, type CreateReportRequest } from './presaleReport'
import { submitPartnerProjectStartRequest } from './project'

vi.mock('./request', () => ({
  default: {
    post: vi.fn(),
  },
}))

const reportPayload: CreateReportRequest = {
  brandName: '测试品牌',
  industry: 'healthcare',
  industryRole: 'chain_brand',
  region: '上海',
}

describe('partner workflow idempotency contracts', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(request.post).mockResolvedValue({ data: { data: 42 } } as never)
  })

  it('adds a requestId when creating a presale report', async () => {
    await createReport(reportPayload)

    expect(request.post).toHaveBeenCalledWith(
      '/presale/reports',
      expect.objectContaining({ requestId: expect.stringMatching(/^presale-report-/) }),
    )
  })

  it('preserves a presale report requestId across caller retries', async () => {
    await createReport({ ...reportPayload, requestId: 'presale-report-fixed' })

    expect(request.post).toHaveBeenCalledWith(
      '/presale/reports',
      expect.objectContaining({ requestId: 'presale-report-fixed' }),
    )
  })

  it('adds a requestId when submitting a partner project start request', async () => {
    await submitPartnerProjectStartRequest(7)

    expect(request.post).toHaveBeenCalledWith(
      '/partner/projects/7/start-requests',
      expect.objectContaining({ requestId: expect.stringMatching(/^project-start-/) }),
    )
  })

  it('preserves a project start requestId across caller retries', async () => {
    await submitPartnerProjectStartRequest(7, { requestId: 'project-start-fixed' })

    expect(request.post).toHaveBeenCalledWith(
      '/partner/projects/7/start-requests',
      expect.objectContaining({ requestId: 'project-start-fixed' }),
    )
  })
})
