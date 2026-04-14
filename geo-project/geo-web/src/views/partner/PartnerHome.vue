<template>
  <div class="space-y-4">
    <el-card>
      <template #header>
        <div class="flex items-center justify-between">
          <span>合伙人首页</span>
          <el-button @click="load">刷新</el-button>
        </div>
      </template>
      <DataState :loading="loading" :empty="!loading && !partnerId" empty-text="当前账号未绑定合伙人">
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12" :md="8">
            <el-statistic title="当前余额(元)" :value="Number(account?.currentBalance || 0).toFixed(2)" />
          </el-col>
          <el-col :xs="24" :sm="12" :md="8">
            <el-statistic title="本月新签客户" :value="monthNewCustomers" />
          </el-col>
          <el-col :xs="24" :sm="12" :md="8">
            <el-statistic title="当前服务客户数" :value="servingCustomers" />
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" class="mt-3">
            <el-statistic title="本月报表交付数" :value="0" />
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" class="mt-3">
            <el-statistic title="待处理项目" :value="pendingProjects" />
          </el-col>
        </el-row>
      </DataState>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getPartnerAccount, type PartnerAccount } from '@/api/partner'
import { getCompanyList } from '@/api/customer'
import { getProjectList } from '@/api/project'
import DataState from '@/components/ui/DataState.vue'

const userStore = useUserStore()
const partnerId = computed(() => userStore.userInfo?.partnerId || null)
const loading = ref(false)
const account = ref<PartnerAccount | null>(null)
const monthNewCustomers = ref(0)
const servingCustomers = ref(0)
const pendingProjects = ref(0)

function inCurrentMonth(datetime: string | undefined) {
  if (!datetime) return false
  const d = new Date(datetime)
  const now = new Date()
  return d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth()
}

async function load() {
  if (!partnerId.value) {
    account.value = null
    monthNewCustomers.value = 0
    servingCustomers.value = 0
    pendingProjects.value = 0
    return
  }
  loading.value = true
  try {
    const [accountRes, companyRes, projectRes] = await Promise.all([
      getPartnerAccount(partnerId.value),
      getCompanyList({ current: 1, size: 200 }),
      getProjectList({ current: 1, size: 200 }),
    ])
    account.value = accountRes.data.data
    const companies = companyRes.data.data.records || []
    const projects = projectRes.data.data.records || []
    servingCustomers.value = companies.length
    monthNewCustomers.value = companies.filter((x) => inCurrentMonth(x.createdAt)).length
    pendingProjects.value = projects.filter((x) => x.status === 'paused').length
  } catch {
    ElMessage.error('加载首页数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

