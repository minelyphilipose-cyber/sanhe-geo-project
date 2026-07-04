<template>
  <div class="admin-page partner-start-page">
    <div class="admin-page-header partner-start-hero">
      <div>
        <div class="admin-page-kicker">交付运营</div>
        <h1 class="admin-page-title">合伙人启动工单</h1>
        <div class="admin-page-subtitle">审批合伙人提交的项目启动资料，分配运营负责人，并跟进启动配置补齐。</div>
      </div>
      <div class="admin-page-actions">
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <div class="admin-filter-panel">
      <el-tabs v-model="query.status" class="scope-tabs" @tab-change="onStatusChange">
        <el-tab-pane label="待审批" name="submitted" />
        <el-tab-pane label="待配置" name="approved" />
        <el-tab-pane label="已驳回" name="rejected" />
        <el-tab-pane label="全部" name="" />
      </el-tabs>
      <div class="admin-filter-controls">
        <el-input
          v-model="searchText"
          placeholder="搜索客户 / 项目 / 合伙人"
          clearable
          style="width: 260px"
          @keyup.enter="load"
        />
        <el-button type="primary" plain @click="load">查询</el-button>
      </div>
    </div>

    <div class="admin-metric-grid">
      <div class="admin-metric-card" style="--metric-accent: #2563eb; --metric-tone: #eff6ff">
        <span class="admin-metric-label">当前筛选</span>
        <strong class="admin-metric-value">{{ page.total }}</strong>
        <span class="admin-metric-hint">启动工单</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #f59e0b; --metric-tone: #fffbeb">
        <span class="admin-metric-label">待审批</span>
        <strong class="admin-metric-value">{{ submittedCount }}</strong>
        <span class="admin-metric-hint">本页需处理</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #059669; --metric-tone: #ecfdf5">
        <span class="admin-metric-label">待配置</span>
        <strong class="admin-metric-value">{{ approvedCount }}</strong>
        <span class="admin-metric-hint">运营补齐资料</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #dc2626; --metric-tone: #fef2f2">
        <span class="admin-metric-label">已驳回</span>
        <strong class="admin-metric-value">{{ rejectedCount }}</strong>
        <span class="admin-metric-hint">合伙人需修正</span>
      </div>
    </div>

    <el-card shadow="never" class="admin-table-card">
      <div class="table-header">
        <div>
          <div class="table-title">工单列表</div>
          <div class="table-subtitle">审批通过后，项目将进入运营配置阶段。</div>
        </div>
      </div>

      <DataState :loading="loading" :empty="!loading && filteredRows.length === 0" empty-text="暂无启动工单">
        <el-table :data="filteredRows" border table-layout="fixed" class="start-request-table">
          <el-table-column label="工单" width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="admin-cell-stack">
                <span class="admin-cell-main">{{ row.requestNo }}</span>
                <span class="admin-cell-sub">{{ formatTime(row.submittedAt) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="客户 / 项目" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="admin-entity-cell compact">
                <div class="admin-entity-avatar request-avatar">{{ initial(row.projectName || row.companyName) }}</div>
                <div class="min-w-0">
                  <div class="admin-entity-main">{{ row.projectName || '-' }}</div>
                  <div class="admin-entity-sub">{{ row.companyName || '-' }} · {{ row.brandName || '-' }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="合伙人" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="admin-cell-stack">
                <span class="admin-cell-main">{{ row.partnerName || '-' }}</span>
                <span class="admin-cell-sub">申请人：{{ row.applicantUserName || '-' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="负责人" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="admin-cell-stack">
                <span class="admin-cell-main">{{ row.assignedInternalOwnerName || row.defaultInternalOwnerName || '待分配' }}</span>
                <span class="admin-cell-sub">{{ row.assignedInternalOwnerName ? '已分配' : row.defaultInternalOwnerName ? '客户默认负责人' : '审批时选择' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="140">
            <template #default="{ row }">
              <span class="admin-status-tag" :class="statusClass(row.status)">
                {{ statusLabel(row.status) }}
              </span>
              <div class="table-subtext">{{ projectStatusLabel(row.projectStatus) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <div class="admin-row-actions">
                <el-button link type="primary" @click="openDetail(row)">详情</el-button>
                <el-button v-if="row.status === 'submitted'" link type="primary" @click="openApprove(row)">审批通过</el-button>
                <el-button v-if="row.status === 'submitted'" link type="danger" @click="openReject(row)">驳回</el-button>
                <el-button
                  v-if="row.status === 'approved' && row.projectStatus === 'approved_pending_setup'"
                  link
                  type="primary"
                  :loading="actionLoading"
                  @click="markSetupReady(row)"
                >
                  配置完成
                </el-button>
                <el-button link @click="goProject(row.projectId)">项目详情</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="admin-table-footer">
          <el-pagination
            background
            layout="prev, pager, next, total"
            :current-page="page.current"
            :page-size="page.size"
            :total="page.total"
            @current-change="onPageChange"
          />
        </div>
      </DataState>
    </el-card>

    <el-drawer v-model="detailVisible" title="启动工单详情" size="720px" class="start-request-drawer">
      <template v-if="current">
        <section class="drawer-section">
          <div class="section-title">基础信息</div>
          <div class="detail-grid">
            <div><span>工单编号</span><strong>{{ current.requestNo }}</strong></div>
            <div><span>状态</span><strong>{{ statusLabel(current.status) }}</strong></div>
            <div><span>客户</span><strong>{{ current.companyName || '-' }}</strong></div>
            <div><span>项目</span><strong>{{ current.projectName || '-' }}</strong></div>
            <div><span>品牌</span><strong>{{ current.brandName || '-' }}</strong></div>
            <div><span>合伙人</span><strong>{{ current.partnerName || '-' }}</strong></div>
            <div><span>申请人</span><strong>{{ current.applicantUserName || '-' }}</strong></div>
            <div><span>运营负责人</span><strong>{{ current.assignedInternalOwnerName || current.defaultInternalOwnerName || '-' }}</strong></div>
          </div>
        </section>
        <section class="drawer-section">
          <div class="section-title">额度快照</div>
          <div class="quota-list">
            <div v-for="item in parsedQuota(current.partnerAllocatedQuotaJson)" :key="item.channelCode" class="quota-item">
              <div>
                <strong>{{ item.channelName || item.channelCode }}</strong>
                <span>{{ item.periodType || '-' }}</span>
              </div>
              <b>{{ item.allocatedCount || 0 }} / {{ item.quotaLimit || 0 }}</b>
            </div>
            <el-empty v-if="parsedQuota(current.partnerAllocatedQuotaJson).length === 0" description="暂无额度快照" :image-size="80" />
          </div>
        </section>
        <section v-if="current.rejectReasonText" class="drawer-section">
          <div class="section-title">驳回原因</div>
          <div class="reject-box">{{ current.rejectReasonText }}</div>
        </section>
      </template>
    </el-drawer>

    <el-dialog v-model="approveVisible" title="审批通过" width="520px" class="admin-editor-dialog">
      <el-form label-width="108px">
        <el-form-item label="运营负责人" required>
          <el-select v-model="approveForm.assignedInternalOwnerId" filterable style="width: 100%" placeholder="请选择运营负责人">
            <el-option v-for="item in ownerOptions" :key="item.id" :label="item.displayName || item.username" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="审批备注">
          <el-input v-model="approveForm.reviewRemark" type="textarea" :rows="3" placeholder="可补充交接说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="submitApprove">确认通过</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rejectVisible" title="驳回工单" width="520px" class="admin-editor-dialog">
      <el-form label-width="92px">
        <el-form-item label="驳回原因" required>
          <el-input v-model="rejectForm.rejectReasonText" type="textarea" :rows="4" placeholder="说明需要合伙人员工修改的资料或问题" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="actionLoading" @click="submitReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="setupIssueVisible" title="启动配置未完成" width="640px" class="admin-editor-dialog setup-issue-dialog">
      <div class="setup-issue-intro">
        <strong>{{ setupIssueRequest?.projectName || '当前项目' }}</strong>
        <span>还缺少以下必配项，补齐后再回到本页点击“配置完成”。</span>
      </div>
      <div class="setup-issue-list">
        <div v-for="(item, index) in setupIssueItems" :key="`${item.type}-${item.platform}-${index}`" class="setup-issue-item">
          <div class="issue-index">{{ index + 1 }}</div>
          <div class="issue-body">
            <div class="issue-title">{{ item.message || setupIssueFallback(item) }}</div>
            <div class="issue-meta">
              <el-tag size="small" :type="item.type === 'self_media_account' ? 'warning' : 'info'">
                {{ item.type === 'self_media_account' ? '自媒体账号' : '指纹环境' }}
              </el-tag>
              <span>{{ item.label || item.platform || '自媒体平台' }}</span>
            </div>
          </div>
          <el-button size="small" @click="goSetupIssue(item)">
            {{ item.type === 'self_media_account' ? '去项目配置' : '去运行环境' }}
          </el-button>
        </div>
      </div>
      <el-alert
        class="setup-issue-tip"
        type="info"
        :closable="false"
        show-icon
        title="Agent 官网和行业资讯站可后续补充；自媒体账号和指纹环境会阻断项目启动。"
      />
      <template #footer>
        <el-button @click="setupIssueVisible = false">稍后处理</el-button>
        <el-button type="primary" @click="goProject(setupIssueRequest?.projectId)">打开项目详情</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  approveAdminProjectStartRequest,
  getAdminProjectStartRequests,
  markAdminProjectStartRequestSetupReady,
  rejectAdminProjectStartRequest,
  type AdminProjectStartRequest,
} from '@/api/project'
import { getDeliveryOwnerOptions, type SalesOwnerOption } from '@/api/customer'

const router = useRouter()
const loading = ref(false)
const actionLoading = ref(false)
const rows = ref<AdminProjectStartRequest[]>([])
const ownerOptions = ref<SalesOwnerOption[]>([])
const searchText = ref('')
const query = reactive({ current: 1, size: 10, status: 'submitted' })
const page = reactive({ current: 1, size: 10, total: 0 })
const current = ref<AdminProjectStartRequest | null>(null)
const detailVisible = ref(false)
const approveVisible = ref(false)
const rejectVisible = ref(false)
const setupIssueVisible = ref(false)
const setupIssueRequest = ref<AdminProjectStartRequest | null>(null)
const setupIssueItems = ref<SetupMissingItem[]>([])
const approveForm = reactive({ assignedInternalOwnerId: undefined as number | undefined, reviewRemark: '' })
const rejectForm = reactive({ rejectReasonText: '' })

type SetupMissingItem = {
  type?: string
  platform?: string
  label?: string
  message?: string
}

const filteredRows = computed(() => {
  const keyword = searchText.value.trim().toLowerCase()
  if (!keyword) return rows.value
  return rows.value.filter((row) =>
    [row.companyName, row.projectName, row.brandName, row.partnerName, row.requestNo]
      .some((value) => String(value || '').toLowerCase().includes(keyword)),
  )
})
const submittedCount = computed(() => rows.value.filter((row) => row.status === 'submitted').length)
const approvedCount = computed(() => rows.value.filter((row) => row.status === 'approved').length)
const rejectedCount = computed(() => rows.value.filter((row) => row.status === 'rejected').length)

async function load() {
  loading.value = true
  try {
    const { data } = await getAdminProjectStartRequests({
      current: query.current,
      size: query.size,
      status: query.status || undefined,
    })
    rows.value = data.data.records || []
    page.current = data.data.current
    page.size = data.data.size
    page.total = data.data.total
  } finally {
    loading.value = false
  }
}

async function loadOwners() {
  const { data } = await getDeliveryOwnerOptions()
  ownerOptions.value = data.data || []
}

function onStatusChange() {
  query.current = 1
  load()
}

function onPageChange(value: number) {
  query.current = value
  load()
}

function openDetail(row: AdminProjectStartRequest) {
  current.value = row
  detailVisible.value = true
}

function openApprove(row: AdminProjectStartRequest) {
  current.value = row
  approveForm.assignedInternalOwnerId = row.defaultInternalOwnerId || row.assignedInternalOwnerId || undefined
  approveForm.reviewRemark = ''
  approveVisible.value = true
}

function openReject(row: AdminProjectStartRequest) {
  current.value = row
  rejectForm.rejectReasonText = ''
  rejectVisible.value = true
}

async function submitApprove() {
  if (!current.value) return
  if (!approveForm.assignedInternalOwnerId) {
    ElMessage.warning('请选择运营负责人')
    return
  }
  actionLoading.value = true
  try {
    await approveAdminProjectStartRequest(current.value.id, {
      assignedInternalOwnerId: approveForm.assignedInternalOwnerId,
      reviewRemark: approveForm.reviewRemark,
    })
    ElMessage.success('已审批通过')
    approveVisible.value = false
    await load()
  } finally {
    actionLoading.value = false
  }
}

async function submitReject() {
  if (!current.value) return
  if (!rejectForm.rejectReasonText.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  actionLoading.value = true
  try {
    await rejectAdminProjectStartRequest(current.value.id, {
      rejectReasonCode: 'material_incomplete',
      rejectReasonText: rejectForm.rejectReasonText,
    })
    ElMessage.success('已驳回')
    rejectVisible.value = false
    await load()
  } finally {
    actionLoading.value = false
  }
}

async function markSetupReady(row: AdminProjectStartRequest) {
  await ElMessageBox.confirm('确认该项目的指纹浏览器与自媒体账号等启动配置已补齐？系统会再次校验必配项。', '配置完成', { type: 'warning' })
  actionLoading.value = true
  try {
    await markAdminProjectStartRequestSetupReady(row.id, {}, true)
    ElMessage.success('已标记配置完成')
    await load()
  } catch (error) {
    if (openSetupIssues(row, error)) return
    ElMessage.error(error instanceof Error ? error.message : '配置完成失败')
  } finally {
    actionLoading.value = false
  }
}

function goProject(projectId?: number) {
  if (projectId) router.push(`/admin/projects/${projectId}`)
}

function openSetupIssues(row: AdminProjectStartRequest, error: unknown) {
  const apiError = error as { data?: { errorCode?: string; missingItems?: SetupMissingItem[] } }
  if (apiError?.data?.errorCode !== 'PROJECT_SETUP_NOT_READY') {
    return false
  }
  setupIssueRequest.value = row
  setupIssueItems.value = Array.isArray(apiError.data.missingItems) ? apiError.data.missingItems : []
  setupIssueVisible.value = true
  return true
}

function setupIssueFallback(item: SetupMissingItem) {
  const label = item.label || item.platform || '自媒体平台'
  return item.type === 'self_media_account'
    ? `${label}未配置启用的自媒体账号`
    : `${label}未绑定启用的指纹浏览器环境`
}

function goSetupIssue(item: SetupMissingItem) {
  if (item.type === 'browser_environment') {
    router.push('/admin/monitoring/self-media-runtime')
    return
  }
  goProject(setupIssueRequest.value?.projectId)
}

function parsedQuota(raw?: string | null): any[] {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function initial(value?: string | null) {
  return String(value || '工').trim().slice(0, 1)
}

function formatTime(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

function statusLabel(status?: string | null) {
  const map: Record<string, string> = {
    submitted: '待审批',
    approved: '待配置',
    rejected: '已驳回',
    cancelled: '已取消',
  }
  return map[status || ''] || status || '-'
}

function projectStatusLabel(status?: string | null) {
  const map: Record<string, string> = {
    submitted: '已提交总部',
    rejected: '已驳回',
    approved_pending_setup: '待运营配置',
    setup_ready: '配置完成',
    active: '已启动',
  }
  return map[status || ''] || status || '-'
}

function statusClass(status?: string | null) {
  return {
    submitted: 'is-warning',
    approved: 'is-info',
    rejected: 'is-danger',
    cancelled: 'is-muted',
  }[status || ''] || 'is-muted'
}

onMounted(async () => {
  await Promise.all([load(), loadOwners()])
})
</script>

<style scoped>
.partner-start-hero {
  background:
    radial-gradient(circle at 92% 0%, rgba(37, 99, 235, 0.12), transparent 28%),
    linear-gradient(135deg, #f8fbff 0%, #eef8f5 100%);
}

.table-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.table-title {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}

.table-subtitle,
.table-subtext {
  margin-top: 4px;
  font-size: 12px;
  color: #64748b;
}

.request-avatar {
  background: linear-gradient(135deg, #2563eb, #14b8a6);
}

.admin-entity-cell.compact {
  gap: 10px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.detail-grid > div,
.quota-item,
.reject-box {
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f8fafc;
  padding: 12px;
}

.detail-grid span,
.quota-item span {
  display: block;
  color: #64748b;
  font-size: 12px;
  margin-bottom: 6px;
}

.detail-grid strong,
.quota-item strong,
.quota-item b {
  color: #0f172a;
}

.drawer-section + .drawer-section {
  margin-top: 20px;
}

.section-title {
  font-size: 15px;
  font-weight: 700;
  margin-bottom: 12px;
  color: #0f172a;
}

.quota-list {
  display: grid;
  gap: 10px;
}

.quota-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.reject-box {
  line-height: 1.7;
  color: #475569;
}

.setup-issue-intro {
  display: grid;
  gap: 6px;
  padding: 14px 16px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #eff6ff;
  color: #334155;
}

.setup-issue-intro strong {
  color: #0f172a;
  font-size: 16px;
}

.setup-issue-list {
  display: grid;
  gap: 10px;
  margin-top: 16px;
}

.setup-issue-item {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
}

.issue-index {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 999px;
  background: #eef2ff;
  color: #2563eb;
  font-weight: 700;
}

.issue-body {
  min-width: 0;
}

.issue-title {
  font-weight: 700;
  color: #0f172a;
}

.issue-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
}

.setup-issue-tip {
  margin-top: 14px;
}
</style>
