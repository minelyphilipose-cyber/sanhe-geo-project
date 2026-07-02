<template>
  <div class="partner-page partner-brand-create-page">
    <div class="partner-page-header">
      <div>
        <div class="partner-page-kicker">品牌资产</div>
        <h1 class="partner-page-title">新建品牌</h1>
        <div class="partner-page-subtitle">录入品牌基础资料、业务介绍与公开联系方式；合规类型、状态和交付阵地由系统与总部处理。</div>
      </div>
      <div class="partner-page-actions">
        <el-button @click="router.back()">返回</el-button>
        <el-button type="primary" :loading="saving" @click="submitBrand">保存品牌</el-button>
      </div>
    </div>

    <el-card shadow="never" class="partner-surface partner-brand-form-card">
      <div class="partner-form-intro">
        <span class="partner-form-intro__icon">品</span>
        <div>
          <strong>品牌资料表单</strong>
          <p>品牌状态默认启用；行业合规类型由后端根据行业和资料自行判断，合伙人无需选择。</p>
        </div>
      </div>

      <el-form ref="formRef" class="partner-brand-form" :model="form" :rules="rules" label-position="top">
        <section class="partner-form-section">
          <div class="section-caption">
            <span>01</span>
            <div>
              <strong>基础信息</strong>
              <p>用于识别客户品牌和后续项目归属。</p>
            </div>
          </div>
          <div class="partner-section-grid">
            <el-form-item label="所属客户" required>
              <el-input :model-value="companyName || '-'" disabled />
            </el-form-item>
            <el-form-item label="品牌名称" prop="brandName" required>
              <el-input v-model="form.brandName" placeholder="请输入品牌名称" />
            </el-form-item>
            <el-form-item label="品牌简称">
              <el-input v-model="form.brandShortName" maxlength="128" show-word-limit placeholder="如：吾悦、阜阳吾悦" />
            </el-form-item>
            <el-form-item label="品牌行业" prop="industry" required>
              <el-select v-if="industryOptions.length" v-model="form.industry" filterable placeholder="请选择品牌行业" style="width: 100%">
                <el-option v-for="tag in industryOptions" :key="tag" :label="industryLabel(tag)" :value="tag" />
              </el-select>
              <el-input v-else v-model="form.industry" placeholder="请输入品牌行业" />
            </el-form-item>
            <el-form-item label="主营业务方向">
              <el-input v-model="form.mainBusiness" placeholder="例如：本地商业综合体、智能家居服务" />
            </el-form-item>
            <el-form-item label="核心产品">
              <el-input v-model="form.coreProducts" maxlength="500" show-word-limit placeholder="多个产品以逗号隔开" />
            </el-form-item>
            <el-form-item class="is-full" label="品牌定位">
              <el-input v-model="form.brandPositioning" maxlength="255" show-word-limit placeholder="如“本地商业综合体”“某行业服务商”" />
            </el-form-item>
          </div>
        </section>

        <section class="partner-form-section">
          <div class="section-caption">
            <span>02</span>
            <div>
              <strong>联系方式与区域</strong>
              <p>只录入合伙人侧可见、可公开或可用于跟进的信息。</p>
            </div>
          </div>
          <div class="partner-section-grid">
            <el-form-item label="所在地区">
              <RegionCascader v-model="form.regionCodes" />
            </el-form-item>
            <el-form-item label="官网">
              <el-input v-model="form.website" placeholder="https://example.com" />
            </el-form-item>
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="form.phone" placeholder="请输入手机号" />
            </el-form-item>
            <el-form-item label="微信">
              <el-input v-model="form.wechat" placeholder="请输入微信号或联系人微信" />
            </el-form-item>
            <el-form-item class="is-full" label="对外公开地址">
              <el-input v-model="form.publicAddress" placeholder="请输入门店、办公或服务地址" />
            </el-form-item>
          </div>
        </section>

        <section class="partner-form-section">
          <div class="section-caption">
            <span>03</span>
            <div>
              <strong>介绍素材</strong>
              <p>用于后续诊断报告、项目资料和交付准备。</p>
            </div>
          </div>
          <div class="partner-section-grid">
            <el-form-item class="is-full" label="业务介绍">
              <el-input v-model="form.businessIntro" type="textarea" :rows="4" placeholder="简要说明品牌业务、服务范围、目标客户与经营特点" />
            </el-form-item>
            <el-form-item class="is-full" label="品牌资质描述">
              <el-input v-model="form.brandQualificationDescription" type="textarea" :rows="3" maxlength="300" show-word-limit placeholder="填写真实可核验的资质、荣誉、认证、专利或平台背书" />
            </el-form-item>
            <el-form-item class="is-full" label="品牌案例描述">
              <el-input v-model="form.brandCaseDescription" type="textarea" :rows="3" maxlength="300" show-word-limit placeholder="填写可公开引用的客户案例、项目背景、交付结果或合作情况" />
            </el-form-item>
          </div>
        </section>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createBrand, getCompanyDetail } from '@/api/customer'
import RegionCascader from '@/components/ui/RegionCascader.vue'
import { regionPayloadFromCodes } from '@/constants/region'
import { useDictStore } from '@/stores/dict'
import { isValidMobile, nullableText } from '@/utils/form'

defineOptions({ name: 'PartnerBrandCreate' })

const route = useRoute()
const router = useRouter()
const dictStore = useDictStore()
const formRef = ref<FormInstance>()
const saving = ref(false)
const companyName = ref('')
const industryOptions = ref<string[]>([])
const companyId = computed(() => Number(route.query.companyId) || 0)

const form = reactive({
  brandName: '',
  brandShortName: '',
  industry: '',
  mainBusiness: '',
  coreProducts: '',
  brandPositioning: '',
  regionCodes: [] as string[],
  website: '',
  phone: '',
  wechat: '',
  publicAddress: '',
  businessIntro: '',
  brandQualificationDescription: '',
  brandCaseDescription: '',
})

const rules: FormRules = {
  brandName: [{ required: true, message: '请输入品牌名称', trigger: 'blur' }],
  industry: [{ required: true, message: '请选择品牌行业', trigger: 'change' }],
  phone: [{
    validator: (_rule, value: string, callback) => {
      callback(isValidMobile(value) ? undefined : new Error('请输入正确的手机号'))
    },
    trigger: 'blur',
  }],
}

function industryLabel(value: string) {
  return dictStore.label('industry_tag', value) || value
}

function parseIndustryTags(value?: string | string[] | null) {
  if (Array.isArray(value)) return value
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

async function loadCompany() {
  if (!companyId.value) {
    ElMessage.error('缺少所属客户，无法创建品牌')
    return
  }
  try {
    const { data } = await getCompanyDetail(companyId.value)
    companyName.value = data.data.companyName || ''
    industryOptions.value = parseIndustryTags((data.data as any).industryTags)
    form.industry = industryOptions.value[0] || data.data.industry || ''
  } catch {
    ElMessage.error('加载客户信息失败')
  }
}

async function submitBrand() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!companyId.value) {
    ElMessage.error('缺少所属客户，无法创建品牌')
    return
  }
  saving.value = true
  try {
    const region = regionPayloadFromCodes(form.regionCodes)
    const { data } = await createBrand({
      companyId: companyId.value,
      brandName: form.brandName,
      brandShortName: nullableText(form.brandShortName),
      industry: form.industry,
      complianceIndustryCode: 'none',
      status: 'active',
      mainBusiness: nullableText(form.mainBusiness),
      coreProducts: nullableText(form.coreProducts),
      brandPositioning: nullableText(form.brandPositioning),
      provinceCode: region.provinceCode,
      provinceName: region.provinceName,
      cityCode: region.cityCode,
      cityName: region.cityName,
      districtCode: region.districtCode,
      districtName: region.districtName,
      serviceArea: nullableText(region.displayName),
      website: nullableText(form.website),
      phone: nullableText(form.phone),
      publicAddress: nullableText(form.publicAddress),
      wechat: nullableText(form.wechat),
      description: nullableText(form.businessIntro),
      businessIntro: nullableText(form.businessIntro),
      brandQualificationDescription: nullableText(form.brandQualificationDescription),
      brandCaseDescription: nullableText(form.brandCaseDescription),
      versionChangeReason: 'partner.brand.create',
    })
    ElMessage.success('品牌已创建')
    router.replace(`/partner/brands/${data.data.id}`)
  } catch {
    ElMessage.error('创建品牌失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await dictStore.ensureLoaded()
  await loadCompany()
})
</script>

<style scoped>
.partner-brand-form-card {
  padding: 0;
}

.partner-form-intro {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px 22px;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: linear-gradient(135deg, #f8fbff 0%, #eff6ff 56%, #ecfdf5 100%);
  margin-bottom: 22px;
}

.partner-form-intro__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: #2563eb;
  color: #fff;
  font-weight: 800;
}

.partner-form-intro strong {
  display: block;
  color: #0f172a;
  font-size: 18px;
  font-weight: 800;
}

.partner-form-intro p {
  margin: 5px 0 0;
  color: #64748b;
}

.partner-brand-form {
  display: grid;
  gap: 20px;
}

.partner-form-section {
  padding: 20px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #fff;
}

.section-caption {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 18px;
}

.section-caption > span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: #eff6ff;
  color: #2563eb;
  font-weight: 800;
}

.section-caption strong {
  display: block;
  color: #0f172a;
  font-weight: 800;
}

.section-caption p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}

.partner-section-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px 22px;
}

.partner-section-grid .is-full {
  grid-column: 1 / -1;
}

@media (max-width: 900px) {
  .partner-section-grid {
    grid-template-columns: 1fr;
  }
}
</style>
