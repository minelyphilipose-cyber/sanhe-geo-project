export interface SelfMediaScheduleRetryEvidence {
  status?: string | null
  runtimeStage?: string | null
  failureCode?: string | null
  diagnosticsJson?: string | null
}

export interface SelfMediaSchedulePlanFailureCandidate {
  projectId: number
  projectName?: string | null
  status?: string | null
  plannedExecutionAt?: string | null
  plannedPublishAt?: string | null
  generationDueAt?: string | null
}

export interface SelfMediaSchedulePlanFailureBatch {
  key: string
  projectId: number
  projectName: string
  targetMonth: string
  visibleFailureCount: number
}

const UNCERTAIN_RUNTIME_STAGES = new Set([
  'content_filled',
  'execution_heartbeat_timeout_uncertain',
  'execution_failed_after_page_mutation',
  'execution_state_quarantined',
])

const CONTENT_MUTATION_DIAGNOSTIC_STAGES = new Set([
  'filling_title',
  'filling_content',
  'filling_tags',
  'verifying_content',
  'filling_publish_options',
  'filling_cover',
  'filling_location',
  'configuring_schedule',
  'submitting_publish',
  'verifying_publish_result',
  'completed',
])

function normalize(value?: string | null) {
  return String(value || '').trim().toLowerCase()
}

function diagnosticExecutionStage(diagnosticsJson?: string | null) {
  if (!diagnosticsJson) return ''
  try {
    const payload = JSON.parse(diagnosticsJson)
    return normalize(
      payload?.lastStage
      || payload?.error?.diagnostics?.page?.activeFillTask?.stage,
    )
  } catch {
    return ''
  }
}

export function requiresManualPlatformVerification(row: SelfMediaScheduleRetryEvidence | null): boolean {
  if (!row) return false
  if (normalize(row.status) === 'filled_verified') return true
  if (UNCERTAIN_RUNTIME_STAGES.has(normalize(row.runtimeStage))) return true

  const failureCode = normalize(row.failureCode)
  if (failureCode === 'local_agent_heartbeat_timeout' || failureCode.includes('cover_upload_timeout')) {
    return true
  }

  const diagnosticStage = diagnosticExecutionStage(row.diagnosticsJson)
  if (CONTENT_MUTATION_DIAGNOSTIC_STAGES.has(diagnosticStage)) return true

  const diagnostics = normalize(row.diagnosticsJson)
  return diagnostics.includes('manual_review_without_refill')
    || diagnostics.includes('execution_heartbeat_timeout_uncertain')
    || diagnostics.includes('content_filled')
}

export function groupSelfMediaSchedulePlanFailureBatches(
  rows: SelfMediaSchedulePlanFailureCandidate[],
): SelfMediaSchedulePlanFailureBatch[] {
  const batches = new Map<string, SelfMediaSchedulePlanFailureBatch>()
  rows.forEach((row) => {
    if (row.status !== 'generation_failed' && row.status !== 'schedule_failed') return
    const targetMonth = (row.plannedPublishAt || row.plannedExecutionAt || row.generationDueAt || '').slice(0, 7)
    if (!targetMonth) return
    const key = `${row.projectId}-${targetMonth}`
    const existing = batches.get(key)
    if (existing) {
      existing.visibleFailureCount += 1
      return
    }
    batches.set(key, {
      key,
      projectId: row.projectId,
      projectName: row.projectName || `项目 #${row.projectId}`,
      targetMonth,
      visibleFailureCount: 1,
    })
  })
  return [...batches.values()]
}
