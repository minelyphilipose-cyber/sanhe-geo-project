import type { FillCommandPayload } from '@/types/extension'
import { profileForUrl } from './contentProfiles'
import { sanitizeContentHtml } from './htmlSanitizer'

export interface EditorFillResult {
  ok: boolean
  message?: string
  errorCode?: string
}

const REQUIRED_SELECTOR_TIMEOUT_MS = 20_000
const OPTIONAL_SELECTOR_TIMEOUT_MS = 5_000
const SELECTOR_POLL_MS = 250

export async function fillEditor(
  payload: FillCommandPayload,
  doc: Document = document,
  pageUrl: string = window.location.href,
  requiredSelectorTimeoutMs = REQUIRED_SELECTOR_TIMEOUT_MS,
): Promise<EditorFillResult> {
  try {
    const profile = profileForUrl(pageUrl)
    if (!profile || profile.platform !== payload.platform) {
      return { ok: false, errorCode: 'PLATFORM_MISMATCH', message: '编辑器平台不匹配' }
    }
    const title = await waitForFirst(doc, profile.titleSelectors, requiredSelectorTimeoutMs)
    const content = await waitForFirst(doc, profile.contentSelectors, requiredSelectorTimeoutMs)
    if (!title) return { ok: false, errorCode: 'SELECTOR_NOT_FOUND', message: 'title selector not found' }
    if (!content) return { ok: false, errorCode: 'SELECTOR_NOT_FOUND', message: 'content selector not found' }
    setField(title, payload.title)
    setField(content, sanitizeContentHtml(payload.contentHtml), true)
    if (payload.coverImageUrl) {
      const cover = await waitForFirst(doc, profile.coverSelectors, OPTIONAL_SELECTOR_TIMEOUT_MS)
      if (!cover) return { ok: false, errorCode: 'SELECTOR_NOT_FOUND', message: 'cover selector not found' }
      setField(cover, payload.coverImageUrl)
    }
    if (payload.tags.length > 0) {
      const tags = await waitForFirst(doc, profile.tagsSelectors, OPTIONAL_SELECTOR_TIMEOUT_MS)
      if (!tags) return { ok: false, errorCode: 'SELECTOR_NOT_FOUND', message: 'tags selector not found' }
      setField(tags, payload.tags.join(','))
    }
    if (payload.category) {
      const category = await waitForFirst(doc, profile.categorySelectors, OPTIONAL_SELECTOR_TIMEOUT_MS)
      if (!category) return { ok: false, errorCode: 'SELECTOR_NOT_FOUND', message: 'category selector not found' }
      setField(category, payload.category)
    }
    return { ok: true }
  } catch (error) {
    return { ok: false, errorCode: 'FILL_FAILED', message: error instanceof Error ? error.message : 'fill failed' }
  }
}

async function waitForFirst(doc: Document, selectors: string[], timeoutMs: number): Promise<Element | null> {
  const deadline = Date.now() + timeoutMs
  let element = first(doc, selectors)
  while (!element && Date.now() < deadline) {
    await delay(SELECTOR_POLL_MS)
    element = first(doc, selectors)
  }
  return element
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
    setNativeValue(element, value)
  } else if (html) {
    element.innerHTML = value
  } else {
    element.textContent = value
  }
  element.dispatchEvent(new Event('input', { bubbles: true }))
  element.dispatchEvent(new Event('change', { bubbles: true }))
}

function setNativeValue(element: HTMLInputElement | HTMLTextAreaElement, value: string) {
  const prototype = element instanceof HTMLInputElement
    ? HTMLInputElement.prototype
    : HTMLTextAreaElement.prototype
  const descriptor = Object.getOwnPropertyDescriptor(prototype, 'value')
  descriptor?.set?.call(element, value)
}

function delay(ms: number) {
  return new Promise(resolve => setTimeout(resolve, ms))
}
