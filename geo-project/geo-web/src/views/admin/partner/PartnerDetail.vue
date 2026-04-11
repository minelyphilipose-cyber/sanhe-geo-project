<template>
  <div class="space-y-4">
    <el-page-header content="合伙人详情" @back="$router.back()" />

    <el-card v-loading="loading">
      <template #header>
        <div class="flex items-center justify-between">
          <span>基础信息</span>
          <el-tag>{{ partner?.status || '-' }}</el-tag>
        </div>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="编号">{{ partner?.partnerCode }}</el-descriptions-item>
        <el-descriptions-item label="名称">{{ partner?.partnerName }}</el-descriptions-item>
        <el-descriptions-item label="等级">{{ partner?.partnerLevel }}</el-descriptions-item>
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
            <el-button type="primary" @click="rechargeVisible = true">充值录入</el-button>
            <el-button @click="adjustVisible = true">手工调整</el-button>
          </div>
        </div>
      </template>
      <el-descriptions :column="4" border>
        <el-descriptions-item label="当前余额">{{ centsToYuan(account?.currentBalance) }}</el-descriptions-item>
        <el-descriptions-item label="累计充值">{{ centsToYuan(account?.totalRecharge) }}</el-descriptions-item>
        <el-descriptions-item label="累计扣款">{{ centsToYuan(account?.totalDeduction) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ account?.status || '-' }}</el-descriptions-item>
      </el-descriptions>

      <DataState :loading="accountLoading" :empty="!accountLoading && txns.length === 0" empty-text="暂无账户流水">
        <el-table class="mt-4" :data="txns" border>
          <el-table-column prop="txnNo" label="流水号" min-width="220" />
          <el-table-column prop="txnType" label="类型" width="120" />
          <el-table-column prop="bizType" label="业务" width="140" />
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

    <el-dialog v-model="rechargeVisible" title="充值录入" width="520px">
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
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adjustPartnerAccount,
  getPartnerAccount,
  getPartnerAccountTxns,
  getPartnerDetail,
  rechargePartnerAccount,
  type PartnerAccount,
  type PartnerItem,
  type PartnerTxn,
} from '@/api/partner'
import DataState from '@/components/ui/DataState.vue'

const route = useRoute()
const partnerId = Number(route.params.id)
const hasValidId = Number.isFinite(partnerId) && partnerId > 0

const loading = ref(false)
const accountLoading = ref(false)
const submitting = ref(false)

const partner = ref<PartnerItem | null>(null)
const account = ref<PartnerAccount | null>(null)
const txns = ref<PartnerTxn[]>([])
const txnPage = reactive({ current: 1, size: 10, total: 0 })

const rechargeVisible = ref(false)
const adjustVisible = ref(false)
const rechargeForm = reactive({ amountYuan: 100, offlineReference: '', remark: '' })
const adjustForm = reactive({ amountYuan: 0, remark: '' })

function centsToYuan(v?: number | null) {
  if (v == null) return '-'
  return (v / 100).toFixed(2)
}

function yuanToCents(v: number) {
  return Math.round(v * 100)
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
    const [accountRes, txnRes] = await Promise.all([
      getPartnerAccount(partnerId),
      getPartnerAccountTxns(partnerId, { current: txnPage.current, size: txnPage.size }),
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

function onTxnPageChange(v: number) {
  txnPage.current = v
  loadAccount()
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
    await loadAccount()
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

onMounted(async () => {
  if (!hasValidId) {
    ElMessage.error('无效的合伙人ID')
    return
  }
  await loadBase()
  await loadAccount()
})
</script>

