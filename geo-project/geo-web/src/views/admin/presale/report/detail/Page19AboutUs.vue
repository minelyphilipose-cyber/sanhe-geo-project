<template>
  <section id="page-19" class="page-anchor">
    <div class="page cover">
      <div class="cover-pattern"></div>
      <div class="cover-pattern-2"></div>

      <div class="p19-wrap">
        <!-- 顶部 brand + END marker -->
        <div class="p19-top">
          <div class="p19-brand">
            <div class="display-serif p19-logo">G</div>
            <div class="mono p19-brand-name">GEO · 可见度诊断</div>
          </div>
          <div class="mono p19-end-marker">
            END · REPORT-{{ reportIdSuffix }}
          </div>
        </div>

        <!-- 中部主内容:ABOUT US 标题 + 描述 + 3 项服务 -->
        <div class="p19-middle">
          <div class="mono p19-eyebrow">ABOUT US</div>
          <h2 class="display-serif p19-title">
            让品牌在AI时代<br>被看见 · 被理解 · 被选择
          </h2>
          <div class="p19-divider"></div>

          <p class="p19-intro">
            幻境AI 为品牌与企业提供 AI 可见性增长解决方案，让品牌在生成式搜索、智能问答与决策场景中建立确定性增长。
          </p>

          <div class="p19-services">
            <div v-for="s in SERVICES" :key="s.no" class="p19-service">
              <div class="mono p19-service-no">{{ s.no }}</div>
              <div class="chinese-serif p19-service-title">{{ s.title }}</div>
              <div class="p19-service-desc">{{ s.desc }}</div>
            </div>
          </div>
        </div>

        <!-- 底部元信息 -->
        <div class="p19-bottom">
          <div>
            <div class="mono p19-meta-label">REPORT ID</div>
            <div class="mono p19-meta-value">{{ reportIdFormatted }}</div>
          </div>
          <div>
            <div class="mono p19-meta-label">ISSUED</div>
            <div class="p19-meta-value">{{ issuedDate }}</div>
          </div>
          <div class="p19-meta-right">
            <div class="mono p19-meta-label">CONFIDENTIAL</div>
            <div class="p19-meta-value p19-meta-accent">仅供指定客户使用</div>
          </div>
        </div>

        <div class="p19-copyright">
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
 * Page19 关于我们 / 封底(γ·2)。
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

/* ─── P19 布局容器 ────────────────────────────────────── */

.p19-wrap {
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
  z-index: 2;
}

/* ─── 顶部 brand + END marker ─────────────────────────── */

.p19-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.p19-brand {
  display: flex;
  align-items: center;
  gap: 14px;
}

.p19-logo {
  width: 34px;
  height: 34px;
  border: 1.5px solid rgba(255, 255, 255, 0.92);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 900;
  color: white;
  line-height: 1;
}

.p19-brand-name {
  font-size: 11px;
  letter-spacing: 4.5px;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.92);
}

.p19-end-marker {
  font-size: 10px;
  letter-spacing: 2.8px;
  color: rgba(255, 255, 255, 0.56);
}

/* ─── 中部 ABOUT US ───────────────────────────────────── */

.p19-middle {
  flex: 1;
  padding-top: 70px;
}

.p19-eyebrow {
  font-size: 10px;
  letter-spacing: 5px;
  color: rgba(255, 255, 255, 0.58);
  margin-bottom: 18px;
}

.p19-title {
  font-size: 50px;
  font-weight: 900;
  line-height: 1.2;
  margin: 0 0 30px;
  letter-spacing: -1.6px;
  color: white;
  text-shadow: 0 14px 34px rgba(0, 0, 0, 0.18);
}

.p19-divider {
  width: 66px;
  height: 2px;
  background: var(--presale-accent);
  margin-bottom: 26px;
}

.p19-intro {
  font-size: 14px;
  line-height: 2;
  letter-spacing: 0.2px;
  color: rgba(255, 255, 255, 0.82);
  max-width: 590px;
  margin: 0 0 56px;
}

.p19-services {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  column-gap: 54px;
  row-gap: 24px;
  margin-bottom: 60px;
}

.p19-service {
  min-width: 0;
}

.p19-service-no {
  font-size: 9px;
  letter-spacing: 2.4px;
  color: var(--presale-accent);
  margin-bottom: 10px;
}

.p19-service-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 6px;
  color: rgba(255, 255, 255, 0.96);
}

.p19-service-desc {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.58);
  line-height: 1.72;
}

/* ─── 底部元信息 ──────────────────────────────────────── */

.p19-bottom {
  padding-top: 36px;
  border-top: 1px solid rgba(255, 255, 255, 0.16);
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 28px;
}

.p19-meta-label {
  font-size: 9px;
  letter-spacing: 2.8px;
  color: rgba(255, 255, 255, 0.46);
  margin-bottom: 8px;
}

.p19-meta-value {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.2px;
  color: rgba(255, 255, 255, 0.94);
}

.p19-meta-right {
  text-align: right;
}

.p19-meta-accent {
  color: var(--presale-accent);
}

/* ─── 版权 ────────────────────────────────────────────── */

.p19-copyright {
  margin-top: 24px;
  font-size: 9px;
  color: rgba(255, 255, 255, 0.36);
  text-align: center;
  letter-spacing: 2.4px;
}
</style>
