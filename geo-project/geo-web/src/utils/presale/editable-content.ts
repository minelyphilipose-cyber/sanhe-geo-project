import type { ComputedSnapshotDTO } from '@/types/presale/computed'
import type {
  CompetitorSceneDescription,
  EditableContentDTO,
  FindingContent,
  KeyTakeaway,
  PhaseDescription
} from '@/types/presale/editable'
import type { RawSnapshotDTO } from '@/types/presale/raw'

const TOP_LEVEL_ORDER: Array<keyof EditableContentDTO> = [
  'report_title',
  'report_subtitle',
  'executive_summary',
  'key_takeaways',
  'optimization_findings_content',
  'phase_descriptions',
  'competitor_scene_descriptions',
  'roi_disclaimer'
]

export interface EditableValidationError {
  field: string
  message: string
}

export function parseEditableContent(
  json: string | null | undefined,
  raw: RawSnapshotDTO,
  computed: ComputedSnapshotDTO
): EditableContentDTO {
  const parsed = json && json.trim() ? JSON.parse(json) : {}
  return normalizeEditableContent(parsed, raw, computed)
}

export function normalizeEditableContent(
  value: Partial<EditableContentDTO>,
  raw: RawSnapshotDTO,
  computed: ComputedSnapshotDTO
): EditableContentDTO {
  void raw
  void computed

  const phasesByNo = new Map<number, PhaseDescription>()
  for (const item of Array.isArray(value.phase_descriptions) ? value.phase_descriptions : []) {
    if (item?.phase_no) phasesByNo.set(item.phase_no, item)
  }

  return {
    report_title: value.report_title ?? null,
    report_subtitle: value.report_subtitle ?? null,
    executive_summary: value.executive_summary ?? null,
    key_takeaways: Array.isArray(value.key_takeaways)
      ? value.key_takeaways.map((item, idx) => ({
          order_no: item.order_no ?? idx + 1,
          title: item.title ?? '',
          description: item.description ?? ''
        }))
      : [],
    optimization_findings_content: Array.isArray(value.optimization_findings_content)
      ? value.optimization_findings_content
          .filter((item) => item?.finding_id)
          .map((item) => ({
            finding_id: item.finding_id,
            title: item.title ?? null,
            description: item.description ?? null,
            evidence_text: item.evidence_text ?? null,
            sort_order: item.sort_order ?? null,
            is_hidden: item.is_hidden
          }))
      : [],
    phase_descriptions: ([1, 2, 3] as const).map((phaseNo) => {
      const existing = phasesByNo.get(phaseNo)
      return {
        phase_no: phaseNo,
        title: existing?.title ?? null,
        description: existing?.description ?? null
      }
    }),
    competitor_scene_descriptions: Array.isArray(value.competitor_scene_descriptions)
      ? value.competitor_scene_descriptions
          .filter((item) => item?.competitor_rank)
          .map((item) => ({
            competitor_rank: item.competitor_rank,
            scene_advantages_polished:
              'scene_advantages_polished' in item ? item.scene_advantages_polished ?? null : null
          }))
      : [],
    roi_disclaimer: value.roi_disclaimer ?? null
  }
}

export function serializeEditableContent(value: EditableContentDTO): string {
  return stableStringify(toOrderedEditableContent(value))
}

export function toOrderedEditableContent(value: EditableContentDTO): EditableContentDTO {
  const ordered = {} as EditableContentDTO
  for (const key of TOP_LEVEL_ORDER) {
    ;(ordered as unknown as Record<string, unknown>)[key] = value[key]
  }
  ordered.key_takeaways = value.key_takeaways.map((item, idx) => ({
    order_no: idx + 1,
    title: item.title,
    description: item.description
  }))
  return ordered
}

export function stableStringify(value: unknown): string {
  if (Array.isArray(value)) {
    return `[${value.map(stableStringify).join(',')}]`
  }
  if (value && typeof value === 'object') {
    const obj = value as Record<string, unknown>
    return `{${Object.keys(obj)
      .filter((key) => obj[key] !== undefined)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${stableStringify(obj[key])}`)
      .join(',')}}`
  }
  return JSON.stringify(value)
}

export function validateEditableContent(value: EditableContentDTO): EditableValidationError[] {
  const errors: EditableValidationError[] = []
  checkText(errors, '报告标题', value.report_title, 40)
  checkText(errors, '报告副标题', value.report_subtitle, 80)
  if (value.executive_summary != null) {
    checkRequiredText(errors, '摘要标题', value.executive_summary.headline, 60)
    checkRequiredText(errors, '摘要正文', value.executive_summary.paragraph, 500)
  }
  if (value.key_takeaways.length > 8) {
    errors.push({ field: 'key_takeaways', message: '关键结论最多 8 条' })
  }
  value.key_takeaways.forEach((item, idx) => {
    checkRequiredText(errors, `关键结论 ${idx + 1} 标题`, item.title, 30)
    checkRequiredText(errors, `关键结论 ${idx + 1} 描述`, item.description, 500)
  })
  value.optimization_findings_content.forEach((item, idx) => {
    checkRequiredText(errors, `优化建议 ${idx + 1} ID`, item.finding_id, 64)
    checkText(errors, `优化建议 ${idx + 1} 标题`, item.title, 50)
    checkText(errors, `优化建议 ${idx + 1} 描述`, item.description, 500)
    checkText(errors, `优化建议 ${idx + 1} 证据`, item.evidence_text, 300)
  })
  value.phase_descriptions.forEach((item) => {
    checkText(errors, `阶段 ${item.phase_no} 标题`, item.title, 30)
    checkText(errors, `阶段 ${item.phase_no} 描述`, item.description, 300)
  })
  value.competitor_scene_descriptions.forEach((item) => {
    const list = item.scene_advantages_polished
    if (list == null) return
    if (list.length > 6) {
      errors.push({
        field: `competitor_${item.competitor_rank}`,
        message: `竞品 ${item.competitor_rank} 场景最多 6 条`
      })
    }
    list.forEach((text, idx) =>
      checkRequiredText(errors, `竞品 ${item.competitor_rank} 场景 ${idx + 1}`, text, 100)
    )
  })
  checkText(errors, 'ROI 免责声明', value.roi_disclaimer, 200)
  return errors
}

export function collectClearedFields(value: EditableContentDTO): string[] {
  const fields: string[] = []
  if (value.report_title === '') fields.push('报告标题')
  if (value.report_subtitle === '') fields.push('报告副标题')
  if (value.executive_summary?.headline === '') fields.push('摘要标题')
  if (value.executive_summary?.paragraph === '') fields.push('摘要正文')
  if (value.key_takeaways.length === 0) fields.push('关键结论')
  for (const item of value.optimization_findings_content) {
    if (item.title === '' || item.description === '' || item.evidence_text === '') {
      fields.push(`优化建议 ${item.finding_id}`)
    }
  }
  for (const item of value.phase_descriptions) {
    if (item.title === '' || item.description === '') fields.push(`阶段 ${item.phase_no}`)
  }
  for (const item of value.competitor_scene_descriptions) {
    if (Array.isArray(item.scene_advantages_polished) && item.scene_advantages_polished.length === 0) {
      fields.push(`竞品 ${item.competitor_rank} 场景`)
    }
  }
  if (value.roi_disclaimer === '') fields.push('ROI 免责声明')
  return Array.from(new Set(fields))
}

function checkRequiredText(
  errors: EditableValidationError[],
  field: string,
  value: string | null | undefined,
  maxLength: number
) {
  if (value == null) {
    errors.push({ field, message: `${field}不能为空` })
    return
  }
  checkText(errors, field, value, maxLength)
}

function checkText(
  errors: EditableValidationError[],
  field: string,
  value: string | null | undefined,
  maxLength: number
) {
  if (value != null && value.length > maxLength) {
    errors.push({ field, message: `${field}不能超过 ${maxLength} 字` })
  }
}
