<template>
  <div class="space-y-4">
    <el-card v-loading="loading">
      <template #header>
        <div class="flex items-center justify-between">
          <span>余额总览</span>
          <el-tag>{{ dictStore.label('partner_status', account?.status) }}</el-tag>
        </div>
      </template>
      <el-descriptions :column="4" border>
        <el-descriptions-item label="当前余额">{{ centsToYuan(account?.currentBalance) }}</el-descriptions-item>
        <el-descriptions-item label="累计充值">{{ centsToYuan(account?.totalRecharge) }}</el-descriptions-item>
        <el-descriptions-item label="累计扣款">{{ centsToYuan(account?.totalDeduction) }}</el-descriptions-item>
        <el-descriptions-item label="币种">{{ account?.currency || '-' }}</el-descriptions-item>
      </el-descriptions>
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getPartnerAccount, getPartnerAccountTxns, type PartnerAccount, type PartnerTxn } from '@/api/partner'
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

function centsToYuan(v?: number | null) {
  if (v == null) return '-'
  return Number(v).toFixed(2)
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
