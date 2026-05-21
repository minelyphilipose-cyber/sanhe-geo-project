<template>
  <div class="space-y-4">
    <el-page-header content="报表预览" @back="$router.back()" />

    <el-card v-loading="loading">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="客户名称">{{ subject.customerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="品牌名称">{{ subject.brandName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="项目名称">{{ subject.projectName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="报表类型">{{ reportTypeLabel(report?.reportType) }}</el-descriptions-item>
        <el-descriptions-item label="可见性">{{ isInternal ? '内部版' : '客户版' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ reportStatusLabel(report?.status) }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ report?.versionNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ report?.createdAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="PDF 生成时间">{{ report?.pdfGeneratedAt || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="mt-3 flex flex-wrap items-center gap-2">
        <el-button v-if="pairedReportId" plain @click="goToPair">
          查看{{ isInternal ? '客户版' : '内部版' }}
        </el-button>
        <el-button v-if="canReview && isDraft" type="warning" :loading="regeneratingPdf" @click="regeneratePdf">重新生成 PDF</el-button>
        <el-button v-if="canReview && isDraft" type="success" :loading="publishing" @click="publish">发布</el-button>
        <el-button v-if="canReview && isDraft" type="danger" :loading="intercepting" @click="openIntercept">拦截</el-button>
        <el-button v-if="canRegeneratePostsale" type="danger" plain :loading="regeneratingReport" @click="regeneratePostsale">
          重新生成报表
        </el-button>
      </div>
    </el-card>

    <el-card>
      <template #header><span>{{ reportTypeLabel(report?.reportType) }}摘要</span></template>
      <el-row :gutter="12">
        <el-col :span="6"><el-statistic title="命中率" :value="num(summary.hit_rate)" suffix="%" /></el-col>
        <el-col :span="6"><el-statistic title="命中数" :value="num(summary.total_hit_count)" /></el-col>
        <el-col :span="6"><el-statistic title="完成请求数" :value="num(summary.total_completed_count)" /></el-col>
        <el-col :span="6"><el-statistic title="平台覆盖数" :value="`${num(summary.platform_coverage_count)}/${num(summary.platform_total_count)}`" /></el-col>
      </el-row>
    </el-card>

    <el-card>
      <template #header><span>{{ trendTitle }}</span></template>
      <el-table :data="trendPoints" border>
        <el-table-column prop="date" label="日期" width="140" />
        <el-table-column prop="hit_rate" label="命中率(%)" width="120" />
        <el-table-column prop="site_rate" label="官网提及率(%)" width="130" />
        <el-table-column prop="contact_rate" label="联系方式提及率(%)" width="150" />
      </el-table>
    </el-card>

    <el-card>
      <template #header><span>发布进度与内容执行</span></template>
      <el-row :gutter="12">
        <el-col :span="8"><el-statistic title="生成文章数" :value="num(contentSummary.articles_generated)" /></el-col>
        <el-col :span="8"><el-statistic title="可发布文章数" :value="num(contentSummary.articles_approved)" /></el-col>
        <el-col :span="8"><el-statistic title="分发完成数" :value="num(contentSummary.articles_distributed)" /></el-col>
      </el-row>
    </el-card>

    <el-dialog v-model="interceptVisible" title="拦截原因" width="520px">
      <el-input v-model="interceptReason" type="textarea" :rows="4" maxlength="500" show-word-limit />
      <template #footer>
        <el-button @click="interceptVisible = false">取消</el-button>
        <el-button type="danger" :loading="intercepting" @click="confirmIntercept">确认拦截</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getReportDetail, interceptReport, publishReport, regeneratePostsaleReport, regenerateReportPdf } from '@/api/report'
import type { Report } from '@/types'
import { useUserStore } from '@/stores/user'
import { REPORT_STATUS_MAP, REPORT_TYPE_MAP } from '@/utils/constants'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const reportId = Number(route.params.id)

const loading = ref(false)
const publishing = ref(false)
const intercepting = ref(false)
const regeneratingPdf = ref(false)
const regeneratingReport = ref(false)

const report = ref<Report | null>(null)
const postsaleSnapshot = ref<any>(null)
const interceptVisible = ref(false)
const interceptReason = ref('')
const subject = ref({ customerName: '', brandName: '', projectName: '' })

const canReview = computed(() => userStore.hasPermission('report.review'))
const isDraft = computed(() => report.value?.status === 'draft')
const isPostsale = computed(() => ['biweekly', 'monthly', 'quarterly'].includes(String(report.value?.reportType || '')))
const isInternal = computed(() => report.value?.visibility === 'internal')
const canRegeneratePostsale = computed(() => canReview.value && isDraft.value && isPostsale.value)
const pairedReportId = computed(() => report.value?.pairReportId || null)

const summary = computed(() => parseJsonObject(postsaleSnapshot.value?.summaryData))
const trendData = computed(() => parseJsonObject(postsaleSnapshot.value?.trendData))
const contentSummary = computed(() => parseJsonObject(postsaleSnapshot.value?.contentExecutionSummary))
const trendPoints = computed<any[]>(() => Array.isArray(trendData.value.daily_points) ? trendData.value.daily_points : [])

const trendTitle = computed(() => {
  const type = String(report.value?.reportType || '')
  if (type === 'monthly') return '月度趋势（30 天）'
  if (type === 'quarterly') return '季度趋势'
  return '双周趋势（14 天）'
})

function reportTypeLabel(v?: string) {
  return REPORT_TYPE_MAP[v as keyof typeof REPORT_TYPE_MAP]?.label || v || '-'
}

function reportStatusLabel(v?: string) {
  return REPORT_STATUS_MAP[v as keyof typeof REPORT_STATUS_MAP]?.label || v || '-'
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

function goToPair() {
  if (!pairedReportId.value) return
  router.push(`/admin/reports/${pairedReportId.value}`)
}

async function load() {
  loading.value = true
  try {
    const { data } = await getReportDetail(reportId)
    report.value = data.data.report
    subject.value = {
      customerName: data.data.subject?.customerName || '',
      brandName: data.data.subject?.brandName || '',
      projectName: data.data.subject?.projectName || '',
    }
    postsaleSnapshot.value = data.data.postsaleSnapshot || null
  } finally {
    loading.value = false
  }
}

async function regeneratePdf() {
  if (!report.value) return
  regeneratingPdf.value = true
  try {
    await regenerateReportPdf(report.value.id)
    ElMessage.success('PDF 已重新生成')
    await load()
  } finally {
    regeneratingPdf.value = false
  }
}

async function publish() {
  if (!report.value) return
  try {
    let sharePassword: string | undefined
    if (report.value.visibility === 'client') {
      const { value } = await ElMessageBox.prompt('可选：设置分享密码（留空则不设密码）', '发布报表', {
        inputPlaceholder: '请输入密码（可空）',
        confirmButtonText: '发布',
        cancelButtonText: '取消',
      })
      sharePassword = value || undefined
    } else {
      await ElMessageBox.confirm('确认发布内部版报表？', '发布确认', {
        type: 'warning',
        confirmButtonText: '发布',
        cancelButtonText: '取消',
      })
    }
    publishing.value = true
    await publishReport(report.value.id, { sharePassword })
    ElMessage.success('报表已发布')
    await load()
  } catch (err: any) {
    const msg = String(err?.message || '')
    if (msg.includes('最新 PDF')) {
      const ok = await ElMessageBox.confirm('内容已修改，是否现在重新生成 PDF？', '发布失败', {
        type: 'warning',
        confirmButtonText: '立即生成',
        cancelButtonText: '取消',
      }).then(() => true).catch(() => false)
      if (ok) await regeneratePdf()
    }
  } finally {
    publishing.value = false
  }
}

function openIntercept() {
  interceptReason.value = ''
  interceptVisible.value = true
}

async function confirmIntercept() {
  if (!report.value) return
  if (!interceptReason.value.trim()) {
    ElMessage.warning('请填写拦截原因')
    return
  }
  intercepting.value = true
  try {
    await interceptReport(report.value.id, interceptReason.value.trim())
    ElMessage.success('已拦截')
    interceptVisible.value = false
    await load()
  } finally {
    intercepting.value = false
  }
}

async function regeneratePostsale() {
  if (!report.value) return
  const ok = await ElMessageBox.confirm('将废弃当前版本并重新生成一对新版本报表，是否继续？', '重新生成确认', {
    type: 'warning',
    confirmButtonText: '确认重生成',
    cancelButtonText: '取消',
  }).then(() => true).catch(() => false)
  if (!ok) return
  regeneratingReport.value = true
  try {
    await regeneratePostsaleReport(report.value.id)
    ElMessage.success('已触发重生成，请在列表中查看新版本')
    await load()
  } finally {
    regeneratingReport.value = false
  }
}

void load()
</script>
