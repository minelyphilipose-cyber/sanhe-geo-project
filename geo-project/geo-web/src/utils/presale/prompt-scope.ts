export interface PromptScopeResult {
  platformCount: number
  genericPromptCount: number
  competitorPromptCount: number
  batch1Calls: number
  batch2Calls: number
  totalUpperBound: number
}

export function calculatePromptScope(
  platformCount: number,
  genericPromptCount: number,
  competitorPromptCount: number
): PromptScopeResult {
  const batch1Calls = platformCount * genericPromptCount * 2
  const batch2Calls = platformCount * competitorPromptCount * 2
  return {
    platformCount,
    genericPromptCount,
    competitorPromptCount,
    batch1Calls,
    batch2Calls,
    totalUpperBound: batch1Calls + batch2Calls
  }
}
