import { describe, expect, it } from 'vitest'
import { countMarketLabelPair, resolveMarketIndustryLabel, type IndustryOption } from './presaleMarketInput'

const industryOptions: IndustryOption[] = [
  { value: 'automotive', label: '汽车' },
  { value: 'tech_software', label: 'SaaS 企业软件' }
]

describe('presale market input', () => {
  it('counts selected industries by their visible labels', () => {
    const industryLabel = resolveMarketIndustryLabel('automotive', industryOptions)

    expect(industryLabel).toBe('汽车')
    expect(countMarketLabelPair('亳州', industryLabel)).toBe(4)
  })

  it('counts custom industries by the entered text', () => {
    const industryLabel = resolveMarketIndustryLabel(' 新能源汽车 ', industryOptions)

    expect(industryLabel).toBe('新能源汽车')
    expect(countMarketLabelPair('亳州', industryLabel)).toBe(7)
  })

  it('uses the full visible label for other predefined industries', () => {
    expect(resolveMarketIndustryLabel('tech_software', industryOptions)).toBe('SaaS 企业软件')
  })
})
