<template>
  <div class="automation-page">
    <div class="admin-page-header">
      <div>
        <div class="admin-page-kicker">自媒体自动化</div>
        <h1>运行态势</h1>
        <div class="admin-page-subtitle">跟踪自动排期、填充、发布回查和本地执行容量。</div>
      </div>
      <el-button type="primary" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <DataState :loading="loading" :empty="!overview" empty-text="暂无自动化态势数据">
      <template v-if="overview">
        <section class="metric-grid">
          <div class="metric-panel" :class="`is-${capacityTone}`">
            <span>本地执行容量</span>
            <strong>{{ overview.localExecution.estimatedCapacity }}</strong>
            <small>{{ overview.localExecution.message || '-' }}</small>
          </div>
          <div class="metric-panel">
            <span>在线助手</span>
            <strong>{{ overview.localExecution.onlineAgents }} / {{ overview.localExecution.activeSessions }}</strong>
            <small>5 分钟内有心跳 / 有效会话</small>
          </div>
          <div class="metric-panel">
            <span>待领取任务</span>
            <strong>{{ waitingForLocalAgent }}</strong>
            <small>排期执行 {{ overview.queue.dueScheduleExecution }}，发布回查 {{ overview.queue.duePublishCheck }}</small>
          </div>
          <div class="metric-panel">
            <span>异常待处理</span>
            <strong>{{ overview.queue.failedTotal + overview.queue.manualRequired + overview.queue.publishUnknown }}</strong>
            <small>失败 {{ overview.queue.failedTotal }}，人工 {{ overview.queue.manualRequired }}，待确认 {{ overview.queue.publishUnknown }}</small>
          </div>
        </section>

        <section class="split-layout">
          <div class="panel">
            <div class="panel-head">
              <strong>队列状态</strong>
              <span>{{ formatTime(overview.generatedAt) }}</span>
            </div>
            <div class="status-list">
              <div v-for="item in sortedStatusCounts" :key="item.status" class="status-row">
                <span>{{ statusLabel(item.status) }}</span>
                <strong>{{ item.count }}</strong>
              </div>
            </div>
          </div>

          <div class="panel">
            <div class="panel-head">
              <strong>平台压力</strong>
              <span>按活跃排期排序</span>
            </div>
            <el-table :data="overview.platformCounts" border table-layout="fixed" height="310">
              <el-table-column label="平台" min-width="120">
                <template #default="{ row }">{{ platformLabel(row.platform) }}</template>
              </el-table-column>
              <el-table-column prop="activeCount" label="活跃" width="80" />
              <el-table-column prop="dueCount" label="到期" width="80" />
              <el-table-column prop="failedCount" label="异常" width="80" />
            </el-table>
          </div>
        </section>

        <section class="split-layout">
          <div class="panel">
            <div class="panel-head">
              <strong>失败码修复动作</strong>
              <span>Top {{ overview.failureCodeCounts.length }}</span>
            </div>
            <el-table :data="overview.failureCodeCounts" border table-layout="fixed" height="330">
              <el-table-column label="失败码" min-width="190">
                <template #default="{ row }">
                  <div class="code-cell">
                    <strong>{{ row.label || row.code }}</strong>
                    <span>{{ row.code }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="count" label="数量" width="80" />
              <el-table-column label="动作" width="150">
                <template #default="{ row }">
                  <el-button link type="primary" @click="runFailureAction(row)">
                    {{ row.actionLabel || '查看诊断' }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div class="panel">
            <div class="panel-head">
              <strong>平台能力</strong>
              <span>官方 API 与本地助手链路</span>
            </div>
            <el-table :data="overview.platformCapabilities" border table-layout="fixed" height="330">
              <el-table-column label="平台" min-width="130">
                <template #default="{ row }">{{ row.displayName || platformLabel(row.platform) }}</template>
              </el-table-column>
              <el-table-column label="链路" width="110">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.requiresLocalAgent ? 'warning' : 'success'">
                    {{ row.requiresLocalAgent ? '本地助手' : '官方 API' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.scheduleReady ? 'success' : 'info'">
                    {{ row.scheduleReady ? '可自动化' : '未就绪' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="说明" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">{{ row.readinessMessage || row.strategy || '-' }}</template>
              </el-table-column>
            </el-table>
          </div>
        </section>

        <section class="panel">
          <div class="panel-head">
            <div>
              <strong>第三方主体池</strong>
              <span class="panel-subtitle">排期前检查信源覆盖行业与候选主体</span>
            </div>
            <div class="subject-pool-summary">
              <el-tag size="small" type="success">可轮换 {{ thirdPartySubjectPool?.readySourceTotal || 0 }}</el-tag>
              <el-tag size="small" type="warning">待配置 {{ thirdPartySubjectPool?.missingCoverageTotal || 0 }}</el-tag>
              <el-tag size="small" type="danger">候选为 0 {{ thirdPartySubjectPool?.emptyCandidateTotal || 0 }}</el-tag>
            </div>
          </div>
          <el-empty
            v-if="!thirdPartySubjectPool || thirdPartySubjectPool.sourceTotal === 0"
            description="暂无第三方视角信源"
          />
          <el-table v-else :data="thirdPartySubjectPool.sources" border table-layout="fixed">
            <el-table-column label="信源品牌" min-width="150">
              <template #default="{ row }">
                <div class="code-cell">
                  <strong>{{ row.sourceBrandName }}</strong>
                  <span>ID {{ row.sourceBrandId }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="覆盖行业" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.coverableIndustries?.length ? row.coverableIndustries.join('、') : '-' }}</template>
            </el-table-column>
            <el-table-column prop="candidateCount" label="候选" width="90" />
            <el-table-column prop="excludedCount" label="排除" width="90" />
            <el-table-column label="下一候选" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ row.nextCandidateBrandName || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="130">
              <template #default="{ row }">
                <el-tag size="small" :type="subjectPoolStatusTone(row.status)">
                  {{ subjectPoolStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="说明" min-width="170" show-overflow-tooltip>
              <template #default="{ row }">{{ row.message || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openSourceBrand(row.sourceBrandId)">配置</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div
            v-if="thirdPartySubjectPool && thirdPartySubjectPool.sources.length < thirdPartySubjectPool.sourceTotal"
            class="panel-footnote"
          >
            当前展示前 {{ thirdPartySubjectPool.sources.length }} / {{ thirdPartySubjectPool.sourceTotal }} 个信源。
          </div>
        </section>
      </template>
    </DataState>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import DataState from '@/components/ui/DataState.vue'
import { getSelfMediaAutomationOverview } from '@/api/content'
import type { SelfMediaAutomationOverview } from '@/types'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const overview = ref<SelfMediaAutomationOverview | null>(null)

const waitingForLocalAgent = computed(() => overview.value?.localExecution.waitingForLocalAgent || 0)
const capacityTone = computed(() => {
  const status = overview.value?.localExecution.capacityStatus
  if (status === 'blocked' || status === 'saturated') return 'danger'
  if (status === 'pressure') return 'warning'
  return 'success'
})
const sortedStatusCounts = computed(() =>
  [...(overview.value?.statusCounts || [])].sort((left, right) => right.count - left.count),
)
const thirdPartySubjectPool = computed(() => overview.value?.thirdPartySubjectPool || null)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const { data } = await getSelfMediaAutomationOverview()
    overview.value = data.data
  } finally {
    loading.value = false
  }
}

function runFailureAction(row: { actionKey?: string | null; code: string }) {
  const action = row.actionKey || 'OPEN_DIAGNOSTICS'
  const openScheduleDiagnostics = (status?: string) => {
    router.push({
      path: '/admin/content/execution',
      query: {
        scheduleFailureCode: row.code,
        ...(status ? { scheduleStatus: status } : {}),
      },
    })
  }
  if (action === 'OPEN_SCHEDULE_CAPABILITY') {
    router.push('/admin/content/self-media-schedule-capabilities')
    return
  }
  if (action === 'OPEN_PACKAGE_QUOTA') {
    router.push('/admin/settings/packages')
    return
  }
  if (action === 'OPEN_BRAND_SELF_MEDIA_ACCOUNTS') {
    router.push('/admin/customers')
    return
  }
  if (action === 'RETRY_NOW') {
    openScheduleDiagnostics('manual_required')
    return
  }
  openScheduleDiagnostics()
}

function openSourceBrand(brandId: number) {
  router.push(`/admin/brands/${brandId}`)
}

function formatTime(value?: string | null) {
  return value ? formatDateTime(value) : '-'
}

function statusLabel(status?: string | null) {
  const map: Record<string, string> = {
    pending: '待领取',
    filling: '填充中',
    filled_verified: '填充已核验',
    scheduling: '平台定时中',
    scheduled: '已定时',
    publish_due: '到点待核验',
    checking_publish_result: '回查中',
    publish_unknown: '发布待确认',
    published_confirmed: '已确认发布',
    schedule_failed: '排期失败',
    publish_failed: '发布失败',
    manual_required: '人工处理',
    cancelled: '已取消',
    cancel_pending_platform: '取消待平台处理',
    routed_to_semi_auto: '已转半自动',
  }
  return status ? map[status] || status : '-'
}

function platformLabel(platform?: string | null) {
  const map: Record<string, string> = {
    wechat_mp: '微信公众号',
    douyin: '抖音图文',
    toutiao: '今日头条',
    baijiahao: '百家号',
    zhihu: '知乎',
    xiaohongshu: '小红书',
  }
  return platform ? map[platform] || platform : '-'
}

function subjectPoolStatusLabel(status?: string | null) {
  const map: Record<string, string> = {
    ready: '可轮换',
    missing_coverage: '待配置覆盖行业',
    empty_candidate: '候选为 0',
  }
  return status ? map[status] || status : '-'
}

function subjectPoolStatusTone(status?: string | null) {
  if (status === 'ready') return 'success'
  if (status === 'missing_coverage') return 'warning'
  if (status === 'empty_candidate') return 'danger'
  return 'info'
}
</script>

<style scoped>
.automation-page {
  display: grid;
  gap: 18px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.metric-panel,
.panel {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.metric-panel {
  display: grid;
  gap: 6px;
  min-height: 112px;
  padding: 16px;
}

.metric-panel span,
.metric-panel small,
.panel-head span,
.code-cell span {
  color: #64748b;
  font-size: 12px;
}

.metric-panel strong {
  color: #0f172a;
  font-size: 28px;
  line-height: 1.1;
}

.metric-panel.is-success {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.metric-panel.is-warning {
  border-color: #fde68a;
  background: #fffbeb;
}

.metric-panel.is-danger {
  border-color: #fecaca;
  background: #fef2f2;
}

.split-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 14px;
}

.panel {
  min-width: 0;
  padding: 14px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.panel-head strong {
  color: #111827;
  font-size: 15px;
}

.panel-subtitle,
.panel-footnote {
  display: block;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.subject-pool-summary {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.panel-footnote {
  margin-top: 10px;
}

.status-list {
  display: grid;
  gap: 8px;
  max-height: 310px;
  overflow: auto;
}

.status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 34px;
  padding: 8px 10px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.status-row span {
  color: #334155;
  font-size: 13px;
}

.status-row strong {
  color: #0f172a;
}

.code-cell {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.code-cell strong {
  color: #111827;
  font-size: 13px;
}

@media (max-width: 980px) {
  .split-layout {
    grid-template-columns: 1fr;
  }
}
</style>
