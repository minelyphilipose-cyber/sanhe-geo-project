<template>
  <section id="page-02" class="page-anchor">
    <div class="page">
      <div class="page-topbar">
        <span>GEO 诊断报告 · {{ mergedView.brand_name }}</span>
        <span>01 / 诊断对象</span>
      </div>

      <div class="p02-body">
        <!-- 章节标题 -->
        <div class="section-title">
          <span class="section-number">01</span>
          <div>
            <div class="section-label">SECTION ONE</div>
            <div class="section-heading">诊断对象信息</div>
          </div>
        </div>

        <!-- 品牌名 + 地区 双栏 -->
        <div class="p02-top-grid">
          <div>
            <div class="mono p02-field-label">BRAND NAME</div>
            <div class="chinese-serif p02-brand-name">{{ mergedView.brand_name }}</div>
            <div class="p02-brand-sub">{{ brandSubText }}</div>
          </div>
          <div>
            <div class="mono p02-field-label">REGION</div>
            <div class="chinese-serif p02-region">{{ mergedView.region }}</div>
            <div class="p02-region-sub">{{ regionSubText }}</div>
          </div>
        </div>

        <!-- 数据矩阵:行业 / 身份 / 客户诉求 -->
        <div class="data-matrix p02-matrix">
          <div class="data-matrix-row p02-matrix-row">
            <div class="mono p02-field-label">INDUSTRY</div>
            <div class="p02-matrix-value">{{ mergedView.industry }}</div>
          </div>
          <div class="data-matrix-row p02-matrix-row">
            <div class="mono p02-field-label">BUSINESS ROLE</div>
            <div class="p02-matrix-value">{{ mergedView.industry_role }}</div>
          </div>
          <div class="data-matrix-row p02-matrix-row">
            <div class="mono p02-field-label">USER INQUIRY</div>
            <div class="p02-matrix-inquiry">
              <template v-if="mergedView.user_demand">"{{ mergedView.user_demand }}"</template>
              <span v-else class="p02-inquiry-muted">(客户未填写诉求)</span>
            </div>
          </div>
        </div>

        <!-- 诊断范围 dark strip -->
        <div class="p02-scope">
          <div class="mono p02-scope-label">DIAGNOSTIC SCOPE / 诊断范围</div>
          <div class="p02-scope-grid">
            <div>
              <div class="metric-hero p02-scope-number">
                {{ mergedView.test_summary.total_platforms }}
              </div>
              <div class="p02-scope-text">AI 平台<br />主流模型全覆盖</div>
            </div>
            <div>
              <div class="metric-hero p02-scope-number">
                {{ mergedView.test_summary.total_prompts }}
              </div>
              <div class="p02-scope-text">测试查询<br />5 类意图覆盖</div>
            </div>
            <div>
              <div class="metric-hero p02-scope-number">
                {{ independentTestCount }}
              </div>
              <div class="p02-scope-text">独立测试<br />平台 × 查询</div>
            </div>
            <div>
              <div class="metric-hero p02-scope-number">5</div>
              <div class="p02-scope-text">分析维度<br />提及 / 排名 / 情感 / 覆盖 / 竞品</div>
            </div>
          </div>
        </div>

        <!-- 底部说明引用块 -->
        <div class="p02-quote-wrap">
          <div class="pull-quote">
            本报告基于 {{ issuedText }}当日 AI 模型表现生成,由于大模型持续演进,建议季度级监测以追踪动态变化。
          </div>
        </div>
      </div>

      <div class="page-footer-brand">GEO · CONFIDENTIAL</div>
      <div class="page-label">02</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useMergedView } from '@/composables/presale/useMergedView'

/**
 * Page02 诊断对象。
 *
 * 原型里几处"虚"文案(HAIDILAO / 海底捞国际控股 / 中国 · 华北区域)
 * 是设计稿的占位,并非契约字段。β·1 显式降级:
 *   - BRAND 副行:若将来后端补 "brand_name_en / legal_entity" 字段再显示,
 *     现阶段留空字符串(不占位,不硬造)
 *   - REGION 副行:同上,不硬造"华北"等
 * 独立测试次数 = total_platforms × total_prompts(原型"336" = 8 × 42)。
 */

const { mergedView: mergedViewRef, reportCreatedAt } = useMergedView()
const mergedView = computed(() => mergedViewRef.value!)

const brandSubText = computed(() => {
  // 未来若 MergedViewDTO 补 brand_name_en / legal_entity,从那里读;现阶段返回空串
  return ''
})
const regionSubText = computed(() => {
  // 未来若补 region_group / country,从那里读;现阶段返回空串
  return ''
})

const independentTestCount = computed(() => {
  const s = mergedView.value.test_summary
  return s.total_platforms * s.total_prompts
})

const issuedText = computed(() => {
  const raw = reportCreatedAt.value
  if (!raw) return '生成'
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return '生成'
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

.p02-body {
  margin-top: 60px;
}

.p02-top-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 32px;
  margin-bottom: 40px;
}

.p02-field-label {
  font-size: 10px;
  letter-spacing: 2px;
  color: #6b6456;
  margin-bottom: 6px;
}

.p02-brand-name {
  font-size: 32px;
  font-weight: 700;
  color: #0b1426;
  letter-spacing: -0.5px;
}
.p02-brand-sub {
  font-size: 12px;
  color: #6b6456;
  margin-top: 4px;
  min-height: 18px;
}

.p02-region {
  font-size: 22px;
  font-weight: 500;
  color: #0b1426;
}
.p02-region-sub {
  font-size: 12px;
  color: #6b6456;
  margin-top: 4px;
  min-height: 18px;
}

.p02-matrix {
  margin-bottom: 48px;
}
.p02-matrix-row {
  grid-template-columns: 180px 1fr;
}
.p02-matrix-value {
  font-size: 16px;
}
.p02-matrix-inquiry {
  font-size: 14px;
  line-height: 1.7;
  color: #1a2942;
}
.p02-inquiry-muted {
  color: #6b6456;
  font-style: italic;
}

/* 诊断范围深色条:撑出左右贴边效果 */
.p02-scope {
  background: #0b1426;
  color: #fefcf7;
  padding: 36px 64px;
  margin: 0 -64px;
}
.p02-scope-label {
  font-size: 11px;
  letter-spacing: 3px;
  color: rgba(255, 255, 255, 0.5);
  margin-bottom: 20px;
}
.p02-scope-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
}
.p02-scope-number {
  font-size: 56px;
  color: #d97706;
}
.p02-scope-text {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
  margin-top: 8px;
  line-height: 1.5;
}

.p02-quote-wrap {
  margin-top: 36px;
}
</style>
