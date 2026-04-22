<template>
  <section id="page-17" class="page-anchor">
    <div class="page">
      <div class="page-topbar">
        <span>GEO 诊断报告 · {{ mergedView.brand_name }}</span>
        <span>10 / 关键发现总结</span>
      </div>

      <div class="p17-body">
        <!-- 章节标题 -->
        <div class="section-title">
          <span class="section-number">10</span>
          <div>
            <div class="section-label">KEY TAKEAWAYS</div>
            <div class="section-heading">关键发现总结</div>
          </div>
        </div>

        <div v-if="takeaways.length > 0" class="p17-list">
          <div
            v-for="(t, idx) in takeaways"
            :key="`${t.order_no}-${idx}`"
            class="p17-item"
          >
            <div class="display-serif p17-number">{{ formatOrder(idx + 1) }}</div>
            <div class="p17-content">
              <div class="chinese-serif p17-title">{{ t.title }}</div>
              <div class="p17-description">{{ t.description }}</div>
            </div>
          </div>
        </div>

        <!-- 空态兜底:L3 未配置 key_takeaways -->
        <div v-else class="p17-empty">
          <div class="mono p17-empty-label">KEY TAKEAWAYS</div>
          <div class="p17-empty-text">本报告暂未生成关键发现总结,请联系运营补充。</div>
        </div>

        <!-- Methodology note -->
        <div class="p17-methodology">
          <div class="mono p17-methodology-label">METHODOLOGY NOTE</div>
          <div class="p17-methodology-text">
            <template v-if="methodologyDate">
              本报告基于 {{ methodologyDate }} 的 AI 平台测试数据生成。大语言模型持续演进,推荐结果会随时间变化。建议以季度级频率持续监测关键指标。
            </template>
            <template v-else>
              本报告基于近期 AI 平台测试数据生成。大语言模型持续演进,推荐结果会随时间变化。建议以季度级频率持续监测关键指标。
            </template>
          </div>
        </div>
      </div>

      <div class="page-footer-brand">GEO · CONFIDENTIAL</div>
      <div class="page-label">17</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useMergedView } from '@/composables/presale/useMergedView'
import type { KeyTakeaway } from '@/types/presale/editable'

/**
 * Page17 关键发现总结(γ·2)。
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
  const arr = [...mergedView.value.key_takeaways]
  return arr.sort((a, b) => a.order_no - b.order_no)
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

/** 1 → "01" / 12 → "12"。 */
function formatOrder(n: number): string {
  return n.toString().padStart(2, '0')
}
</script>

<style scoped>
.page-anchor {
  display: flex;
  justify-content: center;
}

.p17-body {
  margin-top: 60px;
}

.p17-list {
  margin-top: 8px;
}

.p17-item {
  display: flex;
  gap: 24px;
  padding: 24px 0;
  border-bottom: 1px solid var(--presale-line);
}

.p17-number {
  font-size: 64px;
  font-weight: 900;
  color: var(--presale-primary);
  line-height: 0.9;
  font-style: italic;
  min-width: 80px;
}

.p17-content {
  flex: 1;
}

.p17-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--presale-ink);
  margin-bottom: 8px;
  line-height: 1.4;
}

.p17-description {
  font-size: 13px;
  color: var(--presale-ink-soft);
  line-height: 1.8;
}

/* ─── 空态 ─────────────────────────────────────────────── */

.p17-empty {
  margin: 40px 0;
  padding: 32px 24px;
  border: 1px dashed var(--presale-line);
  background: var(--presale-paper-alt);
  text-align: center;
}
.p17-empty-label {
  font-size: 10px;
  letter-spacing: 3px;
  color: var(--presale-muted);
  margin-bottom: 12px;
}
.p17-empty-text {
  font-size: 13px;
  color: var(--presale-muted);
}

/* ─── Methodology note ────────────────────────────────── */

.p17-methodology {
  margin-top: 40px;
  padding: 20px;
  border: 1px solid var(--presale-line);
  border-left: 3px solid var(--presale-accent);
  background: var(--presale-paper-alt);
}
.p17-methodology-label {
  font-size: 10px;
  letter-spacing: 2px;
  color: var(--presale-muted);
  margin-bottom: 8px;
}
.p17-methodology-text {
  font-size: 12px;
  color: var(--presale-ink-soft);
  line-height: 1.7;
}
</style>
