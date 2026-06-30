<template>
  <div class="business-calendar-page">
    <div class="page-header">
      <div>
        <h2>工作日历</h2>
        <p>维护自媒体自动排期使用的年度工作日历。</p>
      </div>
      <el-button :loading="loading" @click="loadStatus">刷新</el-button>
    </div>

    <el-alert
      class="status-alert"
      :title="status?.message || '正在读取工作日历状态'"
      :type="status?.calendar.exists ? 'success' : 'warning'"
      show-icon
      :closable="false"
    />

    <div class="status-grid">
      <div class="status-item">
        <span class="status-label">目标年份</span>
        <strong>{{ status?.targetYear || '-' }}</strong>
      </div>
      <div class="status-item">
        <span class="status-label">当前日期</span>
        <strong>{{ status?.today || '-' }}</strong>
      </div>
      <div class="status-item">
        <span class="status-label">生成窗口</span>
        <strong>{{ status?.generationWindow || '12-20 ~ 12-31' }}</strong>
      </div>
      <div class="status-item">
        <span class="status-label">当前生效来源</span>
        <strong>{{ sourceLabel }}</strong>
      </div>
    </div>

    <el-card shadow="never" class="calendar-card">
      <template #header>
        <div class="card-header">
          <span>{{ status?.targetYear || '下一年' }} 年日历状态</span>
          <el-tag :type="status?.calendar.exists ? 'success' : 'warning'">
            {{ status?.calendar.exists ? '已存在' : '缺失' }}
          </el-tag>
        </div>
      </template>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="可发布工作日">
          {{ status?.calendar.publishAllowedDays ?? '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="调休补班">
          {{ status?.calendar.adjustedWorkdays ?? '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="法定节假日">
          {{ status?.calendar.holidays ?? '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="更新时间">
          {{ status?.calendar.updatedAt || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="数据源">
          {{ status?.calendar.sourceUrl || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="运行目录文件">
          {{ status?.calendar.runtimePath || '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <div class="actions">
        <el-button
          type="primary"
          :loading="generating"
          :disabled="!canGenerate"
          @click="confirmGenerate(false)"
        >
          生成下一年日历
        </el-button>
        <el-button
          v-if="status?.forceAvailable"
          type="warning"
          :loading="generating"
          @click="confirmGenerate(true)"
        >
          强制生成
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  generateNextYearBusinessCalendar,
  getNextYearBusinessCalendarStatus,
  type BusinessCalendarAdminStatus,
} from '@/api/system'

const loading = ref(false)
const generating = ref(false)
const status = ref<BusinessCalendarAdminStatus | null>(null)

const sourceLabel = computed(() => {
  const source = status.value?.calendar.activeSource
  if (source === 'runtime') return '运行目录'
  if (source === 'classpath') return '内置资源'
  if (source === 'missing') return '未生成'
  return source || '-'
})

const canGenerate = computed(() => Boolean(status.value?.generationAllowed))

async function loadStatus() {
  loading.value = true
  try {
    const res = await getNextYearBusinessCalendarStatus()
    status.value = res.data.data
  } finally {
    loading.value = false
  }
}

async function confirmGenerate(force: boolean) {
  const targetYear = status.value?.targetYear || '下一年'
  await ElMessageBox.confirm(
    force
      ? `当前不在常规生成窗口，将强制重新生成 ${targetYear} 年工作日历。`
      : `将从节假日数据源获取并生成 ${targetYear} 年工作日历。`,
    force ? '强制生成工作日历' : '生成工作日历',
    {
      type: force ? 'warning' : 'info',
      confirmButtonText: '确认生成',
      cancelButtonText: '取消',
    },
  )
  generating.value = true
  try {
    const res = await generateNextYearBusinessCalendar(force)
    status.value = res.data.data
    ElMessage.success('工作日历已生成，后续自动排期会直接使用')
  } finally {
    generating.value = false
  }
}

onMounted(loadStatus)
</script>

<style scoped>
.business-calendar-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-header h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
}

.page-header p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 14px;
}

.status-alert {
  border-radius: 8px;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.status-item {
  min-height: 78px;
  padding: 14px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
}

.status-label {
  font-size: 13px;
  color: #64748b;
}

.status-item strong {
  font-size: 18px;
  color: #0f172a;
}

.calendar-card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-weight: 700;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}

@media (max-width: 960px) {
  .status-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .page-header,
  .actions {
    align-items: stretch;
    flex-direction: column;
  }

  .status-grid {
    grid-template-columns: 1fr;
  }
}
</style>
