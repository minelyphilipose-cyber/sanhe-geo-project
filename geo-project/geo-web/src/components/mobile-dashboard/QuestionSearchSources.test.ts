import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import QuestionSearchSources from './QuestionSearchSources.vue'

const { showToast } = vi.hoisted(() => ({ showToast: vi.fn() }))

vi.mock('vant', () => ({ showToast }))

describe('QuestionSearchSources', () => {
  beforeEach(() => {
    showToast.mockReset()
  })

  it('renders a sanitized real domain and a mobile copy fallback', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    })
    const wrapper = mount(QuestionSearchSources, {
      props: {
        platformLabel: '豆包',
        searchSources: [{
          sourceId: 1,
          title: '餐厅榜单来源',
          url: 'https://www.example.com/article?id=7&utm_source=search',
          domain: 'fake.example.net',
          snippet: '来源摘要',
          cited: true,
        }],
      },
      global: {
        stubs: { MobileIcon: true },
      },
    })

    expect(wrapper.text()).toContain('豆包 联网内容出处')
    expect(wrapper.text()).toContain('example.com')
    expect(wrapper.text()).not.toContain('fake.example.net')
    expect(wrapper.get('a').attributes('href')).toBe('https://www.example.com/article?id=7')
    expect(wrapper.get('a').attributes('rel')).toBe('noopener noreferrer')

    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(writeText).toHaveBeenCalledWith('https://www.example.com/article?id=7')
    expect(showToast).toHaveBeenCalledWith('链接已复制')
  })
})
