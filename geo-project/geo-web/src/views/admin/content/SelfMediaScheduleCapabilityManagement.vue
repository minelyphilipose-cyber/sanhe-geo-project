<template>
  <div class="schedule-capability-page admin-page">
    <div class="admin-page-header">
      <div>
        <div class="admin-page-kicker">自媒体排期</div>
        <h1 class="admin-page-title">平台能力管理</h1>
        <div class="admin-page-subtitle">维护各自媒体平台是否允许进入手动/自动定时分发链路，以及当前使用的排期策略。</div>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadCapabilities">刷新</el-button>
    </div>

    <el-card shadow="never" class="admin-table-card">
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无平台能力配置">
        <el-table :data="rows" border table-layout="fixed">
          <el-table-column label="平台" min-width="150">
            <template #default="{ row }">
              <div class="platform-cell">
                <strong>{{ row.displayName || platformLabel(row.platform) }}</strong>
                <span>{{ row.platform }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="验证状态" width="130">
            <template #default="{ row }">
              <el-tag :type="verificationTag(row.verificationStatus)">{{ verificationLabel(row.verificationStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="策略" min-width="190">
            <template #default="{ row }">
              <div class="strategy-cell">
                <strong>{{ strategyLabel(row.v1Strategy) }}</strong>
                <span>契约：{{ scheduleModeLabel(row.scheduleMode) }} / {{ publishChannelLabel(row.publishChannel) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="延迟范围" width="150">
            <template #default="{ row }">
              {{ row.minDelayMinutes ?? '-' }} ~ {{ row.maxDelayMinutes ?? '-' }} 分钟
            </template>
          </el-table-column>
          <el-table-column label="能力" min-width="240">
            <template #default="{ row }">
              <div class="capability-tags">
                <el-tag :type="row.supportsSchedule ? 'success' : 'info'">{{ row.supportsSchedule ? '允许排期' : '不允许排期' }}</el-tag>
                <el-tag v-if="row.contractRequiresCoverUpload" type="warning">需封面</el-tag>
                <el-tag v-if="row.contractSupportsLocation" type="success">位置</el-tag>
                <el-tag v-if="row.contractSupportsOneClickFormat" type="success">一键排版</el-tag>
                <el-tag v-if="row.supportsPublishCheck" type="success">结果校验</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.updatedAt || row.verifiedAt) || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEditor(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </DataState>
    </el-card>

    <el-drawer v-model="editorVisible" title="编辑平台能力" size="520px" append-to-body>
      <el-form v-if="editingRow" label-width="116px" class="capability-form">
        <el-form-item label="平台">
          <div class="readonly-field">
            {{ editingRow.displayName || platformLabel(editingRow.platform) }}
            <span>{{ editingRow.platform }}</span>
          </div>
        </el-form-item>
        <el-form-item label="验证状态">
          <el-select v-model="form.verificationStatus" class="form-control">
            <el-option label="未验证" value="unverified" />
            <el-option label="已验证" value="verified" />
            <el-option label="验证失败" value="failed" />
          </el-select>
        </el-form-item>
        <el-form-item label="允许排期">
          <el-switch v-model="form.supportsSchedule" />
        </el-form-item>
        <el-form-item label="排期策略">
          <el-select v-model="form.v1Strategy" class="form-control">
            <el-option label="待定" value="pending" />
            <el-option label="平台原生定时" value="platform_schedule" />
            <el-option label="后台延迟发布" value="backend_delayed_publish" />
            <el-option label="半自动" value="semi_auto" />
          </el-select>
          <div class="form-tip">知乎使用后台延迟发布；头条、小红书、百家号使用平台原生定时。</div>
        </el-form-item>
        <el-form-item label="延迟范围">
          <div class="range-row">
            <el-input-number v-model="form.minDelayMinutes" :min="0" :step="10" controls-position="right" />
            <span>至</span>
            <el-input-number v-model="form.maxDelayMinutes" :min="0" :step="60" controls-position="right" />
            <span>分钟</span>
          </div>
        </el-form-item>
        <el-form-item label="保存即排期">
          <el-switch v-model="form.saveCreatesSchedule" />
        </el-form-item>
        <el-form-item label="支持能力">
          <div class="switch-list">
            <el-checkbox v-model="form.supportsCancel">取消</el-checkbox>
            <el-checkbox v-model="form.supportsModify">修改</el-checkbox>
            <el-checkbox v-model="form.supportsPublishCheck">发布结果校验</el-checkbox>
          </div>
        </el-form-item>
        <el-form-item label="选择器状态">
          <el-input v-model="form.selectorStatus" class="form-control" placeholder="如 stable / needs_review" />
        </el-form-item>
        <el-form-item label="验证说明">
          <el-input v-model="form.notes" type="textarea" :rows="3" placeholder="记录当前平台验证结论、页面限制或注意事项" />
        </el-form-item>
        <el-form-item label="证据 JSON">
          <el-input v-model="form.evidenceJson" type="textarea" :rows="5" placeholder="可选，保存截图说明、选择器证据等 JSON 文本" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveCapability">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import DataState from '@/components/ui/DataState.vue'
import { getSelfMediaScheduleCapabilities, updateSelfMediaScheduleCapability } from '@/api/content'
import type { SelfMediaScheduleCapability } from '@/types'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const saving = ref(false)
const rows = ref<SelfMediaScheduleCapability[]>([])
const editorVisible = ref(false)
const editingRow = ref<SelfMediaScheduleCapability | null>(null)

const form = reactive({
  verificationStatus: 'unverified',
  supportsSchedule: false,
  minDelayMinutes: 0 as number | null,
  maxDelayMinutes: 0 as number | null,
  saveCreatesSchedule: true,
  supportsCancel: false,
  supportsModify: false,
  supportsPublishCheck: true,
  v1Strategy: 'pending',
  selectorStatus: '',
  evidenceJson: '',
  notes: '',
})

onMounted(loadCapabilities)

async function loadCapabilities() {
  loading.value = true
  try {
    const response = await getSelfMediaScheduleCapabilities()
    rows.value = response.data.data || []
  } catch {
    ElMessage.error('加载平台能力失败')
  } finally {
    loading.value = false
  }
}

function openEditor(row: SelfMediaScheduleCapability) {
  editingRow.value = row
  form.verificationStatus = row.verificationStatus || 'unverified'
  form.supportsSchedule = Boolean(row.supportsSchedule)
  form.minDelayMinutes = row.minDelayMinutes ?? defaultMinDelay(row)
  form.maxDelayMinutes = row.maxDelayMinutes ?? defaultMaxDelay(row)
  form.saveCreatesSchedule = row.saveCreatesSchedule ?? true
  form.supportsCancel = Boolean(row.supportsCancel)
  form.supportsModify = Boolean(row.supportsModify)
  form.supportsPublishCheck = row.supportsPublishCheck ?? row.contractSupportsPublishCheck ?? true
  form.v1Strategy = row.v1Strategy || defaultStrategy(row)
  form.selectorStatus = row.selectorStatus || ''
  form.evidenceJson = row.evidenceJson || ''
  form.notes = row.notes || ''
  editorVisible.value = true
}

async function saveCapability() {
  if (!editingRow.value) return
  saving.value = true
  try {
    await updateSelfMediaScheduleCapability(editingRow.value.platform, {
      verificationStatus: form.verificationStatus,
      supportsSchedule: form.supportsSchedule,
      minDelayMinutes: form.minDelayMinutes,
      maxDelayMinutes: form.maxDelayMinutes,
      saveCreatesSchedule: form.saveCreatesSchedule,
      supportsCancel: form.supportsCancel,
      supportsModify: form.supportsModify,
      supportsPublishCheck: form.supportsPublishCheck,
      v1Strategy: form.v1Strategy,
      selectorStatus: form.selectorStatus || null,
      evidenceJson: form.evidenceJson || null,
      notes: form.notes || null,
    })
    ElMessage.success('平台能力已保存')
    editorVisible.value = false
    await loadCapabilities()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '保存平台能力失败')
  } finally {
    saving.value = false
  }
}

function defaultStrategy(row: SelfMediaScheduleCapability) {
  if (row.scheduleMode === 'BACKEND_DELAYED') return 'backend_delayed_publish'
  if (row.scheduleMode === 'PLATFORM_NATIVE') return 'platform_schedule'
  return row.supportsSchedule ? 'platform_schedule' : 'semi_auto'
}

function defaultMinDelay(row: SelfMediaScheduleCapability) {
  if (row.platform === 'toutiao') return 120
  return 0
}

function defaultMaxDelay(row: SelfMediaScheduleCapability) {
  if (row.platform === 'toutiao') return 7 * 24 * 60
  if (row.scheduleMode === 'BACKEND_DELAYED') return 30 * 24 * 60
  return 7 * 24 * 60
}

function platformLabel(platform?: string | null) {
  const map: Record<string, string> = {
    toutiao: '今日头条',
    zhihu: '知乎',
    xiaohongshu: '小红书',
    baijiahao: '百家号',
    douyin: '抖音',
    wechat_mp: '公众号',
  }
  return map[(platform || '').toLowerCase()] || platform || '-'
}

function verificationLabel(value?: string | null) {
  if (value === 'verified') return '已验证'
  if (value === 'failed') return '验证失败'
  return '未验证'
}

function verificationTag(value?: string | null) {
  if (value === 'verified') return 'success'
  if (value === 'failed') return 'danger'
  return 'info'
}

function strategyLabel(value?: string | null) {
  if (value === 'platform_schedule') return '平台原生定时'
  if (value === 'backend_delayed_publish') return '后台延迟发布'
  if (value === 'semi_auto') return '半自动'
  return '待定'
}

function scheduleModeLabel(value?: string | null) {
  if (value === 'PLATFORM_NATIVE') return '原生定时'
  if (value === 'BACKEND_DELAYED') return '后台延迟'
  if (value === 'UNSUPPORTED') return '不支持'
  return value || '-'
}

function publishChannelLabel(value?: string | null) {
  if (value === 'ADSPOWER_AUTOMATION') return 'AdsPower'
  if (value === 'OFFICIAL_API') return '官方 API'
  return value || '-'
}
</script>

<style scoped>
.schedule-capability-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.platform-cell,
.strategy-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.platform-cell span,
.strategy-cell span {
  color: #64748b;
  font-size: 12px;
}

.capability-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.capability-form {
  padding-right: 16px;
}

.form-control {
  width: 100%;
}

.readonly-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: #0f172a;
  font-weight: 700;
}

.readonly-field span,
.form-tip {
  color: #64748b;
  font-size: 12px;
  font-weight: 400;
}

.range-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.switch-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
}
</style>
