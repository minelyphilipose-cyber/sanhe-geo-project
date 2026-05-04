<template>
  <div class="space-y-4">
    <el-card v-loading="loading">
      <template #header>
        <div class="flex items-center justify-between">
          <span>余额总览</span>
          <div class="flex items-center gap-2">
            <el-button v-if="canApplyRecharge" type="primary" @click="applyVisible = true">发起充值申请</el-button>
            <el-tag>{{ dictStore.label('partner_status', account?.status) }}</el-tag>
          </div>
        </div>
      </template>
      <el-descriptions :column="4" border>
        <el-descriptions-item label="当前余额">{{ centsToYuan(account?.currentBalance) }}</el-descriptions-item>
        <el-descriptions-item label="累计充值">{{ centsToYuan(account?.totalRecharge) }}</el-descriptions-item>
        <el-descriptions-item label="累计扣款">{{ centsToYuan(account?.totalDeduction) }}</el-descriptions-item>
        <el-descriptions-item label="币种">{{ account?.currency || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card v-loading="orderLoading">
      <template #header>
        <div class="flex items-center justify-between">
          <span>充值申请</span>
          <el-select v-model="orderQuery.status" clearable placeholder="申请状态" style="width: 160px" @change="onOrderSearch">
            <el-option label="待审核" value="pending" />
            <el-option label="已通过" value="approved" />
            <el-option label="已驳回" value="rejected" />
            <el-option label="已取消" value="cancelled" />
            <el-option label="已超时" value="expired" />
          </el-select>
        </div>
      </template>

      <DataState :loading="orderLoading" :empty="!orderLoading && rechargeOrders.length === 0" empty-text="暂无充值申请">
        <el-table :data="rechargeOrders" border>
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
          <el-table-column prop="expiresAt" label="处理截止" width="180" />
          <el-table-column prop="createdAt" label="申请时间" width="180" />
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="scope">
              <el-button
                v-if="canApplyRecharge && scope.row.status === 'pending'"
                size="small"
                type="danger"
                @click="cancelRecharge(scope.row)"
              >
                取消
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

    <el-card>
      <template #header>
        <div class="flex items-center gap-2">
          <el-select v-model="query.txnType" clearable placeholder="流水类型" style="width: 160px" @change="onSearch">
            <el-option
              v-for="item in dictStore.options('partner_txn_type')"
              :key="item.dictKey"
              :label="item.dictValue"
              :value="item.dictKey"
            />
          </el-select>
          <el-select v-model="query.bizType" clearable placeholder="业务类型" style="width: 180px" @change="onSearch">
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
            range-separator="to"
            start-placeholder="Start"
            end-placeholder="End"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 360px"
            @change="onSearch"
          />
        </div>
      </template>

      <DataState :loading="loading" :empty="!loading && txns.length === 0" empty-text="暂无流水数据">
        <el-table :data="txns" border>
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
          <el-table-column label="余额前(元)" width="120">
            <template #default="scope">{{ centsToYuan(scope.row.balanceBefore) }}</template>
          </el-table-column>
          <el-table-column label="余额后(元)" width="120">
            <template #default="scope">{{ centsToYuan(scope.row.balanceAfter) }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="时间" width="170" />
        </el-table>

        <div class="mt-4 flex justify-end">
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
    </el-card>

    <el-dialog v-model="applyVisible" title="充值申请" width="520px">
      <el-form :model="applyForm" label-width="100px">
        <el-form-item label="金额(元)" required>
          <el-input-number v-model="applyForm.amountYuan" :min="0.01" :precision="2" :step="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="线下凭证"><el-input v-model="applyForm.offlineReference" maxlength="128" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="applyForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitApplyRecharge">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  applyPartnerRecharge,
  cancelPartnerRechargeOrder,
  getPartnerAccount,
  getPartnerAccountTxns,
  getPartnerRechargeOrders,
  type PartnerAccount,
  type PartnerRechargeOrder,
  type PartnerTxn,
} from '@/api/partner'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'
import DataState from '@/components/ui/DataState.vue'

const userStore = useUserStore()
const dictStore = useDictStore()
const loading = ref(false)
const orderLoading = ref(false)
const submitting = ref(false)
const account = ref<PartnerAccount | null>(null)
const txns = ref<PartnerTxn[]>([])
const rechargeOrders = ref<PartnerRechargeOrder[]>([])
const page = reactive({ current: 1, size: 20, total: 0 })
const orderPage = reactive({ current: 1, size: 10, total: 0 })
const query = reactive<{
  txnType: string
  bizType: string
  dateRange: [string, string] | []
}>({ txnType: '', bizType: '', dateRange: [] })
const orderQuery = reactive({ status: '' })
const applyVisible = ref(false)
const applyForm = reactive({ amountYuan: 100, offlineReference: '', remark: '' })
const canApplyRecharge = computed(() => userStore.hasPermission('partner.account.recharge.apply'))

function centsToYuan(v?: number | null) {
  if (v == null) return '-'
  return Number(v).toFixed(2)
}

function yuanToCents(v: number) {
  return Number(v.toFixed(2))
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

async function loadRechargeOrders() {
  const partnerId = userStore.userInfo?.partnerId
  if (!partnerId) return
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

function onPageChange(v: number) {
  page.current = v
  load()
}

function onSearch() {
  page.current = 1
  load()
}

function onOrderPageChange(v: number) {
  orderPage.current = v
  loadRechargeOrders()
}

function onOrderSearch() {
  orderPage.current = 1
  loadRechargeOrders()
}

async function submitApplyRecharge() {
  const partnerId = userStore.userInfo?.partnerId
  if (!partnerId) {
    ElMessage.error('当前账号未绑定合伙人信息')
    return
  }
  if (!applyForm.amountYuan || applyForm.amountYuan <= 0) {
    ElMessage.warning('充值金额需大于0')
    return
  }
  submitting.value = true
  try {
    await applyPartnerRecharge(partnerId, {
      amount: yuanToCents(applyForm.amountYuan),
      offlineReference: applyForm.offlineReference || undefined,
      remark: applyForm.remark || undefined,
    })
    ElMessage.success('充值申请已提交')
    applyVisible.value = false
    applyForm.amountYuan = 100
    applyForm.offlineReference = ''
    applyForm.remark = ''
    await loadRechargeOrders()
  } finally {
    submitting.value = false
  }
}

async function cancelRecharge(order: PartnerRechargeOrder) {
  const partnerId = userStore.userInfo?.partnerId
  if (!partnerId) return
  await ElMessageBox.confirm('确认取消该充值申请？', '取消充值申请', {
    type: 'warning',
    confirmButtonText: '确认取消',
    cancelButtonText: '返回',
  })
  await cancelPartnerRechargeOrder(partnerId, order.id)
  ElMessage.success('充值申请已取消')
  await loadRechargeOrders()
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

onMounted(async () => {
  await dictStore.ensureLoaded()
  await Promise.all([load(), loadRechargeOrders()])
})
</script>
