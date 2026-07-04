<template>
  <el-card class="admin-rich-card mobile-competitor-panel" v-loading="loading">
    <template #header>
      <div class="section-header">
        <div class="panel-title">
          <span>项目竞品信息</span>
          <el-tag size="small" type="info">{{ rows.length }} 个</el-tag>
        </div>
        <div class="panel-actions">
          <el-button size="small" :loading="loading" @click="loadCompetitors">刷新</el-button>
          <el-button v-if="editable && rows.length < MAX_COMPETITORS" size="small" plain @click="addRow">新增竞品</el-button>
          <el-button v-if="editable" size="small" type="primary" :loading="saving" @click="saveRows">保存竞品信息</el-button>
        </div>
      </div>
    </template>

    <el-table :data="rows" border empty-text="暂无竞品信息">
      <el-table-column label="竞品全名" min-width="180">
        <template #default="{ row }">
          <el-input
            v-model="row.competitorName"
            :disabled="!editable"
            maxlength="128"
            placeholder="请输入竞品名称"
          />
        </template>
      </el-table-column>
      <el-table-column label="别名 / 简称" min-width="260">
        <template #default="{ row }">
          <el-input
            v-model="row.aliasText"
            :disabled="!editable"
            maxlength="500"
            placeholder="用逗号、分号或换行分隔"
          />
        </template>
      </el-table-column>
      <el-table-column label="优势" min-width="220">
        <template #default="{ row }">
          <el-input
            v-model="row.advantages"
            :disabled="!editable"
            type="textarea"
            :rows="2"
            maxlength="500"
            placeholder="用于拓词信息补全，可后续编辑"
          />
        </template>
      </el-table-column>
      <el-table-column label="劣势" min-width="220">
        <template #default="{ row }">
          <el-input
            v-model="row.disadvantages"
            :disabled="!editable"
            type="textarea"
            :rows="2"
            maxlength="500"
            placeholder="用于拓词信息补全，可后续编辑"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="86" fixed="right">
        <template #default="{ $index }">
          <el-button v-if="editable" link type="danger" @click="removeRow($index)">删除</el-button>
          <span v-else>-</span>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getProjectMobileDashboardCompetitors,
  updateProjectMobileDashboardCompetitors,
  type ProjectCompetitorConfig,
  type ProjectCompetitorConfigPayloadItem,
} from '@/api/mobileDashboard'

const props = defineProps<{
  projectId: number
  editable: boolean
}>()

interface CompetitorEditRow {
  id?: number
  competitorName: string
  aliasText: string
  advantages: string
  disadvantages: string
  displayOrder: number
  active: boolean
  qaStatus: string
  configVersion?: number
}

const loading = ref(false)
const saving = ref(false)
const rows = ref<CompetitorEditRow[]>([])
const MAX_COMPETITORS = 3

function toEditRow(item: ProjectCompetitorConfig): CompetitorEditRow {
  return {
    id: item.id,
    competitorName: item.competitorName || '',
    aliasText: (item.aliases || []).join('、'),
    advantages: item.advantages || '',
    disadvantages: item.disadvantages || '',
    displayOrder: item.displayOrder || 1,
    active: item.status !== 'disabled',
    qaStatus: item.qaStatus || 'passed',
    configVersion: item.configVersion,
  }
}

function parseAliases(text: string) {
  const seen = new Set<string>()
  return (text || '')
    .split(/[,，;；\n]/)
    .map((item) => item.trim())
    .filter((item) => {
      if (!item) return false
      const key = item.toLowerCase()
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })
}

function validateRows() {
  if (rows.value.length > MAX_COMPETITORS) {
    ElMessage.warning('项目竞品最多配置3个')
    return false
  }
  for (const row of rows.value) {
    if (!row.competitorName.trim()) {
      ElMessage.warning('请填写竞品名称')
      return false
    }
  }
  return true
}

function toPayload(): ProjectCompetitorConfigPayloadItem[] {
  return rows.value
    .map((row, index) => ({
      id: row.id,
      competitorName: row.competitorName.trim(),
      aliases: parseAliases(row.aliasText),
      advantages: row.advantages?.trim() || null,
      disadvantages: row.disadvantages?.trim() || null,
      displayOrder: index + 1,
      active: true,
      qaStatus: 'passed',
    }))
}

function addRow() {
  if (rows.value.length >= MAX_COMPETITORS) {
    ElMessage.warning('项目竞品最多配置3个')
    return
  }
  rows.value.push({
    competitorName: '',
    aliasText: '',
    advantages: '',
    disadvantages: '',
    displayOrder: rows.value.length + 1,
    active: true,
    qaStatus: 'passed',
  })
}

function removeRow(index: number) {
  rows.value.splice(index, 1)
}

async function loadCompetitors() {
  loading.value = true
  try {
    const { data } = await getProjectMobileDashboardCompetitors(props.projectId)
    rows.value = (data.data || []).map(toEditRow)
  } catch (error: any) {
    ElMessage.error(error?.message || '竞品配置加载失败')
  } finally {
    loading.value = false
  }
}

async function saveRows() {
  if (!validateRows()) return
  saving.value = true
  try {
    const { data } = await updateProjectMobileDashboardCompetitors(props.projectId, toPayload())
    rows.value = (data.data || []).map(toEditRow)
    ElMessage.success('竞品信息已保存')
  } catch (error: any) {
    ElMessage.error(error?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadCompetitors)
</script>

<style scoped>
.mobile-competitor-panel {
  overflow: hidden;
}

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

</style>
