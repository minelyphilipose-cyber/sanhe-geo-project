import { describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { activatePublishListener, handlePublishClick } from './publishListener'

describe('publish listener', () => {
  it('reports published after operator clicks publish selector', () => {
    vi.stubGlobal('chrome', {
      runtime: { sendMessage: vi.fn(async () => undefined) },
    })
    document.body.innerHTML = '<button data-geo-publish>发布</button>'
    activatePublishListener(30)

    const reported = handlePublishClick(
      document.querySelector('[data-geo-publish]'),
      'https://mp.toutiao.com/editor',
    )

    expect(reported).toBe(true)
    expect(chrome.runtime.sendMessage).toHaveBeenCalledWith({
      type: 'GEO_TASK_PUBLISHED',
      payload: {
        taskId: 30,
        href: 'https://mp.toutiao.com/editor',
        platform: 'toutiao',
      },
    })
  })

  it('reports distributed after operator clicks draft selector', () => {
    vi.stubGlobal('chrome', {
      runtime: { sendMessage: vi.fn(async () => undefined) },
    })
    document.body.innerHTML = '<button data-geo-save-draft>保存草稿</button>'
    activatePublishListener(31)

    const reported = handlePublishClick(
      document.querySelector('[data-geo-save-draft]'),
      'https://zhuanlan.zhihu.com/p/123/edit',
    )

    expect(reported).toBe(true)
    expect(chrome.runtime.sendMessage).toHaveBeenCalledWith({
      type: 'GEO_TASK_PUBLISHED',
      payload: {
        taskId: 31,
        href: 'https://zhuanlan.zhihu.com/p/123/edit',
        platform: 'zhihu',
      },
    })
  })

  it('reports distributed after operator clicks a platform button with draft text', () => {
    vi.stubGlobal('chrome', {
      runtime: { sendMessage: vi.fn(async () => undefined) },
    })
    document.body.innerHTML = '<div role="button" class="publish-button"><span>保存到草稿</span></div>'
    activatePublishListener(32)

    const reported = handlePublishClick(
      document.querySelector('.publish-button span'),
      'https://mp.toutiao.com/editor',
    )

    expect(reported).toBe(true)
    expect(chrome.runtime.sendMessage).toHaveBeenCalledWith(expect.objectContaining({
      payload: expect.objectContaining({
        taskId: 32,
        platform: 'toutiao',
      }),
    }))
  })

  it('does not report twice after one completion click', () => {
    vi.stubGlobal('chrome', {
      runtime: { sendMessage: vi.fn(async () => undefined) },
    })
    document.body.innerHTML = '<button data-geo-publish>发布</button>'
    const button = document.querySelector('[data-geo-publish]')
    activatePublishListener(33)

    expect(handlePublishClick(button, 'https://mp.toutiao.com/editor')).toBe(true)
    expect(handlePublishClick(button, 'https://mp.toutiao.com/editor')).toBe(false)

    expect(chrome.runtime.sendMessage).toHaveBeenCalledTimes(1)
  })

  it('does not contain any programmatic publish trigger', () => {
    const indexSource = readFileSync(resolve(process.cwd(), 'src/content-script/index.ts'), 'utf8')
    const listenerSource = readFileSync(resolve(process.cwd(), 'src/content-script/publishListener.ts'), 'utf8')

    expect(`${indexSource}\n${listenerSource}`).not.toContain('.click()')
  })
})
