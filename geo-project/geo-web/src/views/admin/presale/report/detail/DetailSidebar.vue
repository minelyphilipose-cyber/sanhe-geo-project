<template>
  <aside class="detail-sidebar">
    <!-- ─── 顶部:报告标题 + 版本信息 ─── -->
    <div class="sidebar-section sidebar-head">
      <div class="label">REPORT</div>
      <div class="brand-name">{{ mergedView?.brand_name || '—' }}</div>
      <div class="brand-sub">
        {{ mergedView?.industry || '' }}
        <template v-if="mergedView?.industry_role"> · {{ mergedView.industry_role }}</template>
      </div>
    </div>

    <!-- ─── 版本切换 ─── -->
    <div class="sidebar-section">
      <div class="label">版本</div>
      <!--
        v1 暂时只有当前版本可选。版本列表加载接口(versions list)在
        b·1 后端未提供,需要后端新增 /presale/reports/{id}/versions 列表接口
        或前端走 /versions/latest 逐步反查。β/γ 完成后可补。
        现在只显示当前版本号,禁用下拉。
      -->
      <el-select
        :model-value="currentVersionNo ?? undefined"
        disabled
        size="small"
        class="version-select"
        placeholder="—"
      >
        <el-option
          v-if="currentVersionNo"
          :label="`v${currentVersionNo}${isFrozen ? ' (已冻结)' : ''}`"
          :value="currentVersionNo"
        />
      </el-select>

      <!-- 状态徽标 -->
      <div class="meta-line">
        <el-tag v-if="isFrozen" size="small" type="info" effect="light">
          <el-icon><Lock /></el-icon> 已冻结
        </el-tag>
        <el-tag v-if="isDegraded" size="small" type="warning" effect="light">
          降级 {{ degradedCount }} 平台
        </el-tag>
        <el-tag
          v-if="generationStatus && generationStatus !== 'DONE'"
          size="small"
          type="warning"
        >
          {{ generationStatus }}
        </el-tag>
      </div>
    </div>

    <!-- ─── 操作按钮 ─── -->
    <div class="sidebar-section action-section">
      <div class="label">操作</div>

      <!-- derive:派生新版本;仅 DONE 状态可用 -->
      <el-button
        size="small"
        class="action-btn"
        :disabled="!canDerive"
        :loading="acting === 'derive'"
        @click="handleDerive"
      >
        <el-icon><DocumentAdd /></el-icon>
        派生新版本
      </el-button>

      <!-- freeze/unfreeze 切换 -->
      <el-button
        v-if="!isFrozen"
        size="small"
        class="action-btn"
        :disabled="!canFreeze"
        :loading="acting === 'freeze'"
        @click="handleFreeze"
      >
        <el-icon><Lock /></el-icon>
        冻结版本
      </el-button>
      <el-button
        v-else
        size="small"
        class="action-btn"
        :loading="acting === 'unfreeze'"
        @click="handleUnfreeze"
      >
        <el-icon><Unlock /></el-icon>
        解冻(manager)
      </el-button>

      <!-- retry 仅 FAILED 可见 -->
      <el-button
        v-if="isFailed"
        size="small"
        type="primary"
        class="action-btn"
        :loading="acting === 'retry'"
        @click="handleRetry"
      >
        <el-icon><Refresh /></el-icon>
        重试生成
      </el-button>

      <!-- delete:manager 专属;有导出记录时 disable -->
      <el-button
        size="small"
        class="action-btn"
        :disabled="!canDelete"
        :loading="acting === 'delete'"
        @click="handleDelete"
      >
        <el-icon><Delete /></el-icon>
        删除版本(manager)
      </el-button>

      <el-button
        size="small"
        class="action-btn"
        :disabled="!canExport"
        :loading="acting === 'export'"
        @click="handleExport"
      >
        <el-icon><Download /></el-icon>
        导出 PDF
      </el-button>
    </div>

    <!-- ─── 18 页锚点导航 ─── -->
    <div class="sidebar-section anchor-section">
      <div class="label">章节</div>
      <ul class="anchor-list">
        <li
          v-for="p in PAGE_ANCHORS"
          :key="p.id"
          :class="{ active: activeAnchor === p.id }"
        >
          <a :href="`#${p.id}`" @click.prevent="scrollTo(p.id)">
            <span class="anchor-num">{{ p.num }}</span>
            <span class="anchor-title">{{ p.title }}</span>
          </a>
        </li>
      </ul>
    </div>

    <!-- ─── 回列表 ─── -->
    <div class="sidebar-section">
      <el-button link type="primary" size="small" @click="goList">
        ← 返回列表
      </el-button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  DocumentAdd,
  Lock,
  Unlock,
  Refresh,
  Delete,
  Download
} from '@element-plus/icons-vue'

import {
  deriveVersion,
  freezeVersion,
  unfreezeVersion,
  deleteVersion,
  retryVersion
} from '@/api/presaleReport'
import {
  buildPresaleExportDownloadUrl,
  createPresaleExport,
  getPresaleExport,
  type PresaleExportResponse
} from '@/api/presaleExport'
import { useMergedView } from '@/composables/presale/useMergedView'

const route = useRoute()
const router = useRouter()

const { mergedView, currentVersionNo, refresh, switchVersion } = useMergedView()

// ─── 安全读取 meta(非 DONE 降级视图也能用) ─────────────
const meta = computed(() => mergedView.value?.meta)
const isFrozen = computed(() => meta.value?.frozen === true)
const isDegraded = computed(() => meta.value?.is_degraded === true)
const degradedCount = computed(() => meta.value?.degraded_platforms?.length ?? 0)
const generationStatus = computed(() => meta.value?.generation_status)
const isFailed = computed(() => generationStatus.value === 'FAILED')
const isDone = computed(() => generationStatus.value === 'DONE')
const hasExports = computed(() => (meta.value?.export_success_count ?? 0) > 0)

// 操作按钮可用性(权限靠后端 ensurePermission 兜底,前端只做状态禁用)
const canDerive = computed(() => isDone.value)
const canFreeze = computed(() => isDone.value && !isFrozen.value)
const canDelete = computed(() => !hasExports.value)
const canExport = computed(() => isDone.value && Boolean(meta.value?.version_id))

// ─── 写动作 ───────────────────────────────────────────────
type ActionKind = 'derive' | 'freeze' | 'unfreeze' | 'delete' | 'retry' | 'export'
const acting = ref<ActionKind | null>(null)

const reportId = computed(() => Number(route.params.id))

async function runAction<T>(kind: ActionKind, fn: () => Promise<T>): Promise<T | null> {
  if (acting.value) return null
  acting.value = kind
  try {
    return await fn()
  } catch (e: unknown) {
    // request 拦截器已弹消息,这里不重复;仅在 dev 打日志便于定位
    // eslint-disable-next-line no-console
    console.warn('[sidebar action failed]', kind, e)
    return null
  } finally {
    acting.value = null
  }
}

async function handleDerive() {
  if (!currentVersionNo.value) return
  const confirmed = await ElMessageBox.confirm(
    '派生会基于当前版本创建一个新版本,并切换为当前版本。继续吗?',
    '派生新版本',
    { confirmButtonText: '派生', cancelButtonText: '取消', type: 'info' }
  ).catch(() => false)
  if (!confirmed) return

  const res = await runAction('derive', () =>
    deriveVersion(reportId.value, currentVersionNo.value!)
  )
  if (res) {
    ElMessage.success(`已派生 v${res.newVersionNo},已切换为当前版本`)
    await switchVersion(res.newVersionNo)
  }
}

async function handleFreeze() {
  if (!currentVersionNo.value) return
  const confirmed = await ElMessageBox.confirm(
    '冻结后当前版本不可编辑。确认冻结?',
    '冻结版本',
    { confirmButtonText: '冻结', cancelButtonText: '取消', type: 'warning' }
  ).catch(() => false)
  if (!confirmed) return

  const res = await runAction('freeze', () =>
    freezeVersion(reportId.value, currentVersionNo.value!)
  )
  if (res) {
    ElMessage.success('已冻结')
    await refresh()
  }
}

async function handleUnfreeze() {
  if (!currentVersionNo.value) return
  const confirmed = await ElMessageBox.confirm(
    '解冻需要 manager 权限。继续?',
    '解冻版本',
    { confirmButtonText: '解冻', cancelButtonText: '取消', type: 'warning' }
  ).catch(() => false)
  if (!confirmed) return

  const res = await runAction('unfreeze', () =>
    unfreezeVersion(reportId.value, currentVersionNo.value!)
  )
  if (res) {
    ElMessage.success('已解冻')
    await refresh()
  }
}

async function handleDelete() {
  if (!currentVersionNo.value) return
  const confirmed = await ElMessageBox.confirm(
    '删除为物理删除,不可恢复。仅 manager 可操作。确认?',
    '删除版本',
    { confirmButtonText: '删除', cancelButtonText: '取消', type: 'error' }
  ).catch(() => false)
  if (!confirmed) return

  const ok = await runAction('delete', () =>
    deleteVersion(reportId.value, currentVersionNo.value!)
  )
  if (ok !== null) {
    ElMessage.success('已删除')
    // 删除后可能无版本可看,回列表
    void router.push('/admin/presale/report')
  }
}

async function handleRetry() {
  if (!currentVersionNo.value) return
  const res = await runAction('retry', () =>
    retryVersion(reportId.value, currentVersionNo.value!)
  )
  if (res) {
    ElMessage.success('已提交重试,跳转进度页')
    void router.push(`/admin/presale/report/${reportId.value}/progress`)
  }
}

async function handleExport() {
  const versionId = meta.value?.version_id
  if (!versionId) return
  const res = await runAction('export', () =>
    createPresaleExport(reportId.value, {
      versionId,
      exportProfile: 'PDF_A4_DPR2'
    })
  )
  if (!res) return

  const exportId = res.runningExportId ?? res.exportId
  if (res.runningExportId) {
    ElMessage.info('已有导出任务进行中,继续等待当前任务完成')
  } else {
    ElMessage.success('已提交 PDF 导出任务')
  }
  await waitExportAndDownload(exportId)
}

async function waitExportAndDownload(exportId: number) {
  for (let i = 0; i < 120; i++) {
    const task: PresaleExportResponse = await getPresaleExport(reportId.value, exportId)
    if (task.status === 'SUCCESS') {
      window.location.href = buildPresaleExportDownloadUrl(reportId.value, exportId)
      await refresh()
      return
    }
    if (task.status === 'FAILED') {
      ElMessage.error(task.errorMsg || 'PDF 导出失败')
      return
    }
    if (task.status === 'CANCELED') {
      ElMessage.warning('PDF 导出已取消')
      return
    }
    await new Promise((resolve) => window.setTimeout(resolve, 2000))
  }
  ElMessage.warning('PDF 导出仍在处理中,请稍后重试下载')
}

// ─── 18 页锚点 ────────────────────────────────────────────
const PAGE_ANCHORS = [
  { id: 'page-01', num: '01', title: '封面' },
  { id: 'page-02', num: '02', title: '诊断对象' },
  { id: 'page-03', num: '03', title: '执行摘要' },
  { id: 'page-04', num: '04', title: '可见度评分' },
  { id: 'page-05', num: '05', title: '多平台热力图' },
  { id: 'page-06', num: '06', title: '平台详细数据' },
  { id: 'page-07', num: '07', title: '竞品对标总览' },
  { id: 'page-08', num: '08', title: '竞品场景差异' },
  { id: 'page-09', num: '09', title: '情感倾向' },
  { id: 'page-10', num: '10', title: '覆盖度总览' },
  { id: 'page-11', num: '11', title: '覆盖度详情' },
  { id: 'page-12', num: '12', title: '优化机会(高)' },
  { id: 'page-13', num: '13', title: '优化机会(中)' },
  { id: 'page-14', num: '14', title: '优化机会(低)' },
  { id: 'page-15', num: '15', title: '预期收益' },
  { id: 'page-16', num: '16', title: '分阶段路径' },
  { id: 'page-17', num: '17', title: '关键发现总结' },
  { id: 'page-18', num: '18', title: '关于我们' }
] as const

const activeAnchor = ref<string>('page-01')

function scrollTo(id: string) {
  const el = document.getElementById(id)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
    activeAnchor.value = id
  }
}

// ─── 滚动监听:高亮当前章节(IntersectionObserver) ──────
let observer: IntersectionObserver | null = null

onMounted(() => {
  observer = new IntersectionObserver(
    (entries) => {
      // 取可见度最高的那一条作为当前章节
      const visible = entries
        .filter((e) => e.isIntersecting)
        .sort((a, b) => b.intersectionRatio - a.intersectionRatio)
      if (visible[0]) {
        activeAnchor.value = visible[0].target.id
      }
    },
    { rootMargin: '-20% 0px -70% 0px', threshold: [0, 0.25, 0.5, 0.75, 1] }
  )
  // 延迟一帧等 Viewer 渲染出锚点节点
  requestAnimationFrame(() => {
    PAGE_ANCHORS.forEach((p) => {
      const el = document.getElementById(p.id)
      if (el && observer) observer.observe(el)
    })
  })
})

onBeforeUnmount(() => {
  if (observer) {
    observer.disconnect()
    observer = null
  }
})

function goList() {
  void router.push('/admin/presale/report')
}
</script>

<style scoped>
.detail-sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
  background: #1a2942;
  color: #d4cfc2;
  padding: 24px 16px;
  border-right: 1px solid rgba(255, 255, 255, 0.1);
  font-family: 'Noto Sans SC', sans-serif;
}

.sidebar-section {
  padding: 12px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.sidebar-section:last-child {
  border-bottom: none;
}

.label {
  font-family: 'JetBrains Mono', monospace;
  font-size: 10px;
  letter-spacing: 2px;
  color: rgba(255, 255, 255, 0.5);
  margin-bottom: 8px;
  text-transform: uppercase;
}

.sidebar-head .brand-name {
  font-size: 20px;
  font-weight: 700;
  color: #fefcf7;
  letter-spacing: 1px;
}
.sidebar-head .brand-sub {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
  margin-top: 4px;
}

.version-select {
  width: 100%;
}

.meta-line {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.action-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.action-btn {
  width: 100%;
  justify-content: flex-start;
  background: rgba(255, 255, 255, 0.05);
  color: #d4cfc2;
  border: 1px solid rgba(255, 255, 255, 0.12);
}
.action-btn:not(:disabled):hover {
  background: rgba(255, 255, 255, 0.1);
  color: #fefcf7;
}

.anchor-list {
  list-style: none;
  padding: 0;
  margin: 0;
  max-height: 360px;
  overflow-y: auto;
}
.anchor-list li a {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 5px 8px;
  color: rgba(255, 255, 255, 0.65);
  font-size: 12px;
  text-decoration: none;
  border-left: 2px solid transparent;
  transition: all 0.15s;
}
.anchor-list li a:hover {
  background: rgba(255, 255, 255, 0.05);
  color: #fefcf7;
}
.anchor-list li.active a {
  background: rgba(217, 119, 6, 0.12);
  color: #fefcf7;
  border-left-color: #d97706;
}
.anchor-num {
  font-family: 'JetBrains Mono', monospace;
  font-size: 10px;
  color: rgba(255, 255, 255, 0.4);
  min-width: 18px;
}
.anchor-list li.active .anchor-num {
  color: #d97706;
}
.anchor-title {
  flex: 1;
}
</style>
