<template>
  <div class="package-config-page admin-page">
    <div class="admin-page-header package-header">
      <div>
        <div class="admin-page-kicker">系统配置</div>
        <h1 class="admin-page-title">套餐配置</h1>
        <div class="admin-page-subtitle">维护套餐消耗积分、服务周期、拓词额度和渠道分发额度。</div>
      </div>
      <div class="admin-page-actions">
        <el-button type="primary" @click="openCreate">新增套餐</el-button>
      </div>
    </div>

    <el-card shadow="never" class="admin-surface package-toolbar-card">
      <div class="package-toolbar">
        <el-input v-model="query.keyword" class="filter-keyword" placeholder="搜索套餐类型/名称" clearable @keyup.enter="load" />
        <el-select v-model="query.enabled" class="filter-status" placeholder="状态" clearable @change="load">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
        <el-select v-model="query.audienceType" class="filter-audience" placeholder="适用对象" clearable @change="load">
          <el-option label="总部直营" value="internal" />
          <el-option label="合伙人" value="partner" />
        </el-select>
        <el-button type="primary" plain @click="load">查询</el-button>
      </div>
    </el-card>

    <div class="admin-metric-grid package-metric-grid">
      <div class="admin-metric-card" style="--metric-accent: #2563eb; --metric-tone: #eff6ff">
        <span class="admin-metric-label">套餐总数</span>
        <strong class="admin-metric-value">{{ page.total }}</strong>
        <span class="admin-metric-hint">当前筛选结果</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #059669; --metric-tone: #ecfdf5">
        <span class="admin-metric-label">启用套餐</span>
        <strong class="admin-metric-value">{{ enabledCount }}</strong>
        <span class="admin-metric-hint">当前页启用状态</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #7c3aed; --metric-tone: #f5f3ff">
        <span class="admin-metric-label">平均消耗积分</span>
        <strong class="admin-metric-value">{{ avgPriceText }}</strong>
        <span class="admin-metric-hint">按当前页消耗积分计算</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #f59e0b; --metric-tone: #fffbeb">
        <span class="admin-metric-label">平均周期</span>
        <strong class="admin-metric-value">{{ avgServiceMonths }}</strong>
        <span class="admin-metric-hint">服务月数均值</span>
      </div>
    </div>

    <el-card shadow="never" class="admin-table-card package-table-card">
      <div class="table-header">
        <div>
          <div class="table-title">套餐列表</div>
          <div class="table-subtitle">按消耗积分、服务周期、拓词额度和渠道额度核对套餐能力。</div>
        </div>
        <div class="chips">
          <span class="chip chip-muted">当前页 {{ rows.length }}</span>
          <span class="chip chip-success">启用 {{ enabledCount }}</span>
        </div>
      </div>

      <DataState :loading="loading" :empty="!loading && rows.length === 0" empty-text="暂无套餐配置">
        <el-table :data="rows" border table-layout="fixed">
          <el-table-column label="套餐" min-width="240" show-overflow-tooltip>
            <template #default="scope">
              <div class="admin-entity-cell">
                <div class="admin-entity-avatar package-avatar" :class="scope.row.enabled ? 'is-success' : 'is-muted'">
                  {{ packageInitial(scope.row.packageName) }}
                </div>
                <div class="min-w-0">
                  <div class="admin-entity-main">{{ scope.row.packageName }}</div>
                  <div class="admin-entity-sub">{{ scope.row.packageType }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="消耗积分" width="120">
            <template #default="scope">{{ Number(scope.row.standardPrice || 0).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="适用对象" width="110">
            <template #default="scope">
              <el-tag size="small" :type="scope.row.audienceType === 'partner' ? 'warning' : 'info'">
                {{ audienceLabel(scope.row.audienceType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="serviceMonths" label="服务月数" width="100" />
          <el-table-column label="拓词问题额度" min-width="190">
            <template #default="scope">
              {{ scope.row.keywordGroupLimit }}（A {{ scope.row.keywordGroupLimitA ?? scope.row.keywordGroupLimit }} / B {{ scope.row.keywordGroupLimitB ?? 0 }} / C {{ scope.row.keywordGroupLimitC ?? 0 }}）
            </template>
          </el-table-column>
          <el-table-column label="渠道额度" min-width="260">
            <template #default="scope">{{ quotaSummary(scope.row.channelQuotaConfigs) }}</template>
          </el-table-column>
          <el-table-column prop="sortOrder" label="排序" width="80" />
          <el-table-column label="状态" width="90">
            <template #default="scope">
              <span class="admin-status-tag" :class="packageStatusClass(scope.row)">
                {{ packageStatusLabel(scope.row) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="openEdit(scope.row)">{{ canEditPackage(scope.row) ? '编辑' : '查看' }}</el-button>
              <el-button link :type="scope.row.packageStatus === 'active' ? 'warning' : 'success'" @click="toggleStatus(scope.row)">
                {{ statusActionLabel(scope.row) }}
              </el-button>
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

    <el-dialog v-model="formVisible" :title="formMode === 'create' ? '新增套餐' : '编辑套餐'" width="960px" class="admin-editor-dialog package-editor-dialog">
      <el-form ref="formRef" :model="form" :rules="rules" :disabled="!formEditable" label-position="top" class="package-config-form">
        <section class="form-section">
          <div class="form-section-head">
            <div>
              <span>基础信息</span>
              <strong>套餐标识与名称</strong>
            </div>
          </div>
          <div class="form-grid is-two">
            <el-form-item label="套餐类型" prop="packageType" required>
              <el-input
                v-model="form.packageType"
                :disabled="formMode === 'edit'"
                maxlength="32"
                placeholder="如: partner_a，不能使用大写字母"
                @blur="trimPackageTypeInput"
              />
              <div class="form-tip">套餐类型是系统唯一标识，仅支持小写字母、数字和下划线，需以小写字母开头，长度 3-32；例如 partner_a。</div>
            </el-form-item>
            <el-form-item label="套餐名称" prop="packageName" required>
              <el-input v-model="form.packageName" />
            </el-form-item>
            <el-form-item label="适用对象" prop="audienceType" required>
              <el-segmented
                v-model="form.audienceType"
                :options="[
                  { label: '总部直营', value: 'internal' },
                  { label: '合伙人', value: 'partner' },
                ]"
              />
            </el-form-item>
          </div>
        </section>

        <section class="form-section">
          <div class="form-section-head">
            <div>
              <span>积分与拓词额度</span>
              <strong>消耗积分、服务周期与 A/B/C 档问题数</strong>
            </div>
            <em :class="{ invalid: tierTotal !== form.keywordGroupLimit }">
              A/B/C 合计 {{ tierTotal }} / 总数 {{ form.keywordGroupLimit }}
            </em>
          </div>
          <div class="form-grid is-three">
            <el-form-item label="消耗积分" prop="standardPrice" required>
              <el-input-number v-model="form.standardPrice" :min="0.01" :precision="2" />
            </el-form-item>
            <el-form-item label="服务月数" prop="serviceMonths" required>
              <el-input-number v-model="form.serviceMonths" :min="1" />
            </el-form-item>
            <el-form-item label="拓词问题总数" prop="keywordGroupLimit" required>
              <el-input-number v-model="form.keywordGroupLimit" :min="1" />
            </el-form-item>
            <el-form-item label="A档问题数" prop="keywordGroupLimitA" required>
              <el-input-number v-model="form.keywordGroupLimitA" :min="0" />
            </el-form-item>
            <el-form-item label="B档问题数" prop="keywordGroupLimitB" required>
              <el-input-number v-model="form.keywordGroupLimitB" :min="0" />
            </el-form-item>
            <el-form-item label="C档问题数" prop="keywordGroupLimitC" required>
              <el-input-number v-model="form.keywordGroupLimitC" :min="0" />
            </el-form-item>
          </div>
        </section>

        <section class="form-section">
          <div class="form-section-head">
            <div>
              <span>分发渠道额度</span>
              <strong>{{ form.audienceType === 'partner' ? '合伙人侧可显示渠道与总部隐藏渠道' : '官网、行业站、平台网站、各自媒体平台和权重媒体额度' }}</strong>
            </div>
            <em v-if="form.audienceType === 'partner'">
              可见 {{ partnerVisibleChannelQuotaConfigs.length }} / 隐藏 {{ partnerHiddenChannelQuotaConfigs.length }}
            </em>
          </div>
          <template v-if="form.audienceType === 'partner'">
            <div class="quota-visibility-layout">
              <div class="quota-visibility-block is-visible">
                <div class="quota-block-head">
                  <div>
                    <strong>合伙人侧可显示渠道</strong>
                    <span>会在合伙人套餐、客户详情和项目额度中展示，用于合伙人理解可交付范围。</span>
                  </div>
                  <el-tag type="success" size="small">{{ partnerVisibleChannelQuotaConfigs.length }} 个渠道</el-tag>
                </div>
                <el-table class="quota-editor-table is-embedded" :data="partnerVisibleChannelQuotaConfigs" border table-layout="fixed">
                  <el-table-column label="渠道" min-width="180">
                    <template #default="scope">{{ channelLabel(scope.row.channelCode) }}</template>
                  </el-table-column>
                  <el-table-column label="周期" min-width="160">
                    <template #default="scope">
                      <el-select v-model="scope.row.periodType" :disabled="scope.row.channelCode === 'authority_media'">
                        <el-option v-for="item in periodOptions(scope.row.channelCode)" :key="item.value" :label="item.label" :value="item.value" />
                      </el-select>
                    </template>
                  </el-table-column>
                  <el-table-column label="额度" min-width="160">
                    <template #default="scope">
                      <el-input-number v-model="scope.row.quotaLimit" :min="0" />
                    </template>
                  </el-table-column>
                  <el-table-column label="启用" width="100">
                    <template #default="scope"><el-switch v-model="scope.row.enabled" /></template>
                  </el-table-column>
                </el-table>
              </div>

              <div class="quota-visibility-block is-hidden">
                <div class="quota-block-head">
                  <div>
                    <strong>总部隐藏渠道</strong>
                    <span>仅供总部交付配置和内部消耗使用，合伙人侧不展示具体渠道名称与额度。</span>
                  </div>
                  <el-tag type="warning" size="small">{{ partnerHiddenChannelQuotaConfigs.length }} 个渠道</el-tag>
                </div>
                <el-table class="quota-editor-table is-embedded" :data="partnerHiddenChannelQuotaConfigs" border table-layout="fixed">
                  <el-table-column label="渠道" min-width="180">
                    <template #default="scope">{{ channelLabel(scope.row.channelCode) }}</template>
                  </el-table-column>
                  <el-table-column label="周期" min-width="160">
                    <template #default="scope">
                      <el-select v-model="scope.row.periodType" :disabled="scope.row.channelCode === 'authority_media'">
                        <el-option v-for="item in periodOptions(scope.row.channelCode)" :key="item.value" :label="item.label" :value="item.value" />
                      </el-select>
                    </template>
                  </el-table-column>
                  <el-table-column label="额度" min-width="160">
                    <template #default="scope">
                      <el-input-number v-model="scope.row.quotaLimit" :min="0" />
                    </template>
                  </el-table-column>
                  <el-table-column label="启用" width="100">
                    <template #default="scope"><el-switch v-model="scope.row.enabled" /></template>
                  </el-table-column>
                </el-table>
              </div>
            </div>
          </template>
          <template v-else>
            <el-table class="quota-editor-table" :data="baseChannelQuotaConfigs" border table-layout="fixed">
              <el-table-column label="渠道" min-width="180">
                <template #default="scope">{{ channelLabel(scope.row.channelCode) }}</template>
              </el-table-column>
              <el-table-column label="周期" min-width="160">
                <template #default="scope">
                  <el-select v-model="scope.row.periodType" :disabled="scope.row.channelCode === 'authority_media'">
                    <el-option v-for="item in periodOptions(scope.row.channelCode)" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="额度" min-width="160">
                <template #default="scope">
                  <el-input-number v-model="scope.row.quotaLimit" :min="0" />
                </template>
              </el-table-column>
              <el-table-column label="启用" width="100">
                <template #default="scope"><el-switch v-model="scope.row.enabled" /></template>
              </el-table-column>
            </el-table>
            <el-collapse v-if="selfMediaChannelQuotaConfigs.length" class="quota-channel-groups" model-value="self_media">
              <el-collapse-item name="self_media">
                <template #title>
                  <div class="quota-group-title">
                    <span>自媒体平台</span>
                    <el-tag size="small" type="info">{{ selfMediaChannelQuotaConfigs.length }} 个平台</el-tag>
                  </div>
                </template>
                <el-table class="quota-editor-table" :data="selfMediaChannelQuotaConfigs" border table-layout="fixed">
                  <el-table-column label="平台" min-width="180">
                    <template #default="scope">{{ channelLabel(scope.row.channelCode) }}</template>
                  </el-table-column>
                  <el-table-column label="周期" min-width="160">
                    <template #default="scope">
                      <el-select v-model="scope.row.periodType">
                        <el-option v-for="item in periodOptions(scope.row.channelCode)" :key="item.value" :label="item.label" :value="item.value" />
                      </el-select>
                    </template>
                  </el-table-column>
                  <el-table-column label="额度" min-width="160">
                    <template #default="scope">
                      <el-input-number v-model="scope.row.quotaLimit" :min="0" />
                    </template>
                  </el-table-column>
                  <el-table-column label="启用" width="100">
                    <template #default="scope"><el-switch v-model="scope.row.enabled" /></template>
                  </el-table-column>
                </el-table>
              </el-collapse-item>
            </el-collapse>
          </template>
        </section>

        <section class="form-section">
          <div class="form-section-head">
            <div>
              <span>发布状态</span>
              <strong>排序与备注</strong>
            </div>
          </div>
          <div class="form-grid is-two">
            <el-form-item label="排序" prop="sortOrder" required>
              <el-input-number v-model="form.sortOrder" :min="0" />
            </el-form-item>
            <el-form-item label="备注" class="is-full">
              <el-input v-model="form.remark" type="textarea" :rows="3" />
            </el-form-item>
          </div>
        </section>
      </el-form>

      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button v-if="formEditable" type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createPackagePlan,
  getAdminPackagePlans,
  updatePackagePlan,
  updatePackagePlanStatus,
} from '@/api/packagePlan'
import type { PackageChannelQuotaConfig, PackagePlan } from '@/types'
import DataState from '@/components/ui/DataState.vue'
import { DISTRIBUTION_CHANNELS, distributionChannelLabel, isSelfMediaQuotaChannel } from '@/constants/distributionChannels'

const loading = ref(false)
const saving = ref(false)
const rows = ref<PackagePlan[]>([])
const formVisible = ref(false)
const formRef = ref<FormInstance>()
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const editingPackageStatus = ref<'draft' | 'active' | 'inactive' | string>('draft')
const page = reactive({ current: 1, size: 20, total: 0 })
const query = reactive({
  keyword: '',
  enabled: undefined as boolean | undefined,
  audienceType: undefined as 'internal' | 'partner' | undefined,
})
const PARTNER_VISIBLE_SELF_MEDIA_PLATFORMS = new Set(['wechat', 'douyin', 'toutiao', 'zhihu', 'baijiahao', 'xiaohongshu'])

const form = reactive({
  packageType: '',
  packageName: '',
  audienceType: 'internal' as 'internal' | 'partner',
  standardPrice: 0,
  partnerPoints: 0,
  serviceMonths: 12,
  keywordGroupLimit: 100,
  keywordGroupLimitA: 100,
  keywordGroupLimitB: 0,
  keywordGroupLimitC: 0,
  monthlyReportDepth: 'L2',
  quarterlyReportDepth: 'L2',
  consultantIntensity: 'L2',
  competitorInsightDepth: 'L2',
  mediaDistributionIntensity: 'L2',
  commitmentTargetIntensity: 'L2',
  targetMetricType: 'visibility_rate',
  targetMetricValue: 0.05,
  targetWindowDays: 90,
  sortOrder: 10,
  remark: '',
  channelQuotaConfigs: defaultChannelQuotas() as PackageChannelQuotaConfig[],
})

const PACKAGE_TYPE_PATTERN = /^[a-z][a-z0-9_]{2,31}$/

const rules: FormRules = {
  packageType: [
    { required: true, message: '请输入套餐类型', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!PACKAGE_TYPE_PATTERN.test(String(value || '').trim())) {
          callback(new Error('套餐类型仅支持小写字母、数字和下划线，需以小写字母开头，长度 3-32'))
          return
        }
        callback()
      },
      trigger: ['blur', 'change'],
    },
  ],
  packageName: [{ required: true, message: '请输入套餐名称', trigger: 'blur' }],
  audienceType: [{ required: true, message: '请选择适用对象', trigger: 'change' }],
  standardPrice: [{ required: true, message: '请输入消耗积分', trigger: 'change' }],
  serviceMonths: [{ required: true, message: '请输入服务月数', trigger: 'change' }],
  keywordGroupLimit: [{ required: true, message: '请输入拓词问题总数', trigger: 'change' }],
  keywordGroupLimitA: [{ required: true, message: '请输入 A 档问题数', trigger: 'change' }],
  keywordGroupLimitB: [{ required: true, message: '请输入 B 档问题数', trigger: 'change' }],
  keywordGroupLimitC: [{ required: true, message: '请输入 C 档问题数', trigger: 'change' }],
}

const tierTotal = computed(() => Number(form.keywordGroupLimitA || 0) + Number(form.keywordGroupLimitB || 0) + Number(form.keywordGroupLimitC || 0))
const enabledCount = computed(() => rows.value.filter((item) => item.enabled).length)
const avgPriceText = computed(() => {
  if (!rows.value.length) return '-'
  const avg = rows.value.reduce((sum, item) => sum + Number(item.standardPrice || 0), 0) / rows.value.length
  return avg.toFixed(0)
})
const avgServiceMonths = computed(() => {
  if (!rows.value.length) return '-'
  const avg = rows.value.reduce((sum, item) => sum + Number(item.serviceMonths || 0), 0) / rows.value.length
  return `${Math.round(avg)} 月`
})
const baseChannelQuotaConfigs = computed(() => form.channelQuotaConfigs.filter((item) => !isSelfMediaQuotaChannel(item.channelCode)))
const selfMediaChannelQuotaConfigs = computed(() => form.channelQuotaConfigs.filter((item) => isSelfMediaQuotaChannel(item.channelCode)))
const partnerVisibleChannelQuotaConfigs = computed(() => form.channelQuotaConfigs.filter((item) => isPartnerVisibleQuotaChannel(item.channelCode)))
const partnerHiddenChannelQuotaConfigs = computed(() => form.channelQuotaConfigs.filter((item) => !isPartnerVisibleQuotaChannel(item.channelCode)))
const formEditable = computed(() => formMode.value === 'create' || editingPackageStatus.value === 'draft')

function defaultChannelQuotas(): PackageChannelQuotaConfig[] {
  return DISTRIBUTION_CHANNELS.map((item) => ({
    channelCode: item.code,
    periodType: item.periodType,
    quotaLimit: item.quotaLimit,
    enabled: item.enabled,
  }))
}

function channelLabel(code: string) {
  return distributionChannelLabel(code)
}

function trimPackageTypeInput() {
  form.packageType = form.packageType.trim()
}

function isPartnerVisibleQuotaChannel(code?: string | null) {
  if (code === 'official_site') return true
  if (!code || !isSelfMediaQuotaChannel(code)) return false
  const platform = code.slice('self_media:'.length)
  return PARTNER_VISIBLE_SELF_MEDIA_PLATFORMS.has(platform)
}

function periodOptions(channelCode: string) {
  if (channelCode === 'authority_media') {
    return [{ label: '总额度', value: 'total' }]
  }
  return [
    { label: '日', value: 'day' },
    { label: '周', value: 'week' },
    { label: '月', value: 'month' },
  ]
}

function periodLabel(value: string) {
  return periodOptions(value === 'total' ? 'authority_media' : '').find((item) => item.value === value)?.label || value
}

function quotaSummary(configs?: PackageChannelQuotaConfig[]) {
  const list = configs?.length ? configs : []
  if (!list.length) return '-'
  const base = list
    .filter((item) => !isSelfMediaQuotaChannel(item.channelCode))
    .map((item) => `${channelLabel(item.channelCode)} ${item.quotaLimit}/${periodLabel(item.periodType)}`)
  const selfMedia = list.filter((item) => isSelfMediaQuotaChannel(item.channelCode))
  if (selfMedia.length) {
    const enabled = selfMedia.filter((item) => item.enabled).length
    const total = selfMedia.reduce((sum, item) => sum + Number(item.quotaLimit || 0), 0)
    base.push(`自媒体平台 ${total}（${enabled}/${selfMedia.length} 平台）`)
  }
  return base.join('；')
}

function packageInitial(value?: string | null) {
  const text = String(value || '').trim()
  return text ? Array.from(text)[0] : '套'
}

function audienceLabel(value?: string | null) {
  return value === 'partner' ? '合伙人' : '总部直营'
}

function canEditPackage(row: PackagePlan) {
  return row.packageStatus === 'draft'
}

function packageStatusLabel(row: PackagePlan) {
  if (row.packageStatus === 'draft') return '草稿'
  if (row.packageStatus === 'active') return '已上架'
  if (row.packageStatus === 'inactive') return '已停用'
  return row.enabled ? '已上架' : '已停用'
}

function packageStatusClass(row: PackagePlan) {
  if (row.packageStatus === 'active') return 'is-success'
  if (row.packageStatus === 'draft') return 'is-warning'
  return 'is-muted'
}

function statusActionLabel(row: PackagePlan) {
  if (row.packageStatus === 'draft') return '上架'
  return row.packageStatus === 'active' ? '停用' : '启用'
}

function normalizeChannelQuotas(configs?: PackageChannelQuotaConfig[]) {
  const existing = new Map((configs || []).map((item) => [item.channelCode, item]))
  return defaultChannelQuotas().map((item) => ({
    ...item,
    ...(existing.get(item.channelCode) || {}),
    periodType: item.channelCode === 'authority_media' ? 'total' : (existing.get(item.channelCode)?.periodType || item.periodType),
  }))
}

function resetForm() {
  form.packageType = ''
  form.packageName = ''
  form.audienceType = 'internal'
  form.standardPrice = 0
  form.partnerPoints = 0
  form.serviceMonths = 12
  form.keywordGroupLimit = 100
  form.keywordGroupLimitA = 100
  form.keywordGroupLimitB = 0
  form.keywordGroupLimitC = 0
  form.sortOrder = 10
  form.remark = ''
  form.channelQuotaConfigs = defaultChannelQuotas()
}

async function load() {
  loading.value = true
  try {
    const { data } = await getAdminPackagePlans({
      current: page.current,
      size: page.size,
      keyword: query.keyword || undefined,
      enabled: query.enabled,
      audienceType: query.audienceType,
    })
    rows.value = data.data.records || []
    page.total = data.data.total || 0
  } finally {
    loading.value = false
  }
}

function onPageChange(v: number) {
  page.current = v
  load()
}

function openCreate() {
  formMode.value = 'create'
  editingId.value = null
  editingPackageStatus.value = 'draft'
  resetForm()
  formVisible.value = true
}

function openEdit(row: PackagePlan) {
  formMode.value = 'edit'
  editingId.value = row.id
  editingPackageStatus.value = row.packageStatus || (row.enabled ? 'active' : 'inactive')
  form.packageType = row.packageType
  form.packageName = row.packageName
  form.audienceType = row.audienceType === 'partner' ? 'partner' : 'internal'
  form.standardPrice = Number(row.standardPrice || 0)
  form.partnerPoints = Number(row.partnerPoints || 0)
  form.serviceMonths = row.serviceMonths
  form.keywordGroupLimit = row.keywordGroupLimit
  form.keywordGroupLimitA = row.keywordGroupLimitA ?? row.keywordGroupLimit
  form.keywordGroupLimitB = row.keywordGroupLimitB ?? 0
  form.keywordGroupLimitC = row.keywordGroupLimitC ?? 0
  form.monthlyReportDepth = row.monthlyReportDepth
  form.quarterlyReportDepth = row.quarterlyReportDepth
  form.consultantIntensity = row.consultantIntensity
  form.competitorInsightDepth = row.competitorInsightDepth
  form.mediaDistributionIntensity = row.mediaDistributionIntensity
  form.commitmentTargetIntensity = row.commitmentTargetIntensity
  form.targetMetricType = row.targetMetricType
  form.targetMetricValue = row.targetMetricValue
  form.targetWindowDays = row.targetWindowDays
  form.sortOrder = row.sortOrder
  form.remark = row.remark || ''
  form.channelQuotaConfigs = normalizeChannelQuotas(row.channelQuotaConfigs)
  formVisible.value = true
}

function buildPayload() {
  const consumePoints = Number(form.standardPrice || 0)
  return {
    packageType: form.packageType.trim().toLowerCase(),
    packageName: form.packageName,
    audienceType: form.audienceType,
    standardPrice: consumePoints,
    partnerPoints: form.audienceType === 'partner' ? consumePoints : null,
    serviceMonths: form.serviceMonths,
    keywordGroupLimit: form.keywordGroupLimit,
    keywordGroupLimitA: form.keywordGroupLimitA,
    keywordGroupLimitB: form.keywordGroupLimitB,
    keywordGroupLimitC: form.keywordGroupLimitC,
    monthlyReportDepth: form.monthlyReportDepth,
    quarterlyReportDepth: form.quarterlyReportDepth,
    consultantIntensity: form.consultantIntensity,
    competitorInsightDepth: form.competitorInsightDepth,
    mediaDistributionIntensity: form.mediaDistributionIntensity,
    commitmentTargetIntensity: form.commitmentTargetIntensity,
    targetMetricType: form.targetMetricType,
    targetMetricValue: form.targetMetricValue,
    targetWindowDays: form.targetWindowDays,
    sortOrder: form.sortOrder,
    remark: form.remark || undefined,
    channelQuotaConfigs: form.channelQuotaConfigs.map((item) => ({
      channelCode: item.channelCode,
      periodType: item.channelCode === 'authority_media' ? 'total' : item.periodType,
      quotaLimit: item.quotaLimit,
      enabled: item.enabled,
    })),
  }
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    if (!PACKAGE_TYPE_PATTERN.test(form.packageType.trim())) {
      ElMessage.warning('请检查套餐类型：只能使用小写字母、数字和下划线，并以小写字母开头')
    }
    return
  }
  if (tierTotal.value !== Number(form.keywordGroupLimit || 0)) {
    ElMessage.warning('A/B/C 问题数合计必须等于拓词问题总数')
    return
  }
  saving.value = true
  try {
    const payload = buildPayload()
    if (formMode.value === 'create') {
      await createPackagePlan(payload)
    } else if (editingId.value) {
      await updatePackagePlan(editingId.value, payload)
    }
    ElMessage.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: PackagePlan) {
  const target = row.packageStatus !== 'active'
  await ElMessageBox.confirm(`确认${statusActionLabel(row)}套餐「${row.packageName}」？`, '状态变更确认', {
    type: 'warning',
    confirmButtonText: '确认',
    cancelButtonText: '取消',
  })
  await updatePackagePlanStatus(row.id, target)
  ElMessage.success('状态已更新')
  await load()
}

onMounted(load)
</script>

<style scoped>
.package-header {
  align-items: center;
}

.package-toolbar-card :deep(.el-card__body) {
  padding: 12px;
}

.package-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.filter-keyword {
  width: 240px;
}

.filter-status {
  width: 130px;
}

.filter-audience {
  width: 140px;
}

.package-metric-grid {
  margin-bottom: 0;
}

.package-table-card :deep(.el-card__body) {
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

.package-avatar.is-success {
  background: linear-gradient(135deg, #059669, #14b8a6);
}

.package-avatar.is-muted {
  background: linear-gradient(135deg, #64748b, #94a3b8);
}

.package-editor-dialog :deep(.el-dialog__body) {
  background: #f8fafc;
}

.package-config-form {
  display: grid;
  gap: 14px;
}

.form-section {
  overflow: hidden;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 10px 22px rgba(15, 23, 42, 0.04);
}

.form-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 13px 16px 11px;
  border-bottom: 1px solid #e7edf5;
  background: linear-gradient(90deg, #f8fbff 0%, #ffffff 62%, #f0fdf4 100%);
}

.form-section-head span {
  display: block;
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
}

.form-section-head strong {
  display: block;
  margin-top: 4px;
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
}

.form-section-head em {
  color: #047857;
  font-size: 12px;
  font-style: normal;
  font-weight: 800;
  text-align: right;
}

.form-section-head em.invalid {
  color: #b45309;
}

.form-grid {
  display: grid;
  gap: 13px 14px;
  padding: 16px;
}

.form-grid.is-two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.form-grid.is-three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.form-grid .is-full {
  grid-column: 1 / -1;
}

.form-tip {
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
}

.quota-channel-groups {
  margin-top: 12px;
}

.quota-visibility-layout {
  display: grid;
  gap: 14px;
  padding: 16px;
}

.quota-visibility-block {
  overflow: hidden;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: #ffffff;
}

.quota-visibility-block.is-visible {
  border-color: #bbf7d0;
  background: linear-gradient(180deg, #f0fdf4 0%, #ffffff 118px);
}

.quota-visibility-block.is-hidden {
  border-color: #fed7aa;
  background: linear-gradient(180deg, #fff7ed 0%, #ffffff 118px);
}

.quota-block-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px 12px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.22);
}

.quota-block-head strong {
  display: block;
  color: #0f172a;
  font-size: 14px;
  font-weight: 800;
}

.quota-block-head span {
  display: block;
  margin-top: 5px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.55;
}

.quota-group-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.package-config-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.package-config-form :deep(.el-form-item__label) {
  padding-bottom: 7px;
  color: #475569;
  font-size: 12px;
  font-weight: 800;
  line-height: 1.2;
}

.package-config-form :deep(.el-input-number),
.package-config-form :deep(.el-select) {
  width: 100%;
}

.quota-editor-table {
  margin: 16px;
  width: calc(100% - 32px);
}

.quota-editor-table.is-embedded {
  margin: 0;
  width: 100%;
  border-right: 0;
  border-left: 0;
}

.quota-editor-table :deep(.el-select),
.quota-editor-table :deep(.el-input-number) {
  width: 100%;
}

@media (max-width: 768px) {
  .package-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-keyword,
  .filter-status,
  .filter-audience,
  .package-toolbar .el-button {
    width: 100%;
  }

  .form-grid.is-two,
  .form-grid.is-three {
    grid-template-columns: 1fr;
  }

  .form-section-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .form-section-head em {
    text-align: left;
  }
}
</style>
