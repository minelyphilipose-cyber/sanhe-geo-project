<template>
  <div>
    <div class="mb-4 flex items-center justify-between gap-3">
      <div class="flex items-center gap-2">
        <el-select v-model="query.status" clearable placeholder="启用状态" style="width: 140px" @change="load">
          <el-option label="启用" value="active" />
          <el-option label="停用" value="suspended" />
        </el-select>
        <el-select
          v-model="query.industry"
          clearable
          filterable
          allow-create
          default-first-option
          placeholder="行业分类"
          style="width: 180px"
          @change="load"
        >
          <el-option
            v-for="item in dictStore.options('industry_tag')"
            :key="item.dictKey"
            :label="item.dictValue"
            :value="item.dictKey"
          />
        </el-select>
        <el-button @click="load">查询</el-button>
      </div>
      <el-button type="primary" @click="openCreate">新增站点</el-button>
    </div>

    <el-card shadow="never">
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无行业资讯站配置">
        <el-table :data="rows" border>
          <el-table-column label="图标" width="72">
            <template #default="scope">
              <el-avatar v-if="scope.row.iconUrl" :src="scope.row.iconUrl" shape="square" :size="32" />
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="siteName" label="站点名称" min-width="150" />
          <el-table-column prop="apiEndpoint" label="发布接口 URL" min-width="240" show-overflow-tooltip />
          <el-table-column prop="domain" label="域名" min-width="170" />
          <el-table-column label="行业分类" min-width="220">
            <template #default="scope">
              <div class="flex flex-wrap gap-1">
                <el-tag v-for="tag in parseIndustryTags(scope.row.industryTags)" :key="`${scope.row.id}-${tag}`" type="info">
                  {{ dictStore.label('industry_tag', tag) || tag }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="接入方式" width="120">
            <template #default="scope">{{ methodLabel(scope.row.integrationMethod) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="scope">
              <el-tag :type="scope.row.status === 'active' ? 'success' : 'info'">{{ statusLabel(scope.row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" width="170" />
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
              <el-button link type="primary" @click="test(scope.row.id)">测试连通</el-button>
              <el-button
                link
                :type="scope.row.status === 'active' ? 'warning' : 'success'"
                @click="changeStatus(scope.row.id, scope.row.status === 'active' ? 'suspended' : 'active')"
              >
                {{ scope.row.status === 'active' ? '停用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </DataState>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="mode === 'create' ? '新增行业资讯站' : '编辑行业资讯站'" width="860px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-row :gutter="12">
          <el-col :xs="24" :md="8">
            <el-form-item label="站点名称" prop="siteName">
              <el-input v-model="form.siteName" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="域名" prop="domain">
              <el-input v-model="form.domain" placeholder="example.com" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="是否启用" prop="status">
              <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="发布接口 URL" prop="apiEndpoint">
          <el-input v-model="form.apiEndpoint" placeholder="https://example.com/api/article/publish" />
        </el-form-item>

        <el-row :gutter="12">
          <el-col :xs="24" :md="12">
            <el-form-item label="行业分类" prop="industryTags">
              <el-select
                v-model="form.industryTags"
                multiple
                filterable
                allow-create
                default-first-option
                style="width: 100%"
                placeholder="选择或输入行业分类"
              >
                <el-option
                  v-for="item in dictStore.options('industry_tag')"
                  :key="item.dictKey"
                  :label="item.dictValue"
                  :value="item.dictKey"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="网站图标">
              <el-input v-model="form.iconUrl" placeholder="https://example.com/favicon.ico" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="接入方式" prop="integrationMethod">
          <el-select v-model="form.integrationMethod" style="width: 240px">
            <el-option label="REST API" value="rest_api" />
            <el-option label="手动分发" value="manual" />
          </el-select>
        </el-form-item>

        <el-form-item label="请求头信息">
          <div class="header-editor">
            <div v-for="(row, index) in form.headers" :key="index" class="header-row">
              <el-input v-model="row.key" placeholder="Header Key，如 X-Admin-Token" />
              <el-input v-model="row.value" placeholder="Header Value" show-password />
              <el-button @click="removeHeader(index)">删除</el-button>
            </div>
            <el-button @click="addHeader">添加请求头</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import DataState from '@/components/ui/DataState.vue'
import { createPublishSite, getPublishSites, testPublishSite, updatePublishSite, updatePublishSiteStatus } from '@/api/publishSite'
import type { PublishSite } from '@/types'
import { useDictStore } from '@/stores/dict'

interface HeaderRow {
  key: string
  value: string
}

const dictStore = useDictStore()
const loading = ref(false)
const saving = ref(false)
const rows = ref<PublishSite[]>([])

const query = reactive({
  status: '',
  industry: '',
})

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const mode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const form = reactive({
  siteName: '',
  domain: '',
  iconUrl: '',
  apiEndpoint: '',
  industryTags: [] as string[],
  integrationMethod: 'rest_api',
  enabled: true,
  headers: [{ key: 'X-Admin-Token', value: '' }] as HeaderRow[],
})

const rules: FormRules = {
  siteName: [{ required: true, message: '请输入站点名称', trigger: 'blur' }],
  domain: [{ required: true, message: '请输入域名', trigger: 'blur' }],
  apiEndpoint: [{ required: true, message: '请输入发布接口 URL', trigger: 'blur' }],
  industryTags: [{ required: true, message: '请选择或输入至少一个行业分类', trigger: 'change' }],
  integrationMethod: [{ required: true, message: '请选择接入方式', trigger: 'change' }],
}

function parseIndustryTags(raw?: string | string[] | null) {
  if (Array.isArray(raw)) return raw
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function parseHeaders(raw?: string | null): HeaderRow[] {
  if (!raw) return [{ key: 'X-Admin-Token', value: '' }]
  try {
    const parsed = JSON.parse(raw)
    const rows = Object.entries(parsed).map(([key, value]) => ({ key, value: String(value ?? '') }))
    return rows.length ? rows : [{ key: 'X-Admin-Token', value: '' }]
  } catch {
    return [{ key: 'X-Admin-Token', value: '' }]
  }
}

function buildHeaders() {
  const headers: Record<string, string> = {}
  form.headers.forEach((row) => {
    const key = row.key.trim()
    if (key && row.value) {
      headers[key] = row.value
    }
  })
  return Object.keys(headers).length ? JSON.stringify(headers) : undefined
}

function methodLabel(v?: string) {
  if (v === 'rest_api') return 'REST API'
  if (v === 'manual') return '手动分发'
  return v || '-'
}

function statusLabel(v?: string) {
  if (v === 'active') return '启用'
  if (v === 'suspended') return '停用'
  return v || '-'
}

function resetForm() {
  form.siteName = ''
  form.domain = ''
  form.iconUrl = ''
  form.apiEndpoint = ''
  form.industryTags = []
  form.integrationMethod = 'rest_api'
  form.enabled = true
  form.headers = [{ key: 'X-Admin-Token', value: '' }]
}

function fillForm(row: PublishSite) {
  form.siteName = row.siteName
  form.domain = row.domain
  form.iconUrl = row.iconUrl || ''
  form.apiEndpoint = row.apiEndpoint || ''
  form.industryTags = parseIndustryTags(row.industryTags)
  form.integrationMethod = row.integrationMethod || 'rest_api'
  form.enabled = row.status !== 'suspended'
  form.headers = parseHeaders(row.requestHeaderTemplate)
}

async function load() {
  loading.value = true
  try {
    const { data } = await getPublishSites({
      status: query.status || undefined,
      industry: query.industry || undefined,
    })
    rows.value = data.data || []
  } catch {
    rows.value = []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  mode.value = 'create'
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: PublishSite) {
  mode.value = 'edit'
  editingId.value = row.id
  fillForm(row)
  dialogVisible.value = true
}

function addHeader() {
  form.headers.push({ key: '', value: '' })
}

function removeHeader(index: number) {
  form.headers.splice(index, 1)
  if (!form.headers.length) addHeader()
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      siteName: form.siteName.trim(),
      domain: form.domain.trim(),
      iconUrl: form.iconUrl.trim() || undefined,
      industryTags: form.industryTags,
      tier: 'S2',
      status: form.enabled ? 'active' : 'suspended',
      integrationMethod: form.integrationMethod,
      currentHealthStatus: 'normal',
      apiEndpoint: form.apiEndpoint.trim(),
      httpMethod: 'POST',
      requestHeaderTemplate: buildHeaders(),
      requestBodyTemplate: '{"title":"{{title}}","content":"{{content}}","contentMarkdown":"{{contentMarkdown}}","contentHtml":"{{contentHtml}}","author":"{{author}}"}',
    }
    if (mode.value === 'create') {
      await createPublishSite(payload)
    } else if (editingId.value) {
      await updatePublishSite(editingId.value, payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function changeStatus(id: number, status: string) {
  await updatePublishSiteStatus(id, status)
  ElMessage.success('状态已更新')
  await load()
}

async function test(id: number) {
  const { data } = await testPublishSite(id)
  if (data.data.success) {
    ElMessage.success(`连通测试成功，耗时 ${data.data.elapsedMs ?? '-'}ms`)
  } else {
    ElMessage.warning(`连通测试失败: ${data.data.message || 'ping unreachable'}`)
  }
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  await load()
})
</script>

<style scoped>
.header-editor {
  display: flex;
  width: 100%;
  flex-direction: column;
  gap: 8px;
}

.header-row {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) minmax(220px, 1.2fr) auto;
  gap: 8px;
}

@media (max-width: 720px) {
  .header-row {
    grid-template-columns: 1fr;
  }
}
</style>
