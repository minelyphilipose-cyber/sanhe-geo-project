import { describe, expect, it } from 'vitest'
import type { OptimizationFinding } from '@/types/presale/computed'
import type { FindingContent } from '@/types/presale/editable'
import { mergeFindings } from './merge-snapshot'

describe('mergeFindings', () => {
  it('keeps report 663 low-priority findings distinct when legacy L3 IDs do not match', () => {
    const l2Findings: OptimizationFinding[] = [
      finding('F001', 'RULE_PLATFORM_DEPTH_SHALLOW', '平台扩展', {
        scene_example: '安徽蒙城学车，驾校哪家好？',
        target_platforms: 4,
        evaluated_platforms: 10,
        shallow_scene_count: 10,
        hv_reco_total: 11
      }),
      finding('F002', 'RULE_LONG_TAIL_SCENE_GAP', '内容建设', {
        long_tail_gap: 6,
        mid_gap: 5,
        mid_total: 14,
        low_gap: 1,
        low_total: 2
      }),
      finding('F003', 'RULE_CONTENT_CONSISTENCY_CHECK', '内容建设', {
        covered_platform_count: 6,
        total_platforms: 10,
        gap_pp: 92,
        strong_mention_rate: 92,
        weak_mention_rate: 0
      }),
      finding('F004', 'RULE_PERIODIC_RETEST_MONITORING', '平台扩展', {
        service_action: '周期复测与变化预警',
        monitoring_focus: '核心推荐场景、竞品进入与 AI 回答口径变化'
      })
    ]
    const legacyNarrativeContent: FindingContent[] = [
      {
        finding_id: 'NF001',
        title: '平台覆盖存在明显盲区',
        description: '旧叙事文案不应覆盖 L2 优化项',
        is_hidden: false
      }
    ]

    const merged = mergeFindings(l2Findings, legacyNarrativeContent)

    expect(merged).toHaveLength(4)
    expect(merged.map((item) => item.finding.finding_id)).toEqual(['F001', 'F002', 'F003', 'F004'])
    expect(merged.map((item) => item.title)).toEqual([
      '平台出现深度待补齐',
      '长尾场景可持续补齐',
      '品牌信息一致性建议检查',
      '周期复测与变化预警'
    ])
    expect(new Set(merged.map((item) => `${item.title}|${item.description}`)).size).toBe(4)
    expect(merged[0].description).toContain('4/10 个平台')
    expect(merged[3].description).toContain('周期复测与变化预警')
  })
})

function finding(
  findingId: string,
  ruleCode: string,
  category: string,
  evidenceData: Record<string, unknown>
): OptimizationFinding {
  return {
    finding_id: findingId,
    rule_code: ruleCode,
    priority: 'LOW',
    category,
    evidence_data: evidenceData
  }
}
