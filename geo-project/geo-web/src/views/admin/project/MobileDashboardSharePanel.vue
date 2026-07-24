<template>
  <section class="mobile-share-panel" v-loading="loading">
    <div class="section-header">
      <div class="panel-title">
        <div class="panel-title-icon">
          <el-icon><Link /></el-icon>
        </div>
        <div>
          <h2>移动看板分享链接</h2>
          <p>在微信内打开看板后，通过右上角“发送给朋友”生成品牌优先的项目卡片。</p>
        </div>
      </div>
      <el-tag round type="info">{{ shares.length }} 条</el-tag>
    </div>

    <el-alert
      v-if="createdShareUrl"
      type="success"
      :closable="true"
      class="mb-3"
      @close="createdShareUrl = ''"
    >
      <template #title>
        <div class="created-share">
          <span>新链接仅本次展示：</span>
          <a
            class="created-share-link"
            :href="createdShareUrl"
            target="_blank"
            rel="noopener noreferrer"
          >
            {{ shareDisplayName }}
          </a>
          <el-button size="small" type="success" plain @click="copyUrl(createdShareUrl)">复制链接</el-button>
          <el-button size="small" type="success" @click="openShareGuide(createdShareUrl)">扫码分享</el-button>
        </div>
      </template>
    </el-alert>

    <el-alert
      type="warning"
      :closable="false"
      class="mb-3"
      title="生成新链接会自动停用同项目旧 active 链接；停用后客户侧立即失效。"
    />

    <div class="share-workbench">
      <div class="share-steps" aria-label="微信卡片分享步骤">
        <span class="workbench-eyebrow">微信分享方式</span>
        <ol>
          <li><b>1</b><span>使用微信扫描二维码并打开看板</span></li>
          <li><b>2</b><span>等待“微信分享已就绪”提示</span></li>
          <li><b>3</b><span>点击右上角“···”发送给朋友</span></li>
        </ol>
        <p>直接粘贴链接不保证生成卡片；修改名称或卡片配置无需重新生成链接。</p>
      </div>

      <div class="share-card-preview" aria-label="微信分享卡片预览">
        <div class="preview-heading">
          <span class="workbench-eyebrow">卡片预览</span>
          <el-tag
            round
            size="small"
            :type="sharePreview.wechatJsSdkEnabled ? 'success' : 'warning'"
          >
            {{ sharePreview.wechatJsSdkEnabled ? '当前项目已灰度' : '当前项目未灰度' }}
          </el-tag>
        </div>
        <div class="wechat-card">
          <div class="wechat-card-copy">
            <strong>{{ shareDisplayName }}</strong>
            <p>{{ sharePreview.description }}</p>
            <span>手机数据看板</span>
          </div>
          <img :src="sharePreview.imageUrl" alt="手机数据看板分享封面" />
        </div>
      </div>
    </div>

    <div class="table-toolbar">
      <div>
        <h3>链接列表</h3>
        <p>仅 active 链接可访问；停用或过期链接可删除。</p>
      </div>
      <div class="panel-actions">
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button v-if="editable" type="primary" :loading="creating" @click="createShare">
          <el-icon><Plus /></el-icon>
          生成新链接
        </el-button>
      </div>
    </div>

    <el-table :data="shares" class="share-table" border empty-text="暂无分享链接">
      <el-table-column label="分享短码" width="128">
        <template #default="{ row }">
          <code class="token-prefix">{{ row.shareCode || '-' }}</code>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="96">
        <template #default="{ row }">
          <el-tag round :type="row.status === 'active' ? 'success' : 'info'">
            {{ row.status === 'active' ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="过期时间" min-width="170">
        <template #default="{ row }">{{ formatTime(row.expiresAt) }}</template>
      </el-table-column>
      <el-table-column label="访问摘要" min-width="220">
        <template #default="{ row }">
          <div class="access-summary">
            <span>总 {{ summaryOf(row.id)?.totalAccess || 0 }}</span>
            <span>失败 {{ summaryOf(row.id)?.failedAccess || 0 }}</span>
            <span>IP {{ summaryOf(row.id)?.distinctIpCount || 0 }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="最后访问" min-width="160">
        <template #default="{ row }">{{ formatTime(summaryOf(row.id)?.lastAccessAt || row.lastAccessAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button
              v-if="row.shareUrl"
              link
              type="primary"
              @click="copyUrl(row.shareUrl)"
            >
              复制链接
            </el-button>
            <el-button
              v-if="row.shareUrl && row.status === 'active'"
              link
              type="success"
              @click="openShareGuide(row.shareUrl)"
            >
              扫码分享
            </el-button>
            <el-popconfirm
              v-if="editable && row.status === 'active'"
              title="确认停用这条移动看板链接？"
              confirm-button-text="停用"
              cancel-button-text="取消"
              @confirm="disableShare(row.id)"
            >
              <template #reference>
                <el-button link type="danger" :loading="disablingId === row.id">停用</el-button>
              </template>
            </el-popconfirm>
            <el-popconfirm
              v-else-if="editable"
              title="确认删除这条无效链接？删除后不可恢复。"
              confirm-button-text="删除"
              cancel-button-text="取消"
              @confirm="deleteShare(row.id)"
            >
              <template #reference>
                <el-button link type="danger" :loading="deletingId === row.id">删除</el-button>
              </template>
            </el-popconfirm>
            <span v-else>-</span>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="shareGuideVisible"
      title="在微信中打开并分享"
      width="min(92vw, 680px)"
      destroy-on-close
    >
      <div class="share-dialog">
        <div class="qr-panel">
          <div class="qr-frame" v-loading="qrLoading">
            <img v-if="qrDataUrl" :src="qrDataUrl" alt="手机数据看板微信扫码二维码" />
          </div>
          <strong>微信扫码打开</strong>
          <p>二维码仅编码当前看板链接，不经过第三方二维码服务。</p>
        </div>
        <div class="dialog-instructions">
          <span class="workbench-eyebrow">打开后这样操作</span>
          <ol>
            <li>确认看板页面正常加载。</li>
            <li>等待页面提示“微信分享已就绪”。</li>
            <li>点击右上角“···”，选择“发送给朋友”。</li>
          </ol>
          <div class="dialog-card-title">
            <span>卡片标题</span>
            <strong>{{ shareDisplayName }}</strong>
          </div>
          <el-button type="primary" plain @click="copyUrl(selectedShareUrl)">复制备用链接</el-button>
        </div>
      </div>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Link, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
import { getProjectDetail } from '@/api/project'
import {
  createMobileDashboardShare,
  deleteMobileDashboardShare,
  disableMobileDashboardShare,
  getMobileDashboardShareAccessSummary,
  getMobileDashboardShares,
  getMobileDashboardWechatSharePreview,
} from '@/api/mobileDashboard'
import type {
  MobileDashboardShare,
  MobileDashboardShareAccessSummary,
  MobileDashboardWechatSharePreview,
} from '@/types/mobileDashboard'

const props = defineProps<{
  projectId: number
  editable: boolean
  customerName?: string
}>()

const loading = ref(false)
const creating = ref(false)
const disablingId = ref<number | null>(null)
const deletingId = ref<number | null>(null)
const shares = ref<MobileDashboardShare[]>([])
const summaries = ref<MobileDashboardShareAccessSummary[]>([])
const createdShareUrl = ref('')
const loadedCustomerName = ref('')
const shareGuideVisible = ref(false)
const qrLoading = ref(false)
const qrDataUrl = ref('')
const selectedShareUrl = ref('')
const sharePreview = ref<MobileDashboardWechatSharePreview>({
  title: '',
  description: '手机数据看板｜查看核心问题监测与内容数据',
  imageUrl: '/favicon.png',
  wechatJsSdkEnabled: false,
  rolloutMode: 'off',
})
const shareDisplayName = computed(() => (
  sharePreview.value.title?.trim()
  || props.customerName?.trim()
  || loadedCustomerName.value
  || '客户移动数据看板'
))

const summaryMap = computed(() => {
  const map = new Map<number, MobileDashboardShareAccessSummary>()
  for (const item of summaries.value) {
    map.set(item.shareId, item)
  }
  return map
})

function summaryOf(id: number) {
  return summaryMap.value.get(id)
}

function formatTime(value?: string | null) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

async function copyUrl(url: string) {
  if (!url) return
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('链接已复制。若需卡片分享，请先在微信中打开，再从右上角发送给朋友')
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
}

async function loadData() {
  loading.value = true
  try {
    const [shareRes, summaryRes, previewRes] = await Promise.all([
      getMobileDashboardShares(props.projectId),
      getMobileDashboardShareAccessSummary(props.projectId),
      getMobileDashboardWechatSharePreview(props.projectId),
    ])
    shares.value = shareRes.data.data || []
    summaries.value = summaryRes.data.data || []
    if (previewRes.data.data) {
      sharePreview.value = previewRes.data.data
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '分享链接加载失败')
  } finally {
    loading.value = false
  }
}

async function loadCustomerName() {
  if (props.customerName?.trim()) return
  try {
    const { data } = await getProjectDetail(props.projectId)
    loadedCustomerName.value = data.data?.brandName?.trim()
      || data.data?.companyName?.trim()
      || data.data?.projectName?.trim()
      || ''
  } catch {
    loadedCustomerName.value = ''
  }
}

async function openShareGuide(url: string) {
  if (!url) return
  selectedShareUrl.value = url
  shareGuideVisible.value = true
  qrLoading.value = true
  qrDataUrl.value = ''
  try {
    qrDataUrl.value = await QRCode.toDataURL(url, {
      width: 280,
      margin: 1,
      errorCorrectionLevel: 'H',
      color: {
        dark: '#0f172a',
        light: '#ffffff',
      },
    })
  } catch {
    ElMessage.error('二维码生成失败，请使用复制链接方式')
  } finally {
    qrLoading.value = false
  }
}

async function createShare() {
  creating.value = true
  try {
    const { data } = await createMobileDashboardShare(props.projectId)
    createdShareUrl.value = data.data?.shareUrl || ''
    ElMessage.success('分享链接已生成')
    await loadData()
  } catch (error: any) {
    ElMessage.error(error?.message || '生成失败')
  } finally {
    creating.value = false
  }
}

async function disableShare(id: number) {
  disablingId.value = id
  try {
    await disableMobileDashboardShare(id)
    ElMessage.success('链接已停用')
    await loadData()
  } catch (error: any) {
    ElMessage.error(error?.message || '停用失败')
  } finally {
    disablingId.value = null
  }
}

async function deleteShare(id: number) {
  deletingId.value = id
  try {
    await deleteMobileDashboardShare(id)
    ElMessage.success('无效链接已删除')
    await loadData()
  } catch (error: any) {
    ElMessage.error(error?.message || '删除失败')
  } finally {
    deletingId.value = null
  }
}

onMounted(() => {
  loadData()
  loadCustomerName()
})
</script>

<style scoped>
.mobile-share-panel {
  padding: 18px 20px 20px;
  border: 1px solid #e5eef7;
  border-radius: 18px;
  background:
    linear-gradient(120deg, rgba(7, 166, 107, 0.08), rgba(255, 255, 255, 0) 32%),
    #fff;
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.07);
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.panel-title,
.panel-actions,
.created-share,
.access-summary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.panel-title {
  align-items: flex-start;
}

.panel-title-icon {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: #e6f7ef;
  color: #07a66b;
  font-size: 18px;
}

.panel-title h2 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
  font-weight: 800;
  line-height: 1.3;
}

.panel-title p,
.table-toolbar p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.panel-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 14px 0 12px;
}

.share-workbench {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(280px, 0.9fr);
  gap: 14px;
  margin: 14px 0 18px;
}

.share-steps,
.share-card-preview {
  padding: 16px;
  border: 1px solid #e5eef7;
  border-radius: 14px;
  background: rgba(248, 251, 255, 0.86);
}

.workbench-eyebrow {
  color: #047857;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.06em;
}

.share-steps ol,
.dialog-instructions ol {
  margin: 12px 0;
  padding: 0;
  list-style: none;
}

.share-steps li {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-top: 9px;
  color: #334155;
  font-size: 13px;
}

.share-steps li b {
  flex: 0 0 24px;
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #e6f7ef;
  color: #047857;
}

.share-steps > p {
  margin: 12px 0 0;
  padding-top: 12px;
  border-top: 1px dashed #d7e3ed;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.preview-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.wechat-card {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-top: 14px;
  padding: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.05);
}

.wechat-card-copy {
  min-width: 0;
  flex: 1;
}

.wechat-card-copy strong {
  display: -webkit-box;
  overflow: hidden;
  color: #111827;
  font-size: 15px;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.wechat-card-copy p {
  margin: 6px 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.wechat-card-copy span {
  color: #94a3b8;
  font-size: 11px;
}

.wechat-card img {
  flex: 0 0 62px;
  width: 62px;
  height: 62px;
  border-radius: 8px;
  object-fit: cover;
}

.table-toolbar h3 {
  margin: 0;
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
}

.created-share {
  width: 100%;
}

.created-share-link {
  min-width: 0;
  max-width: 560px;
  overflow: hidden;
  color: #047857;
  font-weight: 700;
  text-decoration: underline;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.access-summary {
  flex-wrap: wrap;
  color: #606266;
  font-size: 12px;
}

.token-prefix {
  padding: 3px 7px;
  border-radius: 8px;
  background: #f8fafc;
  color: #334155;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
}

.row-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.share-table {
  border-radius: 12px;
  overflow: hidden;
}

.share-table :deep(.el-table__header th) {
  background: #f8fbff;
  color: #334155;
  font-weight: 800;
}

.share-table :deep(.el-table__row) {
  height: 58px;
}

.share-dialog {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 28px;
  align-items: center;
}

.qr-panel {
  text-align: center;
}

.qr-frame {
  width: 292px;
  min-height: 292px;
  display: grid;
  place-items: center;
  margin: 0 auto 12px;
  padding: 6px;
  border: 1px solid #dbe7ef;
  border-radius: 16px;
  background: #fff;
}

.qr-frame img {
  width: 280px;
  height: 280px;
  display: block;
}

.qr-panel strong {
  color: #0f172a;
  font-size: 15px;
}

.qr-panel p {
  margin: 5px auto 0;
  max-width: 260px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.dialog-instructions li {
  position: relative;
  margin-top: 10px;
  padding-left: 18px;
  color: #334155;
  font-size: 13px;
  line-height: 1.55;
}

.dialog-instructions li::before {
  position: absolute;
  top: 8px;
  left: 2px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #07a66b;
  content: '';
}

.dialog-card-title {
  margin: 16px 0;
  padding: 12px;
  border-radius: 10px;
  background: #f0fdf7;
}

.dialog-card-title span {
  display: block;
  color: #64748b;
  font-size: 11px;
}

.dialog-card-title strong {
  display: block;
  margin-top: 4px;
  color: #047857;
  line-height: 1.45;
}

@media (max-width: 720px) {
  .section-header,
  .table-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .panel-actions {
    justify-content: flex-start;
  }

  .share-workbench,
  .share-dialog {
    grid-template-columns: 1fr;
  }

  .qr-frame {
    width: min(292px, 100%);
  }
}
</style>
