<template>
  <div class="presale-report-detail">
    <!-- 加载态 -->
    <div v-if="loading && !mergedView" class="state-panel">
      <el-icon class="state-icon is-loading"><Loading /></el-icon>
      <div class="state-text">正在加载报告数据...</div>
    </div>

    <!-- 错误态 -->
    <el-alert
      v-else-if="error"
      type="error"
      :closable="false"
      show-icon
      class="error-alert"
    >
      <template #title>{{ error }}</template>
      <template #default>
        <el-button size="small" @click="reload">重试</el-button>
        <el-button size="small" @click="goList">返回列表</el-button>
      </template>
    </el-alert>

    <!-- 版本未 DONE 提示(非 DONE 不渲染 19 页,引导回进度页) -->
    <el-alert
      v-else-if="mergedView && mergedView.meta.generation_status !== 'DONE'"
      type="warning"
      :closable="false"
      show-icon
      class="error-alert"
    >
      <template #title>
        当前版本状态为 {{ mergedView.meta.generation_status }},报告内容尚未就绪
      </template>
      <template #default>
        <el-button
          v-if="isFailed"
          size="small"
          type="primary"
          :loading="retrying"
          @click="handleRetry"
        >
          重试生成
        </el-button>
        <el-button size="small" type="primary" @click="goProgress">查看生成进度</el-button>
        <el-button size="small" @click="goList">返回列表</el-button>
      </template>
    </el-alert>

    <!-- 正常态:Sidebar + Viewer -->
    <div v-else-if="mergedView" class="detail-layout">
      <DetailSidebar />
      <ReportViewer />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'

import {
  getLatestDetail,
  getVersionDetail,
  retryVersion,
  type ReportDetailVO
} from '@/api/presaleReport'
import {
  mergeSnapshot,
  type VersionRowMeta
} from '@/utils/presale/merge-snapshot'
import type { MergedViewDTO } from '@/types/presale'
import { provideMergedViewContext } from '@/composables/presale/useMergedView'

import DetailSidebar from './detail/DetailSidebar.vue'
import ReportViewer from './detail/ReportViewer.vue'

const route = useRoute()
const router = useRouter()

// ─── 路由参数 ─────────────────────────────────────────────
const reportId = computed(() => Number(route.params.id))
/**
 * 可选 query:?versionNo=3 指定历史版本,缺省加载 latest。
 * 数字解析失败(NaN)按 undefined 处理。
 */
const queryVersionNo = computed<number | undefined>(() => {
  const q = route.query.versionNo
  if (q == null) return undefined
  const n = Number(q)
  return Number.isFinite(n) && n > 0 ? n : undefined
})

// ─── 响应态 ────────────────────────────────────────────────
const detail = ref<ReportDetailVO | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const retrying = ref(false)
const isFailed = computed(() => detail.value?.version.generationStatus === 'FAILED')

/**
 * 从 ReportDetailVO 构造 mergeSnapshot 需要的 VersionRowMeta。
 *
 * 注意字段差异(ReportVersionMetaVO 未返的字段):
 *   - schema_version:硬编码 'v1.2'(P1 阶段全仓锁定版本)
 *   - frozen_by / frozen_reason / content_updated_by:后端 VO 未返,置 null
 *     (MergedViewMeta 会保留 null,前端展示层当前不使用)
 *   - report_id:从 detail 顶层拿(VersionMetaVO 里没有)
 */
function toVersionRowMeta(d: ReportDetailVO): VersionRowMeta {
  const v = d.version
  return {
    version_id: v.versionId,
    report_id: d.reportId,
    version_no: v.versionNo,
    schema_version: 'v1.2',
    generation_status: v.generationStatus,
    frozen_at: v.frozenAt,
    frozen_by: null,
    frozen_reason: null,
    content_updated_at: v.contentUpdatedAt,
    content_updated_by: null,
    is_degraded: v.isDegraded ?? false,
    degraded_platforms: v.degradedPlatforms ?? null,
    export_success_count: v.exportSuccessCount,
    export_success_at: v.exportSuccessAt
  }
}

/**
 * 合并视图 computed:仅 DONE 状态尝试合成,任一解析失败抛到 error。
 * 非 DONE 返回 null(上层根据 generation_status 提示进度页)。
 */
const mergedView = computed<MergedViewDTO | null>(() => {
  if (!detail.value) return null
  const d = detail.value
  if (d.version.generationStatus !== 'DONE') {
    // 非 DONE 时业务字段多半为空,不尝试 mergeSnapshot;
    // 但 meta 视图层仍需要状态信息,我们在 template 里直接读 detail.version.generationStatus
    // 不走 mergedView(上面模板已处理 generation_status !== 'DONE' 的提示分支)。
    // 这里返回一个最小 meta 对象让上层能读 meta.generation_status。
    return buildMetaOnlyView(d)
  }
  if (!d.rawSnapshotJson || !d.computedSnapshotJson || !d.editableContentJson) {
    error.value = '报告数据不完整:L1/L2/L3 快照缺失'
    return null
  }
  try {
    const raw = JSON.parse(d.rawSnapshotJson)
    const computedSnap = JSON.parse(d.computedSnapshotJson)
    const editable = JSON.parse(d.editableContentJson)
    return mergeSnapshot(raw, computedSnap, editable, toVersionRowMeta(d))
  } catch (e: unknown) {
    error.value = `报告数据解析失败:${(e as Error).message}`
    return null
  }
})

/**
 * 非 DONE 状态下,构造一个"降级视图":meta + 客户信息(brand_name 等)填齐,
 * 业务字段(test_summary / scores / merged_* 等)不填。
 *
 * 降级视图的消费约定:
 *   - Sidebar 可安全读:meta.*、brand_name、industry、industry_role、region、user_demand
 *   - Viewer 和 19 页 Page SFC 必须用 meta.generation_status === 'DONE' 做守卫,
 *     不得在非 DONE 时消费业务字段
 */
function buildMetaOnlyView(d: ReportDetailVO): MergedViewDTO {
  const v = d.version
  return {
    meta: {
      version_id: v.versionId,
      report_id: d.reportId,
      version_no: v.versionNo,
      schema_version: 'v1.2',
      generation_status: v.generationStatus,
      generated_at: null,
      frozen: v.frozen,
      frozen_at: v.frozenAt,
      frozen_by: null,
      frozen_reason: null,
      content_updated_at: v.contentUpdatedAt,
      content_updated_by: null,
      is_degraded: v.isDegraded ?? false,
      degraded_platforms: v.degradedPlatforms ?? [],
      match_level: 'EXACT',
      export_success_count: v.exportSuccessCount,
      export_success_at: v.exportSuccessAt
    },
    // 客户信息字段来自 ReportDetailVO 顶层,非 DONE 也可用
    brand_name: d.brandName,
    industry: d.industry,
    industry_role: d.industryRole,
    region: d.region,
    user_demand: d.userDemand
    // 其余业务字段(test_summary / scores / merged_* 等)不填;
    // 消费方必须用 meta.generation_status === 'DONE' 做守卫。
    // 使用 any cast 避开 TS 结构补全检查(本对象只会被 meta + 客户信息消费路径读到)。
  } as unknown as MergedViewDTO
}

const currentVersionNo = computed<number | null>(
  () => mergedView.value?.meta.version_no ?? null
)

// ─── 加载逻辑 ──────────────────────────────────────────────
async function load(versionNo?: number): Promise<void> {
  if (!Number.isFinite(reportId.value) || reportId.value <= 0) {
    error.value = 'URL 参数错误:reportId 非法'
    return
  }
  loading.value = true
  error.value = null
  try {
    detail.value = versionNo
      ? await getVersionDetail(reportId.value, versionNo)
      : await getLatestDetail(reportId.value)
  } catch (e: unknown) {
    // request 拦截器已弹 ElMessage,这里只更新局部 error 便于页面展示
    error.value = (e as Error).message || '加载报告失败'
    detail.value = null
  } finally {
    loading.value = false
  }
}

async function refresh(): Promise<void> {
  await load(currentVersionNo.value ?? queryVersionNo.value)
}

async function switchVersion(versionNo: number): Promise<void> {
  // 只改 URL;下方 watch(queryVersionNo) 会触发 load(),避免重复请求。
  // 这样用户手动改 URL 的 versionNo 也能走同一条路径,行为一致。
  await router.replace({ query: { ...route.query, versionNo: String(versionNo) } })
}

function reload() {
  void load(queryVersionNo.value)
}

function goList() {
  void router.push('/admin/presale/report')
}

function goProgress() {
  void router.push(`/admin/presale/report/${reportId.value}/progress`)
}

async function handleRetry() {
  const versionNo = detail.value?.version.versionNo
  if (retrying.value || !versionNo || !isFailed.value) return
  retrying.value = true
  try {
    await retryVersion(reportId.value, versionNo)
    ElMessage.success('已提交重试')
    void router.push(`/admin/presale/report/${reportId.value}/progress`)
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '提交重试失败')
  } finally {
    retrying.value = false
  }
}

// ─── provide 上下文 ───────────────────────────────────────
/**
 * W4: reportCreatedAt 优先读 mergedView.meta.generated_at(生成时间),
 * 再回退 ReportDetailVO.createdAt(报告创建时间)。
 */
const reportCreatedAt = computed<string | null>(() => {
  const generatedAt = mergedView.value?.meta?.generated_at ?? null
  return generatedAt ?? detail.value?.createdAt ?? null
})

provideMergedViewContext({
  mergedView,
  currentVersionNo,
  loading,
  error,
  refresh,
  switchVersion,
  reportCreatedAt
})

// ─── 生命周期 ─────────────────────────────────────────────
onMounted(() => {
  void load(queryVersionNo.value)
})

// 路由 reportId 变化(从别的报告跳来)或 query.versionNo 变化(侧栏切版本外也支持手改 URL),重新加载。
//
// 为什么传 [reportId, queryVersionNo] 而不是 () => [reportId.value, queryVersionNo.value]:
//   传 getter 返回新数组时,Vue 按引用比较整个数组,每次 getter 调用都产生新数组,
//   会导致 watch 无限触发。传 ref 数组形式 Vue 内部会逐 ref 监听,正确浅比较。
watch(
  [reportId, queryVersionNo],
  ([newId, newV], [oldId, oldV]) => {
    if (newId !== oldId || newV !== oldV) {
      void load(newV)
    }
  }
)

// 加载成功后若状态是 FAILED,给个轻提示,避免用户以为是页面坏了
watch(
  () => detail.value?.version.generationStatus,
  (st) => {
    if (st === 'FAILED') {
      ElMessage.warning('当前版本生成失败,点击"重试"可重新生成')
    }
  }
)
</script>

<style scoped>
.presale-report-detail {
  min-height: calc(100vh - 60px);
  background: #2d2a26; /* 对齐原型 body bg */
  color: #0b1426;
}

.state-panel {
  padding: 80px 24px;
  text-align: center;
  color: #d4cfc2;
}
.state-icon {
  font-size: 36px;
  color: #d4cfc2;
}
.is-loading {
  animation: a2-spin 1.6s linear infinite;
}
@keyframes a2-spin {
  to {
    transform: rotate(360deg);
  }
}
.state-text {
  margin-top: 16px;
  font-size: 14px;
  letter-spacing: 1px;
}
.error-alert {
  margin: 24px;
  max-width: 800px;
}

.detail-layout {
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: 0;
  align-items: start;
  min-height: calc(100vh - 60px);
}
</style>
