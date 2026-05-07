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

  it('does not contain any programmatic publish trigger', () => {
    const indexSource = readFileSync(resolve(process.cwd(), 'src/content-script/index.ts'), 'utf8')
    const listenerSource = readFileSync(resolve(process.cwd(), 'src/content-script/publishListener.ts'), 'utf8')

    expect(`${indexSource}\n${listenerSource}`).not.toContain('.click()')
  })
})
