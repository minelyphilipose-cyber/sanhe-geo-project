import type { LlmQuestionItem } from '@/types'

export interface KeywordGroupFormState {
  companyId: number | null
  projectId: number | null
  name: string
  type: string
  legacyType: boolean
  remark: string
  previewCount: number
  areaEnabled: boolean
  functionIndustryTag: string
  llmSeedText: string
  llmGenerationToken: string
  llmQuestions: LlmQuestionItem[]
  areaText: string
  prefixSystemWords: string[]
  prefixCustomText: string
  coreText: string
  industryWords: string[]
  suffixSystemWords: string[]
  suffixCustomText: string
  coreTextA: string
  compareWords: string[]
  coreTextB: string
}
