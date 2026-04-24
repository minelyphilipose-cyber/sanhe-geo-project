<template>
  <section id="page-18" class="page-anchor">
    <div class="page cover">
      <div class="cover-pattern"></div>
      <div class="cover-pattern-2"></div>

      <div class="p18-wrap">
        <!-- 顶部 brand + END marker -->
        <div class="p18-top">
          <div class="p18-brand">
            <div class="display-serif p18-logo">G</div>
            <div class="mono p18-brand-name">GEO · 可见度诊断</div>
          </div>
          <div class="mono p18-end-marker">
            END · REPORT-{{ reportIdSuffix }}
          </div>
        </div>

        <!-- 中部主内容:ABOUT US 标题 + 描述 + 3 项服务 -->
        <div class="p18-middle">
          <div class="mono p18-eyebrow">ABOUT US</div>
          <h2 class="display-serif p18-title">
            让每一个品牌<br>被 AI 看见
          </h2>
          <div class="p18-divider"></div>

          <p class="p18-intro">
            我们专注于生成式引擎优化(GEO)领域,帮助品牌在 AI 驱动的新搜索时代重建可见度。通过数据驱动的诊断、优化和持续监控,让您的品牌成为 AI 回答中的首选答案。
          </p>

          <div class="p18-services">
            <div v-for="s in SERVICES" :key="s.no" class="p18-service">
              <div class="mono p18-service-no">{{ s.no }}</div>
              <div class="chinese-serif p18-service-title">{{ s.title }}</div>
              <div class="p18-service-desc">{{ s.desc }}</div>
            </div>
          </div>
        </div>

        <!-- 底部元信息 -->
        <div class="p18-bottom">
          <div>
            <div class="mono p18-meta-label">REPORT ID</div>
            <div class="mono p18-meta-value">{{ reportIdFormatted }}</div>
          </div>
          <div>
            <div class="mono p18-meta-label">ISSUED</div>
            <div class="p18-meta-value">{{ issuedDate }}</div>
          </div>
          <div class="p18-meta-right">
            <div class="mono p18-meta-label">CONFIDENTIAL</div>
            <div class="p18-meta-value p18-meta-accent">仅供指定客户使用</div>
          </div>
        </div>

        <div class="p18-copyright">
          © {{ copyrightYear }} GEO · ALL RIGHTS RESERVED
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useMergedView } from '@/composables/presale/useMergedView'

/**
 * Page18 关于我们 / 封底(γ·2)。
 *
 * 样式:沿用 .page.cover 深色渐变背景(P01 同款),theme.css 已定义。
 *
 * 数据映射:
 *   - 顶部 END · REPORT-{suffix}:report_id 末 3 位 padStart
 *   - 中部 ABOUT US / 3 项服务:硬编码文案(非报告数据,是公司介绍)
 *   - 底部 REPORT ID:GEO-{YYYY}-{MMDD}-{report_id 3 位 pad}
 *   - 底部 ISSUED:reportCreatedAt 格式化为 YYYY.MM.DD
 *   - © 年份:取 reportCreatedAt 的年份,兜底当前年份
 */

const { mergedView: mergedViewRef, reportCreatedAt } = useMergedView()
const mergedView = computed(() => mergedViewRef.value!)

// ─── 硬编码服务列表 ────────────────────────────────────

const SERVICES = [
  { no: '01', title: '可见度诊断', desc: '全平台可见度量化评估' },
  { no: '02', title: '全平台优化执行', desc: '从基础设施到内容策略的闭环执行' },
  { no: '03', title: '持续监控', desc: '动态响应 AI 平台演进' }
] as const

// ─── 日期/ID 派生 ───────────────────────────────────────

interface DateParts {
  y: string
  m: string
  d: string
}

/**
 * reportCreatedAt → {y,m,d},失败返回当前日期作为兜底。
 * 取 RFC3339 前 10 字符(YYYY-MM-DD)避免时区换算误差。
 */
const dateParts = computed<DateParts>(() => {
  const raw = reportCreatedAt.value
  if (raw) {
    const datePart = raw.slice(0, 10)
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(datePart)
    if (m) return { y: m[1], m: m[2], d: m[3] }
  }
  // 兜底:当前日期
  const now = new Date()
  return {
    y: String(now.getFullYear()),
    m: String(now.getMonth() + 1).padStart(2, '0'),
    d: String(now.getDate()).padStart(2, '0')
  }
})

/** REPORT ID 3 位后缀(报告 ID 取末 3 位,不足左填 0)。 */
const reportIdSuffix = computed(() => {
  const id = mergedView.value.meta.report_id
  return String(id).padStart(3, '0').slice(-3)
})

/** 完整 REPORT ID:GEO-YYYY-MMDD-NNN */
const reportIdFormatted = computed(() => {
  const { y, m, d } = dateParts.value
  return `GEO-${y}-${m}${d}-${reportIdSuffix.value}`
})

/** ISSUED 日期:YYYY.MM.DD */
const issuedDate = computed(() => {
  const { y, m, d } = dateParts.value
  return `${y}.${m}.${d}`
})

/** © 年份:优先报告年份,兜底当前年份。 */
const copyrightYear = computed(() => dateParts.value.y)
</script>

<style scoped>
.page-anchor {
  display: flex;
  justify-content: center;
}

/* ─── P18 布局容器 ────────────────────────────────────── */

.p18-wrap {
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
  z-index: 2;
}

/* ─── 顶部 brand + END marker ─────────────────────────── */

.p18-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.p18-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.p18-logo {
  width: 32px;
  height: 32px;
  border: 2px solid white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 900;
  color: white;
}

.p18-brand-name {
  font-size: 12px;
  letter-spacing: 4px;
  text-transform: uppercase;
  color: white;
}

.p18-end-marker {
  font-size: 11px;
  letter-spacing: 2px;
  color: rgba(255, 255, 255, 0.6);
}

/* ─── 中部 ABOUT US ───────────────────────────────────── */

.p18-middle {
  flex: 1;
  padding-top: 60px;
}

.p18-eyebrow {
  font-size: 11px;
  letter-spacing: 4px;
  color: rgba(255, 255, 255, 0.6);
  margin-bottom: 20px;
}

.p18-title {
  font-size: 54px;
  font-weight: 900;
  line-height: 1.1;
  margin: 0 0 32px;
  letter-spacing: -1px;
  color: white;
}

.p18-divider {
  width: 60px;
  height: 2px;
  background: var(--presale-accent);
  margin-bottom: 28px;
}

.p18-intro {
  font-size: 15px;
  line-height: 1.9;
  color: rgba(255, 255, 255, 0.85);
  max-width: 540px;
  margin: 0 0 48px;
}

.p18-services {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
  margin-bottom: 60px;
}

.p18-service-no {
  font-size: 10px;
  letter-spacing: 2px;
  color: var(--presale-accent);
  margin-bottom: 8px;
}

.p18-service-title {
  font-size: 15px;
  font-weight: 500;
  margin-bottom: 4px;
  color: white;
}

.p18-service-desc {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
  line-height: 1.6;
}

/* ─── 底部元信息 ──────────────────────────────────────── */

.p18-bottom {
  padding-top: 40px;
  border-top: 1px solid rgba(255, 255, 255, 0.2);
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 20px;
}

.p18-meta-label {
  font-size: 10px;
  letter-spacing: 2px;
  color: rgba(255, 255, 255, 0.5);
  margin-bottom: 6px;
}

.p18-meta-value {
  font-size: 13px;
  font-weight: 500;
  color: white;
}

.p18-meta-right {
  text-align: right;
}

.p18-meta-accent {
  color: var(--presale-accent);
}

/* ─── 版权 ────────────────────────────────────────────── */

.p18-copyright {
  margin-top: 24px;
  font-size: 10px;
  color: rgba(255, 255, 255, 0.4);
  text-align: center;
  letter-spacing: 2px;
}
</style>
