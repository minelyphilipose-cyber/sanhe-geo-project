<template>
  <div class="platform-config-page admin-page">
    <div class="admin-page-header platform-config-header">
      <div>
        <div class="admin-page-kicker">系统配置</div>
        <h1 class="admin-page-title">AI平台配置</h1>
        <div class="admin-page-subtitle">维护平台模型、密钥引用、调用控制和业务能力开关。</div>
      </div>
      <div class="admin-page-actions">
        <el-button v-if="canManage" type="primary" @click="openCreate">新增平台</el-button>
      </div>
    </div>

    <el-card shadow="never" class="admin-surface platform-toolbar-card">
      <div class="platform-toolbar">
        <el-input v-model="query.keyword" class="filter-keyword" placeholder="搜索平台编码/名称/模型" clearable @keyup.enter="load" />
        <el-select v-model="query.priorityLevel" class="filter-level" placeholder="平台等级" clearable @change="load">
          <el-option
            v-for="item in dictStore.options('platform_priority')"
            :key="item.dictKey"
            :label="item.dictValue"
            :value="item.dictKey"
          />
        </el-select>
        <el-select v-model="query.enabled" class="filter-status" placeholder="状态" clearable @change="load">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
        <el-button type="primary" plain @click="load">查询</el-button>
      </div>
    </el-card>

    <div class="admin-metric-grid platform-metric-grid">
      <div class="admin-metric-card" style="--metric-accent: #2563eb; --metric-tone: #eff6ff">
        <span class="admin-metric-label">平台总数</span>
        <strong class="admin-metric-value">{{ page.total }}</strong>
        <span class="admin-metric-hint">当前筛选结果</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #059669; --metric-tone: #ecfdf5">
        <span class="admin-metric-label">启用平台</span>
        <strong class="admin-metric-value">{{ enabledCount }}</strong>
        <span class="admin-metric-hint">当前页可用配置</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #f59e0b; --metric-tone: #fffbeb">
        <span class="admin-metric-label">降级处理</span>
        <strong class="admin-metric-value">{{ degradedCount }}</strong>
        <span class="admin-metric-hint">需要关注模型链路</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #7c3aed; --metric-tone: #f5f3ff">
        <span class="admin-metric-label">问题池生成</span>
        <strong class="admin-metric-value">{{ questionCount }}</strong>
        <span class="admin-metric-hint">已开启拓词能力</span>
      </div>
    </div>

    <el-card shadow="never" class="admin-table-card platform-table-card">
      <div class="table-header">
        <div>
          <div class="table-title">平台配置列表</div>
          <div class="table-subtitle">按平台等级、启用状态和降级状态核对模型配置。</div>
        </div>
        <div class="chips">
          <span class="chip chip-muted">当前页 {{ rows.length }}</span>
          <span class="chip chip-success">启用 {{ enabledCount }}</span>
          <span class="chip chip-warning">降级 {{ degradedCount }}</span>
        </div>
      </div>

      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无平台配置">
        <el-table :data="rows" border table-layout="fixed">
          <el-table-column label="平台" min-width="220" show-overflow-tooltip>
            <template #default="scope">
              <div class="admin-entity-cell">
                <div
                  class="admin-entity-avatar platform-avatar"
                  :class="[enabledClass(scope.row.enabled), { 'has-logo': platformLogoSrc(scope.row) }]"
                >
                  <img
                    v-if="platformLogoSrc(scope.row)"
                    :src="platformLogoSrc(scope.row)"
                    :alt="scope.row.platformName"
                    @error="fallbackPlatformLogo($event, scope.row)"
                  />
                  <template v-else>{{ platformInitial(scope.row.platformName) }}</template>
                </div>
                <div class="min-w-0">
                  <div class="admin-entity-main">{{ scope.row.platformName }}</div>
                  <div class="admin-entity-sub">{{ scope.row.platformCode }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="等级" width="120">
            <template #default="scope">
              <span class="priority-pill" :class="priorityClass(scope.row.priorityLevel)">
                {{ dictStore.label('platform_priority', scope.row.priorityLevel) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="modelName" label="高性能版本" min-width="140" />
          <el-table-column prop="lowModelId" label="低性能版本" min-width="140">
            <template #default="scope">{{ scope.row.lowModelId || '-' }}</template>
          </el-table-column>
          <el-table-column prop="concurrencyLimit" label="并发上限" width="100">
            <template #default="scope">{{ scope.row.concurrencyLimit ?? 1 }}</template>
          </el-table-column>
          <el-table-column prop="rpmLimit" label="RPM" width="90">
            <template #default="scope">{{ scope.row.rpmLimit ?? 60 }}</template>
          </el-table-column>
          <el-table-column prop="tpmLimit" label="TPM" width="110">
            <template #default="scope">{{ scope.row.tpmLimit ?? 60000 }}</template>
          </el-table-column>
          <el-table-column label="能力开关" min-width="210">
            <template #default="scope">
              <div class="capability-tags">
                <span class="capability-tag" :class="scope.row.enabledForGeoQuestion ? 'is-success' : 'is-muted'">问题池</span>
                <span class="capability-tag" :class="isPresaleEnabled(scope.row) ? 'is-success' : 'is-muted'">售前</span>
                <span class="capability-tag" :class="scope.row.presaleEvaluateEnabled ? 'is-success' : 'is-muted'">评估</span>
                <span class="capability-tag" :class="scope.row.enabledForArticle ? 'is-success' : 'is-muted'">文章</span>
                <span class="capability-tag" :class="scope.row.enabledForQuestionPoll ? 'is-success' : 'is-muted'">跑批</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="降级处理" width="100">
            <template #default="scope">
              <span class="admin-status-tag" :class="scope.row.degraded ? 'is-warning' : 'is-muted'">
                {{ scope.row.degraded ? '是' : '否' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="degradedReason" label="降级原因" min-width="220" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.degradedReason || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="scope">
              <span class="admin-status-tag" :class="enabledClass(scope.row.enabled)">
                {{ scope.row.enabled ? '启用' : '停用' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="180">
            <template #default="scope">{{ scope.row.createdAt || '-' }}</template>
          </el-table-column>
          <el-table-column v-if="canManage" label="操作" width="150" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
              <el-button link type="danger" @click="remove(scope.row)">删除</el-button>
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

    <el-dialog
      v-model="dialogVisible"
      width="1040px"
      class="admin-editor-dialog platform-editor-dialog"
      :show-close="false"
    >
      <template #header>
        <div class="platform-modal-header">
          <div class="platform-modal-title">
            <span class="modal-title-accent" />
            <span>{{ mode === 'create' ? '新增平台配置' : '编辑平台配置' }}</span>
            <em v-if="form.platformCode">{{ form.platformCode }}</em>
          </div>
          <button class="modal-close-button" type="button" aria-label="关闭" @click="dialogVisible = false">×</button>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="platform-config-form">
        <section class="modal-section">
          <div class="modal-section-head">
            <span class="section-icon">基</span>
            <strong>基础信息</strong>
            <small>平台识别与优先级</small>
            <i />
          </div>
          <div class="form-grid basic-info-grid">
            <el-form-item label="平台编码" prop="platformCode">
              <el-input v-model="form.platformCode" placeholder="如: doubao / deepseek" />
            </el-form-item>
            <el-form-item label="平台名称" prop="platformName">
              <el-input v-model="form.platformName" placeholder="如: 豆包" />
            </el-form-item>
            <el-form-item label="平台等级" prop="priorityLevel">
              <el-select v-model="form.priorityLevel">
                <el-option
                  v-for="item in dictStore.options('platform_priority')"
                  :key="item.dictKey"
                  :label="item.dictValue"
                  :value="item.dictKey"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="平台地址" prop="platformHomeUrl">
              <el-input v-model="form.platformHomeUrl" placeholder="如: https://www.doubao.com/" />
            </el-form-item>
          </div>
          <div class="platform-logo-row">
            <div
              class="platform-logo-preview platform-avatar"
              :class="[enabledClass(form.enabled), { 'has-logo': formPlatformLogoSrc }]"
            >
              <img
                v-if="formPlatformLogoSrc"
                :src="formPlatformLogoSrc"
                :alt="form.platformName || '平台Logo'"
                @error="fallbackFormPlatformLogo"
              />
              <template v-else>{{ platformInitial(form.platformName) }}</template>
            </div>
            <div class="platform-logo-actions">
              <span>平台Logo</span>
              <el-upload :show-file-list="false" :before-upload="handleLogoUpload" accept="image/*" :disabled="mode === 'create' || logoUploading">
                <el-button size="small" type="primary" plain :loading="logoUploading" :disabled="mode === 'create'">上传Logo</el-button>
              </el-upload>
              <small>{{ mode === 'create' ? '保存平台后可上传Logo' : '支持图片上传后反显' }}</small>
            </div>
          </div>
        </section>

        <section class="modal-section">
          <div class="modal-section-head">
            <span class="section-icon">密</span>
            <strong>密钥与接入</strong>
            <small>API 地址、密钥与 Key 引用</small>
            <i />
          </div>
          <div class="constraint-hint">「密钥」与「主密钥引用」至少填写一个</div>
          <div class="form-grid is-two">
            <el-form-item label="接口地址" prop="apiUrl">
              <el-input v-model="form.apiUrl" placeholder="https://xxx/v1" />
            </el-form-item>
            <el-form-item label="密钥" prop="apiKey">
              <el-input v-model="form.apiKey" type="password" show-password placeholder="输入平台密钥" />
            </el-form-item>
            <el-form-item label="主密钥引用" prop="primaryKeyRef">
              <el-input v-model="form.primaryKeyRef" placeholder="如: vault://keys/doubao-primary" />
            </el-form-item>
            <el-form-item label="备用密钥引用" prop="backupKeyRef">
              <el-input v-model="form.backupKeyRef" placeholder="如: vault://keys/doubao-backup" />
            </el-form-item>
          </div>
        </section>

        <section class="modal-section">
          <div class="modal-section-head">
            <span class="section-icon">模</span>
            <strong>模型能力</strong>
            <small>主模型、低性能模型与备用服务商</small>
            <i />
          </div>
          <div class="subgroup-title"><span />主模型</div>
          <div class="form-grid model-main-grid">
            <el-form-item label="高性能模型 ID" prop="modelId">
              <el-input v-model="form.modelId" placeholder="如: gpt-5.4" />
            </el-form-item>
            <el-form-item label="模型名称" prop="modelName">
              <el-input v-model="form.modelName" placeholder="如: DeepSeek Chat" />
            </el-form-item>
            <el-form-item label="并发上限" prop="concurrencyLimit">
              <el-input-number v-model="form.concurrencyLimit" :min="1" :max="10000" :step="1" />
            </el-form-item>
            <el-form-item class="model-low-field" label="低性能模型 ID" prop="lowModelId">
              <el-input v-model="form.lowModelId" placeholder="如: gpt-5.3" />
            </el-form-item>
          </div>
          <div class="subgroup-title"><span />调用额度</div>
          <div class="form-grid is-two compact-grid">
            <el-form-item label="RPM 每分钟请求数" prop="rpmLimit">
              <el-input-number v-model="form.rpmLimit" :min="1" :max="1000000" :step="10" />
            </el-form-item>
            <el-form-item label="TPM 每分钟 Token 数" prop="tpmLimit">
              <el-input-number v-model="form.tpmLimit" :min="1" :max="100000000" :step="1000" />
            </el-form-item>
          </div>
          <div class="subgroup-title"><span />备用服务商（可选）</div>
          <div class="form-grid is-three compact-grid">
            <el-form-item label="备用服务商名称" prop="backupProviderName">
              <el-input v-model="form.backupProviderName" placeholder="如: deepseek" />
            </el-form-item>
            <el-form-item label="备用接口地址" prop="backupApiUrl">
              <el-input v-model="form.backupApiUrl" placeholder="https://backup.xxx/v1" />
            </el-form-item>
            <el-form-item label="备用模型 ID" prop="backupModelId">
              <el-input v-model="form.backupModelId" placeholder="如: deepseek-chat" />
            </el-form-item>
          </div>
        </section>

        <section class="modal-section">
          <div class="business-section-head">
            <span class="business-section-icon" aria-hidden="true"><span /><span /><span /></span>
            <strong>业务能力</strong>
            <small>总开关决定该平台是否参与任何业务链路</small>
            <i />
          </div>

          <div class="master-capability-card">
            <div class="master-capability-body">
              <div class="master-capability-info">
                <span class="master-power-icon" aria-hidden="true" />
                <div>
                  <strong title="模型总启用状态">模型总启用状态</strong>
                  <span title="关闭后，该平台不会参与售前、拓词、文章、跑批等任何任务">
                    关闭后，该平台不会参与售前、拓词、文章、跑批等任何任务
                  </span>
                </div>
              </div>
              <div class="master-switch-control">
                <span>{{ form.enabled ? '已启用' : '已停用' }}</span>
                <el-switch v-model="form.enabled" class="master-switch" />
              </div>
            </div>
            <span class="master-capability-connector" aria-hidden="true" />
          </div>

          <div v-if="!form.enabled" class="capability-disabled-alert">
            <span class="alert-triangle-icon" aria-hidden="true" />
            <span>模型总开关已关闭，所有子能力暂不生效</span>
          </div>

          <div class="capability-group">
            <div class="capability-group-head">
              <span class="capability-group-strip is-report" />
              <strong>AI 可见度诊断报告</strong>
              <small>售前诊断与评估链路</small>
            </div>
            <div class="capability-card-grid is-two">
              <div class="child-capability-card" :class="{ 'is-master-off': !form.enabled }">
                <div class="child-capability-info">
                  <strong title="售前问答能力">售前问答能力</strong>
                  <span title="支持诊断报告中的售前问答生成">支持诊断报告中的售前问答生成</span>
                </div>
                <div class="switch-control">
                  <span class="switch-status" :class="{ 'is-off': !form.enabled || !form.enabledForPresale }">
                    {{ form.enabled && form.enabledForPresale ? '启用' : '停用' }}
                  </span>
                  <el-switch
                    :model-value="form.enabled && form.enabledForPresale"
                    :disabled="!form.enabled"
                    @update:model-value="form.enabledForPresale = $event"
                  />
                </div>
              </div>
              <div class="child-capability-card" :class="{ 'is-master-off': !form.enabled }">
                <div class="child-capability-info">
                  <strong title="售前评估模型">售前评估模型</strong>
                  <span title="支持报告评估与模型判断">支持报告评估与模型判断</span>
                </div>
                <div class="switch-control">
                  <span
                    class="switch-status"
                    :class="{
                      'is-off':
                        !form.enabled ||
                        !form.presaleEvaluateEnabled ||
                        !canEnablePresaleEvaluate(form.platformCode),
                    }"
                  >
                    {{
                      form.enabled && form.presaleEvaluateEnabled && canEnablePresaleEvaluate(form.platformCode)
                        ? '启用'
                        : '停用'
                    }}
                  </span>
                  <el-switch
                    :model-value="
                      form.enabled && form.presaleEvaluateEnabled && canEnablePresaleEvaluate(form.platformCode)
                    "
                    :disabled="!form.enabled || !canEnablePresaleEvaluate(form.platformCode)"
                    @update:model-value="form.presaleEvaluateEnabled = $event"
                  />
                </div>
              </div>
            </div>
          </div>

          <div class="capability-group">
            <div class="capability-group-head">
              <span class="capability-group-strip is-content" />
              <strong>内容生成</strong>
              <small>拓词管理与文章生产链路</small>
            </div>
            <div class="capability-card-grid is-two">
              <div class="child-capability-card" :class="{ 'is-master-off': !form.enabled }">
                <div class="child-capability-info">
                  <strong title="拓词问题生成">拓词问题生成</strong>
                  <span title="支持 GEO 分层问题池生成">支持 GEO 分层问题池生成</span>
                </div>
                <div class="switch-control">
                  <span
                    class="switch-status"
                    :class="{
                      'is-off': !form.enabled || !form.enabledForGeoQuestion,
                    }"
                  >
                    {{
                      form.enabled && form.enabledForGeoQuestion ? '启用' : '停用'
                    }}
                  </span>
                  <el-switch
                    :model-value="form.enabled && form.enabledForGeoQuestion"
                    :disabled="!form.enabled"
                    @update:model-value="form.enabledForGeoQuestion = $event"
                  />
                </div>
              </div>
              <div class="child-capability-card" :class="{ 'is-master-off': !form.enabled }">
                <div class="child-capability-info">
                  <strong title="文章生成能力">文章生成能力</strong>
                  <span title="支持文章内容生成任务">支持文章内容生成任务</span>
                </div>
                <div class="switch-control">
                  <span class="switch-status" :class="{ 'is-off': !form.enabled || !form.enabledForArticle }">
                    {{ form.enabled && form.enabledForArticle ? '启用' : '停用' }}
                  </span>
                  <el-switch
                    :model-value="form.enabled && form.enabledForArticle"
                    :disabled="!form.enabled"
                    @update:model-value="form.enabledForArticle = $event"
                  />
                </div>
              </div>
            </div>
          </div>

          <div class="capability-group">
            <div class="capability-group-head">
              <span class="capability-group-strip is-batch" />
              <strong>跑批任务</strong>
              <small>周期性后台调度</small>
            </div>
            <div class="capability-card-grid is-single">
              <div class="child-capability-card" :class="{ 'is-master-off': !form.enabled }">
                <div class="child-capability-info">
                  <strong title="问题池定时跑批">问题池定时跑批</strong>
                  <span title="支持 BI 日常问题池轮询与定时批处理">支持 BI 日常问题池轮询与定时批处理</span>
                </div>
                <div class="switch-control">
                  <span class="switch-status" :class="{ 'is-off': !form.enabled || !form.enabledForQuestionPoll }">
                    {{ form.enabled && form.enabledForQuestionPoll ? '启用' : '停用' }}
                  </span>
                  <el-switch
                    :model-value="form.enabled && form.enabledForQuestionPoll"
                    :disabled="!form.enabled"
                    @update:model-value="form.enabledForQuestionPoll = $event"
                  />
                </div>
              </div>
            </div>
          </div>

          <div class="degrade-panel">
            <div class="degrade-panel-head">
              <div>
                <strong>降级处理</strong>
                <span>开启后该平台将进入降级状态，请记录原因</span>
              </div>
              <div class="switch-control">
                <span class="switch-status">{{ form.degraded ? '是' : '否' }}</span>
                <el-switch v-model="form.degraded" />
              </div>
            </div>
            <el-form-item label="降级原因" prop="degradedReason">
              <el-input v-model="form.degradedReason" type="textarea" :rows="2" placeholder="降级处理开启时建议填写" />
            </el-form-item>
          </div>
        </section>

        <section class="modal-section">
          <div class="modal-section-head">
            <span class="section-icon">备</span>
            <strong>平台备注</strong>
            <small>记录人工说明与维护信息</small>
            <i />
          </div>
          <div class="form-grid is-one">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="可填写供应商、限制或维护说明" />
            </el-form-item>
          </div>
        </section>
      </el-form>

      <template #footer>
        <div class="platform-modal-footer">
          <span>带 <b>*</b> 为必填项</span>
          <div>
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="saving" @click="submit">保存配置</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadRawFile } from 'element-plus'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'
import DataState from '@/components/ui/DataState.vue'
import {
  createPlatformConfig,
  deletePlatformConfig,
  getPlatformConfigPage,
  updatePlatformConfig,
  uploadPlatformLogo,
} from '@/api/platformConfig'
import type { AIPlatformConfigItem } from '@/types'
import { normalizeObjectStorageUrl } from '@/utils/objectStorageUrl'
import ai360Logo from '@/assets/ai-model-logos/ai360-color.png'
import deepseekLogo from '@/assets/ai-model-logos/deepseek-color.png'
import doubaoLogo from '@/assets/ai-model-logos/doubao.png'
import glmLogo from '@/assets/ai-model-logos/glm.png'
import hunyuanLogo from '@/assets/ai-model-logos/hunyuan-color.png'
import kimiLogo from '@/assets/ai-model-logos/kimi.png'
import minimaxLogo from '@/assets/ai-model-logos/minimax-color.png'
import qwenLogo from '@/assets/ai-model-logos/qwen-color.png'
import wenxinLogo from '@/assets/ai-model-logos/文心一言.png'
import xiaomiMimoLogo from '@/assets/ai-model-logos/xiaomimimo.png'
import yuanbaoLogo from '@/assets/ai-model-logos/yuanbao-color.svg'

const dictStore = useDictStore()
const userStore = useUserStore()
const canManage = computed(() => userStore.hasPermission('user.manage'))

const loading = ref(false)
const saving = ref(false)
const logoUploading = ref(false)
const rows = ref<AIPlatformConfigItem[]>([])
const page = reactive({ current: 1, size: 10, total: 0 })
const query = reactive<{ keyword: string; priorityLevel: string; enabled: boolean | undefined }>({
  keyword: '',
  priorityLevel: '',
  enabled: undefined,
})

const enabledCount = computed(() => rows.value.filter((item) => item.enabled).length)
const degradedCount = computed(() => rows.value.filter((item) => item.degraded).length)
const questionCount = computed(() => rows.value.filter((item) => item.enabledForGeoQuestion).length)
const builtinPlatformLogos: Record<string, string> = {
  ai360: ai360Logo,
  '360': ai360Logo,
  deepseek: deepseekLogo,
  doubao: doubaoLogo,
  豆包: doubaoLogo,
  glm: glmLogo,
  zhipu: glmLogo,
  智谱: glmLogo,
  hunyuan: hunyuanLogo,
  kimi: kimiLogo,
  minimax: minimaxLogo,
  qwen: qwenLogo,
  tongyi: qwenLogo,
  通义千问: qwenLogo,
  wenxin: wenxinLogo,
  文心一言: wenxinLogo,
  mimo: xiaomiMimoLogo,
  xiaomimimo: xiaomiMimoLogo,
  小米mimo: xiaomiMimoLogo,
  yuanbao: yuanbaoLogo,
}

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const mode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)

const form = reactive({
  platformCode: '',
  platformName: '',
  platformHomeUrl: '',
  platformLogoUrl: '',
  priorityLevel: 'P1',
  apiKey: '',
  primaryKeyRef: '',
  backupKeyRef: '',
  backupProviderName: '',
  backupApiUrl: '',
  backupModelId: '',
  apiUrl: '',
  modelId: '',
  lowModelId: '',
  modelName: '',
  concurrencyLimit: 1,
  rpmLimit: 60,
  tpmLimit: 60000,
  enabled: true,
  enabledForPresale: true,
  enabledForArticle: true,
  enabledForGeoQuestion: true,
  enabledForQuestionPoll: false,
  presaleEvaluateEnabled: true,
  degraded: false,
  degradedReason: '',
  remark: '',
})
const formPlatformLogoSrc = computed(() => platformLogoSrc({
  id: editingId.value || 0,
  platformCode: form.platformCode,
  platformName: form.platformName,
  platformLogoUrl: form.platformLogoUrl,
  platformLogoObjectKey: rows.value.find((item) => item.id === editingId.value)?.platformLogoObjectKey || null,
}))

const rules: FormRules = {
  platformCode: [
    { required: true, message: '请输入平台编码', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_-]{1,63}$/, message: '编码格式: 小写字母开头, 支持字母数字_-', trigger: 'blur' },
  ],
  platformName: [{ required: true, message: '请输入平台名称', trigger: 'blur' }],
  priorityLevel: [{ required: true, message: '请选择平台等级', trigger: 'change' }],
  apiKey: [
    {
      validator: (_rule, value, callback) => {
        const hasApiKey = !!(value && String(value).trim())
        const hasPrimaryRef = !!(form.primaryKeyRef && form.primaryKeyRef.trim())
        if (hasApiKey || hasPrimaryRef) callback()
        else callback(new Error('API Key 与 primary_key_ref 至少填写一个'))
      },
      trigger: ['blur', 'change'],
    },
  ],
  apiUrl: [{ required: true, message: '请输入API URL', trigger: 'blur' }],
  modelId: [{ required: true, message: '请输入Model ID', trigger: 'blur' }],
  modelName: [{ required: true, message: '请输入Model名称', trigger: 'blur' }],
  concurrencyLimit: [{ required: true, type: 'number', min: 1, message: '并发上限必须大于0', trigger: 'change' }],
  rpmLimit: [{ required: true, type: 'number', min: 1, message: 'RPM 必须大于0', trigger: 'change' }],
  tpmLimit: [{ required: true, type: 'number', min: 1, message: 'TPM 必须大于0', trigger: 'change' }],
}

watch(
  () => form.degraded,
  (v) => {
    if (!v) {
      form.degradedReason = ''
    }
  },
)
watch(
  () => form.platformCode,
  (value) => {
    if (!canEnablePresaleEvaluate(value)) {
      form.presaleEvaluateEnabled = false
    }
  },
)

function resetForm() {
  form.platformCode = ''
  form.platformName = ''
  form.platformHomeUrl = ''
  form.platformLogoUrl = ''
  form.priorityLevel = 'P1'
  form.apiKey = ''
  form.primaryKeyRef = ''
  form.backupKeyRef = ''
  form.backupProviderName = ''
  form.backupApiUrl = ''
  form.backupModelId = ''
  form.apiUrl = ''
  form.modelId = ''
  form.lowModelId = ''
  form.modelName = ''
  form.concurrencyLimit = 1
  form.rpmLimit = 60
  form.tpmLimit = 60000
  form.enabled = true
  form.enabledForPresale = true
  form.enabledForArticle = true
  form.enabledForGeoQuestion = true
  form.enabledForQuestionPoll = false
  form.presaleEvaluateEnabled = true
  form.degraded = false
  form.degradedReason = ''
  form.remark = ''
}

async function load() {
  loading.value = true
  try {
    const { data } = await getPlatformConfigPage({
      current: page.current,
      size: page.size,
      keyword: query.keyword || undefined,
      priorityLevel: query.priorityLevel || undefined,
      enabled: query.enabled,
    })
    rows.value = (data.data.records || []).map((item) => ({
      ...item,
      platformLogoUrl: normalizeObjectStorageUrl(item.platformLogoUrl) || item.platformLogoUrl,
      platformLogoObjectKey: item.platformLogoObjectKey || null,
    }))
    page.total = data.data.total || 0
  } catch {
    rows.value = []
    page.total = 0
  } finally {
    loading.value = false
  }
}

function onPageChange(v: number) {
  page.current = v
  load()
}

function enabledClass(enabled?: boolean) {
  return enabled ? 'is-success' : 'is-muted'
}

function priorityClass(priority?: string) {
  if (priority === 'P0') return 'is-critical'
  if (priority === 'P1') return 'is-high'
  return 'is-normal'
}

function platformInitial(value?: string | null) {
  const text = String(value || '').trim()
  return text ? Array.from(text)[0] : '平'
}

function builtinPlatformLogo(platformCode?: string | null, platformName?: string | null) {
  const code = String(platformCode || '').trim()
  const name = String(platformName || '').trim()
  return builtinPlatformLogos[code] || builtinPlatformLogos[code.toLowerCase()] || builtinPlatformLogos[name] || ''
}

function platformLogoSrc(platform: Pick<AIPlatformConfigItem, 'id' | 'platformCode' | 'platformName' | 'platformLogoUrl' | 'platformLogoObjectKey'>) {
  const logoVersion = platform.platformLogoObjectKey || normalizeObjectStorageUrl(platform.platformLogoUrl)
  if (logoVersion && platform.id) {
    return `/api/public/platform-configs/${platform.id}/logo?v=${encodeURIComponent(logoVersion)}`
  }
  return builtinPlatformLogo(platform.platformCode, platform.platformName)
}

function fallbackPlatformLogo(event: Event, platform: Pick<AIPlatformConfigItem, 'platformCode' | 'platformName'>) {
  const image = event.target as HTMLImageElement
  const fallback = builtinPlatformLogo(platform.platformCode, platform.platformName)
  if (fallback && image.dataset.logoFallbackApplied !== 'true') {
    image.dataset.logoFallbackApplied = 'true'
    image.src = fallback
  } else {
    image.style.display = 'none'
  }
}

function fallbackFormPlatformLogo(event: Event) {
  fallbackPlatformLogo(event, {
    platformCode: form.platformCode,
    platformName: form.platformName,
  })
}

function openCreate() {
  mode.value = 'create'
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: AIPlatformConfigItem) {
  mode.value = 'edit'
  editingId.value = row.id
  form.platformCode = row.platformCode
  form.platformName = row.platformName
  form.platformHomeUrl = row.platformHomeUrl || ''
  form.platformLogoUrl = normalizeObjectStorageUrl(row.platformLogoUrl) || row.platformLogoUrl || ''
  form.priorityLevel = row.priorityLevel
  form.apiKey = row.apiKey
  form.primaryKeyRef = row.primaryKeyRef || ''
  form.backupKeyRef = row.backupKeyRef || ''
  form.backupProviderName = row.backupProviderName || ''
  form.backupApiUrl = row.backupApiUrl || ''
  form.backupModelId = row.backupModelId || ''
  form.apiUrl = row.apiUrl
  form.modelId = row.modelId
  form.lowModelId = row.lowModelId || ''
  form.modelName = row.modelName
  form.concurrencyLimit = row.concurrencyLimit || 1
  form.rpmLimit = row.rpmLimit || 60
  form.tpmLimit = row.tpmLimit || 60000
  form.enabled = row.enabled
  form.enabledForPresale = row.enabledForPresale ?? true
  form.enabledForArticle = !!row.enabledForArticle
  form.enabledForGeoQuestion = !!row.enabledForGeoQuestion
  form.enabledForQuestionPoll = !!row.enabledForQuestionPoll
  form.presaleEvaluateEnabled = !!row.presaleEvaluateEnabled
  form.degraded = row.degraded
  form.degradedReason = row.degradedReason || ''
  form.remark = row.remark || ''
  dialogVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (form.degraded && !form.degradedReason.trim()) {
    ElMessage.warning('开启降级处理时，请填写降级原因')
    return
  }
  saving.value = true
  try {
    const payload = {
      platformCode: form.platformCode.trim(),
      platformName: form.platformName.trim(),
      platformHomeUrl: form.platformHomeUrl.trim() || undefined,
      platformLogoUrl: normalizeObjectStorageUrl(form.platformLogoUrl) || undefined,
      priorityLevel: form.priorityLevel,
      apiKey: form.apiKey.trim(),
      primaryKeyRef: form.primaryKeyRef.trim() || undefined,
      backupKeyRef: form.backupKeyRef.trim() || undefined,
      backupProviderName: form.backupProviderName.trim() || undefined,
      backupApiUrl: form.backupApiUrl.trim() || undefined,
      backupModelId: form.backupModelId.trim() || undefined,
      apiUrl: form.apiUrl.trim(),
      modelId: form.modelId.trim(),
      lowModelId: form.lowModelId.trim() || undefined,
      modelName: form.modelName.trim(),
      concurrencyLimit: form.concurrencyLimit,
      rpmLimit: form.rpmLimit,
      tpmLimit: form.tpmLimit,
      enabled: form.enabled,
      enabledForPresale: form.enabledForPresale,
      enabledForArticle: form.enabledForArticle,
      enabledForGeoQuestion: form.enabledForGeoQuestion,
      enabledForQuestionPoll: form.enabledForQuestionPoll,
      presaleEvaluateEnabled: form.presaleEvaluateEnabled,
      degraded: form.degraded,
      degradedReason: form.degraded ? form.degradedReason.trim() : undefined,
      remark: form.remark || undefined,
    }
    if (mode.value === 'create') {
      await createPlatformConfig(payload)
    } else if (editingId.value) {
      await updatePlatformConfig(editingId.value, payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function handleLogoUpload(file: UploadRawFile) {
  if (!editingId.value) {
    ElMessage.warning('请先保存平台配置后再上传Logo')
    return false
  }
  logoUploading.value = true
  try {
    const { data } = await uploadPlatformLogo(editingId.value, file as File)
    const updated = data.data
    form.platformLogoUrl = normalizeObjectStorageUrl(updated.platformLogoUrl) || updated.platformLogoUrl || ''
    const index = rows.value.findIndex((item) => item.id === updated.id)
    if (index >= 0) {
      rows.value[index] = {
        ...updated,
        platformLogoUrl: normalizeObjectStorageUrl(updated.platformLogoUrl) || updated.platformLogoUrl,
        platformLogoObjectKey: updated.platformLogoObjectKey || null,
      }
    }
    ElMessage.success('平台Logo已更新')
  } finally {
    logoUploading.value = false
  }
  return false
}

async function remove(row: AIPlatformConfigItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除平台「${row.platformName}」？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
    await deletePlatformConfig(row.id)
    ElMessage.success('删除成功')
    await load()
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return
  }
}

function isPresaleEnabled(row: AIPlatformConfigItem) {
  return row.enabledForPresale ?? true
}

const presaleEvaluateCodes = new Set(['deepseek', 'doubao', 'qwen', 'mimo', 'zhipu'])

function canEnablePresaleEvaluate(platformCode: string) {
  return presaleEvaluateCodes.has((platformCode || '').trim())
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  await load()
})
</script>

<style scoped>
.platform-config-header {
  align-items: center;
}

.platform-toolbar-card :deep(.el-card__body) {
  padding: 12px;
}

.platform-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.filter-keyword {
  width: 260px;
}

.filter-level,
.filter-status {
  width: 130px;
}

.platform-metric-grid {
  margin-bottom: 0;
}

.platform-table-card :deep(.el-card__body) {
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

.chip-success {
  background: #ecfdf5;
  color: #047857;
}

.chip-warning {
  background: #fffbeb;
  color: #b45309;
}

.platform-avatar.is-success {
  background: linear-gradient(135deg, #059669, #14b8a6);
}

.platform-avatar.is-muted {
  background: linear-gradient(135deg, #64748b, #94a3b8);
}

.platform-avatar.has-logo {
  background: #fff;
  border: 1px solid #e5e7eb;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
}

.platform-avatar img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  border-radius: inherit;
}

.platform-logo-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #f8fafc;
}

.platform-logo-preview {
  flex: 0 0 auto;
}

.platform-logo-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.platform-logo-actions span {
  color: #374151;
  font-size: 13px;
  font-weight: 800;
}

.platform-logo-actions small {
  color: #94a3b8;
  font-size: 12px;
}

.priority-pill {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}

.priority-pill.is-critical {
  background: #fef2f2;
  color: #b91c1c;
}

.priority-pill.is-high {
  background: #fffbeb;
  color: #b45309;
}

.priority-pill.is-normal {
  background: #eff6ff;
  color: #1d4ed8;
}

.capability-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.capability-tag {
  display: inline-flex;
  align-items: center;
  height: 22px;
  border-radius: 6px;
  padding: 0 7px;
  font-size: 12px;
  font-weight: 800;
}

.capability-tag.is-success {
  background: #ecfdf5;
  color: #047857;
}

.capability-tag.is-muted {
  background: #f1f5f9;
  color: #64748b;
}

.platform-editor-dialog :deep(.el-dialog) {
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.18);
}

.platform-editor-dialog :deep(.el-dialog__header),
.platform-editor-dialog :deep(.el-dialog__body),
.platform-editor-dialog :deep(.el-dialog__footer) {
  margin: 0;
  padding: 0;
}

.platform-editor-dialog :deep(.el-dialog__body) {
  background: #ffffff;
}

.platform-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 56px;
  padding: 14px 24px;
  border-bottom: 1px solid #dbeafe;
  background: linear-gradient(90deg, #f8fbff 0%, #eef6ff 54%, #ecfdf5 100%);
}

.platform-modal-title {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  gap: 10px;
  color: #111827;
  font-size: 18px;
  font-weight: 800;
}

.platform-modal-title em {
  display: inline-flex;
  align-items: center;
  max-width: 180px;
  height: 24px;
  overflow: hidden;
  border: 1px solid #dbeafe;
  border-radius: 999px;
  padding: 0 10px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-style: normal;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.modal-title-accent {
  width: 4px;
  height: 18px;
  border-radius: 3px;
  background: #378add;
}

.modal-close-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  font-size: 24px;
  line-height: 1;
}

.modal-close-button:hover {
  background: #f1f5f9;
  color: #0f172a;
}

.platform-config-form {
  display: block;
  max-height: 72vh;
  overflow-y: auto;
  padding: 24px 28px 26px;
}

.modal-section {
  margin-bottom: 30px;
}

.modal-section:last-child {
  margin-bottom: 0;
}

.modal-section-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.modal-section-head .section-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 30px;
  height: 24px;
  border-radius: 7px;
  background: #eaf4ff;
  color: #2563eb;
  font-size: 10px;
  font-weight: 900;
  letter-spacing: 0;
}

.modal-section-head strong {
  flex: 0 0 auto;
  color: #111827;
  font-size: 14px;
  font-weight: 800;
}

.modal-section-head small {
  flex: 0 0 auto;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
}

.modal-section-head i {
  flex: 1;
  height: 1px;
  background: #e5e7eb;
}

.constraint-hint {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  margin-bottom: 12px;
  border: 1px solid #fde68a;
  border-radius: 8px;
  padding: 0 11px;
  background: #fffbeb;
  color: #92400e;
  font-size: 12px;
  font-weight: 800;
}

.form-grid {
  display: grid;
  gap: 13px 14px;
}

.form-grid.is-two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.form-grid.is-three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.basic-info-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.form-grid.is-one {
  grid-template-columns: 1fr;
}

.model-main-grid {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) 150px;
}

.model-low-field {
  grid-column: 1 / -1;
}

.compact-grid {
  margin-top: 10px;
}

.subgroup-title {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin: 2px 0 10px;
  color: #334155;
  font-size: 12px;
  font-weight: 900;
}

.subgroup-title span {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: #378add;
}

.business-section-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.business-section-head strong {
  color: #111827;
  font-size: 14px;
  font-weight: 500;
}

.business-section-head small {
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
}

.business-section-head i {
  flex: 1;
  height: 1px;
  background: #e5e7eb;
  transform: scaleY(0.5);
  transform-origin: center;
}

.business-section-icon {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  color: #378add;
}

.business-section-icon span {
  position: absolute;
  left: 2px;
  width: 12px;
  height: 2px;
  border-radius: 999px;
  background: currentColor;
}

.business-section-icon span:nth-child(1) {
  top: 3px;
}

.business-section-icon span:nth-child(2) {
  top: 7px;
}

.business-section-icon span:nth-child(3) {
  top: 11px;
}

.business-section-icon span::after {
  position: absolute;
  top: -2px;
  width: 4px;
  height: 4px;
  border: 1px solid currentColor;
  border-radius: 999px;
  background: #ffffff;
  content: '';
}

.business-section-icon span:nth-child(1)::after {
  left: 2px;
}

.business-section-icon span:nth-child(2)::after {
  right: 2px;
}

.business-section-icon span:nth-child(3)::after {
  left: 5px;
}

.master-capability-card {
  position: relative;
  border: 1px solid #b5d4f4;
  border-radius: 8px;
  padding: 14px 16px 16px;
  background: #e6f1fb;
  transition: background-color 0.16s ease, border-color 0.16s ease;
}

.master-capability-card:hover {
  border-color: #9ac5ef;
  background: #d9ebf8;
}

.master-capability-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.master-capability-info {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 12px;
}

.master-capability-info strong {
  display: block;
  overflow: hidden;
  color: #0c447c;
  font-size: 13px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.master-capability-info span {
  display: block;
  overflow: hidden;
  margin-top: 5px;
  color: #185fa5;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.master-power-icon {
  position: relative;
  flex: 0 0 28px;
  width: 28px;
  height: 28px;
  border-radius: 7px;
  background: #378add;
}

.master-power-icon::before {
  position: absolute;
  top: 8px;
  left: 8px;
  width: 10px;
  height: 10px;
  border: 2px solid #ffffff;
  border-top-color: transparent;
  border-radius: 999px;
  content: '';
}

.master-power-icon::after {
  position: absolute;
  top: 6px;
  left: 13px;
  width: 2px;
  height: 9px;
  border-radius: 999px;
  background: #ffffff;
  content: '';
}

.master-switch-control {
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 9px;
}

.master-switch-control span {
  min-width: 40px;
  color: #378add;
  font-size: 12px;
  font-weight: 800;
  text-align: right;
}

.master-switch :deep(.el-switch__core) {
  min-width: 32px;
  height: 18px;
}

.master-switch :deep(.el-switch__action) {
  width: 14px;
  height: 14px;
}

.master-capability-connector {
  display: block;
  width: 1px;
  height: 10px;
  margin: 10px 0 -8px 13px;
  border-left: 1px dashed #85b7eb;
}

.capability-disabled-alert {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  border-radius: 8px;
  padding: 8px 12px;
  background: #faeeda;
  color: #854f0b;
  font-size: 12px;
  font-weight: 800;
}

.alert-triangle-icon {
  position: relative;
  flex: 0 0 14px;
  width: 14px;
  height: 14px;
}

.alert-triangle-icon::before {
  position: absolute;
  left: 1px;
  top: 2px;
  width: 10px;
  height: 10px;
  border-left: 2px solid currentColor;
  border-top: 2px solid currentColor;
  transform: rotate(45deg);
  content: '';
}

.alert-triangle-icon::after {
  position: absolute;
  left: 6px;
  top: 5px;
  width: 2px;
  height: 6px;
  border-radius: 999px;
  background: currentColor;
  box-shadow: 0 8px 0 currentColor;
  content: '';
}

.capability-group {
  margin-top: 16px;
}

.capability-group-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.capability-group-strip {
  flex: 0 0 3px;
  width: 3px;
  height: 12px;
  border-radius: 999px;
}

.capability-group-strip.is-report {
  background: #7f77dd;
}

.capability-group-strip.is-content {
  background: #1d9e75;
}

.capability-group-strip.is-batch {
  background: #ba7517;
}

.capability-group-head strong {
  color: #111827;
  font-size: 13px;
  font-weight: 500;
}

.capability-group-head small {
  overflow: hidden;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.capability-card-grid {
  display: grid;
  gap: 10px;
}

.capability-card-grid.is-two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.capability-card-grid.is-single {
  grid-template-columns: 1fr;
}

.child-capability-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 72px;
  gap: 16px;
  border: 0.5px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px 14px;
  background: #ffffff;
  transition: opacity 0.16s ease, border-color 0.16s ease;
}

.child-capability-card.is-master-off {
  cursor: not-allowed;
  opacity: 0.5;
}

.child-capability-card.is-master-off :deep(.el-switch) {
  cursor: not-allowed;
  pointer-events: none;
}

.child-capability-info {
  min-width: 0;
}

.child-capability-info strong,
.degrade-panel-head strong {
  display: block;
  overflow: hidden;
  color: #111827;
  font-size: 13px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.child-capability-info span,
.degrade-panel-head span {
  display: block;
  margin-top: 5px;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.5;
}

.switch-control {
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 9px;
}

.switch-status {
  min-width: 28px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
  text-align: right;
}

.switch-status.is-off {
  color: #94a3b8;
}

.degrade-panel {
  margin-top: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 18px 20px 20px;
  background: #f8fafc;
}

.degrade-panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.platform-modal-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 24px;
  border-top: 1px solid #edf0f4;
  background: #f8fafc;
}

.platform-modal-footer span {
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
}

.platform-modal-footer b {
  color: #ef4444;
  font-weight: 900;
}

.platform-config-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.platform-config-form :deep(.el-form-item__label) {
  padding-bottom: 7px;
  color: #475569;
  font-size: 12px;
  font-weight: 800;
  line-height: 1.2;
}

.platform-config-form :deep(.el-select),
.platform-config-form :deep(.el-input-number) {
  width: 100%;
}

@media (max-width: 768px) {
  .platform-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-keyword,
  .filter-level,
  .filter-status,
  .platform-toolbar .el-button {
    width: 100%;
  }

  .form-grid.is-two,
  .form-grid.is-three,
  .basic-info-grid,
  .model-main-grid,
  .capability-card-grid.is-two {
    grid-template-columns: 1fr;
  }

  .model-low-field {
    grid-column: auto;
  }

  .platform-modal-header,
  .platform-modal-footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .modal-section-head {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .business-section-head {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .modal-section-head i {
    flex-basis: 100%;
  }

  .business-section-head i {
    flex-basis: 100%;
  }

  .master-capability-body,
  .child-capability-card {
    align-items: flex-start;
    flex-direction: column;
  }

  .master-switch-control,
  .child-capability-card .switch-control {
    justify-content: flex-end;
    width: 100%;
  }

  .platform-modal-footer > div {
    width: 100%;
  }

  .platform-modal-footer .el-button {
    width: 100%;
    margin-left: 0;
    margin-top: 8px;
  }
}
</style>
