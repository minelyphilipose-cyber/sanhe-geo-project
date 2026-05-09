<template>
  <div class="presale-report-list">
    <!-- 页头 -->
    <div class="page-header">
      <div class="header-left">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/admin' }">管理后台</el-breadcrumb-item>
          <el-breadcrumb-item>售前报告</el-breadcrumb-item>
        </el-breadcrumb>
        <h2 class="page-title">报告列表</h2>
      </div>
      <div class="header-right">
        <el-button v-if="canCreateReportPermission" type="primary" :icon="Plus" @click="goCreate">新建报告</el-button>
      </div>
    </div>

    <!-- 筛选工具栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form :model="filter" inline label-position="top">
        <el-form-item label="品牌名">
          <el-input
            v-model="filter.keyword"
            placeholder="搜索品牌名"
            clearable
            style="width: 200px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item label="行业">
          <el-select v-model="filter.industry" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="opt in industryOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="身份">
          <el-select v-model="filter.industryRole" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="opt in roleOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="生成状态">
          <el-tooltip content="v1 暂不支持此筛选,仅用于展示" placement="top">
            <el-select v-model="filter.generationStatus" placeholder="全部" clearable style="width: 140px" disabled>
              <el-option label="已完成" value="DONE" />
              <el-option label="生成中" value="RUNNING" />
              <el-option label="失败" value="FAILED" />
            </el-select>
          </el-tooltip>
        </el-form-item>
        <el-form-item label="冻结">
          <el-tooltip content="v1 暂不支持此筛选" placement="top">
            <el-radio-group v-model="filter.frozen" disabled>
              <el-radio-button :value="undefined">全部</el-radio-button>
              <el-radio-button :value="true">已冻结</el-radio-button>
              <el-radio-button :value="false">未冻结</el-radio-button>
            </el-radio-group>
          </el-tooltip>
        </el-form-item>
        <el-form-item label="创建日期">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            start-placeholder="开始"
            end-placeholder="结束"
            value-format="YYYY-MM-DD"
            style="width: 280px"
          />
        </el-form-item>
        <el-form-item label=" ">
          <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <el-table
        :data="tableData"
        v-loading="loading"
        stripe
        highlight-current-row
        style="width: 100%"
        @row-click="onRowClick"
      >
        <el-table-column prop="brandName" label="品牌" min-width="140">
          <template #default="{ row }">
            <span class="brand-name">{{ row.brandName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="行业" width="120">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ industryLabel(row.industry) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="身份" width="120">
          <template #default="{ row }">{{ roleLabel(row.industryRole) }}</template>
        </el-table-column>
        <el-table-column prop="region" label="地区" width="120" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag
              :type="getStatusMeta(row.latestVersion?.generationStatus).tagType"
              size="small"
            >
              <el-icon v-if="getStatusMeta(row.latestVersion?.generationStatus).loading" class="is-loading">
                <Loading />
              </el-icon>
              {{ getStatusMeta(row.latestVersion?.generationStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="冻结" width="80" align="center">
          <template #default="{ row }">
            <el-tooltip
              v-if="row.latestVersion?.frozen"
              :content="`冻结于 ${formatDateTime(row.latestVersion.frozenAt)}`"
              placement="top"
            >
              <el-icon class="frozen-icon"><Lock /></el-icon>
            </el-tooltip>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="版本" width="80" align="center">
          <template #default="{ row }">
            <span class="version-badge">v{{ row.latestVersion?.versionNo ?? 1 }}</span>
            <span v-if="row.versionCount > 1" class="version-count">({{ row.versionCount }})</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">
            <div>{{ formatDateTime(row.createdAt) }}</div>
            <div v-if="row.latestVersion?.exportSuccessAt" class="text-muted text-xs">
              最近导出 {{ formatDateTime(row.latestVersion.exportSuccessAt) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="360" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click.stop="goDetail(row)">
              {{ isInProgress(row.latestVersion?.generationStatus) ? '查看进度' : '查看' }}
            </el-button>
            <el-tooltip
              :disabled="canViewPrompts(row)"
              content="报告生成完成后可查看 Prompt 详情"
              placement="top"
            >
              <span>
                <el-button
                  text
                  type="primary"
                  size="small"
                  :disabled="!canViewPrompts(row)"
                  @click.stop="goPrompts(row)"
                >
                  查看 Prompt
                </el-button>
              </span>
            </el-tooltip>
            <el-tooltip
              :disabled="canEdit(row)"
              :content="editDisabledReason(row)"
              placement="top"
            >
              <span>
                <el-button
                  text
                  type="primary"
                  size="small"
                  :disabled="!canEdit(row)"
                  :loading="derivingReportId === row.reportId"
                  @click.stop="goEdit(row)"
                >
                  {{ editButtonText(row) }}
                </el-button>
              </span>
            </el-tooltip>
            <el-button
              text
              type="primary"
              size="small"
              :disabled="!canRegenerate(row)"
              @click.stop="goRegenerate(row)"
            >
              再次生成
            </el-button>
            <el-button
              v-if="canDeleteReportPermission"
              text
              type="danger"
              size="small"
              :disabled="!canDelete(row)"
              :loading="deletingReportId === row.reportId"
              @click.stop="confirmDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无报告,新建后约 2.5-3.5 分钟生成首版">
            <el-button v-if="canCreateReportPermission" type="primary" :icon="Plus" @click="goCreate">新建报告</el-button>
          </el-empty>
        </template>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <el-dialog v-model="versionDialogVisible" title="选择报告版本" width="560px">
      <el-table :data="viewableVersions" v-loading="versionLoading" stripe>
        <el-table-column label="版本" width="90">
          <template #default="{ row }">v{{ row.versionNo }}</template>
        </el-table-column>
        <el-table-column label="生成时间">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusMeta(row.generationStatus).tagType" size="small">
              {{ getStatusMeta(row.generationStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openVersion(row.versionNo)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Search, Loading, Lock } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteReport,
  deriveVersion,
  listReportVersions,
  listReports,
  type ReportListItemVO,
  type ReportListQueryRequest,
  type ReportVersionOptionVO
} from '@/api/presaleReport'
import { useUserStore } from '@/stores/user'
import { formatDateTime, toRfc3339Range } from '@/utils/presale/formatDateTime'
import { getStatusMeta, isInProgress } from '@/utils/presale/statusMeta'

const router = useRouter()
const userStore = useUserStore()
const canCreateReportPermission = computed(() =>
  userStore.hasPermission('presale.report.create')
)
const canDeleteReportPermission = computed(() =>
  userStore.hasPermission('presale.report.delete')
)

// TODO: 这两份字典应该从 sys_dict_item(presale_industry / presale_industry_role)动态加载
// v1 先写死,P1·F·1·b 补全字典加载逻辑
const industryOptions = [
  { value: 'restaurant', label: '餐饮' },
  { value: 'education', label: '教培' },
  { value: 'automotive', label: '汽车' },
  { value: 'retail', label: '电商零售' },
  { value: 'finance', label: '金融' },
  { value: 'tourism', label: '旅游酒店' },
  { value: 'medical_beauty', label: '医美美容' },
  { value: 'tech_software', label: 'SaaS 企业软件' }
]
const roleOptions = [
  { value: 'chain_brand', label: '连锁品牌' },
  { value: 'single_store', label: '单店' },
  { value: 'franchise', label: '加盟商' },
  { value: 'manufacturer', label: '生产厂家' },
  { value: 'dealer', label: '经销商' },
  { value: 'platform', label: '平台方' },
  { value: 'service_provider', label: '服务商' },
  { value: 'kol', label: '个人/KOL' }
]

const industryLabel = (key: string) =>
  industryOptions.find((x) => x.value === key)?.label ?? key
const roleLabel = (key: string) => roleOptions.find((x) => x.value === key)?.label ?? key

const filter = reactive<ReportListQueryRequest>({
  keyword: '',
  industry: '',
  industryRole: '',
  generationStatus: '',
  frozen: undefined
})

const dateRange = ref<[Date, Date] | null>(null)

const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0
})

const tableData = ref<ReportListItemVO[]>([])
const loading = ref(false)
const deletingReportId = ref<number | null>(null)
const derivingReportId = ref<number | null>(null)
const versionDialogVisible = ref(false)
const versionLoading = ref(false)
const versionDialogReportId = ref<number | null>(null)
const viewableVersions = ref<ReportVersionOptionVO[]>([])

async function loadData() {
  loading.value = true
  try {
    const dateParams = toRfc3339Range(dateRange.value)
    const params: ReportListQueryRequest = {
      page: pagination.page,
      pageSize: pagination.pageSize,
      keyword: filter.keyword || undefined,
      industry: filter.industry || undefined,
      industryRole: filter.industryRole || undefined,
      ...dateParams
    }
    const page = await listReports(params)
    tableData.value = page.records
    pagination.total = page.total
  } finally {
    loading.value = false
  }
}

function onSearch() {
  pagination.page = 1
  loadData()
}

function onReset() {
  filter.keyword = ''
  filter.industry = ''
  filter.industryRole = ''
  filter.generationStatus = ''
  filter.frozen = undefined
  dateRange.value = null
  pagination.page = 1
  loadData()
}

function canEdit(row: ReportListItemVO): boolean {
  return row.canEdit === true
}

function editButtonText(row: ReportListItemVO): string {
  return row.latestVersion?.frozen ? '派生并编辑' : '编辑'
}

function editDisabledReason(row: ReportListItemVO): string {
  return row.canEditReason || '当前报告不可编辑'
}

function canDelete(row: ReportListItemVO): boolean {
  return !isInProgress(row.latestVersion?.generationStatus)
}

function canRegenerate(row: ReportListItemVO): boolean {
  return row.latestVersion?.generationStatus === 'DONE'
}

function canViewPrompts(row: ReportListItemVO): boolean {
  return row.latestVersion?.generationStatus === 'DONE' && Boolean(row.latestVersion?.versionNo)
}

async function confirmDelete(row: ReportListItemVO) {
  if (!canDelete(row) || deletingReportId.value) return
  const confirmed = await ElMessageBox.confirm(
    `删除后「${row.brandName}」将从报告列表中移除。确认删除?`,
    '删除售前报告',
    { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
  ).catch(() => false)
  if (!confirmed) return

  deletingReportId.value = row.reportId
  try {
    await deleteReport(row.reportId)
    ElMessage.success('已删除')
    if (tableData.value.length === 1 && pagination.page > 1) {
      pagination.page -= 1
    }
    await loadData()
  } finally {
    deletingReportId.value = null
  }
}

function onRowClick(row: ReportListItemVO) {
  goDetail(row)
}

function goCreate() {
  router.push('/admin/presale/report/create')
}

function goDetail(row: ReportListItemVO) {
  if (isInProgress(row.latestVersion?.generationStatus)) {
    router.push(`/admin/presale/report/${row.reportId}/progress`)
  } else if (row.versionCount > 1) {
    void showVersionDialog(row)
  } else {
    router.push(`/admin/presale/report/${row.reportId}/detail`)
  }
}

async function showVersionDialog(row: ReportListItemVO) {
  versionDialogReportId.value = row.reportId
  versionDialogVisible.value = true
  versionLoading.value = true
  try {
    const versions = await listReportVersions(row.reportId)
    viewableVersions.value = versions
      .filter((item) => item.generationStatus === 'DONE' && !item.disabled)
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
  } finally {
    versionLoading.value = false
  }
}

function openVersion(versionNo: number) {
  if (!versionDialogReportId.value) return
  versionDialogVisible.value = false
  router.push(`/admin/presale/report/${versionDialogReportId.value}/detail?versionNo=${versionNo}`)
}

function goRegenerate(row: ReportListItemVO) {
  if (!canRegenerate(row)) return
  router.push({
    path: '/admin/presale/report/create',
    query: { regenerateFrom: String(row.reportId) }
  })
}

function goPrompts(row: ReportListItemVO) {
  if (!canViewPrompts(row)) return
  router.push(`/admin/presale/report/${row.reportId}/versions/${row.latestVersion!.versionNo}/prompts`)
}

async function goEdit(row: ReportListItemVO) {
  if (!canEdit(row) || !row.latestVersion || derivingReportId.value) return
  if (!row.latestVersion.frozen) {
    router.push(`/admin/presale/report/${row.reportId}/edit`)
    return
  }
  const confirmed = await ElMessageBox.confirm(
    `将基于版本 v${row.latestVersion.versionNo} 创建新版本,新版本会复制当前的所有内容,原版本保持冻结不变。`,
    '派生新版本并编辑',
    { confirmButtonText: '创建并编辑', cancelButtonText: '取消', type: 'warning' }
  ).catch(() => false)
  if (!confirmed) return

  derivingReportId.value = row.reportId
  try {
    const res = await deriveVersion(row.reportId, row.latestVersion.versionNo)
    ElMessage.success(`已创建 v${res.newVersionNo}`)
    router.push(`/admin/presale/report/${row.reportId}/edit`)
  } finally {
    derivingReportId.value = null
  }
}

onMounted(loadData)
</script>

<style scoped>
.presale-report-list {
  padding: 16px 24px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 16px;
}
.page-title {
  margin: 8px 0 0 0;
  font-size: 22px;
  font-weight: 600;
}
.filter-card {
  margin-bottom: 16px;
}
.table-card {
  margin-bottom: 16px;
}
.brand-name {
  font-weight: 500;
}
.frozen-icon {
  color: #409eff;
  font-size: 16px;
}
.version-badge {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 13px;
}
.version-count {
  color: #909399;
  font-size: 12px;
  margin-left: 4px;
}
.text-muted {
  color: #909399;
}
.text-xs {
  font-size: 11px;
}
.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
