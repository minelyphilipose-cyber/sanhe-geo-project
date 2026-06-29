<template>
  <el-card class="admin-rich-card mobile-ops-panel" v-loading="loading">
    <template #header>
      <div class="section-header">
        <div class="panel-title">
          <span>移动看板运行观测</span>
          <el-tag size="small" type="info">近 14 天</el-tag>
        </div>
        <div class="panel-actions">
          <el-button size="small" :loading="loading" @click="loadData">刷新</el-button>
        </div>
      </div>
    </template>

    <div class="ops-grid">
      <div class="ops-card">
        <span>裁判覆盖率</span>
        <strong>{{ ops?.judgeHealth.coveragePercent ?? 0 }}%</strong>
        <small>成功 {{ ops?.judgeHealth.successCount || 0 }} / 应判 {{ ops?.judgeHealth.expectedCount || 0 }}</small>
      </div>
      <div class="ops-card">
        <span>接口错误率</span>
        <strong>{{ ops?.apiErrorStats.errorRatePercent ?? 0 }}%</strong>
        <small>失败 {{ ops?.apiErrorStats.failed || 0 }} / 总 {{ ops?.apiErrorStats.total || 0 }}</small>
      </div>
      <div class="ops-card">
        <span>裁判调用量</span>
        <strong>{{ ops?.llmUsage.totalCalls || 0 }}</strong>
        <small>约 {{ ops?.llmUsage.estimatedCost || 0 }} {{ ops?.llmUsage.currency || 'CNY' }}</small>
      </div>
      <div class="ops-card">
        <span>异常分享</span>
        <strong>{{ suspiciousShareCount }}</strong>
        <small>多 IP 或失败访问偏高</small>
      </div>
    </div>

    <el-collapse class="ops-collapse">
      <el-collapse-item title="接口错误明细" name="api">
        <el-table :data="ops?.apiErrorStats.endpoints || []" border empty-text="暂无接口访问">
          <el-table-column label="访问内容" min-width="220">
            <template #default="{ row }">
              <div class="api-name">
                <strong>{{ apiEventLabel(row.eventType) }}</strong>
                <small>{{ apiEventDescription(row.eventType) }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="total" label="调用次数" width="100" />
          <el-table-column prop="failed" label="失败次数" width="100" />
          <el-table-column label="错误率" width="100">
            <template #default="{ row }">{{ row.errorRatePercent }}%</template>
          </el-table-column>
          <el-table-column label="最近情况" min-width="220">
            <template #default="{ row }">
              <el-tag v-if="row.failed > 0" type="danger" size="small">{{ failReasonLabel(row.latestFailReason) }}</el-tag>
              <el-tag v-else type="success" size="small">近 14 天没有失败</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-collapse-item>
      <el-collapse-item title="分享访问风险" name="share">
        <el-table :data="ops?.shareRisks || []" border empty-text="暂无访问记录">
          <el-table-column prop="tokenPrefix" label="Token 前缀" width="128" />
          <el-table-column prop="totalAccess" label="访问" width="90" />
          <el-table-column prop="distinctIpCount" label="不同 IP" width="100" />
          <el-table-column prop="failedAccess" label="失败" width="90" />
          <el-table-column label="风险" width="90">
            <template #default="{ row }">
              <el-tag :type="row.suspicious ? 'danger' : 'success'">{{ row.suspicious ? '关注' : '正常' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最后访问" min-width="160">
            <template #default="{ row }">{{ formatTime(row.lastAccessAt) }}</template>
          </el-table-column>
        </el-table>
      </el-collapse-item>
    </el-collapse>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getProjectMobileDashboardOperations } from '@/api/mobileDashboard'
import type { MobileDashboardOperations } from '@/types/mobileDashboard'

const props = defineProps<{
  projectId: number
  editable: boolean
}>()

const loading = ref(false)
const ops = ref<MobileDashboardOperations | null>(null)

const suspiciousShareCount = computed(() => (ops.value?.shareRisks || []).filter((item) => item.suspicious).length)

const apiEventLabels: Record<string, { label: string; description: string }> = {
  bootstrap: { label: '打开看板', description: '客户进入 H5 看板时读取项目和权限信息' },
  home: { label: '首页概览', description: '读取曝光、推荐、情绪等首页汇总数据' },
  monitor: { label: '运行监控页', description: '读取问题监测列表和筛选结果' },
  question_detail: { label: '问题详情', description: '查看单条问题的 AI 回答和判断结果' },
  content: { label: '内容列表', description: '读取文章生成和发布进度' },
  report: { label: '报告页', description: '访问已停用的报告入口' },
  exchange_session: { label: '兑换访问凭证', description: '使用分享短码换取 H5 看板访问权限' },
}

const failReasonLabels: Record<string, string> = {
  BizException: '业务校验未通过',
  UnauthorizedException: '访问凭证无效或已过期',
  AccessDeniedException: '没有访问权限',
  MissingRequestHeaderException: '缺少访问凭证',
  MethodArgumentNotValidException: '提交参数不完整',
  IllegalArgumentException: '提交参数格式不正确',
  RuntimeException: '服务处理异常',
}

function formatTime(value?: string | null) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

function apiEventLabel(eventType?: string | null) {
  if (!eventType) return '未知访问'
  return apiEventLabels[eventType]?.label || eventType
}

function apiEventDescription(eventType?: string | null) {
  if (!eventType) return '未记录具体访问内容'
  return apiEventLabels[eventType]?.description || '系统记录的移动看板访问'
}

function failReasonLabel(reason?: string | null) {
  if (!reason) return '最近失败原因未记录'
  return failReasonLabels[reason] || reason
}

async function loadData() {
  loading.value = true
  try {
    const opsRes = await getProjectMobileDashboardOperations(props.projectId)
    ops.value = opsRes.data.data
  } catch (error: any) {
    ElMessage.error(error?.message || '运行观测加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-title,
.panel-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.panel-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.ops-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.ops-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  padding: 14px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
}

.ops-card span,
.ops-card small {
  color: #909399;
}

.ops-card strong {
  color: #303133;
  font-size: 24px;
  line-height: 1.2;
}

.ops-collapse {
  margin-top: 8px;
}

.api-name {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.api-name strong {
  color: #303133;
  font-weight: 600;
}

.api-name small {
  color: #909399;
}

@media (max-width: 1200px) {
  .ops-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
