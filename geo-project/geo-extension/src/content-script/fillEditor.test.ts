import { beforeEach, describe, expect, it } from 'vitest'
import { fillEditor } from './fillEditor'

beforeEach(() => {
  document.body.innerHTML = ''
})

describe('fillEditor', () => {
  it('returns structured error when required selectors are missing', async () => {
    document.body.innerHTML = '<input data-geo-fill="title">'

    const result = await fillEditor(payload(), document, 'https://mp.toutiao.com/editor', 50)

    expect(result).toEqual({
      ok: false,
      errorCode: 'SELECTOR_NOT_FOUND',
      message: 'content selector not found',
    })
  })

  it('fills title, content, cover, tags, and category without publishing', async () => {
    document.body.innerHTML = '<input data-geo-fill="title"><div data-geo-fill="content" contenteditable="true"></div><input data-geo-fill="cover"><input data-geo-fill="tags"><input data-geo-fill="category">'

    const result = await fillEditor(payload(), document, 'https://mp.toutiao.com/editor')

    expect(result.ok).toBe(true)
    expect(value('title')).toBe('Draft')
    expect(document.querySelector('[data-geo-fill="content"]')?.innerHTML).toBe('<p>Hello</p>')
    expect(value('cover')).toBe('https://cdn/cover.jpg')
    expect(value('tags')).toBe('geo,brand')
    expect(value('category')).toBe('news')
  })

  it('sanitizes content html at the DOM injection point', async () => {
    document.body.innerHTML = '<input data-geo-fill="title"><div data-geo-fill="content" contenteditable="true"></div>'

    const result = await fillEditor({
      ...payload(),
      contentHtml: '<p>Hello</p><img src=x onerror=alert(1)><script>bad()</script>',
      coverImageUrl: null,
      tags: [],
      category: null,
    }, document, 'https://mp.toutiao.com/editor')

    const html = document.querySelector('[data-geo-fill="content"]')?.innerHTML.toLowerCase()
    expect(result.ok).toBe(true)
    expect(html).toContain('<p>Hello</p>'.toLowerCase())
    expect(html).not.toContain('onerror')
    expect(html).not.toContain('script')
  })

  it('waits for editor controls rendered after document_idle', async () => {
    const promise = fillEditor({
      ...payload(),
      coverImageUrl: null,
      tags: [],
      category: null,
    }, document, 'https://mp.toutiao.com/editor')

    setTimeout(() => {
      document.body.innerHTML = '<input data-geo-fill="title"><div data-geo-fill="content" contenteditable="true"></div>'
    }, 20)

    const result = await promise

    expect(result.ok).toBe(true)
    expect(value('title')).toBe('Draft')
    expect(document.querySelector('[data-geo-fill="content"]')?.innerHTML).toBe('<p>Hello</p>')
  })
})

function value(name: string) {
  return (document.querySelector(`[data-geo-fill="${name}"]`) as HTMLInputElement).value
}

function payload() {
  return {
    taskId: 30,
    platform: 'toutiao',
    publishUrl: 'https://mp.toutiao.com/editor',
    title: 'Draft',
    contentHtml: '<p>Hello</p>',
    coverImageUrl: 'https://cdn/cover.jpg',
    tags: ['geo', 'brand'],
    category: 'news',
  }
}
