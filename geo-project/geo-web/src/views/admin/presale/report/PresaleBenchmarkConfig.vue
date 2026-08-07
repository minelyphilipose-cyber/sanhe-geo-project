<template>
  <div class="benchmark-page admin-page">
    <section class="benchmark-hero">
      <div class="hero-copy">
        <div class="hero-kicker">AI 可见度诊断报告 · 数据治理</div>
        <h1>行业基准配置</h1>
        <p>维护行业平均表现、领先水平与 Top10 门槛。每次调整都会生成独立版本，并冻结到后续报告。</p>
      </div>
      <div class="hero-actions">
        <el-button plain @click="goBack">返回报告列表</el-button>
        <el-button type="primary" @click="openCreate">新增基准版本</el-button>
      </div>
    </section>

    <section class="guide-strip">
      <div class="guide-icon">i</div>
      <div>
        <strong>匹配逻辑</strong>
        <span>行业 + 角色优先，其次行业通用，最后使用全局通用基准。历史报告始终使用生成时冻结的数据版本。</span>
      </div>
    </section>

    <section class="summary-grid">
      <div class="summary-card"><span>当前行业基准</span><strong>{{ displayRows.length }}</strong><em>个当前版本</em></div>
      <div class="summary-card"><span>已启用</span><strong>{{ enabledCount }}</strong><em>可用于新报告</em></div>
      <div class="summary-card"><span>低置信度</span><strong>{{ lowConfidenceCount }}</strong><em>建议持续校准</em></div>
      <div class="summary-card summary-card-accent"><span>全局回退</span><strong>{{ globalBenchmark ? '已配置' : '待配置' }}</strong><em>{{ globalBenchmark ? '保障所有报告可生成' : '请优先补齐' }}</em></div>
    </section>

    <el-card shadow="never" class="benchmark-surface">
      <template #header>
        <div class="surface-header">
          <div>
            <h2>当前生效基准</h2>
            <p>仅展示每个“行业 + 角色”的当前版本；历史调整可在详情中查看。</p>
          </div>
          <el-input v-model="keyword" clearable placeholder="搜索行业或角色" class="search-input" />
        </div>
      </template>

      <el-table v-loading="loading" :data="filteredRows" class="benchmark-table" :row-class-name="tableRowClass">
        <el-table-column label="行业 / 角色" min-width="230">
          <template #default="{ row }">
            <div class="industry-cell">
              <strong>{{ industryLabel(row.industry) }}</strong>
              <span>{{ roleLabel(row.industryRole) }}</span>
              <small>{{ row.industry }} · {{ row.industryRole }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="综合表现" min-width="190">
          <template #default="{ row }">
            <div class="score-overview">
              <b>{{ row.avgOverall }}</b><span>行业均值</span>
              <i></i>
              <b>{{ row.top1Overall }}</b><span>领先水平</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Top10 门槛" width="120">
          <template #default="{ row }"><b class="top10-score">{{ row.top10Score }}</b></template>
        </el-table-column>
        <el-table-column label="样本与置信度" min-width="150">
          <template #default="{ row }">
            <div class="meta-stack"><span>{{ formatNumber(row.sampleSize) }} 个样本</span><el-tag size="small" :type="confidenceTagType(row.confidenceLevel)">{{ confidenceLabel(row.confidenceLevel) }}</el-tag></div>
          </template>
        </el-table-column>
        <el-table-column label="当前状态" width="106">
          <template #default="{ row }"><el-tag size="small" :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用中' : '已停用' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="生效日期" width="118"><template #default="{ row }">{{ row.effectiveFrom }}</template></el-table-column>
        <el-table-column label="操作" width="205" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看详情</el-button>
            <el-button link type="primary" @click="openNewVersion(row)">调整版本</el-button>
            <el-button link :type="row.enabled ? 'danger' : 'success'" @click="toggleStatus(row)">{{ row.enabled ? '停用' : '启用' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingFrom ? '调整基准版本' : '新增行业基准版本'" width="920px" destroy-on-close>
      <div class="dialog-intro">{{ editingFrom ? `正在基于「${industryLabel(editingFrom.industry)} · ${roleLabel(editingFrom.industryRole)}」创建新版本，原版本不会被覆盖。` : '填写数据后将创建独立版本；同一行业、角色及生效日期不可重复。' }}</div>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="benchmark-grid benchmark-grid-meta">
          <el-form-item label="行业" prop="industry"><el-select v-model="form.industry" filterable allow-create default-first-option style="width:100%" placeholder="选择行业"><el-option v-for="option in industryOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select></el-form-item>
          <el-form-item label="行业角色" prop="industryRole"><el-select v-model="form.industryRole" filterable allow-create default-first-option style="width:100%" placeholder="选择角色"><el-option v-for="option in roleOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select></el-form-item>
          <el-form-item label="生效日期" prop="effectiveFrom"><el-date-picker v-model="form.effectiveFrom" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
          <el-form-item label="数据置信度" prop="confidenceLevel"><el-select v-model="form.confidenceLevel" style="width:100%"><el-option label="高置信度" value="HIGH" /><el-option label="中置信度" value="MEDIUM" /><el-option label="低置信度（模拟或样本不足）" value="LOW" /></el-select></el-form-item>
        </div>
        <div class="score-section-title"><span>五项评分基准</span><small>Top1 不得低于对应行业平均值</small></div>
        <div class="score-table">
          <div class="score-head">评分维度</div><div class="score-head">行业平均</div><div class="score-head">行业 Top1</div>
          <template v-for="dimension in dimensions" :key="dimension.key"><div class="score-name">{{ dimension.label }}</div><el-form-item :prop="dimension.avgKey"><el-input-number v-model="form[dimension.avgKey]" :min="0" :max="100" :precision="1" controls-position="right" /></el-form-item><el-form-item :prop="dimension.top1Key"><el-input-number v-model="form[dimension.top1Key]" :min="0" :max="100" :precision="1" controls-position="right" /></el-form-item></template>
        </div>
        <div class="benchmark-grid benchmark-grid-bottom"><el-form-item label="Top10 综合门槛" prop="top10Score"><el-input-number v-model="form.top10Score" :min="0" :max="100" :precision="1" controls-position="right" /></el-form-item><el-form-item label="样本量" prop="sampleSize"><el-input-number v-model="form.sampleSize" :min="0" :max="100000000" controls-position="right" /></el-form-item><el-form-item label="新版本状态" prop="enabled"><el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" /></el-form-item><el-form-item label="备注"><el-input v-model="form.remark" maxlength="500" show-word-limit placeholder="例如：模拟基准 v1，待真实样本校准" /></el-form-item></div>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存新版本</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" size="620px" :with-header="false" class="benchmark-drawer">
      <template v-if="selectedRow"><div class="drawer-top"><div><span class="drawer-kicker">基准详情</span><h2>{{ industryLabel(selectedRow.industry) }}</h2><p>{{ roleLabel(selectedRow.industryRole) }} · {{ selectedRow.industry }} / {{ selectedRow.industryRole }}</p></div><el-tag :type="selectedRow.enabled ? 'success' : 'info'">{{ selectedRow.enabled ? '启用中' : '已停用' }}</el-tag></div>
      <div class="drawer-meta"><div><span>生效日期</span><b>{{ selectedRow.effectiveFrom }}</b></div><div><span>样本量</span><b>{{ formatNumber(selectedRow.sampleSize) }}</b></div><div><span>数据置信度</span><b>{{ confidenceLabel(selectedRow.confidenceLevel) }}</b></div></div>
      <section class="detail-section"><div class="detail-title"><h3>五项评分明细</h3><span>差距 = Top1 − 行业平均</span></div><div v-for="dimension in dimensions" :key="dimension.key" class="metric-row"><div><b>{{ dimension.label }}</b><span>行业平均 {{ selectedRow[dimension.avgKey] }}</span></div><div class="metric-values"><strong>{{ selectedRow[dimension.top1Key] }}</strong><em>领先 +{{ scoreGap(selectedRow, dimension) }}</em></div></div></section>
      <section class="detail-section detail-note"><h3>备注</h3><p>{{ selectedRow.remark || '暂无备注' }}</p></section>
      <div class="drawer-actions"><el-button @click="openHistory(selectedRow)">查看变更历史</el-button><el-button type="primary" @click="openNewVersion(selectedRow)">基于此版本调整</el-button></div></template>
    </el-drawer>

    <el-drawer v-model="historyVisible" title="基准变更历史" size="680px"><el-table v-loading="historyLoading" :data="historyRows"><el-table-column prop="createdAt" label="时间" width="170" /><el-table-column label="操作" width="100"><template #default="{ row }">{{ operationLabel(row.operation) }}</template></el-table-column><el-table-column prop="operatorName" label="操作人" width="110" /><el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip /></el-table></el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { createPresaleBenchmark, listPresaleBenchmarkHistory, listPresaleBenchmarks, updatePresaleBenchmarkStatus, type PresaleBenchmark, type PresaleBenchmarkHistory, type PresaleBenchmarkPayload } from '@/api/presaleBenchmark'
import { PRESALE_BENCHMARK_INDUSTRY_OPTIONS } from './presaleIndustryOptions'

type ScoreKey = 'Overall' | 'Mention' | 'Ranking' | 'Sentiment' | 'Coverage'
type AvgKey = `avg${ScoreKey}`
type Top1Key = `top1${ScoreKey}`
type Dimension = { key: string; label: string; avgKey: AvgKey; top1Key: Top1Key }
const router = useRouter()
const loading = ref(false), saving = ref(false), dialogVisible = ref(false), detailVisible = ref(false), historyVisible = ref(false), historyLoading = ref(false)
const formRef = ref<FormInstance>()
const rows = ref<PresaleBenchmark[]>([]), historyRows = ref<PresaleBenchmarkHistory[]>([]), selectedRow = ref<PresaleBenchmark>(), editingFrom = ref<PresaleBenchmark>(), keyword = ref('')
const dimensions: Dimension[] = [{ key: 'overall', label: '综合可见度', avgKey: 'avgOverall', top1Key: 'top1Overall' }, { key: 'mention', label: '品牌提及', avgKey: 'avgMention', top1Key: 'top1Mention' }, { key: 'ranking', label: '推荐排名', avgKey: 'avgRanking', top1Key: 'top1Ranking' }, { key: 'sentiment', label: '认知情感', avgKey: 'avgSentiment', top1Key: 'top1Sentiment' }, { key: 'coverage', label: '问题覆盖', avgKey: 'avgCoverage', top1Key: 'top1Coverage' }]
const industryOptions = PRESALE_BENCHMARK_INDUSTRY_OPTIONS
const roleOptions = [{ value: '_ALL_', label: '通用（不区分角色）' }, { value: 'chain_brand', label: '连锁品牌' }, { value: 'single_store', label: '单店' }, { value: 'franchise', label: '加盟商' }, { value: 'manufacturer', label: '生产厂家' }, { value: 'dealer', label: '经销商' }, { value: 'agent', label: '代理商' }, { value: 'channel', label: '渠道商' }, { value: 'platform', label: '平台方' }, { value: 'service_provider', label: '服务商' }]
function today() { return new Date().toISOString().slice(0, 10) }
function emptyForm(): PresaleBenchmarkPayload { return { industry: '', industryRole: '_ALL_', avgOverall: 0, avgMention: 0, avgRanking: 0, avgSentiment: 0, avgCoverage: 0, top1Overall: 0, top1Mention: 0, top1Ranking: 0, top1Sentiment: 0, top1Coverage: 0, top10Score: 0, confidenceLevel: 'MEDIUM', sampleSize: 0, enabled: true, effectiveFrom: today(), remark: '' } }
const form = reactive<PresaleBenchmarkPayload>(emptyForm())
const required = [{ required: true, message: '请填写此项', trigger: 'blur' }]
const rules: FormRules = { industry: required, industryRole: required, effectiveFrom: required, confidenceLevel: required, sampleSize: [{ required: true, type: 'number', min: 0, message: '样本量不能小于 0', trigger: 'change' }] }
const displayRows = computed(() => {
  const grouped = new Map<string, PresaleBenchmark>()
  for (const row of rows.value) { const key = `${row.industry}__${row.industryRole}`; const old = grouped.get(key); if (!old || `${row.effectiveFrom}-${row.id}` > `${old.effectiveFrom}-${old.id}`) grouped.set(key, row) }
  return [...grouped.values()].sort((a, b) => industryLabel(a.industry).localeCompare(industryLabel(b.industry), 'zh-CN'))
})
const filteredRows = computed(() => { const input = keyword.value.trim().toLowerCase(); return !input ? displayRows.value : displayRows.value.filter(row => `${industryLabel(row.industry)} ${roleLabel(row.industryRole)} ${row.industry} ${row.industryRole}`.toLowerCase().includes(input)) })
const enabledCount = computed(() => displayRows.value.filter(row => row.enabled).length)
const lowConfidenceCount = computed(() => displayRows.value.filter(row => row.confidenceLevel === 'LOW').length)
const globalBenchmark = computed(() => displayRows.value.find(row => row.industry === '_ALL_' && row.industryRole === '_ALL_'))
function industryLabel(value: string) { return industryOptions.find(item => item.value === value)?.label || value }
function roleLabel(value: string) { return roleOptions.find(item => item.value === value)?.label || value }
function confidenceLabel(value: string) { return ({ HIGH: '高置信度', MEDIUM: '中置信度', LOW: '低置信度' } as Record<string, string>)[value] || value }
function confidenceTagType(value: string) { return value === 'HIGH' ? 'success' : value === 'LOW' ? 'warning' : 'info' }
function operationLabel(value: string) { return ({ INSERT: '新增版本', UPDATE: '更新', DISABLE: '停用' } as Record<string, string>)[value] || value }
function formatNumber(value: number) { return new Intl.NumberFormat('zh-CN').format(value || 0) }
function scoreGap(row: PresaleBenchmark, dimension: Dimension) { return Number(row[dimension.top1Key]) - Number(row[dimension.avgKey]) }
function tableRowClass({ row }: { row: PresaleBenchmark }) { return row.industry === '_ALL_' ? 'global-row' : '' }
async function load() { loading.value = true; try { const { data } = await listPresaleBenchmarks(); rows.value = data.data } finally { loading.value = false } }
function openCreate() { editingFrom.value = undefined; Object.assign(form, emptyForm()); dialogVisible.value = true }
function openNewVersion(row: PresaleBenchmark) { editingFrom.value = row; detailVisible.value = false; Object.assign(form, { industry: row.industry, industryRole: row.industryRole, avgOverall: row.avgOverall, avgMention: row.avgMention, avgRanking: row.avgRanking, avgSentiment: row.avgSentiment, avgCoverage: row.avgCoverage, top1Overall: row.top1Overall, top1Mention: row.top1Mention, top1Ranking: row.top1Ranking, top1Sentiment: row.top1Sentiment, top1Coverage: row.top1Coverage, top10Score: row.top10Score, confidenceLevel: row.confidenceLevel, sampleSize: row.sampleSize, enabled: true, effectiveFrom: today(), remark: row.remark || '' }); dialogVisible.value = true }
function openDetail(row: PresaleBenchmark) { selectedRow.value = row; detailVisible.value = true }
async function save() { const valid = await formRef.value?.validate().catch(() => false); if (!valid) return; const invalid = dimensions.find(item => form[item.top1Key] < form[item.avgKey]); if (invalid) return ElMessage.error(`${invalid.label}的行业 Top1 不得低于行业平均值`); saving.value = true; try { await createPresaleBenchmark({ ...form, industry: form.industry.trim(), industryRole: form.industryRole.trim(), remark: form.remark?.trim() }); ElMessage.success('新基准版本已保存'); dialogVisible.value = false; await load() } finally { saving.value = false } }
async function toggleStatus(row: PresaleBenchmark) { const action = row.enabled ? '停用' : '启用'; await ElMessageBox.confirm(`确认${action}「${industryLabel(row.industry)} · ${roleLabel(row.industryRole)}」当前版本吗？`, `${action}基准`, { type: 'warning' }); await updatePresaleBenchmarkStatus(row.id, !row.enabled, `后台${action}`); ElMessage.success(`已${action}`); await load() }
async function openHistory(row: PresaleBenchmark) { historyVisible.value = true; historyLoading.value = true; try { const { data } = await listPresaleBenchmarkHistory(row.id); historyRows.value = data.data } finally { historyLoading.value = false } }
function goBack() { router.push('/admin/presale/report') }
onMounted(load)
</script>

<style scoped>
.benchmark-page { --ink:#172033; --muted:#718096; --line:#e6ebf3; --blue:#2968e8; padding:24px; color:var(--ink); }
.benchmark-hero { min-height:150px; padding:29px 30px; border:1px solid #dce8fb; border-radius:18px; display:flex; justify-content:space-between; gap:24px; align-items:center; background:radial-gradient(circle at 92% 15%,#ddf8ef 0,transparent 32%),linear-gradient(115deg,#fff 0%,#f4f8ff 60%,#effcff 100%); box-shadow:0 14px 32px rgba(48,84,140,.06); }
.hero-kicker,.drawer-kicker { color:#316ee4; font-weight:700; font-size:13px; letter-spacing:.02em; }.hero-copy h1 { margin:7px 0 8px; font-size:27px; line-height:1.2; }.hero-copy p { margin:0; color:#66758b; font-size:14px; }.hero-actions { display:flex; gap:10px; flex-shrink:0; }.guide-strip { display:flex; gap:13px; align-items:center; margin:18px 0; padding:13px 18px; border-radius:12px; color:#66758b; background:#f5f8fc; font-size:14px; }.guide-strip strong { color:#2f3b4d; margin-right:12px; }.guide-icon { width:22px; height:22px; flex:0 0 22px; display:grid; place-items:center; border-radius:50%; background:#93a0b1; color:#fff; font-family:Georgia,serif; font-weight:bold; }
.summary-grid { display:grid; grid-template-columns:repeat(4,1fr); gap:14px; margin-bottom:18px; }.summary-card { display:flex; flex-direction:column; min-height:100px; padding:17px 19px; border:1px solid var(--line); border-radius:14px; background:#fff; box-shadow:0 6px 18px rgba(31,53,88,.035); }.summary-card span,.summary-card em { color:var(--muted); font-size:13px; font-style:normal; }.summary-card strong { margin:6px 0 2px; font-size:24px; }.summary-card-accent { background:linear-gradient(135deg,#f0f6ff,#f8fbff); border-color:#d7e6ff; }.benchmark-surface { border:1px solid var(--line); border-radius:16px; }.surface-header { display:flex; justify-content:space-between; align-items:center; gap:18px; }.surface-header h2 { margin:0 0 5px; font-size:17px; }.surface-header p { margin:0; color:var(--muted); font-size:13px; }.search-input { width:250px; }.industry-cell { display:flex; flex-direction:column; gap:3px; }.industry-cell strong { font-size:14px; }.industry-cell span { color:#526176; font-size:13px; }.industry-cell small { color:#9aa6b6; font-size:11px; }.score-overview { display:flex; align-items:baseline; gap:5px; white-space:nowrap; }.score-overview b,.top10-score { color:#2463d8; font-size:17px; }.score-overview span { color:var(--muted); font-size:12px; }.score-overview i { width:1px; height:18px; margin:0 8px; background:#dbe2eb; }.meta-stack { display:flex; align-items:center; gap:8px; color:#5e6c80; font-size:13px; }.benchmark-table :deep(.global-row td) { background:#f7fbff !important; }.dialog-intro { padding:11px 13px; margin-bottom:17px; border-radius:8px; color:#607089; background:#f4f7fb; font-size:13px; }.benchmark-grid { display:grid; gap:16px; }.benchmark-grid-meta { grid-template-columns:repeat(4,1fr); }.benchmark-grid-bottom { grid-template-columns:repeat(4,1fr); margin-top:18px; }.score-section-title { display:flex; justify-content:space-between; align-items:center; margin:6px 0 10px; }.score-section-title span { font-weight:700; }.score-section-title small { color:var(--muted); }.score-table { display:grid; grid-template-columns:160px 1fr 1fr; gap:10px 18px; align-items:center; padding:17px; border:1px solid #e7edf6; border-radius:12px; background:#fbfcfe; }.score-head { color:#758398; font-size:12px; font-weight:700; }.score-name { font-weight:600; color:#435168; }.score-table :deep(.el-form-item) { margin:0; }.score-table :deep(.el-input-number) { width:100%; }.drawer-top { display:flex; justify-content:space-between; gap:16px; padding:30px 28px 22px; border-bottom:1px solid var(--line); }.drawer-top h2 { margin:7px 0; font-size:25px; }.drawer-top p { margin:0; color:var(--muted); font-size:13px; }.drawer-meta { display:grid; grid-template-columns:repeat(3,1fr); margin:20px 28px; border:1px solid var(--line); border-radius:12px; overflow:hidden; }.drawer-meta div { padding:13px; border-right:1px solid var(--line); display:flex; flex-direction:column; gap:5px; }.drawer-meta div:last-child { border:0; }.drawer-meta span,.detail-title span { color:var(--muted); font-size:12px; }.detail-section { margin:22px 28px; }.detail-title { display:flex; justify-content:space-between; align-items:center; }.detail-section h3 { margin:0 0 12px; font-size:15px; }.metric-row { display:flex; justify-content:space-between; align-items:center; padding:12px 0; border-bottom:1px solid #edf1f6; }.metric-row div:first-child { display:flex; flex-direction:column; gap:3px; }.metric-row span { color:var(--muted); font-size:12px; }.metric-values { display:flex; align-items:baseline; gap:9px; }.metric-values strong { color:#2768e6; font-size:18px; }.metric-values em { color:#1c9a68; font-size:12px; font-style:normal; }.detail-note { padding:14px; margin-inline:28px; border-radius:10px; background:#f7f9fc; }.detail-note p { margin:0; color:#69778b; line-height:1.6; }.drawer-actions { display:flex; justify-content:flex-end; gap:10px; padding:18px 28px 30px; }.benchmark-page :deep(.el-card__header) { padding:18px 20px; border-bottom-color:var(--line); }.benchmark-page :deep(.el-table th.el-table__cell) { color:#738095; font-weight:700; background:#fafbfd; }.benchmark-page :deep(.el-table td.el-table__cell) { padding:11px 0; }
@media (max-width:1000px) { .summary-grid { grid-template-columns:repeat(2,1fr); }.benchmark-grid-meta,.benchmark-grid-bottom { grid-template-columns:repeat(2,1fr); }.benchmark-hero { align-items:flex-start; flex-direction:column; }.search-input { width:100%; }.surface-header { align-items:flex-start; flex-direction:column; } }
</style>
