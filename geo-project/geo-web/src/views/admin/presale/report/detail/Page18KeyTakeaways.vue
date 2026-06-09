<template>
  <section id="page-18" class="page-anchor">
    <div class="page">
      <div class="page-topbar">
        <span>GEO 诊断报告 · {{ mergedView.brand_name }}</span>
        <span>11 / 关键发现总结</span>
      </div>

      <div class="p18-body">
        <!-- 章节标题 -->
        <div class="section-title">
          <span class="section-number">11</span>
          <div>
            <div class="section-label">KEY TAKEAWAYS</div>
            <div class="section-heading">关键发现总结</div>
          </div>
        </div>

        <div v-if="takeaways.length > 0" class="p18-list">
          <div
            v-for="(t, idx) in takeaways"
            :key="`${t.order_no}-${idx}`"
            class="p18-item"
          >
            <div class="display-serif p18-number">{{ formatOrder(idx + 1) }}</div>
            <div class="chinese-serif p18-title">{{ t.title }}</div>
          </div>
        </div>

        <!-- 空态兜底:L3 未配置 key_takeaways -->
        <div v-else class="p18-empty">
          <div class="mono p18-empty-label">KEY TAKEAWAYS</div>
          <div class="p18-empty-text">本报告暂未生成关键发现总结,请联系运营补充。</div>
        </div>

        <div class="p18-cta">
          <div class="p18-cta-icon" aria-hidden="true">🎯</div>
          <div class="p18-cta-content">
            <div class="chinese-serif p18-cta-title">下一步建议</div>
            <div class="p18-cta-copy">
              本报告仅为"诊断"——<strong>真正的可见度增长来自后续 60 天的精准执行</strong>。{{ ctaBrandPhrase }}《60 天 AI 可见度突破执行方案》，包含：
            </div>
            <ul class="p18-cta-list">
              <li>60 天分阶段优化路线图与 KPI</li>
              <li>高价值场景内容生产与平台分发执行</li>
              <li>月度可见度监测与策略迭代</li>
            </ul>
            <div class="p18-cta-action">
              📞 联系您的专属顾问预约方案讲解会（约 45 分钟）
            </div>
          </div>
        </div>

        <!-- Methodology note -->
        <div class="p18-methodology">
          <div class="mono p18-methodology-label">METHODOLOGY NOTE</div>
          <div class="p18-methodology-text">
            <template v-if="methodologyDate">
              本报告基于 {{ methodologyDate }} 的 AI 平台测试数据生成。大语言模型持续演进,推荐结果会随时间变化。建议以季度级频率持续监测关键指标。
            </template>
            <template v-else>
              本报告基于近期 AI 平台测试数据生成。大语言模型持续演进,推荐结果会随时间变化。建议以季度级频率持续监测关键指标。
            </template>
            <div v-if="showOverallBenchmarkNote" class="p18-methodology-extra">
              本品牌无排名数据,综合得分按提及/情感/覆盖三维归一加权；行业均值与 Top1 为四维基准,跨维度比较仅供参考。
            </div>
            <div class="p18-methodology-extra">
              提及率计算中,豆包平台权重为其他平台的 2 倍。场景覆盖判定中,某场景在豆包提及或达到半数有效平台提及时即记为已覆盖。
            </div>
          </div>
        </div>
      </div>

      <div class="page-footer-brand">GEO · CONFIDENTIAL</div>
      <div class="page-label">18</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useMergedView } from '@/composables/presale/useMergedView'
import type { KeyTakeaway } from '@/types/presale/editable'

/**
 * Page18 关键发现总结(γ·2)。
 *
 * 数据映射:
 *   - mergedView.key_takeaways[]:每条 {order_no, title, description}
 *   - 按 order_no 升序排列
 *   - 序号展示使用渲染顺序(1-based),不直接用 order_no(支持 order_no 跳号)
 *   - methodology note 的日期来自 reportCreatedAt(YYYY-MM-DD)
 *
 * 空态:
 *   - key_takeaways 为空数组时显示兜底提示,不影响页面结构(methodology 仍渲染)
 */

const { mergedView: mergedViewRef, reportCreatedAt } = useMergedView()
const mergedView = computed(() => mergedViewRef.value!)

const takeaways = computed<KeyTakeaway[]>(() => {
  const seen = new Set<string>()
  return mergedView.value.key_takeaways
    .slice()
    .sort((a, b) => a.order_no - b.order_no)
    .filter((item) => {
      const key = normalizeDisplayKey(item.title, item.description)
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })
})

const methodologyDate = computed<string>(() => {
  const raw = reportCreatedAt.value
  if (!raw) return ''
  // 取 RFC3339 前 10 字符作为日期,然后转 "YYYY 年 M 月 D 日" 的中文形态
  const datePart = raw.slice(0, 10) // "YYYY-MM-DD"
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(datePart)
  if (!m) return ''
  const y = m[1]
  const mo = parseInt(m[2], 10)
  const d = parseInt(m[3], 10)
  return `${y} 年 ${mo} 月 ${d} 日`
})

const ctaBrandPhrase = computed<string>(() => {
  const brandName = mergedView.value.brand_name?.trim()
  return brandName ? `我们已为 ${brandName} 准备了一份` : '我们已为您准备了一份'
})

const showOverallBenchmarkNote = computed(() => mergedView.value.scores.ranking == null)

/** 1 → "01" / 12 → "12"。 */
function formatOrder(n: number): string {
  return n.toString().padStart(2, '0')
}

function normalizeDisplayKey(title: string, description: string): string {
  return `${title}|${description}`.replace(/\s+/g, '').toLowerCase()
}
</script>

<style scoped>
.page-anchor {
  display: flex;
  justify-content: center;
}

.p18-body {
  margin-top: 60px;
}

.p18-list {
  margin-top: 8px;
}

.p18-item {
  display: inline-flex;
  align-items: center;
  width: 100%;
  gap: 14px;
  padding: 8px 0;
  border-bottom: 1px solid var(--presale-line);
}

.p18-number {
  font-size: 28px;
  font-weight: 900;
  color: var(--presale-primary);
  line-height: 0.9;
  font-style: italic;
  min-width: 42px;
}

.p18-title {
  flex: 1;
  font-size: 15px;
  font-weight: 600;
  color: var(--presale-ink);
  line-height: 1.4;
}

/* ─── 空态 ─────────────────────────────────────────────── */

.p18-empty {
  margin: 40px 0;
  padding: 32px 24px;
  border: 1px dashed var(--presale-line);
  background: var(--presale-paper-alt);
  text-align: center;
}
.p18-empty-label {
  font-size: 10px;
  letter-spacing: 3px;
  color: var(--presale-muted);
  margin-bottom: 12px;
}
.p18-empty-text {
  font-size: 13px;
  color: var(--presale-muted);
}

/* ─── CTA ─────────────────────────────────────────────── */

.p18-cta {
  /* TODO: 后续若 P18 CTA 形态复用到其他页面,将深色渐变 token 抽到 report-theme.css。 */
  --p18-cta-bg-start: var(--presale-ink);
  --p18-cta-bg-end: #16264a;

  margin-top: 24px;
  padding: 24px 28px;
  display: flex;
  gap: 20px;
  background: linear-gradient(135deg, var(--p18-cta-bg-start) 0%, var(--p18-cta-bg-end) 100%);
  border-left: 4px solid var(--presale-accent);
  break-inside: avoid;
  page-break-inside: avoid;
}

.p18-cta-icon {
  flex: 0 0 32px;
  font-size: 30px;
  line-height: 1;
}

.p18-cta-content {
  flex: 1;
}

.p18-cta-title {
  margin-bottom: 10px;
  color: var(--presale-accent);
  font-size: 20px;
  font-weight: 700;
  line-height: 1.3;
}

.p18-cta-copy {
  color: rgba(255, 255, 255, 0.85);
  font-size: 13px;
  line-height: 1.8;
}

.p18-cta-copy strong {
  color: var(--presale-accent);
  font-weight: 700;
}

.p18-cta-list {
  margin: 10px 0 0 18px;
  padding: 0;
  color: rgba(255, 255, 255, 0.75);
  font-size: 12px;
  line-height: 1.9;
}

.p18-cta-action {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid rgba(255, 255, 255, 0.15);
  color: var(--presale-accent);
  font-size: 13px;
  font-weight: 700;
  line-height: 1.7;
}

/* ─── Methodology note ────────────────────────────────── */

.p18-methodology {
  margin-top: 40px;
  padding: 20px;
  border: 1px solid var(--presale-line);
  border-left: 3px solid var(--presale-accent);
  background: var(--presale-paper-alt);
}
.p18-methodology-label {
  font-size: 10px;
  letter-spacing: 2px;
  color: var(--presale-muted);
  margin-bottom: 8px;
}
.p18-methodology-text {
  font-size: 12px;
  color: var(--presale-ink-soft);
  line-height: 1.7;
}
.p18-methodology-extra {
  margin-top: 8px;
}
</style>
