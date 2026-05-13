<template>
  <div class="page03-config">
    <div class="page-head">
      <div>
        <div class="breadcrumb">
          管理后台 <span class="breadcrumb-sep">/</span>
          售前报告 <span class="breadcrumb-sep">/</span>
          <span class="breadcrumb-current">PAGE03 配置</span>
        </div>
        <h2 class="page-title">AI 搜索新战场配置</h2>
      </div>
      <div class="page-actions">
        <el-button class="btn-default" @click="goBack">返回列表</el-button>
        <el-button class="btn-primary" type="primary" :loading="saving" @click="submit">保存配置</el-button>
      </div>
    </div>

    <el-form ref="formRef" v-loading="loading" :model="form" :rules="rules" label-position="top">
      <section class="section">
        <div class="section-head">
          <div class="section-bar"></div>
          <h3 class="section-title">AI 搜索流量总览</h3>
        </div>
        <div class="section-desc">配置市场总体数据，展示在 PAGE03 的头部区域</div>

        <div class="form-grid-2">
          <el-form-item class="form-item" prop="marketLabel">
            <label class="form-label"><span class="required">*</span>模块标题</label>
            <div class="input-wrap">
              <el-input v-model="form.marketLabel" class="config-input" maxlength="32" show-word-limit placeholder="如：AI 搜索流量总览" />
            </div>
          </el-form-item>
          <el-form-item class="form-item" prop="marketSource">
            <label class="form-label"><span class="required">*</span>来源文案</label>
            <div class="input-wrap">
              <el-input v-model="form.marketSource" class="config-input" maxlength="32" show-word-limit placeholder="如：数据来源 QuestMobile" />
            </div>
          </el-form-item>
        </div>

        <div class="group-label">核心指标</div>
        <div class="stat-grid">
          <div v-for="item in statFields" :key="item.valueKey" class="stat-card">
            <div class="stat-card-label">{{ item.label }}</div>
            <div class="stat-card-row">
              <el-form-item :prop="item.valueKey" class="compact-form-item">
                <el-input v-model="form[item.valueKey]" class="stat-value" maxlength="12" placeholder="数值" />
              </el-form-item>
              <el-form-item :prop="item.unitKey" class="compact-form-item stat-unit-item">
                <el-input v-model="form[item.unitKey]" class="stat-unit" maxlength="8" placeholder="单位" />
              </el-form-item>
            </div>
          </div>
        </div>

        <div class="group-label">TOP 平台排名</div>
        <div class="top-grid">
          <div v-for="(item, index) in platformFields" :key="item.nameKey" class="stat-card">
            <div class="rank-badge" :class="`rank-${index + 1}`">{{ index + 1 }}</div>
            <div class="stat-card-label">{{ item.label }}</div>
            <div class="stat-card-row">
              <el-form-item :prop="item.nameKey" class="compact-form-item">
                <el-input v-model="form[item.nameKey]" class="stat-value" maxlength="12" placeholder="平台" />
              </el-form-item>
              <el-form-item :prop="item.valueKey" class="compact-form-item stat-unit-item wide">
                <el-input v-model="form[item.valueKey]" class="stat-unit" maxlength="12" placeholder="数据" />
              </el-form-item>
            </div>
          </div>
        </div>

        <el-form-item class="form-item no-margin" prop="platformSuffix">
          <label class="form-label"><span class="required">*</span>其他平台说明</label>
          <div class="input-wrap">
            <el-input v-model="form.platformSuffix" class="config-input" maxlength="18" show-word-limit placeholder="如：等 12 家头部 AI 搜索平台" />
          </div>
        </el-form-item>
      </section>

      <section class="section section-last">
        <div class="section-head">
          <div class="section-bar"></div>
          <h3 class="section-title">PAGE03 展示口径</h3>
        </div>
        <div class="section-desc">控制底部数据来源与页脚说明</div>

        <div class="form-grid-2">
          <el-form-item class="form-item" prop="page03DataSource">
            <label class="form-label"><span class="required">*</span>核心市场数据来源</label>
            <div class="input-wrap">
              <el-input v-model="form.page03DataSource" class="config-input" maxlength="30" show-word-limit placeholder="请输入数据来源" />
            </div>
          </el-form-item>
          <el-form-item class="form-item" prop="questionCount">
            <label class="form-label">持续询问问题数量</label>
            <div class="stepper-row">
              <div class="stepper">
                <button type="button" disabled>−</button>
                <div class="num">{{ form.questionCount }}</div>
                <button type="button" disabled>+</button>
              </div>
              <span class="stepper-tip">固定 3 条，单条字数由系统限制</span>
            </div>
          </el-form-item>
        </div>

        <el-form-item class="form-item no-margin" prop="footnote">
          <label class="form-label"><span class="required">*</span>页脚说明</label>
          <div class="textarea-wrap">
            <el-input v-model="form.footnote" class="config-textarea" type="textarea" :rows="3" maxlength="150" show-word-limit placeholder="请输入页脚说明文字" />
          </div>
        </el-form-item>
      </section>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  getPresalePage03MarketConfig,
  updatePresalePage03MarketConfig,
  type PresalePage03MarketConfigPayload,
} from '@/api/presalePage03Config'

type TextKey = Exclude<keyof PresalePage03MarketConfigPayload, 'questionCount'>

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<PresalePage03MarketConfigPayload>({
  marketLabel: '',
  marketSource: '',
  appMonthlyActiveValue: '',
  appMonthlyActiveUnit: '',
  dailyActiveUsersValue: '',
  dailyActiveUsersUnit: '',
  dailyQuestionTotalValue: '',
  dailyQuestionTotalUnit: '',
  doubaoMonthlyUsageValue: '',
  doubaoMonthlyUsageUnit: '',
  platform1Name: '',
  platform1Value: '',
  platform2Name: '',
  platform2Value: '',
  platform3Name: '',
  platform3Value: '',
  platformSuffix: '',
  page03DataSource: '',
  footnote: '',
  questionCount: 3,
})

const statFields: Array<{ label: string; valueKey: TextKey; unitKey: TextKey }> = [
  { label: 'AI 原生 APP 月活', valueKey: 'appMonthlyActiveValue', unitKey: 'appMonthlyActiveUnit' },
  { label: '日均活跃用户', valueKey: 'dailyActiveUsersValue', unitKey: 'dailyActiveUsersUnit' },
  { label: '日均提问总量', valueKey: 'dailyQuestionTotalValue', unitKey: 'dailyQuestionTotalUnit' },
  { label: '豆包人均月使用', valueKey: 'doubaoMonthlyUsageValue', unitKey: 'doubaoMonthlyUsageUnit' },
]

const platformFields: Array<{ label: string; nameKey: TextKey; valueKey: TextKey }> = [
  { label: 'TOP 平台 1', nameKey: 'platform1Name', valueKey: 'platform1Value' },
  { label: 'TOP 平台 2', nameKey: 'platform2Name', valueKey: 'platform2Value' },
  { label: 'TOP 平台 3', nameKey: 'platform3Name', valueKey: 'platform3Value' },
]

const requiredText = [{ required: true, message: '必填', trigger: 'blur' }]
const rules: FormRules = {
  marketLabel: requiredText,
  marketSource: requiredText,
  appMonthlyActiveValue: requiredText,
  appMonthlyActiveUnit: requiredText,
  dailyActiveUsersValue: requiredText,
  dailyActiveUsersUnit: requiredText,
  dailyQuestionTotalValue: requiredText,
  dailyQuestionTotalUnit: requiredText,
  doubaoMonthlyUsageValue: requiredText,
  doubaoMonthlyUsageUnit: requiredText,
  platform1Name: requiredText,
  platform1Value: requiredText,
  platform2Name: requiredText,
  platform2Value: requiredText,
  platform3Name: requiredText,
  platform3Value: requiredText,
  platformSuffix: requiredText,
  page03DataSource: requiredText,
  footnote: requiredText,
  questionCount: [{ required: true, type: 'number', min: 3, max: 3, message: '当前固定为 3', trigger: 'change' }],
}

function assignForm(data: PresalePage03MarketConfigPayload) {
  Object.assign(form, data)
}

async function load() {
  loading.value = true
  try {
    const { data } = await getPresalePage03MarketConfig()
    assignForm(data.data)
  } finally {
    loading.value = false
  }
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = Object.fromEntries(
      Object.entries(form).map(([key, value]) => [key, typeof value === 'string' ? value.trim() : value]),
    ) as PresalePage03MarketConfigPayload
    const { data } = await updatePresalePage03MarketConfig(payload)
    assignForm(data.data)
    ElMessage.success('保存成功')
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push('/admin/presale/report')
}

onMounted(load)
</script>

<style scoped>
.page03-config {
  min-height: 100%;
  padding: 24px 32px;
  background: #f5f6f8;
  color: #1d2129;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', 'Helvetica Neue', Arial, sans-serif;
  font-size: 13px;
  -webkit-font-smoothing: antialiased;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}

.breadcrumb {
  margin-bottom: 12px;
  color: #4e5969;
  font-size: 13px;
  font-weight: 500;
}

.breadcrumb-sep {
  margin: 0 8px;
  color: #c0c4cc;
  font-weight: 400;
}

.breadcrumb-current {
  color: #1d2129;
}

.page-title {
  margin: 0;
  color: #1d2129;
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 0.2px;
}

.page-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.page-actions :deep(.el-button) {
  height: 36px;
  padding: 0 18px;
  border-radius: 6px;
  font-size: 13px;
}

.btn-default {
  border-color: #dcdfe6;
  background: #fff;
  color: #606266;
}

.btn-default:hover {
  border-color: #2454ff;
  color: #2454ff;
}

.btn-primary {
  border-color: #2454ff;
  background: #2454ff;
  color: #fff;
  font-weight: 500;
}

.btn-primary:hover {
  border-color: #1d44d9;
  background: #1d44d9;
}

.section {
  margin-bottom: 16px;
  padding: 24px 28px;
  border: 1px solid #ebedf0;
  border-radius: 10px;
  background: #fff;
}

.section-last {
  margin-bottom: 0;
}

.section-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
}

.section-bar {
  width: 3px;
  height: 14px;
  flex-shrink: 0;
  border-radius: 2px;
  background: #2454ff;
}

.section-title {
  margin: 0;
  color: #1d2129;
  font-size: 15px;
  font-weight: 500;
}

.section-desc {
  margin: 0 0 20px 13px;
  color: #8a8f99;
  font-size: 12px;
}

.group-label {
  margin-bottom: 10px;
  color: #8a8f99;
  font-size: 12px;
  letter-spacing: 0.5px;
}

.form-grid-2 {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.form-item {
  min-width: 0;
  margin-bottom: 0;
}

.no-margin {
  margin-bottom: 0;
}

.form-label {
  display: block;
  margin-bottom: 6px;
  color: #4e5969;
  font-size: 13px;
  line-height: 1.4;
}

.required {
  margin-right: 4px;
  color: #f56c6c;
}

.input-wrap,
.textarea-wrap {
  width: 100%;
}

.config-input,
.config-textarea {
  width: 100%;
}

.config-input :deep(.el-input__wrapper) {
  height: 36px;
  padding: 0 56px 0 12px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
  box-shadow: none;
}

.config-input :deep(.el-input__wrapper:hover) {
  border-color: #b8bcc4;
  box-shadow: none;
}

.config-input :deep(.el-input__wrapper.is-focus) {
  border-color: #2454ff;
  box-shadow: 0 0 0 3px rgba(36, 84, 255, 0.08);
}

.config-input :deep(.el-input__inner) {
  color: #1d2129;
  font-size: 13px;
}

.config-input :deep(.el-input__count) {
  right: 10px;
  color: #c0c4cc;
  font-size: 12px;
  background: #fff;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
  margin-bottom: 24px;
}

.top-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 12px;
  margin-bottom: 20px;
}

.stat-card {
  position: relative;
  min-width: 0;
  padding: 14px;
  border: 1px solid #ebedf0;
  border-radius: 8px;
  background: #fafbfc;
  transition: border-color 0.15s;
}

.stat-card:hover {
  border-color: #dcdfe6;
}

.stat-card-label {
  margin-bottom: 10px;
  color: #606266;
  font-size: 12px;
}

.stat-card-row {
  display: flex;
  align-items: flex-start;
  gap: 6px;
}

.compact-form-item {
  flex: 1 1 auto;
  min-width: 0;
  margin-bottom: 0;
}

.stat-unit-item {
  flex: 0 0 60px;
}

.stat-unit-item.wide {
  flex-basis: 88px;
}

.compact-form-item :deep(.el-form-item__content) {
  line-height: 1;
}

.compact-form-item :deep(.el-form-item__error) {
  padding-top: 4px;
  white-space: nowrap;
}

.stat-value :deep(.el-input__wrapper),
.stat-unit :deep(.el-input__wrapper) {
  height: 32px;
  border: 1px solid #dcdfe6;
  border-radius: 5px;
  box-shadow: none;
}

.stat-value :deep(.el-input__wrapper) {
  padding: 0 10px;
  background: #fff;
}

.stat-unit :deep(.el-input__wrapper) {
  padding: 0 8px;
  background: #f5f6f8;
}

.stat-value :deep(.el-input__wrapper.is-focus),
.stat-unit :deep(.el-input__wrapper.is-focus) {
  border-color: #2454ff;
}

.stat-unit :deep(.el-input__wrapper.is-focus) {
  background: #fff;
}

.stat-value :deep(.el-input__inner) {
  color: #1d2129;
  font-size: 14px;
  font-weight: 500;
}

.stat-unit :deep(.el-input__inner) {
  color: #4e5969;
  font-size: 13px;
  text-align: center;
}

.rank-badge {
  position: absolute;
  top: 12px;
  right: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  font-size: 11px;
  font-weight: 600;
}

.rank-1 {
  background: #fff1e6;
  color: #d97706;
}

.rank-2 {
  background: #f0f2f5;
  color: #6b7280;
}

.rank-3 {
  background: #fbefe5;
  color: #92400e;
}

.stepper-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.stepper {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  height: 36px;
  overflow: hidden;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #f5f6f8;
}

.stepper button {
  width: 32px;
  height: 34px;
  border: none;
  background: transparent;
  color: #c0c4cc;
  cursor: not-allowed;
  font-size: 16px;
}

.stepper .num {
  width: 44px;
  color: #4e5969;
  font-size: 13px;
  font-weight: 500;
  text-align: center;
}

.stepper-tip {
  color: #8a8f99;
  font-size: 12px;
  line-height: 1.5;
}

.config-textarea :deep(.el-textarea__inner) {
  height: 88px;
  padding: 10px 12px 24px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  color: #1d2129;
  font-size: 13px;
  line-height: 1.6;
  resize: none;
  box-shadow: none;
}

.config-textarea :deep(.el-textarea__inner:hover) {
  border-color: #b8bcc4;
}

.config-textarea :deep(.el-textarea__inner:focus) {
  border-color: #2454ff;
  box-shadow: 0 0 0 3px rgba(36, 84, 255, 0.08);
}

.config-textarea :deep(.el-input__count) {
  right: 12px;
  bottom: 8px;
  color: #c0c4cc;
  font-size: 12px;
  background: transparent;
}

.form-item :deep(.el-form-item__content) {
  display: block;
}

.form-item :deep(.el-form-item__error) {
  padding-top: 4px;
  color: #f56c6c;
  font-size: 12px;
  line-height: 1.3;
}

@media (max-width: 768px) {
  .page03-config {
    padding: 18px 16px;
  }

  .section {
    padding: 20px 18px;
  }
}
</style>
