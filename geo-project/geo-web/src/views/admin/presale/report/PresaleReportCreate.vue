<template>
  <div class="presale-report-create">
    <!-- 页头 -->
    <div class="page-header">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/admin/presale/report' }">售前报告</el-breadcrumb-item>
        <el-breadcrumb-item>新建报告</el-breadcrumb-item>
      </el-breadcrumb>
      <h2 class="page-title">新建报告</h2>
    </div>

    <el-card shadow="never" class="form-card">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
        label-position="right"
      >
        <el-form-item label="品牌名称" prop="brandName">
          <el-input
            v-model="form.brandName"
            placeholder="如:海底捞"
            maxlength="100"
            show-word-limit
            style="max-width: 480px"
          />
        </el-form-item>

        <el-form-item label="行业" prop="industry">
          <el-select
            v-model="form.industry"
            placeholder="选择或输入行业"
            filterable
            allow-create
            default-first-option
            style="max-width: 320px"
            @change="onIndustryChange"
          >
            <el-option
              v-for="opt in industryOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="身份" prop="industryRole">
          <el-select
            v-model="form.industryRole"
            placeholder="选择或输入身份"
            :disabled="!form.industry"
            filterable
            allow-create
            default-first-option
            style="max-width: 320px"
          >
            <el-option
              v-for="opt in filteredRoleOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <span v-if="!form.industry" class="form-tip">请先选择行业</span>
        </el-form-item>

        <el-form-item label="地区" prop="region">
          <el-input
            v-model="form.region"
            placeholder="如:北京市"
            maxlength="50"
            show-word-limit
            style="max-width: 320px"
          />
        </el-form-item>

        <el-form-item label="客户诉求" prop="userDemand">
          <el-input
            v-model="form.userDemand"
            type="textarea"
            :rows="3"
            placeholder="可选,最多 500 字。例如:了解我们品牌在 AI 推荐中的真实表现。"
            maxlength="500"
            show-word-limit
            style="max-width: 640px"
          />
        </el-form-item>
      </el-form>

      <!-- 诊断范围预览(静态展示) -->
      <el-divider />
      <div class="scope-preview">
        <div class="scope-title">诊断范围预览</div>
        <div class="scope-grid">
          <div class="scope-item">
            <div class="scope-number">11</div>
            <div class="scope-label">AI 平台</div>
          </div>
          <div class="scope-item">
            <div class="scope-number">30</div>
            <div class="scope-label">Prompt 查询</div>
          </div>
          <div class="scope-item">
            <div class="scope-number">660</div>
            <div class="scope-label">LLM 调用</div>
          </div>
          <div class="scope-item">
            <div class="scope-number">5</div>
            <div class="scope-label">分析维度</div>
          </div>
        </div>
        <div class="scope-note">
          预计生成时长 2.5-3.5 分钟。生成过程异步进行,提交后会跳到进度页。
        </div>
      </div>

      <div class="action-bar">
        <el-button @click="onCancel">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">
          {{ submitting ? '提交中...' : '提交生成' }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { createReport, type CreateReportRequest } from '@/api/presaleReport'

const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive<CreateReportRequest>({
  brandName: '',
  industry: '',
  industryRole: '',
  region: '',
  userDemand: ''
})

const rules: FormRules = {
  brandName: [{ required: true, message: '品牌名不能为空', trigger: 'blur' }],
  industry: [{ required: true, message: '请选择行业', trigger: 'change' }],
  industryRole: [{ required: true, message: '请选择身份', trigger: 'change' }],
  region: [{ required: true, message: '请输入地区', trigger: 'blur' }]
}

// TODO: 字典从后端动态加载,v1 先 hardcode
const industryOptions = [
  { value: 'restaurant', label: '餐饮' },
  { value: 'education', label: '教培' },
  { value: 'automotive', label: '汽车' },
  { value: 'retail', label: '电商零售' },
  { value: 'finance', label: '金融' },
  { value: 'tourism', label: '旅游酒店' },
  { value: 'medical_beauty', label: '医美美容' },
  { value: 'tech_software', label: 'SaaS 企业软件' }
]

const allRoleOptions = [
  { value: 'chain_brand', label: '连锁品牌' },
  { value: 'single_store', label: '单店' },
  { value: 'franchise', label: '加盟商' },
  { value: 'manufacturer', label: '生产厂家' },
  { value: 'dealer', label: '经销商' },
  { value: 'platform', label: '平台方' },
  { value: 'service_provider', label: '服务商' },
  { value: 'kol', label: '个人/KOL' }
]

/**
 * 行业 × 身份联动:v1 先允许所有组合,P1·F·1·b 从
 * presale_industry_role_mapping 表加载真实关联。
 */
const filteredRoleOptions = computed(() => allRoleOptions)

function onIndustryChange() {
  // 切换行业时清空身份(可能新行业不支持旧身份)
  form.industryRole = ''
}

async function onSubmit() {
  if (!formRef.value) return
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return

  submitting.value = true
  try {
    const reportId = await createReport({
      brandName: form.brandName.trim(),
      industry: form.industry,
      industryRole: form.industryRole,
      region: form.region.trim(),
      userDemand: form.userDemand?.trim() || undefined
    })
    ElMessage.success('已创建报告,开始生成')
    router.push(`/admin/presale/report/${reportId}/progress`)
  } catch (err: any) {
    ElMessage.error(err?.message || '创建失败')
  } finally {
    submitting.value = false
  }
}

async function onCancel() {
  // 如果表单有任何输入,二次确认
  const hasInput =
    form.brandName || form.industry || form.industryRole || form.region || form.userDemand
  if (hasInput) {
    try {
      await ElMessageBox.confirm('表单有未保存内容,确定要离开吗?', '提示', {
        confirmButtonText: '离开',
        cancelButtonText: '继续填写',
        type: 'warning'
      })
    } catch {
      return
    }
  }
  router.push('/admin/presale/report')
}
</script>

<style scoped>
.presale-report-create {
  padding: 16px 24px;
  max-width: 920px;
}
.page-header {
  margin-bottom: 16px;
}
.page-title {
  margin: 8px 0 0 0;
  font-size: 22px;
  font-weight: 600;
}
.form-card {
  padding: 12px 0;
}
.form-tip {
  margin-left: 12px;
  color: #909399;
  font-size: 12px;
}
.scope-preview {
  padding: 12px 0;
}
.scope-title {
  font-size: 14px;
  color: #606266;
  margin-bottom: 12px;
}
.scope-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  max-width: 640px;
  margin-bottom: 12px;
}
.scope-item {
  text-align: center;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 4px;
}
.scope-number {
  font-size: 28px;
  font-weight: 600;
  color: #409eff;
  font-family: 'JetBrains Mono', Consolas, monospace;
}
.scope-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.scope-note {
  color: #909399;
  font-size: 12px;
  margin-top: 8px;
}
.action-bar {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #e4e7ed;
  text-align: right;
}
.action-bar .el-button + .el-button {
  margin-left: 12px;
}
</style>
