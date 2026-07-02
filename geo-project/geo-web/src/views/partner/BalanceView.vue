<template>
  <div class="partner-page partner-balance-page">
    <section v-loading="loading" class="balance-hero">
      <div class="balance-main">
        <div class="eyebrow">合伙人积分账户</div>
        <div class="hero-title-row">
          <h1>余额与扣款</h1>
          <el-tag :type="accountStatusTag" effect="light" round>
            {{ dictStore.label('partner_status', account?.status) || account?.status || '未启用' }}
          </el-tag>
        </div>
        <div class="balance-value">{{ formatPoints(account?.currentBalance) }}</div>
        <div class="balance-caption">当前可用积分。项目审批、诊断报告超额生成等会从该账户扣减。</div>
      </div>

      <div class="balance-stat-grid">
        <div class="balance-stat">
          <span>累计入账</span>
          <strong>{{ formatPoints(account?.totalRecharge) }}</strong>
        </div>
        <div class="balance-stat">
          <span>累计扣减</span>
          <strong>{{ formatPoints(account?.totalDeduction) }}</strong>
        </div>
        <div class="balance-stat">
          <span>账户币种</span>
          <strong>{{ account?.currency || 'CNY' }}</strong>
        </div>
      </div>
    </section>

    <section class="recharge-guide">
      <div class="guide-copy">
        <div class="guide-icon">i</div>
        <div>
          <h2>充值请联系专属客服工作人员</h2>
          <p>请提交充值金额、转账截图或付款凭证。总部处理人员核验后，会在合伙人详情页完成积分入账。</p>
        </div>
      </div>
      <div class="guide-flow">
        <span>服务群提交</span>
        <i></i>
        <span>总部核验</span>
        <i></i>
        <span>后台入账</span>
        <i></i>
        <span>流水可查</span>
      </div>
    </section>

    <section class="txn-panel">
      <div class="panel-header">
        <div>
          <h2>积分流水</h2>
          <p>查看充值入账、项目扣款、手工调整等账户变动记录。</p>
        </div>
        <div class="filter-row">
          <el-select v-model="query.txnType" clearable placeholder="流水类型" class="filter-control" @change="onSearch">
            <el-option
              v-for="item in dictStore.options('partner_txn_type')"
              :key="item.dictKey"
              :label="item.dictValue"
              :value="item.dictKey"
            />
          </el-select>
          <el-select v-model="query.bizType" clearable placeholder="业务类型" class="filter-control" @change="onSearch">
            <el-option
              v-for="item in dictStore.options('partner_biz_type')"
              :key="item.dictKey"
              :label="item.dictValue"
              :value="item.dictKey"
            />
          </el-select>
          <el-date-picker
            v-model="query.dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            class="date-filter"
            @change="onSearch"
          />
        </div>
      </div>

      <DataState :loading="loading" :empty="!loading && txns.length === 0" empty-text="暂无积分流水">
        <el-table :data="txns" class="txn-table" table-layout="fixed">
          <el-table-column label="类型" width="120">
            <template #default="scope">
              <span class="txn-type" :class="txnTypeClass(scope.row.txnType)">
                {{ dictStore.label('partner_txn_type', scope.row.txnType) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="业务" width="150">
            <template #default="scope">{{ dictStore.label('partner_biz_type', scope.row.bizType) }}</template>
          </el-table-column>
          <el-table-column label="变动积分" width="130">
            <template #default="scope">
              <span class="amount-text" :class="amountClass(scope.row.amount)">
                {{ signedPoints(scope.row.amount) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="变动前" width="130">
            <template #default="scope">{{ formatPoints(scope.row.balanceBefore) }}</template>
          </el-table-column>
          <el-table-column label="变动后" width="130">
            <template #default="scope">{{ formatPoints(scope.row.balanceAfter) }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
          <el-table-column label="时间" width="180">
            <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
          </el-table-column>
        </el-table>

        <div class="table-footer">
          <el-pagination
            background
            layout="prev, pager, next, total"
            :current-page="page.current"
            :page-size="page.size"
            :total="page.total"
            @current-change="onPageChange"
          />
        </div>
      </DataState>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getPartnerAccount,
  getPartnerAccountTxns,
  type PartnerAccount,
  type PartnerTxn,
} from '@/api/partner'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'
import DataState from '@/components/ui/DataState.vue'

const userStore = useUserStore()
const dictStore = useDictStore()
const loading = ref(false)
const account = ref<PartnerAccount | null>(null)
const txns = ref<PartnerTxn[]>([])
const page = reactive({ current: 1, size: 20, total: 0 })
const query = reactive<{
  txnType: string
  bizType: string
  dateRange: [string, string] | []
}>({ txnType: '', bizType: '', dateRange: [] })

const accountStatusTag = computed(() => {
  if (account.value?.status === 'active') return 'success'
  if (account.value?.status === 'paused') return 'warning'
  return 'info'
})

function formatPoints(v?: number | null) {
  if (v == null) return '-'
  return Number(v).toFixed(2)
}

function signedPoints(v?: number | null) {
  if (v == null) return '-'
  const value = Number(v)
  const prefix = value > 0 ? '+' : ''
  return `${prefix}${value.toFixed(2)}`
}

function amountClass(v?: number | null) {
  const value = Number(v || 0)
  if (value > 0) return 'is-credit'
  if (value < 0) return 'is-debit'
  return 'is-neutral'
}

function txnTypeClass(type?: string | null) {
  if (type === 'recharge') return 'is-credit'
  if (type === 'deduction') return 'is-debit'
  return 'is-adjust'
}

function formatDateTime(value?: string | null) {
  if (!value) return '-'
  const normalized = value.replace('T', ' ').replace(/\.\d+$/, '')
  return normalized.length >= 19 ? normalized.slice(0, 19) : normalized
}

async function load() {
  const partnerId = userStore.userInfo?.partnerId
  if (!partnerId) {
    ElMessage.error('当前账号未绑定合伙人信息')
    return
  }
  loading.value = true
  try {
    const [dateFrom, dateTo] = query.dateRange || []
    const [accountRes, txnRes] = await Promise.all([
      getPartnerAccount(partnerId),
      getPartnerAccountTxns(partnerId, {
        current: page.current,
        size: page.size,
        txnType: query.txnType || undefined,
        bizType: query.bizType || undefined,
        dateFrom,
        dateTo,
      }),
    ])
    account.value = accountRes.data.data
    txns.value = txnRes.data.data.records || []
    page.total = txnRes.data.data.total || 0
  } catch {
    account.value = null
    txns.value = []
    page.total = 0
  } finally {
    loading.value = false
  }
}

function onPageChange(v: number) {
  page.current = v
  load()
}

function onSearch() {
  page.current = 1
  load()
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  await load()
})
</script>

<style scoped>
.partner-balance-page {
  display: grid;
  gap: 18px;
}

.balance-hero,
.recharge-guide,
.txn-panel {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.06);
}

.balance-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(360px, 0.9fr);
  overflow: hidden;
}

.balance-main {
  padding: 28px 30px;
  background: linear-gradient(135deg, #f8fbff 0%, #eef6ff 100%);
}

.eyebrow {
  color: #2563eb;
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0;
}

.hero-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
}

.hero-title-row h1,
.guide-copy h2,
.panel-header h2 {
  margin: 0;
  color: #0f172a;
  font-weight: 900;
  letter-spacing: 0;
}

.hero-title-row h1 {
  font-size: 24px;
}

.balance-value {
  margin-top: 18px;
  color: #0f172a;
  font-size: 44px;
  font-weight: 900;
  line-height: 1;
}

.balance-caption {
  max-width: 520px;
  margin-top: 10px;
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.6;
}

.balance-stat-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  align-content: stretch;
}

.balance-stat {
  display: flex;
  min-height: 100%;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  padding: 24px 22px;
  border-left: 1px solid #e2e8f0;
}

.balance-stat span {
  color: #64748b;
  font-size: 13px;
  font-weight: 800;
}

.balance-stat strong {
  color: #0f172a;
  font-size: 22px;
  font-weight: 900;
  line-height: 1.2;
}

.recharge-guide {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 18px 22px;
}

.guide-copy {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-width: 0;
}

.guide-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  flex: 0 0 auto;
  border-radius: 8px;
  background: #e0f2fe;
  color: #0369a1;
  font-weight: 900;
}

.guide-copy h2,
.panel-header h2 {
  font-size: 18px;
}

.guide-copy p,
.panel-header p {
  margin: 5px 0 0;
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.55;
}

.guide-flow {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  gap: 8px;
}

.guide-flow span {
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #334155;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.guide-flow i {
  width: 18px;
  height: 1px;
  background: #cbd5e1;
}

.txn-panel {
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 22px;
  border-bottom: 1px solid #e2e8f0;
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.filter-control {
  width: 150px;
}

.date-filter {
  width: 360px;
}

.txn-table {
  width: calc(100% - 44px);
  margin: 20px 22px 0;
}

.txn-table :deep(.el-table__header th) {
  background: #f8fafc;
  color: #475569;
  font-weight: 900;
}

.txn-table :deep(.el-table__cell) {
  color: #334155;
  font-weight: 600;
}

.txn-type {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 900;
}

.txn-type.is-credit {
  background: #ecfdf5;
  color: #047857;
}

.txn-type.is-debit {
  background: #fef2f2;
  color: #b91c1c;
}

.txn-type.is-adjust {
  background: #eef2ff;
  color: #3730a3;
}

.amount-text {
  font-weight: 900;
}

.amount-text.is-credit {
  color: #047857;
}

.amount-text.is-debit {
  color: #dc2626;
}

.amount-text.is-neutral {
  color: #64748b;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  padding: 16px 22px 22px;
}

@media (max-width: 1100px) {
  .balance-hero {
    grid-template-columns: 1fr;
  }

  .balance-stat-grid {
    border-top: 1px solid #e2e8f0;
  }

  .recharge-guide,
  .panel-header {
    align-items: stretch;
    flex-direction: column;
  }

  .guide-flow {
    flex-wrap: wrap;
  }
}

@media (max-width: 768px) {
  .balance-stat-grid {
    grid-template-columns: 1fr;
  }

  .balance-stat {
    border-left: 0;
    border-top: 1px solid #e2e8f0;
  }

  .filter-row,
  .filter-control,
  .date-filter {
    width: 100%;
  }
}
</style>
