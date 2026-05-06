<template>
  <div class="presale-print-poc">
    <div v-if="loading" class="poc-state">Loading presale print PoC...</div>
    <div v-else-if="error" class="poc-state poc-state--error">{{ error }}</div>
    <ReportViewer v-else-if="mergedView" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeMount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getInstanceByDom } from 'echarts/core'
import type { ECharts } from 'echarts/core'

import { getPresalePrintPocDetail } from '@/api/presalePrintPoc'
import {
  mergeSnapshot,
  type VersionRowMeta
} from '@/utils/presale/merge-snapshot'
import { provideMergedViewContext } from '@/composables/presale/useMergedView'
import type { MergedViewDTO } from '@/types/presale'
import type { ReportDetailVO } from '@/api/presaleReport'
import ReportViewer from './detail/ReportViewer.vue'

declare global {
  interface Window {
    __PRESALE_PRINT_READY__?: boolean
    __PRESALE_PRINT_METRICS__?: PrintMetrics
  }
}

interface ChartMetric {
  index: number
  hasData: boolean
  seriesCount: number
  canvasNonBlank: boolean
}

interface PrintMetrics {
  page_count: number
  chart_count: number
  charts_with_data: number
  canvas_non_blank: boolean
  bottom_band_ok: boolean
  overflow_pages: PageOverflowMetric[]
  content_overflows: ContentOverflowMetric[]
  ready_elapsed_ms: number
  device_scale_factor: number
  viewport: {
    width: number
    height: number
  }
  charts: ChartMetric[]
}

interface ContentOverflowMetric {
  pageId: string
  block: string
  field: string
  overflowPx: number
  scrollHeight: number
  clientHeight: number
  scrollWidth: number
  clientWidth: number
}

interface PageOverflowMetric {
  pageId: string
  overflowPx: number
  maxContentBottom: number
  pageHeight: number
  offender: string
}

const route = useRoute()
const reportId = computed(() => Number(route.params.reportId))

const detail = ref<ReportDetailVO | null>(null)
const mergedView = ref<MergedViewDTO | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const readyStartedAt = ref(0)
const currentVersionNo = computed(() => mergedView.value?.meta.version_no ?? null)
const reportCreatedAt = computed(() => mergedView.value?.meta.generated_at ?? detail.value?.createdAt ?? null)

provideMergedViewContext({
  mergedView,
  currentVersionNo,
  loading,
  error,
  refresh: load,
  switchVersion: async () => undefined,
  reportCreatedAt
})

onBeforeMount(() => {
  window.__PRESALE_PRINT_READY__ = false
  installPrintResizeGuard()
})

onMounted(() => {
  void load()
})

async function load(): Promise<void> {
  if (!Number.isFinite(reportId.value) || reportId.value <= 0) {
    error.value = 'Invalid reportId'
    return
  }
  loading.value = true
  error.value = null
  try {
    const d = await getPresalePrintPocDetail(reportId.value)
    readyStartedAt.value = performance.now()
    detail.value = d
    mergedView.value = buildMergedView(d)
    loading.value = false
    await nextTick()
    await waitForCharts()
  } catch (e: unknown) {
    error.value = (e as Error).message || 'Load print PoC failed'
    window.__PRESALE_PRINT_READY__ = false
    loading.value = false
  }
}

function buildMergedView(d: ReportDetailVO): MergedViewDTO {
  const raw = JSON.parse(d.rawSnapshotJson || '{}')
  const computedSnap = JSON.parse(d.computedSnapshotJson || '{}')
  const editable = JSON.parse(d.editableContentJson || '{}')
  return mergeSnapshot(raw, computedSnap, editable, toVersionRowMeta(d))
}

function toVersionRowMeta(d: ReportDetailVO): VersionRowMeta {
  const v = d.version
  return {
    version_id: v.versionId,
    report_id: d.reportId,
    version_no: v.versionNo,
    schema_version: 'v1.2',
    generation_status: v.generationStatus,
    frozen_at: v.frozenAt,
    frozen_by: null,
    frozen_reason: null,
    content_updated_at: v.contentUpdatedAt,
    content_updated_by: null,
    is_degraded: v.isDegraded ?? false,
    degraded_platforms: v.degradedPlatforms ?? null,
    export_success_count: v.exportSuccessCount,
    export_success_at: v.exportSuccessAt
  }
}

function installPrintResizeGuard(): void {
  const OriginalResizeObserver = window.ResizeObserver
  if (!OriginalResizeObserver) return

  class PrintResizeObserver {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
  }

  window.ResizeObserver = PrintResizeObserver as unknown as typeof ResizeObserver
}

function markPages(): void {
  document.querySelectorAll<HTMLElement>('.page-anchor').forEach((section, idx) => {
    const id = section.id || `page-${String(idx + 1).padStart(2, '0')}`
    const page = section.querySelector<HTMLElement>('.page')
    if (page) page.dataset.pageId = id
  })
}

async function waitForCharts(): Promise<void> {
  await document.fonts.ready
  await nextTick()
  await waitUntil(() => document.querySelectorAll('.page-anchor').length >= 19, 60_000)
  await waitUntil(() => document.querySelectorAll('.presale-chart canvas').length > 0, 60_000)
  markPages()
  fitPagesToPrintBox()

  const chartEls = Array.from(document.querySelectorAll<HTMLElement>('.presale-chart'))
  const charts = chartEls
    .map((el) => getInstanceByDom(el) as ECharts | undefined)
    .filter((chart): chart is ECharts => Boolean(chart))

  charts.forEach((chart) => {
    chart.setOption({ animation: false }, false)
  })

  const waiters = charts.map((chart) => waitForChartFinishedIfNeeded(chart))
  await Promise.all(waiters)
  fitPagesToPrintBox()

  const metrics = collectMetrics(charts, chartEls)
  window.__PRESALE_PRINT_METRICS__ = metrics
  document.documentElement.classList.add('presale-print-ready')
  window.__PRESALE_PRINT_READY__ = true
}

function fitPagesToPrintBox(): void {
  const footerReservePx = 140
  document.querySelectorAll<HTMLElement>('.page[data-page-id]').forEach((page) => {
    const pageId = page.dataset.pageId || ''
    if (pageId === 'page-01' || pageId === 'page-19') return

    const body = Array.from(page.children).find((el) => {
      const className = (el as HTMLElement).className
      return typeof className === 'string' && /\bp\d{2}-body\b/.test(className)
    }) as HTMLElement | undefined
    if (!body) return

    body.style.transform = ''
    body.style.width = ''
    body.dataset.printScale = '1'

    const pageRect = page.getBoundingClientRect()
    const bodyRect = body.getBoundingClientRect()
    const maxBottom = maxBodyContentBottom(page, body)
    const allowedBottom = pageRect.height - footerReservePx
    if (maxBottom <= allowedBottom) return

    const bodyTop = bodyRect.top - pageRect.top
    const bodyHeight = Math.max(body.scrollHeight, bodyRect.height)
    const availableHeight = allowedBottom - bodyTop
    if (availableHeight <= 0 || bodyHeight <= 0) return

    const scale = Math.max(0.6, Math.min(1, (availableHeight / bodyHeight) * 0.98))
    body.style.transformOrigin = 'top left'
    body.style.transform = `scale(${scale})`
    body.style.width = `${100 / scale}%`
    body.dataset.printScale = scale.toFixed(3)
  })
}

function maxBodyContentBottom(page: HTMLElement, body: HTMLElement): number {
  const pageRect = page.getBoundingClientRect()
  let maxBottom = 0
  body.querySelectorAll<HTMLElement>('*').forEach((el) => {
    const style = window.getComputedStyle(el)
    if (style.display === 'none' || style.visibility === 'hidden') return
    const rect = el.getBoundingClientRect()
    maxBottom = Math.max(maxBottom, rect.bottom - pageRect.top)
  })
  return Math.max(maxBottom, body.getBoundingClientRect().bottom - pageRect.top)
}

function waitUntil(predicate: () => boolean, timeoutMs: number): Promise<void> {
  const started = performance.now()
  return new Promise((resolve, reject) => {
    const tick = () => {
      if (predicate()) {
        resolve()
        return
      }
      if (performance.now() - started > timeoutMs) {
        reject(new Error('Print PoC waitUntil timeout'))
        return
      }
      requestAnimationFrame(tick)
    }
    tick()
  })
}

function waitForChartFinishedIfNeeded(chart: ECharts): Promise<void> {
  const option = chart.getOption() as { series?: unknown[] }
  if (!hasSeriesData(option.series)) {
    return Promise.resolve()
  }

  return new Promise((resolve) => {
    let done = false
    const finish = () => {
      if (done) return
      done = true
      chart.off('finished', finish)
      resolve()
    }
    chart.on('finished', finish)
    chart.resize()
    requestAnimationFrame(() => requestAnimationFrame(finish))
  })
}

function hasSeriesData(series: unknown[] | undefined): boolean {
  if (!Array.isArray(series)) return false
  return series.some((item) => {
    const data = (item as { data?: unknown[] }).data
    return Array.isArray(data) && data.length > 0
  })
}

function collectMetrics(charts: ECharts[], chartEls: HTMLElement[]): PrintMetrics {
  const chartMetrics = charts.map((chart, index) => {
    const option = chart.getOption() as { series?: unknown[] }
    return {
      index,
      hasData: hasSeriesData(option.series),
      seriesCount: Array.isArray(option.series) ? option.series.length : 0,
      canvasNonBlank: isCanvasNonBlank(chartEls[index])
    }
  })
  const overflowPages = collectPageOverflowMetrics()
  return {
    page_count: document.querySelectorAll('.page[data-page-id]').length,
    chart_count: charts.length,
    charts_with_data: chartMetrics.filter((item) => item.hasData).length,
    canvas_non_blank: chartMetrics.some((item) => item.hasData && item.canvasNonBlank),
    bottom_band_ok: overflowPages.length === 0,
    overflow_pages: overflowPages,
    content_overflows: [],
    ready_elapsed_ms: Math.round(performance.now() - readyStartedAt.value),
    device_scale_factor: window.devicePixelRatio || 1,
    viewport: {
      width: window.innerWidth,
      height: window.innerHeight
    },
    charts: chartMetrics
  }
}

function collectPageOverflowMetrics(): PageOverflowMetric[] {
  const footerReservePx = 110
  const overflowPages = Array.from(document.querySelectorAll<HTMLElement>('.page[data-page-id]'))
    .map((page) => {
      const pageId = page.dataset.pageId || ''
      if (pageId === 'page-01' || pageId === 'page-19') {
        return null
      }
      if (page.dataset.printBottomBand === 'content-overflow-only') {
        return null
      }
      const pageRect = page.getBoundingClientRect()
      let maxContentBottom = 0
      let offender = ''
      page.querySelectorAll<HTMLElement>('*').forEach((el) => {
        if (
          el.classList.contains('page-footer-brand') ||
          el.classList.contains('page-label') ||
          el.dataset.printFooter === 'true'
        ) return
        const style = window.getComputedStyle(el)
        if (style.display === 'none' || style.visibility === 'hidden') return
        const rect = el.getBoundingClientRect()
        const bottom = rect.bottom - pageRect.top
        if (bottom > maxContentBottom) {
          maxContentBottom = bottom
          offender = describeElement(el)
        }
      })
      const allowedBottom = pageRect.height - footerReservePx
      return {
        pageId,
        overflowPx: Math.max(0, Math.round(maxContentBottom - allowedBottom)),
        maxContentBottom: Math.round(maxContentBottom),
        pageHeight: Math.round(pageRect.height),
        offender
      }
    })
    .filter((item): item is PageOverflowMetric => item !== null)
    .filter((item) => item.overflowPx > 0)

  if (overflowPages.length > 0) {
    console.warn('[presale-print-poc] page content overflows footer reserve', overflowPages)
  }
  return overflowPages
}

function describeElement(el: HTMLElement): string {
  const className = typeof el.className === 'string' ? el.className : ''
  const id = el.id ? `#${el.id}` : ''
  const klass = className
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 4)
    .map((name) => `.${name}`)
    .join('')
  return `${el.tagName.toLowerCase()}${id}${klass}`
}

function isCanvasNonBlank(root: HTMLElement | undefined): boolean {
  const canvas = root?.querySelector('canvas')
  if (!canvas) return false
  const ctx = canvas.getContext('2d')
  if (!ctx) return false
  const { width, height } = canvas
  if (width === 0 || height === 0) return false

  const sampleWidth = Math.min(width, 320)
  const sampleHeight = Math.min(height, 180)
  const image = ctx.getImageData(0, 0, sampleWidth, sampleHeight).data
  for (let i = 3; i < image.length; i += 4) {
    if (image[i] !== 0) return true
  }
  return false
}
</script>

<style scoped>
.presale-print-poc {
  background: white;
  min-height: 100vh;
}

.poc-state {
  padding: 40px;
  color: #1a2942;
  font-size: 14px;
}

.poc-state--error {
  color: #b91c1c;
}

:deep(.report-viewer) {
  padding: 0;
  background: white;
}

:deep(.viewer-alert) {
  display: none;
}

:deep(.page) {
  box-sizing: border-box;
  width: 210mm;
  height: 297mm;
  min-height: 297mm;
  margin: 0 auto;
  box-shadow: none;
  break-after: page;
  page-break-after: always;
}

:deep(.page *) {
  box-sizing: border-box;
}

:deep(.page-anchor) {
  break-after: page;
  page-break-after: always;
}

:deep(.ps-page-scope),
:deep(.ps-page-scope .page) {
  font-family: 'Noto Sans SC', 'Microsoft YaHei', 'PingFang SC', 'Noto Serif SC', sans-serif;
}

:deep(.cover-title) {
  /* 设计确认口径:封面/封底大标题保持英文衬线 + 中文 serif 的正式报告气质。 */
  font-family: 'Playfair Display', 'Noto Serif SC', 'Noto Sans SC', serif;
}

:deep(.p12-total-banner) {
  padding: 28px;
}

:deep(#page-12 .p12-total-banner) {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  column-gap: 24px;
}

:deep(#page-12 .p12-total-right) {
  display: grid;
  grid-template-columns: repeat(3, 88px);
  gap: 22px;
  justify-content: end;
}

:deep(#page-12 .p12-total-metric) {
  min-width: 0;
}

:deep(#page-12 .p12-total-metric-label) {
  white-space: nowrap;
  letter-spacing: 1px;
}

:deep(#page-16 .p16-body) {
  margin-top: 48px;
}

:deep(#page-16 .section-title) {
  margin-bottom: 24px;
  padding-bottom: 16px;
}

:deep(#page-16 .p16-hero-grid) {
  gap: 14px;
  margin-top: 18px;
  margin-bottom: 24px;
}

:deep(#page-16 .p16-hero-card) {
  padding: 24px 16px;
}

:deep(#page-16 .p16-hero-value) {
  font-size: 56px;
}

:deep(#page-16 .p16-chart) {
  height: 250px !important;
  margin-bottom: 12px;
}

:deep(#page-16 .p16-impact) {
  padding: 18px 20px;
  margin-bottom: 16px;
}

:deep(#page-16 .p16-impact-grid) {
  gap: 14px 18px;
}

:deep(#page-16 .p16-phase-strip) {
  gap: 12px;
  padding: 12px 0;
  margin-bottom: 14px;
}

:deep(#page-16 .p16-phase-item) {
  padding: 6px 10px;
}

:deep(#page-16 .p16-disclaimer) {
  padding: 10px 14px;
  line-height: 1.55;
}

:global(html),
:global(body),
:global(#app) {
  margin: 0;
  padding: 0;
  background: white;
}

@page {
  size: A4;
  margin: 0;
}

@media print {
  :deep(.page) {
    margin: 0 !important;
    box-shadow: none !important;
  }
}
</style>
