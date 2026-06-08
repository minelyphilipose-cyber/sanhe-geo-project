<template>
  <section id="page-15" class="page-anchor">
    <div class="page">
      <div class="page-topbar">
        <span>GEO 诊断报告 · {{ mergedView.brand_name }}</span>
        <span>09 / 优化机会清单(续)</span>
      </div>

      <div class="p15-body">
        <!-- priority tag -->
        <div class="p15-priority-tag">
          <span class="priority-badge priority-low">
            <span class="priority-dot"></span>持续运营 · LONG-TERM VALUE
          </span>
        </div>

        <div v-if="diagnosticLowFindings.length > 0" class="p15-group">
          <div class="p15-group-title">
            <span>可执行补强项</span>
            <em>真实诊断出的后续优化点</em>
          </div>
          <FindingCard
            v-for="(m, idx) in diagnosticLowFindings"
            :key="`${m.finding.finding_id}-${idx}`"
            :number="formatNum(idx + 1)"
            priority="LOW"
            :title="m.title"
            :description="m.description"
          />
        </div>

        <div v-if="operationalLowFindings.length > 0" class="p15-group">
          <div class="p15-group-title">
            <span>持续运营价值</span>
            <em>订阅期持续交付,不伪装成诊断缺陷</em>
          </div>
          <FindingCard
            v-for="(m, idx) in operationalLowFindings"
            :key="`${m.finding.finding_id}-${idx}`"
            :number="formatNum(diagnosticLowFindings.length + idx + 1)"
            priority="LOW"
            :title="m.title"
            :description="m.description"
          />
        </div>

        <div v-if="!hasVisibleLowFindings" class="p15-empty">
          <p>{{ emptyText }}</p>
        </div>

        <!-- 底部引用(动态合成) -->
        <div class="p15-quote-wrap">
          <div class="pull-quote">{{ quoteText }}</div>
        </div>

        <!-- CATEGORY BREAKDOWN 4 卡片(跨 priority) -->
        <div class="p15-category-wrap">
          <div class="mono p15-category-label">CATEGORY BREAKDOWN</div>
          <div class="p15-category-grid">
            <div
              v-for="cat in categoryCounts"
              :key="cat.name"
              class="p15-category-card"
            >
              <div class="metric-hero p15-category-num">{{ cat.count }}</div>
              <div class="mono p15-category-name">{{ cat.name }}</div>
            </div>
          </div>
        </div>
      </div>

      <div class="page-footer-brand">GEO · CONFIDENTIAL</div>
      <div class="page-label">15</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useMergedView } from '@/composables/presale/useMergedView'
import FindingCard from './shared/FindingCard.vue'

/**
 * Page15 持续运营 / 长期优化价值。
 *
 * 内容:
 *   1. LOW priority finding 分组:可执行补强项 / 持续运营价值
 *   2. 底部引用 pull-quote(基于总数合成)
 *   3. CATEGORY BREAKDOWN 4 张卡片(跨三 priority,按 category 分组计数)
 *
 * Category 枚举(对齐契约,不跟原型的"长期运营"错误):
 *   基础设施 / 内容建设 / 关系建设 / 平台扩展
 *
 * 原型底部 4 张卡片名称 "基础设施/内容建设/关系建设/长期运营" 与契约
 * ("基础设施/内容建设/关系建设/平台扩展")不一致,我们遵循契约。
 * README 里需标明这个偏差,避免 Codex 复审时误以为漏了"长期运营"文案。
 */

const { mergedView: mergedViewRef } = useMergedView()
const mergedView = computed(() => mergedViewRef.value!)

const CUSTOMER_HIDDEN_RULE_CODES = new Set(['RULE_PLATFORM_COUNT_LOW'])
const OPERATIONAL_VALUE_RULE_CODES = new Set(['RULE_PERIODIC_RETEST_MONITORING'])

const allFindings = computed(() =>
  mergedView.value.merged_findings
    .filter((m) => !CUSTOMER_HIDDEN_RULE_CODES.has(m.finding.rule_code))
    .slice()
    .sort((a, b) => a.sort_order - b.sort_order)
)
const lowFindings = computed(() =>
  allFindings.value.filter((m) => m.finding.priority === 'LOW')
)
const diagnosticLowFindings = computed(() =>
  lowFindings.value.filter((m) => !OPERATIONAL_VALUE_RULE_CODES.has(m.finding.rule_code))
)
const operationalLowFindings = computed(() =>
  lowFindings.value.filter((m) => OPERATIONAL_VALUE_RULE_CODES.has(m.finding.rule_code))
)
const hasVisibleLowFindings = computed(
  () => diagnosticLowFindings.value.length > 0 || operationalLowFindings.value.length > 0
)

const bandGroup = computed<'low' | 'middle' | 'high'>(() => {
  const band = mergedView.value.narrative_profile?.band
  if (band === 'INVISIBLE' || band === 'BEHIND') return 'low'
  if (band === 'STRONG' || band === 'LEADER') return 'high'
  return 'middle'
})

function formatNum(n: number): string {
  return n.toString().padStart(2, '0')
}

// ─── 底部引用(合成) ──────────────────────────────────
const quoteText = computed(() => {
  if (hasVisibleLowFindings.value) {
    return `持续运营项用于承接高、中优先级整改之后的长期优化。它们强调真实内容补强、平台复测和变化预警,适合纳入订阅期持续交付。`
  }
  return emptyQuoteText.value
})

const emptyText = computed(() => {
  if (bandGroup.value === 'low') {
    return '当前重点仍是优先处理高、中优先级缺口。持续运营项将在核心入口补齐后承接,避免用泛化建议稀释真正紧急的问题。'
  }
  if (bandGroup.value === 'high') {
    return '当前没有额外诊断型补强项。后续价值主要在于持续守位、监测竞品进入和 AI 回答变化,及时发现新的风险与机会。'
  }
  return '当前已具备部分基础。后续重点是把已出现的场景做深、把内容表达做稳,通过持续复测确认优化是否稳定沉淀。'
})

const emptyQuoteText = computed(() => {
  if (bandGroup.value === 'high') {
    return '持续合作的价值不只在发现问题,也在守住已有优势:定期复测核心场景,跟踪竞品进入,发现 AI 回答口径变化。'
  }
  if (bandGroup.value === 'low') {
    return '低档客户不需要用低优先级清单凑满页面。先集中解决高、中优先级缺口,再把持续运营接到后续阶段。'
  }
  return '中档客户的长期价值在于一致性:让已经出现的入口更稳定,让不同平台看到的品牌信息更统一。'
})

// ─── CATEGORY BREAKDOWN ────────────────────────────────
const CATEGORY_ORDER = ['基础设施', '内容建设', '关系建设', '平台扩展'] as const

interface CategoryCount {
  name: string
  count: number
}
const categoryCounts = computed<CategoryCount[]>(() => {
  return CATEGORY_ORDER.map((name) => ({
    name,
    count: allFindings.value.filter((m) => m.finding.category === name).length
  }))
})
</script>

<style scoped>
.page-anchor {
  display: flex;
  justify-content: center;
}

.p15-body {
  margin-top: 60px;
}

.p15-priority-tag {
  margin-bottom: 16px;
}

.p15-empty {
  text-align: center;
  padding: 28px 32px;
  font-size: 13px;
  line-height: 1.8;
  color: #1a2942;
  background: #f7f3ea;
  border-left: 3px solid #c8bfa8;
}

.p15-group {
  margin-bottom: 20px;
}
.p15-group-title {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin: 0 0 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(200, 191, 168, 0.8);
}
.p15-group-title span {
  color: #0b1426;
  font-size: 14px;
  font-weight: 700;
}
.p15-group-title em {
  color: #6b6456;
  font-size: 11px;
  font-style: normal;
}

.p15-quote-wrap {
  margin-top: 40px;
}

/* category breakdown */
.p15-category-wrap {
  margin-top: 32px;
}
.p15-category-label {
  font-size: 11px;
  letter-spacing: 3px;
  color: #6b6456;
  margin-bottom: 16px;
}
.p15-category-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.p15-category-card {
  text-align: center;
  padding: 16px;
  border: 1px solid #c8bfa8;
}
.p15-category-num {
  font-size: 32px;
  color: #0b1426;
}
.p15-category-name {
  font-size: 10px;
  color: #6b6456;
  margin-top: 4px;
  letter-spacing: 1px;
}
</style>
