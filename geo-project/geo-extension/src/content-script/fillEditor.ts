import { profileForUrl } from '@/shared/fillProfiles'
import type { FillCommandPayload } from '@/types/extension'

export interface EditorFillResult {
  ok: boolean
  message?: string
  errorCode?: string
}

export function fillEditor(
  payload: FillCommandPayload,
  doc: Document = document,
  pageUrl: string = window.location.href,
): EditorFillResult {
  try {
    const profile = profileForUrl(pageUrl)
    if (!profile || profile.platform !== payload.platform) {
      return { ok: false, errorCode: 'PLATFORM_MISMATCH', message: '编辑器平台不匹配' }
    }
    const title = first(doc, profile.titleSelectors)
    const content = first(doc, profile.contentSelectors)
    if (!title) return { ok: false, errorCode: 'SELECTOR_NOT_FOUND', message: 'title selector not found' }
    if (!content) return { ok: false, errorCode: 'SELECTOR_NOT_FOUND', message: 'content selector not found' }
    setField(title, payload.title)
    setField(content, payload.contentHtml, true)
    if (payload.coverImageUrl) {
      const cover = first(doc, profile.coverSelectors)
      if (!cover) return { ok: false, errorCode: 'SELECTOR_NOT_FOUND', message: 'cover selector not found' }
      setField(cover, payload.coverImageUrl)
    }
    if (payload.tags.length > 0) {
      const tags = first(doc, profile.tagsSelectors)
      if (!tags) return { ok: false, errorCode: 'SELECTOR_NOT_FOUND', message: 'tags selector not found' }
      setField(tags, payload.tags.join(','))
    }
    if (payload.category) {
      const category = first(doc, profile.categorySelectors)
      if (!category) return { ok: false, errorCode: 'SELECTOR_NOT_FOUND', message: 'category selector not found' }
      setField(category, payload.category)
    }
    return { ok: true }
  } catch (error) {
    return { ok: false, errorCode: 'FILL_FAILED', message: error instanceof Error ? error.message : 'fill failed' }
  }
}

function first(doc: Document, selectors: string[]): Element | null {
  for (const selector of selectors) {
    const element = doc.querySelector(selector)
    if (element) return element
  }
  return null
}

function setField(element: Element, value: string, html = false) {
  if (element instanceof HTMLInputElement || element instanceof HTMLTextAreaElement) {
    element.value = value
  } else if (html) {
    element.innerHTML = value
  } else {
    element.textContent = value
  }
  element.dispatchEvent(new Event('input', { bubbles: true }))
  element.dispatchEvent(new Event('change', { bubbles: true }))
}
