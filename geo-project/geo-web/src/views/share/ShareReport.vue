<template>
  <div class="share-page">
    <div class="share-page__bg"><div class="share-page__grid"></div></div>

    <div v-if="state === 'loading'" class="share-page__center">
      <el-icon :size="32" class="is-loading" color="#2563EB"><Loading /></el-icon>
      <p class="mt-4 text-gray-500">加载报告中...</p>
    </div>

    <div v-else-if="state === 'need_password'" class="share-page__center">
      <div class="glass-card p-8 w-[360px] text-center">
        <el-icon :size="40" color="#2563EB"><Lock /></el-icon>
        <h2 class="text-lg font-bold text-gray-800 mt-4 mb-2">访问需要密码</h2>
        <p class="text-sm text-gray-500 mb-4">此报告已设置访问密码</p>
        <el-input v-model="password" type="password" placeholder="请输入访问密码" size="large" show-password @keyup.enter="verifyPassword" />
        <el-button type="primary" class="w-full mt-4" size="large" :loading="verifying" @click="verifyPassword">确认</el-button>
      </div>
    </div>

    <div v-else-if="state === 'expired'" class="share-page__center">
      <div class="glass-card p-8 w-[360px] text-center">
        <el-icon :size="40" color="#EF4444"><CircleClose /></el-icon>
        <h2 class="text-lg font-bold text-gray-800 mt-4 mb-2">链接已失效</h2>
        <p class="text-sm text-gray-500">此报告分享链接已过有效期，请联系服务方获取新链接。</p>
      </div>
    </div>

    <div v-else-if="state === 'not_published'" class="share-page__center">
      <div class="glass-card p-8 w-[380px] text-center">
        <el-icon :size="40" color="#F59E0B"><Warning /></el-icon>
        <h2 class="text-lg font-bold text-gray-800 mt-4 mb-2">报告尚未发布</h2>
        <p class="text-sm text-gray-500">{{ stateMessage || '报告尚未发布，请联系服务团队。' }}</p>
      </div>
    </div>

    <div v-else-if="state === 'superseded_waiting'" class="share-page__center">
      <div class="glass-card p-8 w-[420px] text-center">
        <el-icon :size="40" color="#2563EB"><Loading /></el-icon>
        <h2 class="text-lg font-bold text-gray-800 mt-4 mb-2">报告版本已更新</h2>
        <p class="text-sm text-gray-500">{{ stateMessage || '新版本正在准备中，请稍后再试。' }}</p>
      </div>
    </div>

    <div v-else-if="state === 'loaded'" class="share-page__report">
      <div class="glass-card p-8 max-w-[1000px] mx-auto">
        <div class="report-header">
          <h1 class="text-xl font-bold">{{ reportTypeLabel(reportData?.report?.reportType) }}报告</h1>
          <el-button v-if="!isPrintMode" type="primary" plain @click="downloadPdf">
            <el-icon class="mr-1"><Download /></el-icon>导出 PDF
          </el-button>
        </div>

        <el-descriptions :column="3" border class="mb-4">
          <el-descriptions-item label="客户名称">{{ reportData?.subject?.customerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="品牌名称">{{ reportData?.subject?.brandName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="项目名称">{{ reportData?.subject?.projectName || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-row :gutter="12">
          <el-col :xs="24" :sm="12" :md="6"><el-statistic title="命中率" :value="num(summary.hit_rate)" suffix="%" /></el-col>
          <el-col :xs="24" :sm="12" :md="6"><el-statistic title="命中数" :value="num(summary.total_hit_count)" /></el-col>
          <el-col :xs="24" :sm="12" :md="6"><el-statistic title="完成请求数" :value="num(summary.total_completed_count)" /></el-col>
          <el-col :xs="24" :sm="12" :md="6"><el-statistic title="平台覆盖数" :value="`${num(summary.platform_coverage_count)}/${num(summary.platform_total_count)}`" /></el-col>
        </el-row>

        <el-row :gutter="12" class="mt-3">
          <el-col :xs="24" :sm="12" :md="6"><el-statistic title="官网提及数" :value="num(summary.site_mention_count)" /></el-col>
          <el-col :xs="24" :sm="12" :md="6"><el-statistic title="联系方式提及数" :value="num(summary.contact_mention_count)" /></el-col>
          <el-col :xs="24" :sm="12" :md="6"><el-statistic title="关键词覆盖率" :value="num(summary.keyword_coverage_rate)" suffix="%" /></el-col>
          <el-col :xs="24" :sm="12" :md="6"><el-statistic :title="compareLabel + '命中率变化'" :value="num(summaryVs.hit_rate_change)" suffix="%" /></el-col>
        </el-row>

        <el-divider />
        <h3 class="font-bold mb-2">{{ trendTitle }}</h3>
        <el-table :data="trendPoints" border>
          <el-table-column prop="date" label="日期" width="140" />
          <el-table-column prop="hit_rate" label="命中率(%)" width="120" />
          <el-table-column prop="site_rate" label="官网提及率(%)" width="130" />
          <el-table-column prop="contact_rate" label="联系方式提及率(%)" width="150" />
        </el-table>

        <h3 class="font-bold mt-4 mb-2">问题覆盖汇总</h3>
        <el-table :data="detailItems" border max-height="420">
          <el-table-column prop="question_content" label="问题" min-width="280" />
          <el-table-column prop="question_type" label="类型" width="120" />
          <el-table-column label="平台命中" width="120">
            <template #default="scope">{{ num(scope.row.platforms_hit) }}/{{ num(scope.row.platforms_total) }}</template>
          </el-table-column>
          <el-table-column label="官网提及" width="100">
            <template #default="scope"><el-tag :type="scope.row.site_mentioned ? 'success' : 'info'">{{ scope.row.site_mentioned ? '是' : '否' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="联系方式提及" width="120">
            <template #default="scope"><el-tag :type="scope.row.contact_mentioned ? 'success' : 'info'">{{ scope.row.contact_mentioned ? '是' : '否' }}</el-tag></template>
          </el-table-column>
        </el-table>

        <h3 class="font-bold mt-4 mb-2">平台拆分</h3>
        <el-table :data="platformItems" border>
          <el-table-column prop="platform_name" label="平台" min-width="160" />
          <el-table-column prop="hit_rate" label="命中率(%)" width="120" />
          <el-table-column prop="completed_count" label="完成请求数" width="120" />
          <el-table-column prop="hit_count" label="命中数" width="100" />
        </el-table>

        <h3 class="font-bold mt-4 mb-2">内容执行情况</h3>
        <el-row :gutter="12">
          <el-col :xs="24" :sm="8"><el-statistic title="生成文章数" :value="num(contentSummary.articles_generated)" /></el-col>
          <el-col :xs="24" :sm="8"><el-statistic title="审核通过数" :value="num(contentSummary.articles_approved)" /></el-col>
          <el-col :xs="24" :sm="8"><el-statistic title="分发完成数" :value="num(contentSummary.articles_distributed)" /></el-col>
        </el-row>

        <h3 class="font-bold mt-4 mb-2">口径说明</h3>
        <p class="text-sm text-gray-600">{{ methodologyNote || '-' }}</p>
        <h3 v-if="stageAdvice" class="font-bold mt-4 mb-2">阶段建议</h3>
        <p v-if="stageAdvice" class="text-sm text-gray-700 whitespace-pre-wrap">{{ stageAdvice }}</p>
      </div>
    </div>

    <div v-else-if="state === 'not_found'" class="share-page__center">
      <div class="glass-card p-8 w-[360px] text-center">
        <el-icon :size="40" color="#94A3B8"><Warning /></el-icon>
        <h2 class="text-lg font-bold text-gray-800 mt-4 mb-2">报告不存在</h2>
        <p class="text-sm text-gray-500">请确认链接是否正确。</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getShareReport, verifySharePassword } from '@/api/report'
import { CircleClose, Download, Loading, Lock, Warning } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const token = route.params.token as string
const isPrintMode = computed(() => String(route.query.print || '') === '1')

type PageState = 'loading' | 'need_password' | 'expired' | 'loaded' | 'not_found' | 'not_published' | 'superseded_waiting'
const state = ref<PageState>('loading')
const reportData = ref<any>(null)
const password = ref('')
const verifying = ref(false)
const stateMessage = ref('')
const passwordCacheKey = computed(() => `share_pwd:${token}`)

const postsaleSnapshot = computed(() => reportData.value?.snapshot || {})
const summary = computed(() => parseJsonObject(postsaleSnapshot.value?.summaryData))
const summaryVs = computed(() => parseJsonObject(summary.value.vs_previous))
const trendData = computed(() => parseJsonObject(postsaleSnapshot.value?.trendData))
const detailData = computed(() => parseJsonObject(postsaleSnapshot.value?.detailData))
const platformBreakdown = computed(() => parseJsonObject(postsaleSnapshot.value?.platformBreakdown))
const contentSummary = computed(() => parseJsonObject(postsaleSnapshot.value?.contentExecutionSummary))
const trendPoints = computed<any[]>(() => (Array.isArray(trendData.value.daily_points) ? trendData.value.daily_points : []))
const detailItems = computed<any[]>(() => (Array.isArray(detailData.value.items) ? detailData.value.items : []))
const platformItems = computed<any[]>(() => (Array.isArray(platformBreakdown.value.platforms) ? platformBreakdown.value.platforms : []))
const stageAdvice = computed(() => String(postsaleSnapshot.value?.stageAdvice || '').trim())
const methodologyNote = computed(() => String(postsaleSnapshot.value?.methodologyNote || '').trim())

const compareLabel = computed(() => {
  const type = String(reportData.value?.report?.reportType || '')
  if (type === 'monthly') return '较上月'
  if (type === 'quarterly') return '较上季'
  return '较上双周'
})
const trendTitle = computed(() => {
  const type = String(reportData.value?.report?.reportType || '')
  if (type === 'monthly') return '月度趋势（30 天）'
  if (type === 'quarterly') return '季度趋势'
  return '双周趋势（14 天）'
})

function reportTypeLabel(reportType?: string) {
  const map: Record<string, string> = { biweekly: '双周报', monthly: '月报', quarterly: '季报', management: '管理层汇总' }
  return map[String(reportType || '')] || '售后报告'
}
function parseJsonObject(v: any): Record<string, any> {
  if (!v) return {}
  if (typeof v === 'object' && !Array.isArray(v)) return v as Record<string, any>
  if (typeof v !== 'string') return {}
  try {
    const parsed = JSON.parse(v)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  } catch {
    return {}
  }
}
function num(v: any) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

onMounted(async () => {
  await loadShareReport()
})

async function loadShareReport() {
  try {
    const { data } = await getShareReport(token)
    const res = data.data

    if (res?.bizCode === 'SUCCESS') return applyLoadedData(res)
    if (res?.bizCode === 'PASSWORD_REQUIRED') {
      const cached = sessionStorage.getItem(passwordCacheKey.value)
      if (cached) {
        password.value = cached
        await verifyPassword(true)
        return
      }
      state.value = 'need_password'
      stateMessage.value = res?.message || ''
      return
    }
    if (res?.bizCode === 'NOT_PUBLISHED') {
      state.value = 'not_published'
      stateMessage.value = res?.message || '报告尚未发布，请联系服务团队。'
      return
    }
    if (res?.bizCode === 'VERSION_SUPERSEDED') {
      const latestToken = String(res?.latestToken || '').trim()
      if (latestToken) router.replace(`/r/${latestToken}`)
      else {
        state.value = 'superseded_waiting'
        stateMessage.value = res?.message || '新版本正在准备中，请稍后再试。'
      }
      return
    }
    if (res?.bizCode === 'LINK_EXPIRED') {
      state.value = 'expired'
      stateMessage.value = res?.message || ''
      return
    }
    if (res?.bizCode === 'NOT_FOUND') {
      state.value = 'not_found'
      stateMessage.value = res?.message || ''
      return
    }
    state.value = 'not_found'
  } catch {
    state.value = 'not_found'
  }
}

async function verifyPassword(silent = false) {
  if (!password.value.trim()) return
  if (!silent) verifying.value = true
  try {
    const { data } = await verifySharePassword(token, password.value)
    const res = data.data
    if (res?.bizCode === 'SUCCESS') {
      sessionStorage.setItem(passwordCacheKey.value, password.value)
      applyLoadedData(res)
    }
  } catch {
    if (silent) {
      sessionStorage.removeItem(passwordCacheKey.value)
      state.value = 'need_password'
    }
  } finally {
    if (!silent) verifying.value = false
  }
}

function applyLoadedData(res: any) {
  reportData.value = res
  state.value = 'loaded'
  stateMessage.value = ''
}

function downloadPdf() {
  window.open(`/api/share/${token}/pdf`, '_blank', 'noopener')
}
</script>

<style scoped>
.share-page { min-height: 100vh; background: #f8fafc; position: relative; }
.share-page__bg { position: fixed; inset: 0; pointer-events: none; }
.share-page__grid {
  position: absolute; inset: 0;
  background-image: radial-gradient(#cbd5e1 1px, transparent 1px);
  background-size: 32px 32px; opacity: 0.3;
  mask-image: linear-gradient(to bottom, rgba(0, 0, 0, 1) 0%, rgba(0, 0, 0, 0) 60%);
}
.share-page__center {
  min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: center;
  position: relative; z-index: 10;
}
.share-page__report { padding: 40px 24px; position: relative; z-index: 10; }
.report-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 8px; }
</style>
