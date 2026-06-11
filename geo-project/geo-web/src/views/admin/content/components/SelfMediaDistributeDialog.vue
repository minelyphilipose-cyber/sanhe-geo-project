<template>
  <el-dialog v-model="visible" title="自媒体分发" width="980px" class="media-distribute-dialog">
    <div class="media-distribute">
      <el-alert
        v-if="wechatCapability && (!wechatDistributionAvailable || wechatCapability.liveVerificationBlocked)"
        class="media-capability-alert"
        type="warning"
        :closable="false"
        show-icon
        :title="wechatCapability.description || '微信公众号能力审核中'"
      />
      <el-alert
        v-if="douyinCapability && (!douyinCapability.enabled || douyinCapability.liveVerificationBlocked)"
        class="media-capability-alert"
        type="warning"
        :closable="false"
        show-icon
        :title="douyinCapability.liveVerificationBlocked ? (douyinCapability.description || '抖音图文暂不可联调') : `抖音图文未开启：${douyinCapability.disabledReason || 'feature flag disabled'}`"
      />
      <div v-if="wechatCapability?.readinessChecks?.length" class="platform-readiness-panel">
        <div class="cover-picker-header">
          <span>公众号接入自检</span>
          <el-tag size="small" :type="wechatReadinessSummary.type">{{ wechatReadinessSummary.label }}</el-tag>
        </div>
        <div class="platform-readiness-list">
          <div v-for="item in wechatCapability.readinessChecks" :key="item.code" class="platform-readiness-item">
            <span class="platform-readiness-label">{{ item.label }}</span>
            <el-tag size="small" :type="readinessTagType(item.status)">{{ readinessStatusLabel(item.status) }}</el-tag>
            <span class="platform-readiness-message">{{ item.message }}</span>
          </div>
        </div>
      </div>
      <div v-if="douyinCapability?.readinessChecks?.length" class="platform-readiness-panel">
        <div class="cover-picker-header">
          <span>抖音接入自检</span>
          <el-tag size="small" :type="douyinReadinessSummary.type">{{ douyinReadinessSummary.label }}</el-tag>
        </div>
        <div class="platform-readiness-list">
          <div v-for="item in douyinCapability.readinessChecks" :key="item.code" class="platform-readiness-item">
            <span class="platform-readiness-label">{{ item.label }}</span>
            <el-tag size="small" :type="readinessTagType(item.status)">{{ readinessStatusLabel(item.status) }}</el-tag>
            <span class="platform-readiness-message">{{ item.message }}</span>
          </div>
        </div>
      </div>

      <div class="media-grid">
        <button
          class="media-platform"
          :class="{ active: selectedMediaPlatform === 'wechat_mp', disabled: !wechatDistributionAvailable }"
          type="button"
          @click="actions.handleWechatPlatformClick()"
        >
          <span class="wechat-mark">微</span>
          <span class="media-name">微信公众号</span>
          <el-tag size="small" :type="wechatStatusTagType">{{ wechatStatusLabel }}</el-tag>
        </button>
        <button
          class="media-platform"
          :class="{ active: selectedMediaPlatform === 'douyin', disabled: !douyinDistributionAvailable }"
          type="button"
          @click="actions.handleDouyinPlatformClick()"
        >
          <span class="douyin-mark">抖</span>
          <span class="media-name">抖音图文</span>
          <el-tag size="small" :type="douyinStatusTagType">{{ douyinStatusLabel }}</el-tag>
        </button>
        <button
          class="media-platform"
          :class="{ active: selectedMediaPlatform === 'toutiao', disabled: !toutiaoAccounts.length }"
          type="button"
          @click="actions.handleSemiAutoPlatformClick('toutiao')"
        >
          <span class="toutiao-mark">头</span>
          <span class="media-name">头条</span>
          <el-tag size="small" :type="actions.semiAutoStatusTagType(toutiaoAccounts)">{{ actions.semiAutoStatusLabel(toutiaoAccounts) }}</el-tag>
        </button>
        <button
          class="media-platform"
          :class="{ active: selectedMediaPlatform === 'baijiahao', disabled: !baijiahaoAccounts.length }"
          type="button"
          @click="actions.handleSemiAutoPlatformClick('baijiahao')"
        >
          <span class="baijiahao-mark">百</span>
          <span class="media-name">百家号</span>
          <el-tag size="small" :type="actions.semiAutoStatusTagType(baijiahaoAccounts)">{{ actions.semiAutoStatusLabel(baijiahaoAccounts) }}</el-tag>
        </button>
        <button
          class="media-platform"
          :class="{ active: selectedMediaPlatform === 'zhihu', disabled: !zhihuAccounts.length }"
          type="button"
          @click="actions.handleSemiAutoPlatformClick('zhihu')"
        >
          <span class="zhihu-mark">知</span>
          <span class="media-name">知乎</span>
          <el-tag size="small" :type="actions.semiAutoStatusTagType(zhihuAccounts)">{{ actions.semiAutoStatusLabel(zhihuAccounts) }}</el-tag>
        </button>
        <button
          class="media-platform"
          :class="{ active: selectedMediaPlatform === 'xiaohongshu', disabled: !xiaohongshuAccounts.length }"
          type="button"
          @click="actions.handleSemiAutoPlatformClick('xiaohongshu')"
        >
          <span class="xiaohongshu-mark">红</span>
          <span class="media-name">小红书</span>
          <el-tag size="small" :type="actions.semiAutoStatusTagType(xiaohongshuAccounts)">{{ actions.semiAutoStatusLabel(xiaohongshuAccounts) }}</el-tag>
        </button>
      </div>

      <el-alert
        v-if="selectedSelfMediaQuotaHint"
        class="media-quota-alert"
        type="info"
        :closable="false"
        show-icon
        :title="selectedSelfMediaQuotaHint"
      />

      <div v-if="localHelperHealth" class="helper-health-panel">
        <div class="helper-health-main">
          <strong>本地助手</strong>
          <span>{{ helperHealthSummary }}</span>
        </div>
        <div class="helper-health-meta">
          <el-tag size="small" :type="localHelperHealth.paired ? 'success' : 'warning'">
            {{ localHelperHealth.paired ? '已配对' : '未配对' }}
          </el-tag>
          <el-tag size="small" type="info">v{{ localHelperHealth.version || '未知' }}</el-tag>
          <el-tag v-if="localHelperHealth.schedulePoll?.inFlight" size="small" type="primary">轮询中</el-tag>
        </div>
      </div>

      <div v-if="currentPlatformAccounts.length" class="self-media-account-list">
        <div v-for="account in currentPlatformAccounts" :key="account.id" class="self-media-account-row">
          <div class="self-media-account-main">
            <div class="self-media-account-title">{{ account.accountName }}</div>
            <div class="self-media-account-meta">{{ account.platformAccountId }}</div>
          </div>
          <el-tag size="small" :type="actions.selfMediaAccountStatusTag(account)">
            {{ actions.selfMediaAccountStatusLabel(account) }}
          </el-tag>
          <el-tag v-if="actions.isSemiAutoPlatform(selectedMediaPlatform)" size="small" :type="actions.semiAutoCredentialTagType(account)">
            {{ actions.semiAutoCredentialLabel(account) }}
          </el-tag>
          <el-tag v-if="actions.isSemiAutoPlatform(selectedMediaPlatform)" size="small" :type="actions.environmentAccountTagType(account)">
            {{ actions.environmentAccountLabel(account) }}
          </el-tag>
          <el-button
            v-if="selectedMediaPlatform === 'wechat_mp' && account.status === 'active'"
            size="small"
            :loading="checkingSelfMediaAccountId === account.id"
            @click="actions.checkWechatAccount(account.id)"
          >
            检测登录
          </el-button>
          <el-button
            v-if="selectedMediaPlatform === 'wechat_mp' && account.status === 'active'"
            size="small"
            type="primary"
            @click="actions.startWechatDraft(account)"
          >
            保存草稿
          </el-button>
          <el-button
            v-if="selectedMediaPlatform === 'douyin' && account.status === 'active'"
            size="small"
            type="primary"
            @click="actions.startDouyinImageText(account)"
          >
            选择账号
          </el-button>
          <el-button
            v-if="actions.isSemiAutoPlatform(selectedMediaPlatform) && account.status === 'active'"
            size="small"
            :disabled="!!actions.environmentAccountOf(account)"
            @click="emit('brandConfig', mediaDistributeBrandId)"
          >
            {{ actions.environmentAccountOf(account) ? '已绑定环境' : '去品牌配置环境' }}
          </el-button>
          <el-button
            v-if="actions.isSemiAutoPlatform(selectedMediaPlatform) && account.status === 'active' && actions.environmentAccountOf(account) && !actions.canSubmitSemiAutoEnvironmentTask(account)"
            size="small"
            :loading="semiAutoLoginOpeningAccountId === account.id"
            @click="actions.openSemiAutoEnvironmentForLogin(account)"
          >
            打开环境登录
          </el-button>
          <el-button
            v-if="actions.isSemiAutoPlatform(selectedMediaPlatform) && account.status === 'active' && actions.environmentAccountOf(account)?.loginStatus === 'mismatch'"
            size="small"
            type="warning"
            plain
            :loading="environmentAccountResettingId === account.id"
            @click="actions.resetEnvironmentAccountIdentity(account)"
          >
            重置账号校验
          </el-button>
          <el-button
            v-if="actions.isSemiAutoPlatform(selectedMediaPlatform) && account.status === 'active'"
            size="small"
            type="primary"
            :loading="actions.semiAutoAccountActionLoading(account)"
            :disabled="!actions.canSubmitSemiAutoEnvironmentTask(account)"
            @click="actions.submitSemiAutoEnvironmentTask(account)"
          >
            打开环境并填充
          </el-button>
        </div>
      </div>
      <el-empty
        v-else-if="actions.isSemiAutoPlatform(selectedMediaPlatform)"
        :description="`当前品牌暂无可用的${actions.semiAutoPlatformLabel(selectedMediaPlatform)}账号`"
      />

      <div v-if="selectedMediaPlatform === 'wechat_mp' && selectedSelfMediaAccountId" class="cover-picker">
        <div class="cover-picker-header">
          <span>选择公众号封面</span>
          <el-tag size="small" type="info">{{ imageMaterials.length }} 张图片</el-tag>
        </div>
        <div class="folder-toolbar">
          <el-radio-group v-model="folderScope" size="small" @change="actions.handleFolderScopeChange">
            <el-radio-button label="project">项目关联</el-radio-button>
            <el-radio-button label="all">品牌全部</el-radio-button>
          </el-radio-group>
        </div>
        <div v-if="displayImageFolders.length" class="folder-list">
          <button
            v-for="folder in displayImageFolders"
            :key="folder.id"
            type="button"
            class="folder-item"
            :class="{ selected: selectedImageFolderId === folder.id }"
            @click="actions.selectImageFolder(folder.id)"
          >
            <span>{{ folder.folderName }}</span>
            <el-tag v-if="folder.projectRelated" size="small" type="success">项目</el-tag>
            <el-tag size="small" type="info">{{ folder.materialCount || folder.materials.length }}</el-tag>
          </button>
        </div>
        <el-empty v-if="!imageMaterials.length" description="当前品牌暂无可用图片素材" />
        <div v-else class="cover-grid">
          <button
            v-for="material in imageMaterials"
            :key="material.id"
            type="button"
            class="cover-item"
            :class="{ selected: selectedCoverMaterialId === material.id }"
            @click="selectedCover = material.id"
          >
            <img :src="actions.materialThumbUrl(material)" :alt="material.fileName" loading="lazy" />
            <span>{{ material.fileName }}</span>
          </button>
        </div>
      </div>

      <div v-if="selectedMediaPlatform === 'douyin' && selectedSelfMediaAccountId" class="cover-picker">
        <div class="cover-picker-header">
          <span>选择抖音图文图片</span>
          <el-tag size="small" type="info">{{ selectedDouyinImageMaterialIds.length }}/30</el-tag>
        </div>
        <div class="folder-toolbar">
          <el-radio-group v-model="folderScope" size="small" @change="actions.handleFolderScopeChange">
            <el-radio-button label="project">项目关联</el-radio-button>
            <el-radio-button label="all">品牌全部</el-radio-button>
          </el-radio-group>
        </div>
        <div v-if="displayImageFolders.length" class="folder-list">
          <button
            v-for="folder in displayImageFolders"
            :key="folder.id"
            type="button"
            class="folder-item"
            :class="{ selected: selectedImageFolderId === folder.id }"
            @click="actions.selectImageFolder(folder.id)"
          >
            <span>{{ folder.folderName }}</span>
            <el-tag v-if="folder.projectRelated" size="small" type="success">项目</el-tag>
            <el-tag size="small" type="info">{{ folder.materialCount || folder.materials.length }}</el-tag>
          </button>
        </div>
        <el-empty v-if="!douyinImageMaterials.length" description="当前品牌暂无 JPG/PNG 图片素材" />
        <div v-else class="cover-grid">
          <button
            v-for="material in douyinImageMaterials"
            :key="material.id"
            type="button"
            class="cover-item"
            :class="{ selected: selectedDouyinImageMaterialIds.includes(material.id) }"
            @click="actions.toggleDouyinImage(material.id)"
          >
            <img :src="actions.materialThumbUrl(material)" :alt="material.fileName" loading="lazy" />
            <span>{{ material.fileName }}</span>
          </button>
        </div>
        <div v-if="selectedDouyinMaterials.length" class="douyin-selected-list">
          <div v-for="(material, index) in selectedDouyinMaterials" :key="material.id" class="douyin-selected-row">
            <span class="douyin-selected-index">{{ index + 1 }}</span>
            <span class="douyin-selected-name">{{ material.fileName }}</span>
            <el-button size="small" :disabled="index === 0" @click="actions.moveDouyinImage(index, -1)">上移</el-button>
            <el-button size="small" :disabled="index === selectedDouyinMaterials.length - 1" @click="actions.moveDouyinImage(index, 1)">下移</el-button>
          </div>
        </div>
        <div class="douyin-text-editor">
          <div class="cover-picker-header">
            <span>图文文案</span>
            <el-tag size="small" :type="douyinTextValue.length > 1000 ? 'danger' : 'info'">{{ douyinTextValue.length }}/1000</el-tag>
          </div>
          <el-input
            v-model="douyinTextValue"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            placeholder="可填写抖音图文文案；不填时后端使用文章标题"
          />
        </div>
      </div>

      <div v-if="distributionAttempts.length" class="distribution-history">
        <div class="cover-picker-header">
          <span>分发记录</span>
          <el-tag size="small" type="info">{{ distributionAttempts.length }} 条</el-tag>
        </div>
        <el-table class="distribution-history-table" :data="distributionAttempts" max-height="260">
          <el-table-column label="平台" min-width="180">
            <template #default="scope">
              <div class="distribution-target-cell">
                <span class="distribution-target-avatar">{{ actions.distributionPlatformInitial(scope.row.integrationMethod) }}</span>
                <span class="distribution-target-main">
                  <span class="distribution-target-title">{{ actions.distributionPlatformLabel(scope.row.integrationMethod) }}</span>
                  <span class="distribution-target-sub">{{ scope.row.siteName || scope.row.domain || `任务 #${scope.row.id}` }}</span>
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="任务状态" width="130">
            <template #default="scope">
              <el-tag size="small" :type="actions.distributionStatusTag(scope.row.status)">
                {{ actions.distributionTaskStatusLabel(scope.row) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="审核状态" width="120">
            <template #default="scope">
              <el-tag v-if="scope.row.reviewStatus" size="small" :type="actions.reviewStatusTag(scope.row.reviewStatus)">
                {{ actions.reviewStatusLabel(scope.row.reviewStatus) }}
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="平台反馈" min-width="230" show-overflow-tooltip>
            <template #default="scope">
              <div class="distribution-feedback">
                <span class="distribution-feedback-main">{{ scope.row.externalStatus || '暂无平台状态' }}</span>
                <span v-if="scope.row.errorMessage" class="distribution-feedback-error">{{ scope.row.errorMessage }}</span>
                <span v-else class="distribution-feedback-muted">未返回错误</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="210" align="center">
            <template #default="scope">
              <div class="admin-row-actions distribution-actions">
                <el-button
                  v-if="actions.canRefreshReviewStatus(scope.row)"
                  link
                  type="primary"
                  :loading="refreshingReviewTaskId === scope.row.id"
                  @click="actions.refreshReviewStatus(scope.row)"
                >
                  刷新
                </el-button>
                <el-button
                  v-if="actions.canOperateSemiAutoDistributionTask(scope.row)"
                  link
                  type="primary"
                  :loading="semiAutoConfirmingTaskId === scope.row.id"
                  @click="actions.confirmSemiAutoPublished(scope.row)"
                >
                  确认发布
                </el-button>
                <el-button
                  v-if="actions.canOperateSemiAutoDistributionTask(scope.row)"
                  link
                  type="danger"
                  :loading="semiAutoAbandoningTaskId === scope.row.id"
                  @click="actions.abandonSemiAutoPublished(scope.row)"
                >
                  放弃
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button
        v-if="selectedMediaPlatform === 'wechat_mp' && selectedSelfMediaAccountId"
        type="primary"
        :loading="selfMediaSubmitting"
        :disabled="!selectedCoverMaterialId"
        @click="actions.submitWechatDraft()"
      >
        保存至公众号草稿箱
      </el-button>
      <el-button
        v-if="selectedMediaPlatform === 'douyin' && selectedSelfMediaAccountId"
        type="primary"
        :loading="selfMediaSubmitting"
        :disabled="!selectedDouyinImageMaterialIds.length || douyinTextValue.length > 1000 || !douyinDistributionAvailable"
        @click="actions.submitDouyinImageText()"
      >
        {{ douyinSubmitButtonText }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { LocalHelperHealthResponse } from '@/api/localHelper'
import type { BrandImageFolder, BrandMaterial, DistributionTask, DouyinCapability, SelfMediaAccount, WechatMpCapability } from '@/types'

type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'
type MediaPlatform = 'wechat_mp' | 'douyin' | 'baijiahao' | 'toutiao' | 'zhihu' | 'xiaohongshu' | 'netease' | 'sohu'
type ActionMap = Record<string, (...args: any[]) => any>

const props = defineProps<{
  modelValue: boolean
  selectedMediaPlatform: MediaPlatform
  localHelperHealth: LocalHelperHealthResponse | null
  wechatCapability: WechatMpCapability | null
  wechatDistributionAvailable: boolean
  wechatStatusTagType: TagType
  wechatStatusLabel: string
  douyinCapability: DouyinCapability | null
  douyinDistributionAvailable: boolean
  douyinStatusTagType: TagType
  douyinStatusLabel: string
  toutiaoAccounts: SelfMediaAccount[]
  baijiahaoAccounts: SelfMediaAccount[]
  zhihuAccounts: SelfMediaAccount[]
  xiaohongshuAccounts: SelfMediaAccount[]
  currentPlatformAccounts: SelfMediaAccount[]
  selectedSelfMediaQuotaHint: string
  selectedSelfMediaAccountId: number | null
  checkingSelfMediaAccountId: number | null
  mediaDistributeBrandId: number | null
  semiAutoLoginOpeningAccountId: number | null
  environmentAccountResettingId: number | null
  imageMaterials: BrandMaterial[]
  imageFolderScope: 'project' | 'all'
  displayImageFolders: BrandImageFolder[]
  selectedImageFolderId: number | null
  selectedCoverMaterialId: number | null
  douyinImageMaterials: BrandMaterial[]
  selectedDouyinImageMaterialIds: number[]
  selectedDouyinMaterials: BrandMaterial[]
  douyinText: string
  distributionAttempts: DistributionTask[]
  refreshingReviewTaskId: number | null
  semiAutoConfirmingTaskId: number | null
  semiAutoAbandoningTaskId: number | null
  selfMediaSubmitting: boolean
  douyinSubmitButtonText: string
  actions: ActionMap
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'update:imageFolderScope': [value: 'project' | 'all']
  'update:selectedCoverMaterialId': [value: number | null]
  'update:douyinText': [value: string]
  brandConfig: [brandId: number | null]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const folderScope = computed({
  get: () => props.imageFolderScope,
  set: (value) => emit('update:imageFolderScope', value),
})

const selectedCover = computed({
  get: () => props.selectedCoverMaterialId,
  set: (value) => emit('update:selectedCoverMaterialId', value),
})

const douyinTextValue = computed({
  get: () => props.douyinText,
  set: (value) => emit('update:douyinText', value),
})

const wechatReadinessSummary = computed(() => readinessSummary(props.wechatCapability?.readinessChecks || []))
const douyinReadinessSummary = computed(() => readinessSummary(props.douyinCapability?.readinessChecks || []))
const helperHealthSummary = computed(() => {
  const health = props.localHelperHealth
  if (!health) return ''
  const pid = health.runtime?.pid ? `PID ${health.runtime.pid}` : 'PID 未知'
  const startedAt = health.runtime?.startedAt ? `启动 ${formatShortTime(health.runtime.startedAt)}` : '启动时间未知'
  const supervised = health.runtime?.supervised ? 'supervisor 托管' : '直接运行'
  return `${pid} · ${startedAt} · ${supervised}`
})

function readinessSummary(checks: Array<{ status?: string | null }>) {
  if (checks.some((item) => item.status === 'missing')) {
    return { type: 'danger' as const, label: '需补配置' }
  }
  if (checks.some((item) => item.status === 'warning')) {
    return { type: 'warning' as const, label: '待上线' }
  }
  return { type: 'success' as const, label: '已就绪' }
}

function readinessTagType(status?: string | null): TagType {
  if (status === 'ok') return 'success'
  if (status === 'missing') return 'danger'
  if (status === 'warning') return 'warning'
  return 'info'
}

function readinessStatusLabel(status?: string | null) {
  const map: Record<string, string> = {
    ok: '通过',
    warning: '待处理',
    missing: '缺失',
  }
  return status ? (map[status] || status) : '-'
}

function formatShortTime(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) return value
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}
</script>

<style scoped>
.media-distribute-dialog :deep(.el-dialog) {
  overflow: hidden;
  border-radius: 18px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.99), rgba(248, 251, 255, 0.98));
  box-shadow: 0 28px 72px rgba(15, 23, 42, 0.18);
}

.media-distribute-dialog :deep(.el-dialog__header) {
  margin: 0;
  padding: 20px 24px 14px;
  border-bottom: 1px solid #e2e8f0;
  background:
    linear-gradient(135deg, #ffffff, #eff6ff 62%, #ecfdf5);
}

.media-distribute-dialog :deep(.el-dialog__title) {
  color: #0f172a;
  font-size: 20px;
  font-weight: 800;
}

.media-distribute-dialog :deep(.el-dialog__body) {
  padding: 18px 20px 20px;
}

.media-distribute-dialog :deep(.el-dialog__footer) {
  padding: 14px 20px 18px;
  border-top: 1px solid #e2e8f0;
  background: #f8fafc;
}

.media-distribute {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.media-capability-alert {
  border: 1px solid #fed7aa;
  border-radius: 12px;
  background: #fff7ed;
}

.media-quota-alert {
  border: 1px solid #bfdbfe;
  border-radius: 12px;
  background: #eff6ff;
}

.helper-health-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  padding: 10px 12px;
  background: #f8fbff;
}

.helper-health-main {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.helper-health-main strong {
  color: #0f172a;
  font-size: 13px;
}

.helper-health-main span {
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.helper-health-meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.platform-readiness-panel {
  padding: 14px;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.05);
}

.platform-readiness-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.platform-readiness-item {
  display: grid;
  align-items: center;
  min-height: 44px;
  padding: 9px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
  grid-template-columns: minmax(92px, auto) auto minmax(0, 1fr);
  gap: 8px;
}

.platform-readiness-label {
  color: #0f172a;
  font-size: 13px;
  font-weight: 800;
}

.platform-readiness-message {
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.media-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.media-platform {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 128px;
  padding: 16px 12px;
  overflow: hidden;
  border: 1px solid #dbeafe;
  border-radius: 16px;
  background:
    linear-gradient(135deg, #ffffff 0%, #ffffff 68%, #f8fbff 100%);
  color: #0f172a;
  cursor: pointer;
  flex-direction: column;
  gap: 9px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.055);
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.media-platform::after {
  content: "";
  position: absolute;
  right: -34px;
  bottom: -42px;
  width: 110px;
  height: 110px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.06);
  pointer-events: none;
}

.media-platform:hover {
  transform: translateY(-2px);
  border-color: #93c5fd;
  box-shadow: 0 18px 40px rgba(37, 99, 235, 0.1);
}

.media-platform.active {
  border-color: #22c55e;
  background:
    linear-gradient(135deg, #ffffff 0%, #f0fdf4 100%);
  box-shadow:
    0 18px 42px rgba(34, 197, 94, 0.13),
    inset 0 0 0 1px rgba(34, 197, 94, 0.28);
}

.media-platform.disabled {
  cursor: not-allowed;
  background:
    linear-gradient(135deg, #ffffff, #f8fafc);
  color: #94a3b8;
  opacity: 0.78;
}

.wechat-mark,
.douyin-mark,
.toutiao-mark,
.baijiahao-mark,
.zhihu-mark,
.xiaohongshu-mark {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 16px;
  color: #fff;
  font-size: 23px;
  font-weight: 800;
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.12);
}

.wechat-mark {
  background: linear-gradient(135deg, #16a34a, #22c55e);
}

.douyin-mark {
  background: linear-gradient(135deg, #020617, #1e293b);
}

.toutiao-mark {
  background: linear-gradient(135deg, #dc2626, #ef4444);
}

.baijiahao-mark {
  background: linear-gradient(135deg, #1d4ed8, #2563eb);
}

.zhihu-mark {
  background: linear-gradient(135deg, #2563eb, #3b82f6);
}

.xiaohongshu-mark {
  background: linear-gradient(135deg, #be123c, #f43f5e);
}

.media-name {
  position: relative;
  z-index: 1;
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
}

.self-media-account-list {
  overflow: hidden;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.055);
}

.self-media-account-row {
  display: grid;
  align-items: center;
  min-height: 64px;
  padding: 12px 14px;
  border-bottom: 1px solid #e2e8f0;
  grid-template-columns: minmax(0, 1fr) auto auto auto auto;
  gap: 12px;
}

.self-media-account-row:last-child {
  border-bottom: 0;
}

.self-media-account-row:hover {
  background: #f8fbff;
}

.self-media-account-title {
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
}

.self-media-account-meta {
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
}

.cover-picker {
  border: 1px solid #dbeafe;
  border-radius: 14px;
  padding: 14px;
  background:
    linear-gradient(180deg, #ffffff, #f8fbff);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.05);
}

.cover-picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.folder-toolbar {
  display: flex;
  justify-content: flex-start;
  margin-bottom: 10px;
}

.folder-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.folder-item {
  min-height: 34px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  padding: 6px 10px;
  background: #fff;
  color: var(--el-text-color-primary);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}

.folder-item.selected {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary) inset;
}

.cover-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(132px, 1fr));
  gap: 10px;
  max-height: 260px;
  overflow: auto;
}

.cover-item {
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  padding: 6px;
  background: #fff;
  cursor: pointer;
  text-align: left;
}

.cover-item.selected {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary) inset;
}

.cover-item img {
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
  border-radius: 4px;
  background: #f2f3f5;
  display: block;
}

.cover-item span {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.douyin-selected-list {
  margin-top: 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
  background: #fff;
}

.douyin-selected-row {
  min-height: 44px;
  padding: 8px 10px;
  display: grid;
  grid-template-columns: 32px 1fr auto auto;
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid #ebeef5;
}

.douyin-selected-row:last-child {
  border-bottom: 0;
}

.douyin-selected-index {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #f2f3f5;
  color: var(--el-text-color-secondary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

.douyin-selected-name {
  min-width: 0;
  font-size: 13px;
  color: var(--el-text-color-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.douyin-text-editor,
.distribution-history {
  margin-top: 12px;
}

.distribution-history {
  padding: 14px;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background:
    linear-gradient(180deg, #ffffff, #f8fbff);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.055);
}

.distribution-history-table {
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}

.distribution-history-table :deep(.el-table__header th) {
  border-right-color: transparent;
  background: #eff6ff;
  color: #334155;
  font-weight: 800;
}

.distribution-history-table :deep(.el-table__body td) {
  border-right-color: transparent;
  border-bottom-color: #edf2f7;
}

.distribution-history-table :deep(.el-table__row:hover > td) {
  background: #f8fbff !important;
}

.distribution-target-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.distribution-target-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: linear-gradient(135deg, #2563eb, #06b6d4);
  color: #ffffff;
  font-weight: 800;
  flex-shrink: 0;
}

.distribution-target-main,
.distribution-feedback {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.distribution-target-title,
.distribution-feedback-main {
  overflow: hidden;
  color: #0f172a;
  font-size: 14px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.distribution-target-sub,
.distribution-feedback-muted {
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.distribution-feedback-error {
  overflow: hidden;
  color: #ef4444;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.distribution-actions {
  grid-template-columns: repeat(auto-fit, minmax(44px, 1fr));
}
</style>
