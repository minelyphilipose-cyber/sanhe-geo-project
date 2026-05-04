<template>
  <div class="space-y-4">
    <el-page-header content="合伙人详情" @back="$router.back()" />

    <el-card v-loading="loading">
      <template #header>
        <div class="flex items-center justify-between">
          <span>基础信息</span>
          <el-tag>{{ dictStore.label('partner_status', partner?.status) }}</el-tag>
        </div>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="编号">{{ partner?.partnerCode }}</el-descriptions-item>
        <el-descriptions-item label="名称">{{ partner?.partnerName }}</el-descriptions-item>
        <el-descriptions-item label="等级">{{ dictStore.label('partner_level', partner?.partnerLevel) }}</el-descriptions-item>
        <el-descriptions-item label="折扣">{{ partner ? (partner.discountRate * 100).toFixed(1) + '%' : '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ partner?.contactName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ partner?.contactPhone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="城市">{{ partner?.city || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ partner?.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card v-loading="accountLoading">
      <template #header>
        <div class="flex items-center justify-between">
          <span>虚拟账户</span>
          <div class="space-x-2">
            <el-button v-if="canAuditPartnerRecharge" type="primary" @click="rechargeVisible = true">直接入账</el-button>
            <el-button v-if="canAdjustPartnerAccount" @click="adjustVisible = true">手工调整</el-button>
          </div>
        </div>
      </template>
      <el-descriptions :column="4" border>
        <el-descriptions-item label="当前余额">{{ centsToYuan(account?.currentBalance) }}</el-descriptions-item>
        <el-descriptions-item label="累计充值">{{ centsToYuan(account?.totalRecharge) }}</el-descriptions-item>
        <el-descriptions-item label="累计扣款">{{ centsToYuan(account?.totalDeduction) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ dictStore.label('partner_status', account?.status) }}</el-descriptions-item>
      </el-descriptions>

      <div class="mt-4 flex items-center gap-2">
        <el-select v-model="txnQuery.txnType" clearable placeholder="流水类型" style="width: 160px" @change="reloadTxns">
          <el-option
            v-for="item in dictStore.options('partner_txn_type')"
            :key="item.dictKey"
            :label="item.dictValue"
            :value="item.dictKey"
          />
        </el-select>
        <el-select v-model="txnQuery.bizType" clearable placeholder="业务类型" style="width: 180px" @change="reloadTxns">
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
          range-separator="to"
          start-placeholder="Start"
          end-placeholder="End"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 360px"
          @change="reloadTxns"
        />
      </div>

      <DataState :loading="accountLoading" :empty="!accountLoading && txns.length === 0" empty-text="暂无账户流水">
        <el-table class="mt-4" :data="txns" border>
          <el-table-column prop="txnNo" label="流水号" min-width="220" />
          <el-table-column label="类型" width="120">
            <template #default="scope">{{ dictStore.label('partner_txn_type', scope.row.txnType) }}</template>
          </el-table-column>
          <el-table-column label="业务" width="140">
            <template #default="scope">{{ dictStore.label('partner_biz_type', scope.row.bizType) }}</template>
          </el-table-column>
          <el-table-column label="金额(元)" width="120">
            <template #default="scope">{{ centsToYuan(scope.row.amount) }}</template>
          </el-table-column>
          <el-table-column label="余额前(元)" width="130">
            <template #default="scope">{{ centsToYuan(scope.row.balanceBefore) }}</template>
          </el-table-column>
          <el-table-column label="余额后(元)" width="130">
            <template #default="scope">{{ centsToYuan(scope.row.balanceAfter) }}</template>
          </el-table-column>
          <el-table-column prop="offlineReference" label="线下凭证" width="160" />
          <el-table-column prop="remark" label="备注" min-width="180" />
        </el-table>

        <div class="mt-4 flex justify-end">
          <el-pagination
            background
            layout="prev, pager, next, total"
            :current-page="txnPage.current"
            :page-size="txnPage.size"
            :total="txnPage.total"
            @current-change="onTxnPageChange"
          />
        </div>
      </DataState>
    </el-card>

    <el-card v-loading="orderLoading">
      <template #header>
        <div class="flex items-center justify-between">
          <span>充值申请</span>
          <el-select v-model="orderQuery.status" clearable placeholder="申请状态" style="width: 160px" @change="reloadOrders">
            <el-option label="待审核" value="pending" />
            <el-option label="已通过" value="approved" />
            <el-option label="已驳回" value="rejected" />
            <el-option label="已取消" value="cancelled" />
            <el-option label="已超时" value="expired" />
          </el-select>
        </div>
      </template>

      <el-alert
        v-if="overdueOrderCount > 0"
        class="mb-3"
        type="warning"
        :closable="false"
        :title="`有 ${overdueOrderCount} 笔待审核充值申请已超过处理时间`"
      />

      <DataState :loading="orderLoading" :empty="!orderLoading && rechargeOrders.length === 0" empty-text="暂无充值申请">
        <el-table :data="rechargeOrders" border :row-class-name="orderRowClassName">
          <el-table-column prop="orderNo" label="申请单号" min-width="210" />
          <el-table-column label="金额(元)" width="120">
            <template #default="scope">{{ centsToYuan(scope.row.amount) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="scope">
              <el-tag :type="rechargeOrderStatusTag(scope.row.status)">
                {{ rechargeOrderStatusLabel(scope.row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="offlineReference" label="线下凭证" width="160" />
          <el-table-column prop="applyRemark" label="申请备注" min-width="180" />
          <el-table-column prop="rejectReason" label="驳回原因" min-width="180" />
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
                size="small"
                type="primary"
                @click="openAuditDialog(scope.row, 'approve')"
              >
                通过
              </el-button>
              <el-button
                v-if="canAuditPartnerRecharge && scope.row.status === 'pending'"
                size="small"
                type="danger"
                @click="openAuditDialog(scope.row, 'reject')"
              >
                驳回
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="mt-4 flex justify-end">
          <el-pagination
            background
            layout="prev, pager, next, total"
            :current-page="orderPage.current"
            :page-size="orderPage.size"
            :total="orderPage.total"
            @current-change="onOrderPageChange"
          />
        </div>
      </DataState>
    </el-card>

    <el-dialog v-model="rechargeVisible" title="直接入账" width="520px">
      <el-form :model="rechargeForm" label-width="100px">
        <el-form-item label="金额(元)" required>
          <el-input-number v-model="rechargeForm.amountYuan" :min="0.01" :precision="2" :step="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="线下凭证"><el-input v-model="rechargeForm.offlineReference" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="rechargeForm.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rechargeVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitRecharge">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="adjustVisible" title="手工调整" width="520px">
      <el-form :model="adjustForm" label-width="100px">
        <el-form-item label="金额(元)" required>
          <el-input-number v-model="adjustForm.amountYuan" :precision="2" :step="10" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="adjustForm.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAdjust">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="auditVisible" :title="auditForm.action === 'approve' ? '通过充值申请' : '驳回充值申请'" width="520px">
      <el-form :model="auditForm" label-width="100px">
        <el-form-item label="申请单号">{{ currentOrder?.orderNo }}</el-form-item>
        <el-form-item label="金额(元)">{{ centsToYuan(currentOrder?.amount) }}</el-form-item>
        <el-form-item v-if="auditForm.action === 'reject'" label="驳回原因" required>
          <el-input v-model="auditForm.rejectReason" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="审核备注">
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
import { useRoute } from 'vue-router'
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

function centsToYuan(v?: number | null) {
  if (v == null) return '-'
  return Number(v).toFixed(2)
}

function yuanToCents(v: number) {
  return Number(v.toFixed(2))
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
    ElMessage.warning('充值金额需大于0')
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
    ElMessage.warning('请输入调整金额')
    return
  }
  submitting.value = true
  try {
    await ElMessageBox.confirm(
      `确认执行余额调整 ${adjustForm.amountYuan > 0 ? '+' : ''}${adjustForm.amountYuan.toFixed(2)} 元？`,
      '余额调整确认',
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

function rechargeOrderStatusTag(status: string): 'success' | 'warning' | 'danger' | 'info' {
  const mapping: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
    pending: 'warning',
    approved: 'success',
    rejected: 'danger',
    cancelled: 'info',
    expired: 'danger',
  }
  return mapping[status] || 'info'
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
:deep(.overdue-recharge-order) {
  --el-table-tr-bg-color: var(--el-color-warning-light-9);
}
</style>

