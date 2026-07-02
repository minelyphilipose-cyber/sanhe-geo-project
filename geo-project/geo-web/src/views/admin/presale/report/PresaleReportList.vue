<template>
  <div class="presale-report-list admin-page">
    <!-- 页头 -->
    <div class="page-header admin-page-header">
      <div class="header-left">
        <div class="admin-page-kicker">AI可见度诊断报告</div>
        <h1 class="page-title admin-page-title">报告列表</h1>
        <div class="admin-page-subtitle">沉淀每一次诊断的版本与证据，让 AI 可见度的演进有迹可循。</div>
      </div>
      <div class="header-right admin-page-actions">
        <el-button v-if="canManagePage03Config" @click="goPage03Config">PAGE03 配置</el-button>
        <el-button v-if="canManagePage03Config" @click="goNarrativeConfig">诊断文案配置</el-button>
        <el-button v-if="canCreateReportPermission" type="primary" :icon="Plus" @click="goCreate">新建报告</el-button>
      </div>
    </div>

    <!-- 筛选工具栏 -->
    <el-card shadow="never" class="filter-card admin-surface">
      <el-form :model="filter" class="filter-form" label-position="top">
        <el-form-item label="品牌名">
          <el-input
            v-model="filter.keyword"
            placeholder="搜索品牌名"
            clearable
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item label="行业">
          <el-select
            v-model="filter.industry"
            placeholder="全部 / 手动输入"
            clearable
            filterable
            allow-create
            default-first-option
          >
            <el-option v-for="opt in industryOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="身份">
          <el-select
            v-model="filter.industryRole"
            placeholder="全部 / 手动输入"
            clearable
            filterable
            allow-create
            default-first-option
          >
            <el-option v-for="opt in roleOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建日期">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            start-placeholder="开始"
            end-placeholder="结束"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item class="filter-actions">
          <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <div class="admin-metric-grid">
      <div class="admin-metric-card" style="--metric-accent: #2563eb; --metric-tone: #eff6ff">
        <span class="admin-metric-label">报告总数</span>
        <strong class="admin-metric-value">{{ pagination.total }}</strong>
        <span class="admin-metric-hint">当前筛选结果</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #059669; --metric-tone: #ecfdf5">
        <span class="admin-metric-label">已完成</span>
        <strong class="admin-metric-value">{{ doneCount }}</strong>
        <span class="admin-metric-hint">本页可见</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #f59e0b; --metric-tone: #fffbeb">
        <span class="admin-metric-label">生成中</span>
        <strong class="admin-metric-value">{{ runningCount }}</strong>
        <span class="admin-metric-hint">本页可见</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #ef4444; --metric-tone: #fef2f2">
        <span class="admin-metric-label">失败/需处理</span>
        <strong class="admin-metric-value">{{ failedCount }}</strong>
        <span class="admin-metric-hint">本页可见</span>
      </div>
    </div>

    <el-card shadow="never" class="table-card admin-table-card">
      <el-table
        class="presale-report-table"
        :data="tableData"
        v-loading="loading"
        border
        table-layout="fixed"
        highlight-current-row
        style="width: 100%"
        @row-click="onRowClick"
      >
        <el-table-column label="报告对象" min-width="360" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="admin-entity-cell">
              <div class="admin-entity-avatar is-green">{{ entityInitial(row.brandName) }}</div>
              <div class="min-w-0">
                <div class="admin-entity-main">{{ row.brandName }}</div>
                <div class="admin-entity-sub">
                  {{ industryLabel(row.industry) }} · {{ roleLabel(row.industryRole) }} · {{ row.region || '未设置地区' }}
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="报告记录" min-width="360">
          <template #default="{ row }">
            <div class="report-record-cell">
              <div class="report-record-main">
                <span class="version-badge">v{{ row.latestVersion?.versionNo ?? 1 }}</span>
                <span v-if="row.versionCount > 1" class="version-count">共 {{ row.versionCount }} 版</span>
                <span class="admin-status-tag" :class="reportStatusClass(row.latestVersion?.generationStatus)">
                  <el-icon v-if="getStatusMeta(row.latestVersion?.generationStatus).loading" class="is-loading">
                    <Loading />
                  </el-icon>
                  {{ getStatusMeta(row.latestVersion?.generationStatus).label }}
                </span>
                <span
                  class="admin-mini-pill"
                  :class="row.latestVersion?.frozen ? 'is-blue' : ''"
                >
                  <el-icon v-if="row.latestVersion?.frozen"><Lock /></el-icon>
                  {{ row.latestVersion?.frozen ? '已冻结' : '未冻结' }}
                </span>
              </div>
              <div class="report-record-sub">
                创建 {{ formatDateTime(row.createdAt) }}
                <span v-if="row.latestVersion?.exportSuccessAt">
                  · 最近导出 {{ formatDateTime(row.latestVersion.exportSuccessAt) }}
                </span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <div class="admin-row-actions">
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
              <el-button
                class="is-wide"
                text
                type="primary"
                size="small"
                :disabled="!canRegenerate(row)"
                @click.stop="goRegenerate(row)"
              >
                再次生成
              </el-button>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无报告,请前往新建">
            <el-button v-if="canCreateReportPermission" type="primary" :icon="Plus" @click="goCreate">新建报告</el-button>
          </el-empty>
        </template>
      </el-table>

      <div class="pagination-wrapper admin-table-footer">
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
const canManagePage03Config = computed(() =>
  userStore.hasRole(['delivery_manager', 'manager', 'super_admin'])
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
  industryRole: ''
})

const dateRange = ref<[Date, Date] | null>(null)

const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0
})

const tableData = ref<ReportListItemVO[]>([])
const doneCount = computed(() =>
  tableData.value.filter((row) => row.latestVersion?.generationStatus === 'DONE').length
)
const runningCount = computed(() =>
  tableData.value.filter((row) => isInProgress(row.latestVersion?.generationStatus)).length
)
const failedCount = computed(() =>
  tableData.value.filter((row) => row.latestVersion?.generationStatus === 'FAILED').length
)
const loading = ref(false)
const deletingReportId = ref<number | null>(null)
const derivingReportId = ref<number | null>(null)
const versionDialogVisible = ref(false)
const versionLoading = ref(false)
const versionDialogReportId = ref<number | null>(null)
const viewableVersions = ref<ReportVersionOptionVO[]>([])

function entityInitial(value?: string | null) {
  const text = String(value || '').trim()
  return text ? Array.from(text)[0] : '报'
}

function reportStatusClass(status?: string | null) {
  if (status === 'DONE') return 'is-success'
  if (status === 'FAILED') return 'is-danger'
  if (isInProgress(status)) return 'is-warning'
  return 'is-muted'
}

async function loadData() {
  loading.value = true
  try {
    const dateParams = toRfc3339Range(dateRange.value)
    const params: ReportListQueryRequest = {
      page: pagination.page,
      pageSize: pagination.pageSize,
      keyword: filter.keyword?.trim() || undefined,
      industry: filter.industry?.trim() || undefined,
      industryRole: filter.industryRole?.trim() || undefined,
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
    '删除AI可见度诊断报告',
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

function goPage03Config() {
  router.push('/admin/presale/report/page03-config')
}

function goNarrativeConfig() {
  router.push('/admin/presale/report/narrative-config')
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
.header-left {
  min-width: 0;
}
.header-right {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}
.filter-form {
  display: grid;
  grid-template-columns: minmax(180px, 240px) minmax(140px, 164px) minmax(140px, 164px) minmax(260px, 320px) auto;
  gap: 20px 24px;
  align-items: end;
}
.filter-form :deep(.el-form-item) {
  min-width: 0;
  margin-right: 0;
  margin-bottom: 0;
}
.filter-form :deep(.el-form-item__label) {
  padding-bottom: 8px;
  color: #606266;
  line-height: 1.2;
}
.filter-form :deep(.el-input),
.filter-form :deep(.el-select),
.filter-form :deep(.el-date-editor) {
  width: 100%;
}
.filter-actions :deep(.el-form-item__content) {
  display: flex;
  gap: 12px;
  flex-wrap: nowrap;
}
.filter-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
.presale-report-table :deep(.admin-row-actions) {
  grid-template-columns: repeat(4, minmax(44px, 1fr));
  gap: 4px 8px;
}
.presale-report-table :deep(.admin-row-actions .el-button) {
  min-width: 0;
}
.report-record-cell {
  display: grid;
  gap: 7px;
  min-width: 0;
}
.report-record-main {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-wrap: wrap;
}
.report-record-sub {
  min-width: 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.report-record-main .admin-mini-pill {
  gap: 4px;
}
.frozen-icon {
  color: #409eff;
  font-size: 16px;
}
.version-badge {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border: 1px solid #dbeafe;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 13px;
  font-weight: 700;
}
.version-count {
  color: #64748b;
  font-size: 12px;
}
.text-muted {
  color: #909399;
}
.text-xs {
  font-size: 11px;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
}

.admin-status-tag .el-icon {
  margin-right: 4px;
}

@media (max-width: 1440px) {
  .filter-form {
    grid-template-columns: repeat(4, minmax(150px, 1fr));
  }
}

@media (max-width: 960px) {
  .filter-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .filter-form {
    grid-template-columns: 1fr;
  }
}
</style>
