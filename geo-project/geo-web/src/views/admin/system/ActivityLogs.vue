<template>
  <div class="activity-logs-page admin-page">
    <div class="admin-page-header activity-header">
      <div>
        <div class="admin-page-kicker">监控中心</div>
        <h1 class="admin-page-title">操作日志</h1>
        <div class="admin-page-subtitle">追踪关键业务对象的操作记录、变更摘要和操作者信息，支撑审计回溯。</div>
      </div>
      <div class="admin-page-actions">
        <el-button type="primary" :loading="loading" @click="onSearch">刷新</el-button>
      </div>
    </div>

    <el-card shadow="never" class="admin-surface activity-filter-card">
      <div class="activity-filter-grid">
        <el-select v-model="query.action" class="filter-action" clearable placeholder="操作类型" @change="onSearch">
          <el-option
            v-for="item in dictStore.options('activity_action')"
            :key="item.dictKey"
            :label="item.dictValue"
            :value="item.dictKey"
          />
        </el-select>
        <el-select v-model="query.targetType" class="filter-target-type" clearable placeholder="目标类型" @change="onSearch">
          <el-option label="客户" value="company" />
          <el-option label="品牌" value="brand" />
          <el-option label="项目" value="project" />
          <el-option label="平台" value="platform" />
        </el-select>
        <el-date-picker
          v-model="query.dateRange"
          class="filter-date-range"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始"
          end-placeholder="结束"
          value-format="YYYY-MM-DD HH:mm:ss"
          @change="onSearch"
        />
        <el-input v-model.number="query.targetId" class="filter-target-id" clearable placeholder="目标编号" @keyup.enter="onSearch" />
        <el-button class="filter-submit" type="primary" plain @click="onSearch">查询</el-button>
      </div>
    </el-card>

    <div class="admin-metric-grid activity-metric-grid">
      <div class="admin-metric-card" style="--metric-accent: #2563eb; --metric-tone: #eff6ff">
        <span class="admin-metric-label">日志总数</span>
        <strong class="admin-metric-value">{{ page.total }}</strong>
        <span class="admin-metric-hint">当前筛选条件下记录数</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #059669; --metric-tone: #ecfdf5">
        <span class="admin-metric-label">操作者</span>
        <strong class="admin-metric-value">{{ operatorCount }}</strong>
        <span class="admin-metric-hint">当前页去重统计</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #7c3aed; --metric-tone: #f5f3ff">
        <span class="admin-metric-label">项目操作</span>
        <strong class="admin-metric-value">{{ projectLogCount }}</strong>
        <span class="admin-metric-hint">目标类型为项目的记录</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #f59e0b; --metric-tone: #fffbeb">
        <span class="admin-metric-label">最近操作</span>
        <strong class="admin-metric-value compact-value">{{ latestLogTime }}</strong>
        <span class="admin-metric-hint">按列表时间倒序展示</span>
      </div>
    </div>

    <div class="activity-insight-grid">
      <section class="activity-panel">
        <div class="panel-head">
          <div>
            <div class="panel-kicker">行为分布</div>
            <h3 class="panel-title">当前页操作类型</h3>
          </div>
        </div>
        <div class="action-distribution">
          <div v-for="item in actionInsights" :key="item.action" class="distribution-row">
            <span>{{ item.label }}</span>
            <div class="distribution-track">
              <i :style="{ width: `${item.percent}%` }"></i>
            </div>
            <strong>{{ item.count }}</strong>
          </div>
        </div>
      </section>

      <section class="activity-panel">
        <div class="panel-head">
          <div>
            <div class="panel-kicker">审计范围</div>
            <h3 class="panel-title">对象覆盖</h3>
          </div>
        </div>
        <div class="scope-grid">
          <div class="scope-item">
            <span>客户</span>
            <strong>{{ targetTypeCount.company }}</strong>
          </div>
          <div class="scope-item">
            <span>品牌</span>
            <strong>{{ targetTypeCount.brand }}</strong>
          </div>
          <div class="scope-item">
            <span>项目</span>
            <strong>{{ targetTypeCount.project }}</strong>
          </div>
          <div class="scope-item">
            <span>平台</span>
            <strong>{{ targetTypeCount.platform }}</strong>
          </div>
        </div>
        <div class="scope-note">统计口径为当前页数据，用于快速判断审计记录集中在哪类业务对象。</div>
      </section>
    </div>

    <el-card shadow="never" class="admin-table-card activity-table-card">
      <div class="table-header">
        <div>
          <div class="table-title">日志明细</div>
          <div class="table-subtitle">记录操作人、动作、对象和结构化变更摘要。</div>
        </div>
        <div class="chips">
          <span class="chip chip-muted">总计 {{ page.total }}</span>
          <span class="chip chip-blue">当前页 {{ rows.length }}</span>
        </div>
      </div>

      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无操作日志">
        <el-table :data="rows" border table-layout="fixed">
          <el-table-column label="操作对象" min-width="230" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="admin-entity-cell">
                <div class="admin-entity-avatar log-avatar" :class="targetAvatarClass(row.targetType)">
                  {{ targetInitial(row.targetType) }}
                </div>
                <div class="min-w-0">
                  <div class="admin-entity-main">{{ targetDisplayText(row) }}</div>
                  <div class="admin-entity-sub">{{ formatDateTime(row.createdAt) }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="operatorName" label="操作人" width="140" show-overflow-tooltip />
          <el-table-column label="操作" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="admin-mini-pill is-blue">{{ actionLabel(row.action) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="目标类型" width="120">
            <template #default="{ row }">
              <span class="target-pill" :class="targetAvatarClass(row.targetType)">{{ targetTypeLabel(row.targetType) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="摘要" min-width="300" show-overflow-tooltip>
            <template #default="{ row }">{{ summaryText(row.detailJson) }}</template>
          </el-table-column>
          <el-table-column label="IP 地址" width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.ipAddress || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click="openDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </DataState>

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
    </el-card>

    <el-dialog v-model="detailVisible" title="日志详情" width="760px" class="admin-editor-dialog activity-detail-dialog">
      <pre class="detail-json">{{ selectedDetail }}</pre>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { ActivityLog } from '@/types'
import { getActivityLogs } from '@/api/system'
import { useDictStore } from '@/stores/dict'
import DataState from '@/components/ui/DataState.vue'
import { formatDateTime } from '@/utils/format'

const dictStore = useDictStore()
const loading = ref(false)
const detailVisible = ref(false)
const selectedDetail = ref('{}')
const rows = ref<ActivityLog[]>([])
const page = reactive({ current: 1, size: 20, total: 0 })
const ACTION_LABELS: Record<string, string> = {
  'company.create': '创建客户',
  'company.update': '更新客户',
  'company.delete': '删除客户',
  'company.account.recharge': '客户账户充值',
  'company.account.deduct': '客户账户扣款',
  'brand.create': '创建品牌',
  'brand.update': '更新品牌',
  'brand.delete': '删除品牌',
  'brand.material.upload': '上传品牌素材',
  'brand.material.delete': '删除品牌素材',
  'brand.statement.generate': '生成品牌声明',
  'brand.statement.update': '更新品牌声明',
  'brand.statement.lock': '锁定品牌声明',
  'project.create': '创建项目',
  'project.update': '更新项目',
  'project.delete': '删除项目',
  'project.sign_and_deduct': '签约并扣款',
  'project.status.update': '更新项目状态',
  'project.stage.update': '更新项目阶段',
  'project.flow.update': '更新项目流转',
  'platform.create': '创建平台',
  'platform.update': '更新平台',
  'platform.delete': '删除平台',
  'platform.presale_enabled.update': '更新售前评估开关',
  'dispatch.task.release': '释放分发任务',
}
const FIELD_LABELS: Record<string, string> = {
  id: '编号',
  companyId: '客户',
  companyName: '客户名称',
  brandId: '品牌',
  brandName: '品牌名称',
  projectCode: '项目编码',
  projectName: '项目名称',
  projectAliases: '项目别名',
  platformCode: '平台编码',
  platformName: '平台名称',
  priorityLevel: '优先级',
  apiUrl: '接口地址',
  modelId: '模型 ID',
  lowModelId: '轻量模型 ID',
  modelName: '模型名称',
  rpmLimit: 'RPM 限制',
  tpmLimit: 'TPM 限制',
  primaryKeyRef: '主密钥引用',
  backupKeyRef: '备用密钥引用',
  backupProviderName: '备用供应商',
  concurrencyLimit: '并发数',
  enabled: '启用状态',
  enabledForPresale: '售前可用',
  presaleEvaluateEnabled: '售前评估',
  enabledForArticle: '文章可用',
  enabledForGeoQuestion: 'GEO 问题可用',
  enabledForQuestionPoll: '问题轮询可用',
  maxRetry: '最大重试',
  timeoutMs: '超时时间',
  rateLimitQps: 'QPS 限制',
  degraded: '降级状态',
  degradedReason: '降级原因',
  currentHealthStatus: '健康状态',
  status: '状态',
  stage: '阶段',
  from: '变更前',
  to: '变更后',
  fromStatus: '原状态',
  toStatus: '新状态',
  fromStage: '原阶段',
  toStage: '新阶段',
  contactName: '联系人',
  contactPhone: '联系电话',
  industry: '行业',
  industryTags: '行业标签',
  ownerType: '归属类型',
  sourceType: '来源类型',
  partnerId: '合伙人',
  partnerName: '合伙人名称',
  provinceCode: '省份编码',
  provinceName: '省份',
  cityCode: '城市编码',
  cityName: '城市',
  districtCode: '区县编码',
  districtName: '区县',
  targetRegions: '目标区域',
  targetAudience: '目标人群',
  customStatement: '定制声明',
  contentTone: '内容语气',
  preferredAngles: '偏好角度',
  extraForbiddenPhrases: '额外禁用词',
  contentNote: '内容备注',
  customerRequirements: '客户需求',
  remark: '备注',
  amount: '金额',
  txnNo: '流水号',
  reason: '原因',
}
const STATUS_LABELS: Record<string, string> = {
  active: '启用',
  disabled: '禁用',
  inactive: '停用',
  paused: '暂停',
  pending_start: '待启动',
  expired: '已到期',
  signed: '已签约',
  potential: '潜在',
  normal: '正常',
  degraded: '已降级',
  maintenance: '维护中',
  slow_response: '响应慢',
  high_failure: '高失败率',
  manual_takeover: '人工接管',
}
const STAGE_LABELS: Record<string, string> = {
  pending_start: '待启动',
  collecting_materials: '素材收集',
  baseline_diagnosis: '基线诊断',
  executing: '执行中',
  needs_renewal: '待续费',
  high_risk: '高风险',
  dispute_handling: '争议处理',
  completed: '已完成',
}
const TARGET_NAME_KEYS: Record<string, string[]> = {
  company: ['companyName'],
  brand: ['brandName'],
  project: ['projectName'],
  platform: ['platformName'],
}
const SUMMARY_SKIP_KEYS = new Set([
  'id',
  'companyId',
  'brandId',
  'partnerId',
  'salesOwnerId',
  'selectedKeywordGroupIds',
  'provinceCode',
  'cityCode',
  'districtCode',
  'projectCode',
  'platformCode',
  'geoSiteCode',
  'industrySiteCode',
  'createdBy',
  'createdAt',
  'updatedAt',
  'deletedAt',
  'deletedBy',
])
const query = reactive<{
  action: string
  targetType: string
  targetId: number | undefined
  dateRange: [string, string] | []
}>({
  action: '',
  targetType: '',
  targetId: undefined,
  dateRange: [],
})

const operatorCount = computed(() => new Set(rows.value.map((row) => row.operatorName).filter(Boolean)).size)
const projectLogCount = computed(() => rows.value.filter((row) => row.targetType === 'project').length)
const latestLogTime = computed(() => rows.value[0]?.createdAt ? formatDateTime(rows.value[0].createdAt).slice(5, 16) : '-')
const targetTypeCount = computed(() => ({
  company: rows.value.filter((row) => row.targetType === 'company').length,
  brand: rows.value.filter((row) => row.targetType === 'brand').length,
  project: rows.value.filter((row) => row.targetType === 'project').length,
  platform: rows.value.filter((row) => row.targetType === 'platform').length,
}))
const actionInsights = computed(() => {
  const counts = rows.value.reduce<Record<string, number>>((acc, row) => {
    acc[row.action] = (acc[row.action] || 0) + 1
    return acc
  }, {})
  const max = Math.max(...Object.values(counts), 1)
  const items = Object.entries(counts)
    .map(([action, count]) => ({
      action,
      count,
      label: actionLabel(action),
      percent: Math.max(8, Math.round((count / max) * 100)),
    }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 4)
  if (items.length) return items
  return [{ action: 'empty', count: 0, label: '暂无数据', percent: 8 }]
})

async function load() {
  loading.value = true
  try {
    const [dateFrom, dateTo] = query.dateRange || []
    const { data } = await getActivityLogs({
      current: page.current,
      size: page.size,
      action: query.action || undefined,
      targetType: query.targetType || undefined,
      targetId: query.targetId || undefined,
      dateFrom,
      dateTo,
    })
    rows.value = data.data.records || []
    page.total = data.data.total || 0
  } catch {
    rows.value = []
    page.total = 0
  } finally {
    loading.value = false
  }
}

function targetTypeLabel(type?: string) {
  if (type === 'company') return '客户'
  if (type === 'brand') return '品牌'
  if (type === 'project') return '项目'
  if (type === 'platform') return '平台'
  return type || '-'
}

function targetInitial(type?: string) {
  if (type === 'company') return '客'
  if (type === 'brand') return '品'
  if (type === 'project') return '项'
  if (type === 'platform') return '平'
  return '记'
}

function targetAvatarClass(type?: string) {
  if (type === 'company') return 'is-company'
  if (type === 'brand') return 'is-brand'
  if (type === 'project') return 'is-project'
  if (type === 'platform') return 'is-platform'
  return 'is-muted'
}

function actionLabel(action?: string | null) {
  if (!action) return '-'
  const dictLabel = dictStore.label('activity_action', action)
  if (dictLabel && dictLabel !== action) return dictLabel
  return ACTION_LABELS[action] || humanizeKey(action)
}

function targetDisplayText(row: ActivityLog) {
  const name = row.targetName || targetNameFromDetail(row)
  if (name) return `${targetTypeLabel(row.targetType)} ${name}`
  return `${targetTypeLabel(row.targetType)} ${row.targetId ? `#${row.targetId}` : '-'}`
}

function targetNameFromDetail(row: ActivityLog) {
  const parsed = parseDetail(row.detailJson)
  if (!parsed) return ''
  const keys = TARGET_NAME_KEYS[row.targetType] || []
  for (const source of [parsed.after, parsed.before, parsed.extra]) {
    if (!source || typeof source !== 'object') continue
    for (const key of keys) {
      const value = (source as Record<string, unknown>)[key]
      if (typeof value === 'string' && value.trim()) return value.trim()
    }
  }
  return ''
}

function onSearch() {
  page.current = 1
  load()
}

function onPageChange(v: number) {
  page.current = v
  load()
}

function openDetail(row: ActivityLog) {
  selectedDetail.value = prettyJson(row.detailJson)
  detailVisible.value = true
}

function prettyJson(raw: string | null | undefined) {
  if (!raw) return '{}'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

function summaryText(raw: string | null | undefined) {
  const parsed = parseDetail(raw)
  if (!parsed) return raw ? raw.slice(0, 80) : '-'

  const extra = isRecord(parsed.extra) ? parsed.extra : null
  if (extra?.from != null && extra?.to != null) {
    const valueKey = extra.status != null ? 'stage' : 'status'
    return `由 ${formatAuditValue(valueKey, extra.from)} 变更为 ${formatAuditValue(valueKey, extra.to)}`
  }

  const before = isRecord(parsed.before) ? parsed.before : null
  const after = isRecord(parsed.after) ? parsed.after : null
  if (before && after) {
    const changes = Object.keys(after)
      .filter((key) => !SUMMARY_SKIP_KEYS.has(key) && !isSameValue(before[key], after[key]))
      .slice(0, 3)
      .map((key) => `${fieldLabel(key)}：${formatAuditValue(key, before[key])} → ${formatAuditValue(key, after[key])}`)
    if (changes.length) return changes.join('；')
  }

  const payload = after || before || extra
  if (payload) {
    const pairs = Object.entries(payload)
      .filter(([key, value]) => !SUMMARY_SKIP_KEYS.has(key) && value != null && value !== '')
      .slice(0, 3)
      .map(([key, value]) => `${fieldLabel(key)}：${formatAuditValue(key, value)}`)
    if (pairs.length) return pairs.join('；')
  }

  return '-'
}

function parseDetail(raw: string | null | undefined) {
  if (!raw) return null
  try {
    return JSON.parse(raw) as { before?: unknown; after?: unknown; extra?: unknown }
  } catch {
    return null
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function fieldLabel(key: string) {
  return FIELD_LABELS[key] || humanizeKey(key)
}

function formatAuditValue(key: string, value: unknown): string {
  if (value == null || value === '') return '空'
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (typeof value === 'string') {
    if (key === 'status' || key.endsWith('Status') || key === 'currentHealthStatus') {
      return STATUS_LABELS[value] || humanizeKey(value)
    }
    if (key === 'stage' || key.endsWith('Stage')) {
      return STAGE_LABELS[value] || humanizeKey(value)
    }
    if (key.endsWith('Id') && /^\d+$/.test(value)) return value
    return STATUS_LABELS[value] || STAGE_LABELS[value] || value
  }
  if (Array.isArray(value)) return value.map((item) => formatAuditValue(key, item)).join('、') || '空'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function isSameValue(left: unknown, right: unknown) {
  return JSON.stringify(left ?? null) === JSON.stringify(right ?? null)
}

function humanizeKey(key: string) {
  return key
    .replace(/\./g, ' ')
    .replace(/_/g, ' ')
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .trim()
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  await load()
})
</script>

<style scoped>
.activity-header {
  align-items: center;
}

.activity-filter-card :deep(.el-card__body) {
  padding: 12px;
}

.activity-filter-grid {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 10px;
  flex-wrap: wrap;
  max-width: 980px;
}

.filter-action {
  width: 180px;
  flex: 0 0 180px;
}

.filter-target-type {
  width: 130px;
  flex: 0 0 130px;
}

.filter-date-range {
  width: 360px !important;
  max-width: 360px;
  flex: 0 0 360px;
}

.filter-target-id {
  width: 130px;
  flex: 0 0 130px;
}

.filter-submit {
  flex: 0 0 72px;
  min-width: 76px;
}

.activity-filter-grid :deep(.filter-date-range.el-date-editor) {
  width: 360px !important;
  max-width: 360px;
  flex: 0 0 360px;
}

.activity-filter-grid :deep(.filter-date-range .el-range-input) {
  width: 118px;
  flex: 0 0 118px;
}

.activity-metric-grid {
  margin-bottom: 0;
}

.compact-value {
  font-size: 24px;
}

.activity-insight-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.28fr) minmax(0, 0.72fr);
  gap: 12px;
}

.activity-panel {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--admin-panel-border);
  border-radius: 14px;
  background: linear-gradient(180deg, #ffffff 0%, #ffffff 72%, #f8fafc 100%);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.055);
}

.panel-head {
  min-height: 58px;
  padding: 16px 18px 13px;
  border-bottom: 1px solid var(--admin-panel-border-soft);
  background: linear-gradient(90deg, #f8fbff 0%, #ffffff 58%, #f0fdf4 100%);
}

.panel-kicker {
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
}

.panel-title {
  margin: 4px 0 0;
  color: var(--admin-text-strong);
  font-size: 16px;
  line-height: 1.35;
  font-weight: 800;
}

.action-distribution {
  display: grid;
  gap: 12px;
  padding: 16px;
}

.distribution-row {
  display: grid;
  grid-template-columns: minmax(90px, 0.55fr) minmax(0, 1fr) 34px;
  align-items: center;
  gap: 10px;
}

.distribution-row span {
  overflow: hidden;
  color: #334155;
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.distribution-row strong {
  color: #0f172a;
  font-size: 14px;
  text-align: right;
}

.distribution-track {
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #e2e8f0;
}

.distribution-track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #2563eb, #10b981);
}

.scope-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  padding: 16px 16px 12px;
}

.scope-item {
  border: 1px solid #e7edf5;
  border-radius: 12px;
  background: #ffffff;
  padding: 13px;
}

.scope-item span {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.scope-item strong {
  display: block;
  margin-top: 8px;
  color: #0f172a;
  font-size: 20px;
  font-weight: 800;
}

.scope-note {
  padding: 0 16px 16px;
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.55;
}

.activity-table-card :deep(.el-card__body) {
  padding: 0;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 16px 16px 12px;
  border-bottom: 1px solid var(--admin-panel-border-soft);
  background: linear-gradient(90deg, #f8fbff 0%, #ffffff 55%, #f0fdf4 100%);
}

.table-title {
  color: var(--admin-text-strong);
  font-size: 16px;
  font-weight: 800;
}

.table-subtitle {
  margin-top: 4px;
  color: var(--admin-text-muted);
  font-size: 12px;
}

.chips {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.chip {
  display: inline-flex;
  align-items: center;
  border-radius: 14px;
  padding: 3px 9px;
  font-size: 12px;
  font-weight: 700;
}

.chip-muted {
  background: #f3f4f6;
  color: #6b7280;
}

.chip-blue {
  background: #eff6ff;
  color: #1d4ed8;
}

.log-avatar.is-company,
.target-pill.is-company {
  background: linear-gradient(135deg, #2563eb, #06b6d4);
}

.log-avatar.is-brand,
.target-pill.is-brand {
  background: linear-gradient(135deg, #7c3aed, #a855f7);
}

.log-avatar.is-project,
.target-pill.is-project {
  background: linear-gradient(135deg, #059669, #14b8a6);
}

.log-avatar.is-platform,
.target-pill.is-platform {
  background: linear-gradient(135deg, #64748b, #94a3b8);
}

.log-avatar.is-muted,
.target-pill.is-muted {
  background: linear-gradient(135deg, #64748b, #94a3b8);
}

.target-pill {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 9px;
  border-radius: 999px;
  color: #ffffff;
  font-size: 12px;
  font-weight: 800;
}

.detail-json {
  margin: 0;
  max-height: 420px;
  overflow: auto;
  padding: 14px;
  border: 1px solid #1e293b;
  border-radius: 10px;
  background: #0b1020;
  color: #dbe7ff;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 1100px) {
  .activity-insight-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .activity-filter-grid {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-action,
  .filter-target-type,
  .filter-date-range,
  .filter-target-id,
  .filter-submit {
    width: 100%;
  }

  .scope-grid {
    grid-template-columns: 1fr;
  }
}
</style>
