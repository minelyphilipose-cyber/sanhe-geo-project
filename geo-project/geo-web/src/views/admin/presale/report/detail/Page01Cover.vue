<template>
  <section id="page-01" class="page-anchor">
    <div class="page cover" style="padding: 0;">
      <div class="cover-pattern"></div>
      <div class="cover-pattern-2"></div>

      <div class="cover-inner">
        <!-- 顶部品牌标识 + 报告编号 -->
        <div class="cover-topbar">
          <div class="brand-mark-group">
            <div class="brand-g-mark">G</div>
            <div class="brand-label">GEO · 可见度诊断</div>
          </div>
          <div class="mono report-id">REPORT · {{ reportCode }}</div>
        </div>

        <!-- 主标题区域 -->
        <div class="cover-hero">
          <div class="cover-eyebrow">AI Visibility Diagnostic Report</div>
          <h1 class="display-serif cover-title">
            {{ reportTitleLine1 }}<br />{{ reportTitleLine2 }}
          </h1>
          <div class="cover-divider"></div>
          <div class="chinese-serif cover-custom-for">
            专为 <span class="cover-brand-highlight">{{ mergedView.brand_name }}</span> 定制
          </div>
          <div class="cover-sub">{{ categoryLine }}</div>
        </div>

        <!-- 底部信息 -->
        <div class="cover-footer">
          <div>
            <div class="mono cover-meta-label">ISSUED</div>
            <div class="cover-meta-value">{{ issuedText }}</div>
          </div>
          <div>
            <div class="mono cover-meta-label">TESTED</div>
            <div class="cover-meta-value">
              {{ mergedView.test_summary.total_platforms }} 个 AI 平台 ·
              {{ mergedView.test_summary.total_prompts }} 条查询
            </div>
          </div>
          <div>
            <div class="mono cover-meta-label">CLASSIFIED</div>
            <div class="cover-meta-value cover-meta-classified">机密 · Confidential</div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useMergedView } from '@/composables/presale/useMergedView'

/**
 * Page01 封面。
 *
 * 数据来源:
 *   - brand_name / industry / industry_role / region:MergedViewDTO 顶层
 *   - report_title / report_subtitle:MergedViewDTO 顶层(L3 可覆盖的文案)
 *   - test_summary.total_platforms / total_prompts:MergedViewDTO.test_summary
 *   - reportCreatedAt:从 useMergedView() 取,β·1 临时用 ReportDetailVO.createdAt 代替
 *     (MergedViewDTO 缺"生成时间"字段,见 useMergedView.ts reportCreatedAt 字段注释)
 *
 * 父级守卫:
 *   ReportViewer 用 v-if="isDone && mergedView" 守卫了渲染,所以本组件里
 *   mergedView.value 保证非 null 且 generation_status === 'DONE',
 *   业务字段(test_summary 等)安全可用。
 *
 * 样式作用域:
 *   外层 ReportViewer 有 .ps-page-scope,theme.css 的 .page / .cover /
 *   .display-serif / .chinese-serif / .mono 等在此生效。
 *   本文件内的 scoped style 只放 Page01 独有的排版(hero 区、grid 布局等)。
 */

const { mergedView: mergedViewRef, reportCreatedAt } = useMergedView()

// 父级 v-if 守卫保证 mergedView 非 null;此处用 non-null 断言简化模板
const mergedView = computed(() => mergedViewRef.value!)

/**
 * 报告编号:原型是 "GEO-2026-0418-001",无后端字段对应。
 * β·1 构造为 "GEO-{report_id}-V{version_no}"。
 * TODO:若产品需要正式编号规则(含日期、序号),由后端补 report.code 字段。
 */
const reportCode = computed(() => {
  const m = mergedView.value.meta
  return `GEO-${m.report_id}-V${m.version_no}`
})

/**
 * 标题拆两行(原型大字排版需要)。
 * 策略:优先用 L3 report_title 的中文句;如超 6 字拆在中点,否则整句第一行 + 空。
 * 原型范例:"AI 可见度" / "诊断报告" → 两行。
 */
const reportTitleLine1 = computed(() => {
  const t = mergedView.value.report_title
  // 如果标题里已有手动换行(\n),以换行为界
  if (t.includes('\n')) return t.split('\n')[0]
  // 默认策略:如果含"报告",把"报告"前作为第一行,"诊断报告"或"...报告"作为第二行
  const idx = t.lastIndexOf('诊断报告')
  if (idx > 0) return t.slice(0, idx)
  const idx2 = t.lastIndexOf('报告')
  if (idx2 > 0) return t.slice(0, idx2)
  return t
})
const reportTitleLine2 = computed(() => {
  const t = mergedView.value.report_title
  if (t.includes('\n')) return t.split('\n').slice(1).join('')
  const idx = t.lastIndexOf('诊断报告')
  if (idx > 0) return t.slice(idx)
  const idx2 = t.lastIndexOf('报告')
  if (idx2 > 0) return t.slice(idx2)
  return ''
})

/** 行业 · 身份 · 地区(空段自动跳过)。 */
const categoryLine = computed(() => {
  const parts = [
    mergedView.value.industry,
    mergedView.value.industry_role,
    mergedView.value.region
  ].filter((x) => x && x.length > 0)
  return parts.join(' · ')
})

/**
 * ISSUED 日期:"2026 年 4 月 18 日" 风格。
 * 用原生 toLocaleDateString,不引入 dayjs。
 * reportCreatedAt 格式为 RFC3339 带 +08:00,new Date() 可直接解析。
 */
const issuedText = computed(() => {
  const raw = reportCreatedAt.value
  if (!raw) return '—'
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  })
})
</script>

<style scoped>
.page-anchor {
  display: flex;
  justify-content: center;
}

/* 封面专属布局(内部 padding + flex column,撑满 1123px A4 高度) */
.cover-inner {
  padding: 60px 64px;
  height: 100%;
  min-height: 1123px;
  display: flex;
  flex-direction: column;
  position: relative;
  z-index: 2;
}

/* 顶部品牌条 */
.cover-topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.brand-mark-group {
  display: flex;
  align-items: center;
  gap: 12px;
}
.brand-g-mark {
  width: 32px;
  height: 32px;
  border: 2px solid white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: 'Playfair Display', serif;
  font-weight: 900;
}
.brand-label {
  font-size: 12px;
  letter-spacing: 4px;
  text-transform: uppercase;
}
.report-id {
  font-size: 11px;
  letter-spacing: 2px;
  color: rgba(255, 255, 255, 0.6);
}

/* Hero 主标题 */
.cover-hero {
  margin-top: 120px;
}
.cover-eyebrow {
  font-size: 11px;
  letter-spacing: 6px;
  color: rgba(255, 255, 255, 0.6);
  text-transform: uppercase;
  margin-bottom: 24px;
}
.cover-title {
  font-size: 92px;
  font-weight: 900;
  line-height: 1;
  letter-spacing: -3px;
  margin: 0 0 8px 0;
  color: inherit;
}
.cover-divider {
  width: 80px;
  height: 3px;
  background: #d97706;
  margin: 40px 0 32px 0;
}
.cover-custom-for {
  font-size: 22px;
  color: rgba(255, 255, 255, 0.9);
  font-weight: 500;
}
.cover-brand-highlight {
  color: #f59e0b;
  font-weight: 700;
}
.cover-sub {
  margin-top: 12px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.6);
}

/* 底部 grid */
.cover-footer {
  margin-top: auto;
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 20px;
  padding-top: 40px;
  border-top: 1px solid rgba(255, 255, 255, 0.2);
}
.cover-meta-label {
  font-size: 10px;
  letter-spacing: 2px;
  color: rgba(255, 255, 255, 0.5);
  margin-bottom: 6px;
}
.cover-meta-value {
  font-size: 15px;
  font-weight: 500;
}
.cover-meta-classified {
  color: #f59e0b;
}
</style>
