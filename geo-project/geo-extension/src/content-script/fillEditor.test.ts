import { beforeEach, describe, expect, it } from 'vitest'
import { fillEditor } from './fillEditor'

beforeEach(() => {
  document.body.innerHTML = ''
})

describe('fillEditor', () => {
  it('returns structured error when required selectors are missing', () => {
    document.body.innerHTML = '<input data-geo-fill="title">'

    const result = fillEditor(payload(), document, 'https://mp.toutiao.com/editor')

    expect(result).toEqual({
      ok: false,
      errorCode: 'SELECTOR_NOT_FOUND',
      message: 'content selector not found',
    })
  })

  it('fills title, content, cover, tags, and category without publishing', () => {
    document.body.innerHTML = '<input data-geo-fill="title"><div data-geo-fill="content" contenteditable="true"></div><input data-geo-fill="cover"><input data-geo-fill="tags"><input data-geo-fill="category">'

    const result = fillEditor(payload(), document, 'https://mp.toutiao.com/editor')

    expect(result.ok).toBe(true)
    expect(value('title')).toBe('Draft')
    expect(document.querySelector('[data-geo-fill="content"]')?.innerHTML).toBe('<p>Hello</p>')
    expect(value('cover')).toBe('https://cdn/cover.jpg')
    expect(value('tags')).toBe('geo,brand')
    expect(value('category')).toBe('news')
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
