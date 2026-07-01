<template>
  <div class="partner-detail-page admin-page">
    <div class="admin-page-header partner-detail-header">
      <div>
        <div class="admin-page-kicker">合伙人</div>
        <h1 class="admin-page-title">{{ partner?.partnerName || '合伙人详情' }}</h1>
        <div class="admin-page-subtitle">
          {{ partner?.partnerCode || '查看合伙人基础信息、虚拟账户、充值申请与积分流水。' }}
        </div>
      </div>
      <div class="admin-page-actions">
        <span v-if="partner" class="admin-status-tag" :class="partnerStatusClass(partner.status)">
          {{ dictStore.label('partner_status', partner.status) }}
        </span>
        <el-button @click="router.back()">返回</el-button>
      </div>
    </div>

    <div class="admin-metric-grid partner-account-grid">
      <div class="admin-metric-card" style="--metric-accent: #2563eb; --metric-tone: #eff6ff">
        <span class="admin-metric-label">当前积分</span>
        <strong class="admin-metric-value">{{ centsToYuan(account?.currentBalance) }}</strong>
        <span class="admin-metric-hint">虚拟账户可用积分</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #059669; --metric-tone: #ecfdf5">
        <span class="admin-metric-label">累计充值</span>
        <strong class="admin-metric-value">{{ centsToYuan(account?.totalRecharge) }}</strong>
        <span class="admin-metric-hint">直接入账与审核通过累计</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #ef4444; --metric-tone: #fef2f2">
        <span class="admin-metric-label">累计扣款</span>
        <strong class="admin-metric-value">{{ centsToYuan(account?.totalDeduction) }}</strong>
        <span class="admin-metric-hint">项目消耗与手工调整扣减</span>
      </div>
      <div class="admin-metric-card" style="--metric-accent: #f59e0b; --metric-tone: #fffbeb">
        <span class="admin-metric-label">待审申请</span>
        <strong class="admin-metric-value">{{ pendingOrderCount }}</strong>
        <span class="admin-metric-hint">{{ overdueOrderCount > 0 ? `${overdueOrderCount} 笔已超时` : '暂无超时申请' }}</span>
      </div>
    </div>

    <div class="partner-overview-grid">
      <el-card shadow="never" class="admin-surface overview-card" v-loading="loading">
        <div class="section-head">
          <div>
            <div class="section-kicker">基础信息</div>
            <h2 class="section-title">合作档案</h2>
          </div>
        </div>
        <div class="profile-grid">
          <div class="profile-item">
            <span>编号</span>
            <strong>{{ partner?.partnerCode || '-' }}</strong>
          </div>
          <div class="profile-item">
            <span>等级</span>
            <strong>{{ dictStore.label('partner_level', partner?.partnerLevel) }}</strong>
          </div>
          <div class="profile-item">
            <span>折扣</span>
            <strong>{{ partner ? `${(partner.discountRate * 100).toFixed(1)}%` : '-' }}</strong>
          </div>
          <div class="profile-item">
            <span>诊断免费次数</span>
            <strong>{{ partner?.presaleReportFreeQuotaLimit ?? 0 }} 次</strong>
          </div>
          <div class="profile-item">
            <span>超额诊断积分</span>
            <strong>{{ formatPoints(partner?.presaleReportExtraPoints) }} / 次</strong>
          </div>
          <div class="profile-item">
            <span>联系人</span>
            <strong>{{ partner?.contactName || '-' }}</strong>
          </div>
          <div class="profile-item">
            <span>电话</span>
            <strong>{{ partner?.contactPhone || '-' }}</strong>
          </div>
          <div class="profile-item">
            <span>城市</span>
            <strong>{{ partner?.city || '-' }}</strong>
          </div>
        </div>
        <div class="remark-box">
          <span>备注</span>
          <p>{{ partner?.remark || '暂无备注' }}</p>
        </div>
      </el-card>

      <el-card shadow="never" class="admin-surface account-action-card" v-loading="accountLoading">
        <div class="section-head">
          <div>
            <div class="section-kicker">虚拟账户</div>
            <h2 class="section-title">积分操作</h2>
          </div>
          <span class="admin-status-tag" :class="partnerStatusClass(account?.status)">
            {{ dictStore.label('partner_status', account?.status) }}
          </span>
        </div>
        <div class="action-stack">
          <button
            v-if="canAuditPartnerRecharge"
            type="button"
            class="account-action is-primary"
            @click="rechargeVisible = true"
          >
            <strong>直接入账</strong>
            <span>线下确认后直接增加账户积分</span>
          </button>
          <button
            v-if="canAdjustPartnerAccount"
            type="button"
            class="account-action"
            @click="adjustVisible = true"
          >
            <strong>手工调整</strong>
            <span>用于账务修正或特殊补扣</span>
          </button>
        </div>
      </el-card>
    </div>

    <el-card shadow="never" class="admin-table-card partner-table-card" v-loading="accountLoading">
      <div class="table-header">
        <div>
          <div class="table-title">账户流水</div>
          <div class="table-subtitle">筛选交易类型、业务类型和时间范围，核对积分变更链路。</div>
        </div>
        <div class="compact-filters">
          <el-select v-model="txnQuery.txnType" clearable placeholder="流水类型" class="filter-small" @change="reloadTxns">
            <el-option
              v-for="item in dictStore.options('partner_txn_type')"
              :key="item.dictKey"
              :label="item.dictValue"
              :value="item.dictKey"
            />
          </el-select>
          <el-select v-model="txnQuery.bizType" clearable placeholder="业务类型" class="filter-small" @change="reloadTxns">
            <el-option
              v-for="item in dictStore.options('partner_biz_type')"
              :key="item.dictKey"
              :label="item.dictValue"
              :value="item.dictKey"
            />
          </el-select>
          <el-date-picker
            v-model="txnQuery.dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            value-format="YYYY-MM-DD HH:mm:ss"
            class="filter-date"
            @change="reloadTxns"
          />
        </div>
      </div>

      <DataState :loading="accountLoading" :empty="!accountLoading && txns.length === 0" empty-text="暂无账户流水">
        <el-table :data="txns" border table-layout="fixed">
          <el-table-column prop="txnNo" label="流水号" min-width="220" show-overflow-tooltip />
          <el-table-column label="类型" width="120">
            <template #default="scope">
              <span class="admin-mini-pill is-blue">{{ dictStore.label('partner_txn_type', scope.row.txnType) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="业务" width="140">
            <template #default="scope">{{ dictStore.label('partner_biz_type', scope.row.bizType) }}</template>
          </el-table-column>
          <el-table-column label="积分" width="120">
            <template #default="scope">
              <span :class="amountClass(scope.row.amount)">{{ centsToYuan(scope.row.amount) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="变更前积分" width="120">
            <template #default="scope">{{ centsToYuan(scope.row.balanceBefore) }}</template>
          </el-table-column>
          <el-table-column label="变更后积分" width="120">
            <template #default="scope">{{ centsToYuan(scope.row.balanceAfter) }}</template>
          </el-table-column>
          <el-table-column prop="offlineReference" label="线下凭证" width="160" show-overflow-tooltip />
          <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        </el-table>
      </DataState>

      <div class="admin-table-footer">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :current-page="txnPage.current"
          :page-size="txnPage.size"
          :total="txnPage.total"
          @current-change="onTxnPageChange"
        />
      </div>
    </el-card>

    <el-card shadow="never" class="admin-table-card partner-table-card" v-loading="orderLoading">
      <div class="table-header">
        <div>
          <div class="table-title">充值申请</div>
          <div class="table-subtitle">跟进合伙人提交的充值申请、审核状态和超时风险。</div>
        </div>
        <el-select v-model="orderQuery.status" clearable placeholder="申请状态" class="filter-small" @change="reloadOrders">
          <el-option label="待审核" value="pending" />
          <el-option label="已通过" value="approved" />
          <el-option label="已驳回" value="rejected" />
          <el-option label="已取消" value="cancelled" />
          <el-option label="已超时" value="expired" />
        </el-select>
      </div>

      <el-alert
        v-if="overdueOrderCount > 0"
        class="order-alert"
        type="warning"
        :closable="false"
        :title="`有 ${overdueOrderCount} 笔待审核充值申请已超过处理时间`"
      />

      <DataState :loading="orderLoading" :empty="!orderLoading && rechargeOrders.length === 0" empty-text="暂无充值申请">
        <el-table :data="rechargeOrders" border table-layout="fixed" :row-class-name="orderRowClassName">
          <el-table-column prop="orderNo" label="申请单号" min-width="210" show-overflow-tooltip />
          <el-table-column label="积分" width="120">
            <template #default="scope">{{ centsToYuan(scope.row.amount) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="scope">
              <span class="admin-status-tag" :class="orderStatusClass(scope.row.status)">
                {{ rechargeOrderStatusLabel(scope.row.status) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="offlineReference" label="线下凭证" width="160" show-overflow-tooltip />
          <el-table-column prop="applyRemark" label="申请备注" min-width="180" show-overflow-tooltip />
          <el-table-column prop="rejectReason" label="驳回原因" min-width="180" show-overflow-tooltip />
          <el-table-column label="到期时间" width="180">
            <template #default="scope">
              <span :class="{ 'text-red-500': isRechargeOrderOverdue(scope.row) }">{{ scope.row.expiresAt || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="申请时间" width="180" />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="scope">
              <el-button
                v-if="canAuditPartnerRecharge && scope.row.status === 'pending'"
                link
                type="primary"
                @click="openAuditDialog(scope.row, 'approve')"
              >
                通过
              </el-button>
              <el-button
                v-if="canAuditPartnerRecharge && scope.row.status === 'pending'"
                link
                type="danger"
                @click="openAuditDialog(scope.row, 'reject')"
              >
                驳回
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </DataState>

      <div class="admin-table-footer">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :current-page="orderPage.current"
          :page-size="orderPage.size"
          :total="orderPage.total"
          @current-change="onOrderPageChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="rechargeVisible" title="直接入账" width="560px" class="admin-editor-dialog">
      <el-form :model="rechargeForm" label-width="100px" class="admin-dialog-form">
        <el-form-item label="积分" required>
          <el-input-number v-model="rechargeForm.amountYuan" :min="0.01" :precision="2" :step="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="线下凭证"><el-input v-model="rechargeForm.offlineReference" /></el-form-item>
        <el-form-item label="备注" class="is-full"><el-input v-model="rechargeForm.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rechargeVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitRecharge">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="adjustVisible" title="手工调整" width="560px" class="admin-editor-dialog">
      <el-form :model="adjustForm" label-width="100px" class="admin-dialog-form">
        <el-form-item label="积分" required>
          <el-input-number v-model="adjustForm.amountYuan" :precision="2" :step="10" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注" class="is-full"><el-input v-model="adjustForm.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAdjust">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="auditVisible" :title="auditForm.action === 'approve' ? '通过充值申请' : '驳回充值申请'" width="560px" class="admin-editor-dialog">
      <el-form :model="auditForm" label-width="100px" class="admin-dialog-form">
        <el-form-item label="申请单号">{{ currentOrder?.orderNo }}</el-form-item>
        <el-form-item label="积分">{{ centsToYuan(currentOrder?.amount) }}</el-form-item>
        <el-form-item v-if="auditForm.action === 'reject'" label="驳回原因" required class="is-full">
          <el-input v-model="auditForm.rejectReason" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="审核备注" class="is-full">
          <el-input v-model="auditForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAudit">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useDictStore } from '@/stores/dict'
import {
  adjustPartnerAccount,
  auditPartnerRechargeOrder,
  getPartnerAccount,
  getPartnerAccountTxns,
  getPartnerDetail,
  getPartnerRechargeOrders,
  rechargePartnerAccount,
  type PartnerAccount,
  type PartnerItem,
  type PartnerRechargeOrder,
  type PartnerTxn,
} from '@/api/partner'
import DataState from '@/components/ui/DataState.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const dictStore = useDictStore()
const canAdjustPartnerAccount = computed(() => userStore.hasPermission('partner.account.adjust'))
const canAuditPartnerRecharge = computed(() => userStore.hasPermission('partner.account.recharge.audit'))
const partnerId = Number(route.params.id)
const hasValidId = Number.isFinite(partnerId) && partnerId > 0

const loading = ref(false)
const accountLoading = ref(false)
const orderLoading = ref(false)
const submitting = ref(false)

const partner = ref<PartnerItem | null>(null)
const account = ref<PartnerAccount | null>(null)
const txns = ref<PartnerTxn[]>([])
const rechargeOrders = ref<PartnerRechargeOrder[]>([])
const txnPage = reactive({ current: 1, size: 10, total: 0 })
const orderPage = reactive({ current: 1, size: 10, total: 0 })
const txnQuery = reactive<{
  txnType: string
  bizType: string
  dateRange: [string, string] | []
}>({ txnType: '', bizType: '', dateRange: [] })
const orderQuery = reactive({ status: '' })

const rechargeVisible = ref(false)
const adjustVisible = ref(false)
const auditVisible = ref(false)
const rechargeForm = reactive({ amountYuan: 100, offlineReference: '', remark: '' })
const adjustForm = reactive({ amountYuan: 0, remark: '' })
const currentOrder = ref<PartnerRechargeOrder | null>(null)
const auditForm = reactive<{
  action: 'approve' | 'reject'
  rejectReason: string
  remark: string
}>({ action: 'approve', rejectReason: '', remark: '' })

const overdueOrderCount = computed(() => rechargeOrders.value.filter(isRechargeOrderOverdue).length)
const pendingOrderCount = computed(() => rechargeOrders.value.filter((item) => item.status === 'pending').length)

function centsToYuan(v?: number | null) {
  if (v == null) return '-'
  return Number(v).toFixed(2)
}

function formatPoints(v?: number | null) {
  return `${Number(v || 0).toFixed(2)} 积分`
}

function yuanToCents(v: number) {
  return Number(v.toFixed(2))
}

function partnerStatusClass(status?: string | null) {
  if (status === 'active') return 'is-success'
  if (status === 'paused' || status === 'pending') return 'is-warning'
  if (status === 'closed' || status === 'expired' || status === 'rejected') return 'is-danger'
  return 'is-muted'
}

function orderStatusClass(status: string) {
  if (status === 'approved') return 'is-success'
  if (status === 'pending') return 'is-warning'
  if (status === 'rejected' || status === 'expired') return 'is-danger'
  return 'is-muted'
}

function amountClass(amount: number) {
  if (amount > 0) return 'amount-positive'
  if (amount < 0) return 'amount-negative'
  return ''
}

async function loadBase() {
  loading.value = true
  try {
    const { data } = await getPartnerDetail(partnerId)
    partner.value = data.data
  } catch {
    partner.value = null
  } finally {
    loading.value = false
  }
}

async function loadAccount() {
  accountLoading.value = true
  try {
    const [dateFrom, dateTo] = txnQuery.dateRange || []
    const [accountRes, txnRes] = await Promise.all([
      getPartnerAccount(partnerId),
      getPartnerAccountTxns(partnerId, {
        current: txnPage.current,
        size: txnPage.size,
        txnType: txnQuery.txnType || undefined,
        bizType: txnQuery.bizType || undefined,
        dateFrom,
        dateTo,
      }),
    ])
    account.value = accountRes.data.data
    txns.value = txnRes.data.data.records || []
    txnPage.total = txnRes.data.data.total || 0
  } catch {
    account.value = null
    txns.value = []
    txnPage.total = 0
  } finally {
    accountLoading.value = false
  }
}

async function loadRechargeOrders() {
  orderLoading.value = true
  try {
    const { data } = await getPartnerRechargeOrders(partnerId, {
      current: orderPage.current,
      size: orderPage.size,
      status: orderQuery.status || undefined,
    })
    rechargeOrders.value = data.data.records || []
    orderPage.total = data.data.total || 0
  } catch {
    rechargeOrders.value = []
    orderPage.total = 0
  } finally {
    orderLoading.value = false
  }
}

function onTxnPageChange(v: number) {
  txnPage.current = v
  loadAccount()
}

function reloadTxns() {
  txnPage.current = 1
  loadAccount()
}

function onOrderPageChange(v: number) {
  orderPage.current = v
  loadRechargeOrders()
}

function reloadOrders() {
  orderPage.current = 1
  loadRechargeOrders()
}

async function submitRecharge() {
  if (!rechargeForm.amountYuan || rechargeForm.amountYuan <= 0) {
    ElMessage.warning('充值积分需大于0')
    return
  }
  submitting.value = true
  try {
    await rechargePartnerAccount(partnerId, {
      amount: yuanToCents(rechargeForm.amountYuan),
      offlineReference: rechargeForm.offlineReference || undefined,
      remark: rechargeForm.remark || undefined,
    })
    ElMessage.success('充值录入成功')
    rechargeVisible.value = false
    rechargeForm.amountYuan = 100
    rechargeForm.offlineReference = ''
    rechargeForm.remark = ''
    await Promise.all([loadAccount(), loadRechargeOrders()])
  } finally {
    submitting.value = false
  }
}

function openAuditDialog(order: PartnerRechargeOrder, action: 'approve' | 'reject') {
  currentOrder.value = order
  auditForm.action = action
  auditForm.rejectReason = ''
  auditForm.remark = ''
  auditVisible.value = true
}

async function submitAudit() {
  if (!currentOrder.value) return
  if (auditForm.action === 'reject' && !auditForm.rejectReason.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  submitting.value = true
  try {
    await auditPartnerRechargeOrder(partnerId, currentOrder.value.id, {
      action: auditForm.action,
      rejectReason: auditForm.action === 'reject' ? auditForm.rejectReason.trim() : undefined,
      remark: auditForm.remark || undefined,
    })
    ElMessage.success(auditForm.action === 'approve' ? '充值申请已通过' : '充值申请已驳回')
    auditVisible.value = false
    await Promise.all([loadAccount(), loadRechargeOrders()])
  } finally {
    submitting.value = false
  }
}

async function submitAdjust() {
  if (!adjustForm.amountYuan) {
    ElMessage.warning('请输入调整积分')
    return
  }
  submitting.value = true
  try {
    await ElMessageBox.confirm(
      `确认执行积分调整 ${adjustForm.amountYuan > 0 ? '+' : ''}${adjustForm.amountYuan.toFixed(2)}？`,
      '积分调整确认',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
    )
    await adjustPartnerAccount(partnerId, {
      amount: yuanToCents(adjustForm.amountYuan),
      remark: adjustForm.remark || undefined,
    })
    ElMessage.success('调整成功')
    adjustVisible.value = false
    adjustForm.amountYuan = 0
    adjustForm.remark = ''
    await loadAccount()
  } catch {
    // user canceled
  } finally {
    submitting.value = false
  }
}

function rechargeOrderStatusLabel(status: string) {
  const mapping: Record<string, string> = {
    pending: '待审核',
    cancelled: '已取消',
    approved: '已通过',
    rejected: '已驳回',
    expired: '已超时',
  }
  return mapping[status] || status || '-'
}

function isRechargeOrderOverdue(order: PartnerRechargeOrder) {
  return order.status === 'pending' && !!order.expiresAt && new Date(order.expiresAt).getTime() < Date.now()
}

function orderRowClassName({ row }: { row: PartnerRechargeOrder }) {
  return isRechargeOrderOverdue(row) ? 'overdue-recharge-order' : ''
}

onMounted(async () => {
  if (!hasValidId) {
    ElMessage.error('合伙人参数无效')
    return
  }
  await dictStore.ensureLoaded()
  await loadBase()
  await Promise.all([loadAccount(), loadRechargeOrders()])
})
</script>

<style scoped>
.partner-detail-header {
  align-items: center;
}

.partner-account-grid {
  margin-bottom: 0;
}

.partner-overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(320px, 0.75fr);
  gap: 14px;
}

.overview-card :deep(.el-card__body),
.account-action-card :deep(.el-card__body) {
  padding: 0;
}

.section-head {
  min-height: 58px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 18px 13px;
  border-bottom: 1px solid var(--admin-panel-border-soft);
  background: linear-gradient(90deg, #f8fbff 0%, #ffffff 58%, #f0fdf4 100%);
}

.section-kicker {
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
}

.section-title {
  margin: 4px 0 0;
  color: var(--admin-text-strong);
  font-size: 16px;
  line-height: 1.35;
  font-weight: 800;
}

.profile-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  padding: 16px;
}

.profile-item {
  min-width: 0;
  border: 1px solid #e7edf5;
  border-radius: 12px;
  background: #ffffff;
  padding: 13px 14px;
}

.profile-item span,
.remark-box span {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.profile-item strong {
  display: block;
  overflow: hidden;
  margin-top: 7px;
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.remark-box {
  margin: 0 16px 16px;
  border: 1px solid #e7edf5;
  border-radius: 12px;
  background: #f8fbff;
  padding: 13px 14px;
}

.remark-box p {
  margin: 7px 0 0;
  color: #334155;
  font-size: 13px;
  line-height: 1.6;
}

.action-stack {
  display: grid;
  gap: 12px;
  padding: 16px;
}

.account-action {
  width: 100%;
  min-height: 78px;
  cursor: pointer;
  text-align: left;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: #ffffff;
  padding: 14px 16px;
  transition: 0.18s ease;
}

.account-action:hover {
  border-color: #93c5fd;
  transform: translateY(-1px);
  box-shadow: 0 12px 24px rgba(37, 99, 235, 0.09);
}

.account-action.is-primary {
  background: linear-gradient(135deg, #eff6ff, #ffffff);
}

.account-action strong {
  display: block;
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
}

.account-action span {
  display: block;
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.partner-table-card :deep(.el-card__body) {
  padding: 0;
}

.table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
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

.compact-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.filter-small {
  width: 150px;
}

.filter-date {
  width: 360px;
}

.amount-positive {
  color: #047857;
  font-weight: 800;
}

.amount-negative {
  color: #b91c1c;
  font-weight: 800;
}

.order-alert {
  margin: 12px 16px 0;
  width: calc(100% - 32px);
}

:deep(.overdue-recharge-order) {
  --el-table-tr-bg-color: var(--el-color-warning-light-9);
}

@media (max-width: 1100px) {
  .partner-overview-grid,
  .profile-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .compact-filters,
  .compact-filters > *,
  .filter-small,
  .filter-date {
    width: 100%;
  }
}
</style>
