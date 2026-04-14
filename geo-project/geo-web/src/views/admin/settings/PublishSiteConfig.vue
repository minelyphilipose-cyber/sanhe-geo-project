<template>
  <div>
    <div class="mb-4 flex items-center justify-between gap-3">
      <div class="flex items-center gap-2">
        <el-select v-model="query.tier" clearable placeholder="站点层级" style="width: 130px" @change="load">
          <el-option label="S0" value="S0" />
          <el-option label="S1" value="S1" />
          <el-option label="S2" value="S2" />
        </el-select>
        <el-select v-model="query.status" clearable placeholder="站点状态" style="width: 150px" @change="load">
          <el-option label="启用" value="active" />
          <el-option label="停用" value="suspended" />
          <el-option label="维护中" value="maintenance" />
        </el-select>
        <el-select v-model="query.industry" clearable filterable placeholder="行业标签" style="width: 180px" @change="load">
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

    <el-card>
      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无站点配置">
        <el-table :data="rows" border>
          <el-table-column prop="siteName" label="站点名称" min-width="140" />
          <el-table-column prop="domain" label="域名" min-width="180" />
          <el-table-column label="行业标签" min-width="200">
            <template #default="scope">
              <div class="flex flex-wrap gap-1">
                <el-tag v-for="tag in parseIndustryTags(scope.row.industryTags)" :key="`${scope.row.id}-${tag}`" type="info">
                  {{ dictStore.label('industry_tag', tag) || tag }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="tier" label="层级" width="80" />
          <el-table-column label="健康" width="120">
            <template #default="scope">
              <el-tag :type="healthTagType(scope.row.currentHealthStatus)">{{ healthLabel(scope.row.currentHealthStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="接入方式" width="110">
            <template #default="scope">{{ methodLabel(scope.row.integrationMethod) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="scope">
              <el-tag :type="scope.row.status === 'active' ? 'success' : 'warning'">{{ statusLabel(scope.row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" width="170" />
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
              <el-button link type="primary" @click="test(scope.row.id)">测试连接</el-button>
              <el-dropdown @command="(cmd: string) => changeStatus(scope.row.id, cmd)">
                <el-button link type="warning">状态切换</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="active">启用</el-dropdown-item>
                    <el-dropdown-item command="suspended">停用</el-dropdown-item>
                    <el-dropdown-item command="maintenance">维护中</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </el-table>
      </DataState>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="mode === 'create' ? '新增发布站点' : '编辑发布站点'" width="920px">
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
            <el-form-item label="行业标签" prop="industryTags">
              <el-select v-model="form.industryTags" multiple filterable style="width: 100%">
                <el-option
                  v-for="item in dictStore.options('industry_tag')"
                  :key="item.dictKey"
                  :label="item.dictValue"
                  :value="item.dictKey"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :xs="24" :md="6">
            <el-form-item label="层级" prop="tier">
              <el-select v-model="form.tier" style="width: 100%">
                <el-option label="S0" value="S0" />
                <el-option label="S1" value="S1" />
                <el-option label="S2" value="S2" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="6">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" style="width: 100%">
                <el-option label="启用" value="active" />
                <el-option label="停用" value="suspended" />
                <el-option label="维护中" value="maintenance" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="6">
            <el-form-item label="接入方式" prop="integrationMethod">
              <el-select v-model="form.integrationMethod" style="width: 100%">
                <el-option label="REST API" value="rest_api" />
                <el-option label="手动分发" value="manual" />
                <el-option label="FTP（预留）" value="ftp" />
                <el-option label="邮件（预留）" value="email" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="6">
            <el-form-item label="健康状态" prop="currentHealthStatus">
              <el-select v-model="form.currentHealthStatus" style="width: 100%">
                <el-option label="正常" value="normal" />
                <el-option label="慢响应" value="slow" />
                <el-option label="高失败率" value="high_failure" />
                <el-option label="已降级" value="degraded" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">站点接口配置（REST API）</el-divider>
        <el-row :gutter="12">
          <el-col :xs="24" :md="12">
            <el-form-item label="接口地址（API Endpoint）">
              <el-input v-model="form.apiEndpoint" placeholder="https://xxx/publish" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="6">
            <el-form-item label="请求方法（HTTP Method）">
              <el-select v-model="form.httpMethod" style="width: 100%">
                <el-option label="POST" value="POST" />
                <el-option label="PUT" value="PUT" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="6">
            <el-form-item label="鉴权方式（Auth Type）">
              <el-select v-model="form.authType" style="width: 100%">
                <el-option label="api_key" value="api_key" />
                <el-option label="bearer_token" value="bearer_token" />
                <el-option label="basic_auth" value="basic_auth" />
                <el-option label="oauth2" value="oauth2" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :xs="24" :md="12">
            <el-form-item label="凭据引用（credential_ref）">
              <el-input v-model="form.credentialRef" placeholder="vault://content/site-key" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="站点密钥（api_credential，录入后自动加密）">
              <el-input v-model="form.apiCredential" type="password" show-password />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="请求头模板（JSON）">
          <el-input v-model="form.requestHeaderTemplate" type="textarea" :rows="2" placeholder='{"Content-Type":"application/json"}' />
        </el-form-item>
        <el-form-item label="请求体模板（JSON，占位符支持 {{title}} 等）">
          <el-input
            v-model="form.requestBodyTemplate"
            type="textarea"
            :rows="3"
            placeholder='{"title":"{{title}}","content":"{{content}}","author":"{{author}}"}'
          />
        </el-form-item>
        <el-form-item label="返回链接提取路径（JSONPath）">
          <el-input v-model="form.responseUrlPath" placeholder="$.data.url" />
        </el-form-item>
        <el-form-item label="内容约束（JSON）">
          <el-input v-model="form.contentConstraints" type="textarea" :rows="3" placeholder='{"maxTitleLength":60,"maxBodyLength":5000}' />
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

const dictStore = useDictStore()
const loading = ref(false)
const saving = ref(false)
const rows = ref<PublishSite[]>([])

const query = reactive({
  tier: '',
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
  industryTags: [] as string[],
  tier: 'S2',
  status: 'active',
  integrationMethod: 'rest_api',
  currentHealthStatus: 'normal',
  apiEndpoint: '',
  httpMethod: 'POST',
  authType: 'api_key',
  credentialRef: '',
  apiCredential: '',
  requestHeaderTemplate: '{"Content-Type":"application/json"}',
  requestBodyTemplate: '{"title":"{{title}}","content":"{{content}}","author":"{{author}}"}',
  responseUrlPath: '$.data.url',
  contentConstraints: '{"maxTitleLength":60,"maxBodyLength":5000}',
})

const rules: FormRules = {
  siteName: [{ required: true, message: '请输入站点名称', trigger: 'blur' }],
  domain: [{ required: true, message: '请输入域名', trigger: 'blur' }],
  industryTags: [{ required: true, message: '请选择至少一个行业标签', trigger: 'change' }],
  tier: [{ required: true, message: '请选择层级', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
  integrationMethod: [{ required: true, message: '请选择接入方式', trigger: 'change' }],
  currentHealthStatus: [{ required: true, message: '请选择健康状态', trigger: 'change' }],
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

function healthTagType(v?: string): 'success' | 'warning' | 'danger' | 'info' {
  if (v === 'normal') return 'success'
  if (v === 'slow') return 'warning'
  if (v === 'high_failure' || v === 'degraded') return 'danger'
  return 'info'
}

function healthLabel(v?: string) {
  if (v === 'normal') return '正常'
  if (v === 'slow') return '慢响应'
  if (v === 'high_failure') return '高失败率'
  if (v === 'degraded') return '已降级'
  return v || '-'
}

function methodLabel(v?: string) {
  if (v === 'rest_api') return 'REST API'
  if (v === 'manual') return '手动分发'
  if (v === 'ftp') return 'FTP（预留）'
  if (v === 'email') return '邮件（预留）'
  return v || '-'
}

function statusLabel(v?: string) {
  if (v === 'active') return '启用'
  if (v === 'suspended') return '停用'
  if (v === 'maintenance') return '维护中'
  return v || '-'
}

function resetForm() {
  form.siteName = ''
  form.domain = ''
  form.industryTags = []
  form.tier = 'S2'
  form.status = 'active'
  form.integrationMethod = 'rest_api'
  form.currentHealthStatus = 'normal'
  form.apiEndpoint = ''
  form.httpMethod = 'POST'
  form.authType = 'api_key'
  form.credentialRef = ''
  form.apiCredential = ''
  form.requestHeaderTemplate = '{"Content-Type":"application/json"}'
  form.requestBodyTemplate = '{"title":"{{title}}","content":"{{content}}","author":"{{author}}"}'
  form.responseUrlPath = '$.data.url'
  form.contentConstraints = '{"maxTitleLength":60,"maxBodyLength":5000}'
}

function fillForm(row: PublishSite) {
  form.siteName = row.siteName
  form.domain = row.domain
  form.industryTags = parseIndustryTags(row.industryTags)
  form.tier = row.tier || 'S2'
  form.status = row.status || 'active'
  form.integrationMethod = row.integrationMethod || 'rest_api'
  form.currentHealthStatus = row.currentHealthStatus || 'normal'
  form.apiEndpoint = row.apiEndpoint || ''
  form.httpMethod = (row.httpMethod as string) || 'POST'
  form.authType = (row.authType as string) || 'api_key'
  form.credentialRef = row.credentialRef || ''
  form.apiCredential = ''
  form.requestHeaderTemplate = row.requestHeaderTemplate || '{"Content-Type":"application/json"}'
  form.requestBodyTemplate = row.requestBodyTemplate || '{"title":"{{title}}","content":"{{content}}","author":"{{author}}"}'
  form.responseUrlPath = row.responseUrlPath || '$.data.url'
  form.contentConstraints = row.contentConstraints || '{"maxTitleLength":60,"maxBodyLength":5000}'
}

async function load() {
  loading.value = true
  try {
    const { data } = await getPublishSites({
      tier: query.tier || undefined,
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

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      siteName: form.siteName.trim(),
      domain: form.domain.trim(),
      industryTags: form.industryTags,
      tier: form.tier,
      status: form.status,
      integrationMethod: form.integrationMethod,
      currentHealthStatus: form.currentHealthStatus,
      apiEndpoint: form.apiEndpoint || undefined,
      httpMethod: form.httpMethod || undefined,
      authType: form.authType || undefined,
      credentialRef: form.credentialRef || undefined,
      apiCredential: form.apiCredential || undefined,
      requestHeaderTemplate: form.requestHeaderTemplate || undefined,
      requestBodyTemplate: form.requestBodyTemplate || undefined,
      responseUrlPath: form.responseUrlPath || undefined,
      contentConstraints: form.contentConstraints || undefined,
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
    ElMessage.success(`连接测试成功，状态码: ${data.data.statusCode ?? '-'}`)
  } else {
    ElMessage.warning(`连接测试失败: ${data.data.message || data.data.responseBody || 'unknown'}`)
  }
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  await load()
})
</script>