import ElementPlus, { ElInputNumber, ElSelect, ElSwitch } from 'element-plus'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PackagePlan } from '@/types'
import PackageConfig from './PackageConfig.vue'

const api = vi.hoisted(() => ({
  createPackagePlan: vi.fn(), getAdminPackagePlans: vi.fn(),
  updatePackagePlan: vi.fn(), updatePackagePlanStatus: vi.fn(),
}))
vi.mock('@/api/packagePlan', () => api)
enableAutoUnmount(afterEach)

const plan: PackagePlan = {
  id: 3, packageType: 'test_plan', packageName: '测试套餐', audienceType: 'internal',
  packageStatus: 'active', standardPrice: 100, serviceMonths: 12,
  keywordGroupLimit: 100, keywordGroupLimitA: 100, keywordGroupLimitB: 0, keywordGroupLimitC: 0,
  monthlyReportDepth: 'L2', quarterlyReportDepth: 'L2', consultantIntensity: 'L2',
  competitorInsightDepth: 'L2', mediaDistributionIntensity: 'L2', commitmentTargetIntensity: 'L2',
  targetMetricType: 'visibility_rate', targetMetricValue: 0.05, targetWindowDays: 90,
  enabled: true, sortOrder: 10,
  channelQuotaConfigs: [
    { channelCode: 'self_media:toutiao', periodType: 'month', quotaLimit: 13, enabled: true },
    { channelCode: 'self_media:sohu', periodType: 'week', quotaLimit: 2, enabled: false },
  ],
}

describe('搜狐套餐独立配置', () => {
  beforeEach(() => {
    api.updatePackagePlan.mockResolvedValue({ data: { data: plan } })
  })

  it.each(['internal', 'partner'] as const)('%s 套餐保留搜狐独立周期、数量和启用状态，并提交运营修改', async audienceType => {
    api.getAdminPackagePlans.mockResolvedValue({ data: { data: { records: [{ ...plan, audienceType }], total: 1 } } })
    const wrapper = mount(PackageConfig, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()
    const edit = wrapper.findAll('button').find(button => button.text() === '编辑')
    expect(edit).toBeDefined()
    await edit!.trigger('click')
    await flushPromises()

    const sohu = wrapper.findAll('.el-table__row').find(row => row.text().trim().startsWith('搜狐'))
    const toutiao = wrapper.findAll('.el-table__row').find(row => row.text().trim().startsWith('今日头条'))
    expect(sohu).toBeDefined()
    expect(toutiao).toBeDefined()
    expect(sohu!.text()).not.toContain('同步')
    const period = sohu!.findComponent(ElSelect)
    const quantity = sohu!.findComponent(ElInputNumber)
    const enabled = sohu!.findComponent(ElSwitch)
    expect(period.props('disabled')).toBeFalsy()
    expect(quantity.props('disabled')).toBeFalsy()
    expect(enabled.props('disabled')).toBeFalsy()
    expect(period.props('modelValue')).toBe('week')
    expect(quantity.props('modelValue')).toBe(2)
    expect(enabled.props('modelValue')).toBe(false)

    period.vm.$emit('update:modelValue', 'day')
    quantity.vm.$emit('update:modelValue', 3)
    enabled.vm.$emit('update:modelValue', true)
    toutiao!.findComponent(ElInputNumber).vm.$emit('update:modelValue', 17)
    await flushPromises()
    expect(period.props('modelValue')).toBe('day')
    expect(quantity.props('modelValue')).toBe(3)
    expect(enabled.props('modelValue')).toBe(true)

    const save = wrapper.findAll('button').find(button => button.text() === '保存')
    await save!.trigger('click')
    await flushPromises()
    expect(api.updatePackagePlan).toHaveBeenCalledWith(3, expect.objectContaining({
      channelQuotaConfigs: expect.arrayContaining([
        { channelCode: 'self_media:sohu', periodType: 'day', quotaLimit: 3, enabled: true },
        { channelCode: 'self_media:toutiao', periodType: 'month', quotaLimit: 17, enabled: true },
      ]),
    }))
  })
})
